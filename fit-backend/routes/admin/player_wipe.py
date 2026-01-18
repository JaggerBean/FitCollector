"""Admin endpoints to fully remove player data, including API keys and all related records."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from database import engine
from auth import require_master_admin, require_api_key
from typing import Optional

router = APIRouter()

@router.delete("/v1/admin/players/{minecraft_username}")
def admin_delete_player_everywhere(
    minecraft_username: str,
    _: bool = Depends(require_master_admin),
):
    """
    Delete ALL data for a specific player across all servers (step data, player keys).
    Master admin only (requires X-Admin-Key header).
    """
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    try:
        with engine.begin() as conn:
            # Delete from step_ingest
            step_result = conn.execute(
                text("""
                    DELETE FROM step_ingest WHERE minecraft_username = :minecraft_username
                """),
                {"minecraft_username": minecraft_username}
            )
            # Delete from player_keys
            key_result = conn.execute(
                text("""
                    DELETE FROM player_keys WHERE minecraft_username = :minecraft_username
                """),
                {"minecraft_username": minecraft_username}
            )
        return {
            "ok": True,
            "action": "admin_deleted_player_everywhere",
            "minecraft_username": minecraft_username,
            "step_records_deleted": step_result.rowcount,
            "player_keys_deleted": key_result.rowcount,
            "message": f"Deleted all data for '{minecraft_username}' across all servers."
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete player everywhere: {str(e)}")

@router.delete("/v1/admin/players")
def admin_delete_all_players(
    confirm: Optional[str] = None,
    _: bool = Depends(require_master_admin),
):
    """
    Delete ALL player data (step data and player keys) for ALL players across all servers.
    Requires confirm=yes query parameter.
    Master admin only (requires X-Admin-Key header).
    """
    if confirm != "yes":
        raise HTTPException(status_code=400, detail="This operation deletes ALL player data. Confirm by passing ?confirm=yes")
    try:
        with engine.begin() as conn:
            step_result = conn.execute(text("DELETE FROM step_ingest"))
            key_result = conn.execute(text("DELETE FROM player_keys"))
        return {
            "ok": True,
            "action": "admin_deleted_all_players",
            "step_records_deleted": step_result.rowcount,
            "player_keys_deleted": key_result.rowcount,
            "message": f"Deleted all player data and keys from the system."
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete all players: {str(e)}")


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
