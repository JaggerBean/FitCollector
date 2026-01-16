"""Admin monitoring endpoints."""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import text
from zoneinfo import ZoneInfo
from database import engine
from auth import require_api_key, require_master_admin

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


class BanRequest(BaseModel):
    minecraft_username: str | None = Field(None, min_length=3, max_length=16)
    device_id: str | None = Field(None, min_length=6, max_length=128)
    reason: str | None = Field(None, max_length=500)


@router.get("/v1/latest/{device_id}")
def latest(device_id: str, server_name: str = Depends(require_api_key)):
    """Get latest submission for a device (server-authenticated)."""
    # server_name is validated by require_api_key dependency

    with engine.begin() as conn:
        row = conn.execute(
            text("""
            SELECT
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                created_at
            FROM step_ingest
            WHERE device_id = :device_id
            ORDER BY created_at DESC
            LIMIT 1
            """),
            {"device_id": device_id},
        ).mappings().first()

    if not row:
        return {"error": "No data for device_id"}

    d = dict(row)
    if d.get("created_at"):
        d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
    return d


@router.get("/v1/admin/all")
def admin_all(
    limit: int = 1000,
    _: bool = Depends(require_master_admin),
):
    """
    Master admin monitoring across all servers.
    Returns ALL data from ALL servers grouped by server.
    Requires master admin key (X-Admin-Key header).
    """

    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                id,
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                server_name,
                created_at
            FROM step_ingest
            ORDER BY server_name, created_at DESC
            LIMIT :limit
            """),
            {"limit": limit},
        ).mappings().all()

    # Group by server_name
    grouped: dict[str, list] = {}
    for r in rows:
        d = dict(r)
        if d.get("created_at"):
            d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
        
        srv = d["server_name"]
        if srv not in grouped:
            grouped[srv] = []
        grouped[srv].append(d)

    return grouped


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


@router.post("/v1/admin/servers/{server_name}/players/{minecraft_username}/ban")
def admin_ban_player(
    server_name: str,
    minecraft_username: str,
    request: BanRequest,
    _: bool = Depends(require_master_admin),
):
    """
    Ban a player from a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Parameters:
    - server_name: Server to ban from
    - minecraft_username: Username to ban
    - device_id (optional): Device ID to also ban
    - reason (optional): Reason for the ban
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            banned_items = []
            
            # Ban by username
            if minecraft_username:
                existing = conn.execute(
                    text("""
                        SELECT id FROM bans
                        WHERE server_name = :server_name
                        AND minecraft_username = :minecraft_username
                        AND device_id IS NULL
                    """),
                    {"server_name": server_name, "minecraft_username": minecraft_username}
                ).fetchone()
                
                if not existing:
                    conn.execute(
                        text("""
                            INSERT INTO bans (server_name, minecraft_username, reason)
                            VALUES (:server_name, :minecraft_username, :reason)
                        """),
                        {
                            "server_name": server_name,
                            "minecraft_username": minecraft_username,
                            "reason": request.reason
                        }
                    )
                    banned_items.append(f"username '{minecraft_username}'")
                else:
                    banned_items.append(f"username '{minecraft_username}' (already banned)")
            
            # Ban by device_id if provided
            if request.device_id:
                existing = conn.execute(
                    text("""
                        SELECT id FROM bans
                        WHERE server_name = :server_name
                        AND device_id = :device_id
                        AND minecraft_username IS NULL
                    """),
                    {"server_name": server_name, "device_id": request.device_id}
                ).fetchone()
                
                if not existing:
                    conn.execute(
                        text("""
                            INSERT INTO bans (server_name, device_id, reason)
                            VALUES (:server_name, :device_id, :reason)
                        """),
                        {
                            "server_name": server_name,
                            "device_id": request.device_id,
                            "reason": request.reason
                        }
                    )
                    banned_items.append(f"device '{request.device_id}'")
                else:
                    banned_items.append(f"device '{request.device_id}' (already banned)")
        
        return {
            "ok": True,
            "action": "admin_banned",
            "server_name": server_name,
            "banned_items": banned_items,
            "reason": request.reason,
            "message": f"Banned {', '.join(banned_items)} from server '{server_name}'"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to ban player: {str(e)}")


@router.delete("/v1/admin/servers/{server_name}/players/{minecraft_username}/ban")
def admin_unban_player(
    server_name: str,
    minecraft_username: str,
    device_id: str | None = None,
    _: bool = Depends(require_master_admin),
):
    """
    Unban a player from a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Parameters:
    - server_name: Server to unban from
    - minecraft_username: Username to unban
    - device_id (optional): Device ID to also unban
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            unbanned_items = []
            
            # Unban by username
            result = conn.execute(
                text("""
                    DELETE FROM bans
                    WHERE server_name = :server_name
                    AND minecraft_username = :minecraft_username
                    AND device_id IS NULL
                """),
                {"server_name": server_name, "minecraft_username": minecraft_username}
            )
            if result.rowcount > 0:
                unbanned_items.append(f"username '{minecraft_username}'")
            
            # Unban by device_id if provided
            if device_id:
                result = conn.execute(
                    text("""
                        DELETE FROM bans
                        WHERE server_name = :server_name
                        AND device_id = :device_id
                        AND minecraft_username IS NULL
                    """),
                    {"server_name": server_name, "device_id": device_id}
                )
                if result.rowcount > 0:
                    unbanned_items.append(f"device '{device_id}'")
            
            if not unbanned_items:
                raise HTTPException(
                    status_code=404,
                    detail=f"No bans found for '{minecraft_username}' on server '{server_name}'"
                )
        
        return {
            "ok": True,
            "action": "admin_unbanned",
            "server_name": server_name,
            "unbanned_items": unbanned_items,
            "message": f"Unbanned {', '.join(unbanned_items)} from server '{server_name}'"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to unban player: {str(e)}")


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
