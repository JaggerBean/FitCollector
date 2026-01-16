"""Server registration and management endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from zoneinfo import ZoneInfo
from database import engine
from models import ServerRegistrationRequest, ApiKeyResponse
from utils import generate_opaque_token, hash_token
from auth import require_api_key

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


@router.post("/v1/servers/register")
def register_server(request: ServerRegistrationRequest) -> ApiKeyResponse:
    """
    Register a new Minecraft server and get an API key (opaque token).
    
    This endpoint is public (no authentication required) to allow new server owners to register.
    The API key returned should be stored securely by the server owner.
    Can only access data from this server.
    """
    
    # Generate opaque token and hash it
    plaintext_key = generate_opaque_token()
    key_hash = hash_token(plaintext_key)
    
    try:
        with engine.begin() as conn:
            # Check if server name already exists
            existing = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name AND active = TRUE"),
                {"server_name": request.server_name}
            ).fetchone()
            
            if existing:
                raise HTTPException(status_code=409, detail=f"Server name '{request.server_name}' already registered")
            
            # Insert new API key (hashed)
            conn.execute(
                text("""
                    INSERT INTO api_keys (key, server_name, active)
                    VALUES (:key_hash, :server_name, :active)
                """),
                {"key_hash": key_hash, "server_name": request.server_name, "active": True}
            )
            
            # TODO: Store additional server metadata (owner_name, owner_email, server_address, etc.)
            # For now, we're just using server_name in the api_keys table
            # You might want to create a separate 'servers' table for this metadata
            
            # TODO: Send email to owner_email with the API key
            # Example: send_email(request.owner_email, plaintext_key, request.server_name)
        
        # Return the plaintext key ONLY on creation (never again)
        return ApiKeyResponse(
            api_key=plaintext_key,
            server_name=request.server_name,
            message="Store this key securely in your server config. You won't be able to see it again!"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to register server: {str(e)}")


@router.get("/v1/servers/info")
def get_server_info(server_name: str = Depends(require_api_key)):
    """Get info about the authenticated server."""
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT server_name, active, created_at, last_used
                FROM api_keys
                WHERE server_name = :server_name
                LIMIT 1
            """),
            {"server_name": server_name}
        ).mappings().first()
    
    if not row:
        raise HTTPException(status_code=404, detail="Server not found")
    
    d = dict(row)
    if d.get("created_at"):
        d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
    if d.get("last_used"):
        d["last_used"] = d["last_used"].astimezone(CENTRAL_TZ).isoformat()
    
    return d


@router.get("/v1/servers/players")
def get_server_players(
    limit: int = 1000,
    server_name: str = Depends(require_api_key),
):
    """
    Get all player data for this server.
    Requires server API key. Returns all step submissions scoped to this server.
    """
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                created_at
            FROM step_ingest
            WHERE server_name = :server_name
            ORDER BY minecraft_username, day DESC
            LIMIT :limit
            """),
            {"server_name": server_name, "limit": limit},
        ).mappings().all()

    # Convert timestamps to ISO format in server timezone
    out: list[dict] = []
    for r in rows:
        d = dict(r)
        if d.get("created_at"):
            d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
        out.append(d)

    return {
        "server_name": server_name,
        "player_count": len(set(row["minecraft_username"] for row in out)),
        "total_records": len(out),
        "data": out
    }


@router.delete("/v1/servers/players/{minecraft_username}")
def delete_player(
    minecraft_username: str,
    all: bool = False,
    server_name: str = Depends(require_api_key),
):
    """
    Delete a player's data.
    
    Requires server API key.
    
    Parameters:
    - minecraft_username: Player username to delete
    - all: If true, delete ALL data for this player across all servers.
           If false, delete only data for this specific server.
    
    Returns deletion summary with number of records removed.
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    try:
        with engine.begin() as conn:
            if all:
                # Delete all data for this player across all servers
                result = conn.execute(
                    text("""
                        DELETE FROM step_ingest
                        WHERE minecraft_username = :minecraft_username
                    """),
                    {"minecraft_username": minecraft_username}
                )
                rows_deleted = result.rowcount
                
                # Also disable their player keys (don't delete, just disable)
                conn.execute(
                    text("""
                        UPDATE player_keys
                        SET active = FALSE
                        WHERE minecraft_username = :minecraft_username
                    """),
                    {"minecraft_username": minecraft_username}
                )
                
                return {
                    "ok": True,
                    "action": "deleted_all",
                    "minecraft_username": minecraft_username,
                    "rows_deleted": rows_deleted,
                    "message": f"All data for '{minecraft_username}' deleted across all servers"
                }
            else:
                # Delete only for this specific server
                result = conn.execute(
                    text("""
                        DELETE FROM step_ingest
                        WHERE minecraft_username = :minecraft_username
                        AND server_name = :server_name
                    """),
                    {"minecraft_username": minecraft_username, "server_name": server_name}
                )
                rows_deleted = result.rowcount
                
                return {
                    "ok": True,
                    "action": "deleted_server",
                    "minecraft_username": minecraft_username,
                    "server_name": server_name,
                    "rows_deleted": rows_deleted,
                    "message": f"All data for '{minecraft_username}' on server '{server_name}' deleted"
                }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete player: {str(e)}")
