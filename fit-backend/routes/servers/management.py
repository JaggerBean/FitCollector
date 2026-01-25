"""Server management endpoints for server owners."""

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text
from typing import Optional

from database import engine
from auth import require_api_key, require_master_admin
from fastapi.responses import JSONResponse

router = APIRouter()


class UpdateServerSettingsRequest(BaseModel):
    max_players: Optional[int] = Field(None, ge=1, le=10000, description="Maximum players allowed (NULL = unlimited)")


@router.get("/v1/servers/info")
def get_server_info(server_name: str = Depends(require_api_key)):
    """
    Get your server's information and settings.
    Requires server API key (X-API-Key header).
    """
    try:
        with engine.begin() as conn:
            server = conn.execute(
                text("""
                    SELECT 
                        server_name,
                        max_players,
                        created_at,
                        last_used,
                        active
                    FROM api_keys
                    WHERE server_name = :server_name
                """),
                {"server_name": server_name}
            ).mappings().fetchone()
            
            if not server:
                raise HTTPException(status_code=404, detail="Server not found")
            
            # Get current player count
            player_count = conn.execute(
                text("""
                    SELECT COUNT(DISTINCT minecraft_username) 
                    FROM player_keys 
                    WHERE server_name = :server_name AND active = TRUE
                """),
                {"server_name": server_name}
            ).scalar()
            
            result = dict(server)
            result["current_players"] = player_count
            result["slots_available"] = None if result["max_players"] is None else result["max_players"] - player_count
            
            return result
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get server info: {str(e)}")


@router.patch("/v1/admin/servers/{server_name}/settings")
def admin_update_server_settings(
    server_name: str,
    request: UpdateServerSettingsRequest,
    _: bool = Depends(require_master_admin)
):
    """
    Update a server's settings (master admin only).
    Requires master admin key (X-Admin-Key header).
    
    Settings:
    - max_players: Maximum number of unique players allowed to register (null = unlimited)
    
    Use this to set player limits based on the server owner's payment plan.
    
    Note: Changing max_players to a number lower than current players will not kick existing players,
    but will prevent new registrations until players drop below the limit.
    """
    try:
        with engine.begin() as conn:
            # Verify server exists
            server_exists = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name"),
                {"server_name": server_name}
            ).fetchone()
            
            if not server_exists:
                raise HTTPException(status_code=404, detail=f"Server '{server_name}' not found")
            
            # Get current player count
            current_players = conn.execute(
                text("""
                    SELECT COUNT(DISTINCT minecraft_username) 
                    FROM player_keys 
                    WHERE server_name = :server_name AND active = TRUE
                """),
                {"server_name": server_name}
            ).scalar()
            
            # Update max_players
            if request.max_players is not None:
                conn.execute(
                    text("""
                        UPDATE api_keys
                        SET max_players = :max_players
                        WHERE server_name = :server_name
                    """),
                    {"max_players": request.max_players, "server_name": server_name}
                )
                
                warning = None
                if request.max_players < current_players:
                    warning = f"Warning: You set max_players to {request.max_players}, but server currently has {current_players} registered players. New registrations will be blocked until players are removed."
                
                return {
                    "ok": True,
                    "server_name": server_name,
                    "max_players": request.max_players,
                    "current_players": current_players,
                    "slots_available": request.max_players - current_players,
                    "warning": warning,
                    "message": f"Updated server settings for '{server_name}'"
                }
            else:
                # Set to unlimited
                conn.execute(
                    text("""
                        UPDATE api_keys
                        SET max_players = NULL
                        WHERE server_name = :server_name
                    """),
                    {"server_name": server_name}
                )
                
                return {
                    "ok": True,
                    "server_name": server_name,
                    "max_players": None,
                    "current_players": current_players,
                    "message": f"Set '{server_name}' to unlimited players"
                }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to update server settings: {str(e)}")


@router.get("/v1/servers/players/list")
def list_server_players(
    server_name: str = Depends(require_api_key),
    limit: int = 100,
    offset: int = 0,
    q: str | None = None
):
    """
    List all registered players on your server.
    Requires server API key (X-API-Key header).
    
    Returns paginated list of players with their registration info.
    """
    try:
        with engine.begin() as conn:
            if q:
                players = conn.execute(
                    text("""
                        SELECT 
                            minecraft_username,
                            device_id,
                            created_at,
                            last_used,
                            active
                        FROM player_keys
                        WHERE server_name = :server_name
                          AND minecraft_username ILIKE :q
                        ORDER BY created_at DESC
                        LIMIT :limit OFFSET :offset
                    """),
                    {"server_name": server_name, "limit": limit, "offset": offset, "q": f"%{q}%"}
                ).mappings().all()

                total = conn.execute(
                    text("""
                        SELECT COUNT(*) 
                        FROM player_keys 
                        WHERE server_name = :server_name
                          AND minecraft_username ILIKE :q
                    """),
                    {"server_name": server_name, "q": f"%{q}%"}
                ).scalar()
            else:
                players = conn.execute(
                    text("""
                        SELECT 
                            minecraft_username,
                            device_id,
                            created_at,
                            last_used,
                            active
                        FROM player_keys
                        WHERE server_name = :server_name
                        ORDER BY created_at DESC
                        LIMIT :limit OFFSET :offset
                    """),
                    {"server_name": server_name, "limit": limit, "offset": offset}
                ).mappings().all()

                total = conn.execute(
                    text("""
                        SELECT COUNT(*) 
                        FROM player_keys 
                        WHERE server_name = :server_name
                    """),
                    {"server_name": server_name}
                ).scalar()
            
            return {
                "server_name": server_name,
                "total_players": total,
                "players": [dict(p) for p in players],
                "limit": limit,
                "offset": offset
            }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to list players: {str(e)}")


@router.delete("/v1/servers/players/{minecraft_username}")
def server_wipe_player(
    minecraft_username: str,
    server_name: str = Depends(require_api_key),
):
    """
    Wipe all data for a specific player on your server only.
    Server owners can use this to reset a player's data and API key.
    Requires server API key (X-API-Key header).
    
    This deletes:
    - Player's API key registration for this server
    - All step data for this player on this server
    - Any bans for this player on this server
    
    After wiping, the player will need to re-register to submit data again.
    """
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    try:
        with engine.begin() as conn:
            # Delete player's API key for this server
            key_result = conn.execute(
                text("""
                    DELETE FROM player_keys 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
            
            # Delete player's step data for this server
            step_result = conn.execute(
                text("""
                    DELETE FROM step_ingest 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
            
            # Delete any bans for this player on this server
            ban_result = conn.execute(
                text("""
                    DELETE FROM bans 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
        
        return {
            "ok": True,
            "action": "server_wiped_player",
            "minecraft_username": minecraft_username,
            "server_name": server_name,
            "player_keys_deleted": key_result.rowcount,
            "step_records_deleted": step_result.rowcount,
            "bans_deleted": ban_result.rowcount,
            "message": f"Wiped all data for '{minecraft_username}' on server '{server_name}'. Player will need to re-register."
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to wipe player data: {str(e)}")