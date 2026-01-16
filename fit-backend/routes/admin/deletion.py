"""Admin data deletion endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text

from database import engine
from auth import require_master_admin

router = APIRouter()


@router.delete("/v1/admin/servers/{server_name}/players/{minecraft_username}")
def admin_delete_player(
    server_name: str,
    minecraft_username: str,
    _: bool = Depends(require_master_admin),
):
    """
    Delete a specific player's data from a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Parameters:
    - server_name: Server to delete from
    - minecraft_username: Player username to delete
    
    Returns deletion summary with number of records removed.
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            # Check if player exists on this server
            exists = conn.execute(
                text("""
                    SELECT COUNT(*) as count FROM step_ingest
                    WHERE minecraft_username = :minecraft_username
                    AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            ).fetchone()
            
            if exists[0] == 0:
                raise HTTPException(
                    status_code=404,
                    detail=f"Player '{minecraft_username}' not found on server '{server_name}'"
                )
            
            # Delete player data from this server
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
                "action": "admin_deleted_player",
                "server_name": server_name,
                "minecraft_username": minecraft_username,
                "rows_deleted": rows_deleted,
                "message": f"Deleted {rows_deleted} record(s) for '{minecraft_username}' on server '{server_name}'"
            }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete player: {str(e)}")


@router.delete("/v1/admin/servers/{server_name}/players")
def admin_delete_all_server_players(
    server_name: str,
    _: bool = Depends(require_master_admin),
):
    """
    Delete ALL player data from a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Parameters:
    - server_name: Server to clear all player data from
    
    Returns deletion summary with number of records removed.
    """
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            # Check if server has any data
            exists = conn.execute(
                text("""
                    SELECT COUNT(*) as count FROM step_ingest
                    WHERE server_name = :server_name
                """),
                {"server_name": server_name}
            ).fetchone()
            
            if exists[0] == 0:
                raise HTTPException(
                    status_code=404,
                    detail=f"No player data found on server '{server_name}'"
                )
            
            # Delete all player data from this server
            result = conn.execute(
                text("""
                    DELETE FROM step_ingest
                    WHERE server_name = :server_name
                """),
                {"server_name": server_name}
            )
            rows_deleted = result.rowcount
            
            return {
                "ok": True,
                "action": "admin_deleted_all_server_players",
                "server_name": server_name,
                "rows_deleted": rows_deleted,
                "message": f"Deleted {rows_deleted} record(s) for all players on server '{server_name}'"
            }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete server players: {str(e)}")


@router.delete("/v1/admin/all-data")
def admin_delete_all_data(
    confirm: str = None,
    _: bool = Depends(require_master_admin),
):
    """
    Delete ALL stored data in the system (master admin only).
    This is a destructive operation that cannot be undone.
    
    Requires X-Admin-Key header and confirm=yes query parameter to prevent accidents.
    
    Deletes:
    - All step submissions from all servers
    - All player keys/tokens
    - All server API keys
    
    Keeps:
    - Ban records (for enforcement)
    """
    
    if confirm != "yes":
        raise HTTPException(
            status_code=400,
            detail="This operation deletes ALL data. Confirm by passing ?confirm=yes"
        )
    
    try:
        with engine.begin() as conn:
            # Delete all step data
            step_result = conn.execute(
                text("DELETE FROM step_ingest")
            )
            steps_deleted = step_result.rowcount
            
            # Delete all player keys
            keys_result = conn.execute(
                text("DELETE FROM player_keys")
            )
            keys_deleted = keys_result.rowcount
            
            # Delete all server API keys
            api_result = conn.execute(
                text("DELETE FROM api_keys")
            )
            api_deleted = api_result.rowcount
        
        return {
            "ok": True,
            "action": "admin_deleted_all_data",
            "warning": "ALL DATA HAS BEEN PERMANENTLY DELETED",
            "records_deleted": {
                "step_submissions": steps_deleted,
                "player_tokens": keys_deleted,
                "server_api_keys": api_deleted
            },
            "message": f"Deleted {steps_deleted} step records, {keys_deleted} player tokens, and {api_deleted} server keys"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete all data: {str(e)}")
