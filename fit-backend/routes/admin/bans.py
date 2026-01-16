"""Admin ban management endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text
from zoneinfo import ZoneInfo
import secrets

from database import engine
from auth import require_master_admin

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


class BanRequest(BaseModel):
    reason: str = Field(default="broke code of conduct", max_length=500)


@router.get("/v1/admin/bans")
def admin_get_all_bans(
    limit: int = 1000,
    _: bool = Depends(require_master_admin),
):
    """
    Get all bans across all servers.
    Master admin only (requires X-Admin-Key header).
    
    Returns all bans grouped by server.
    """
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                server_name,
                ban_group_id,
                minecraft_username,
                device_id,
                reason,
                banned_at
            FROM bans
            ORDER BY server_name, banned_at DESC
            LIMIT :limit
            """),
            {"limit": limit},
        ).mappings().all()

    # Group by server, then by ban_group_id
    grouped_by_server: dict[str, dict] = {}
    for r in rows:
        d = dict(r)
        if d.get("banned_at"):
            d["banned_at"] = d["banned_at"].astimezone(CENTRAL_TZ).isoformat()
        
        server = d["server_name"]
        group = d["ban_group_id"]
        
        if server not in grouped_by_server:
            grouped_by_server[server] = {}
        
        if group not in grouped_by_server[server]:
            grouped_by_server[server][group] = {
                "ban_group_id": group,
                "username": None,
                "devices": [],
                "reason": d["reason"],
                "banned_at": d["banned_at"]
            }
        
        if d["minecraft_username"]:
            grouped_by_server[server][group]["username"] = d["minecraft_username"]
        if d["device_id"]:
            grouped_by_server[server][group]["devices"].append(d["device_id"])

    # Convert to list format for each server
    result = {}
    for server, bans_dict in grouped_by_server.items():
        result[server] = {
            "total_bans": len(bans_dict),
            "bans": list(bans_dict.values())
        }

    return result


@router.get("/v1/admin/servers/{server_name}/bans")
def admin_get_server_bans(
    server_name: str,
    limit: int = 1000,
    _: bool = Depends(require_master_admin),
):
    """
    Get all bans for a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Parameters:
    - server_name: Server to view bans for
    """
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                ban_group_id,
                minecraft_username,
                device_id,
                reason,
                banned_at
            FROM bans
            WHERE server_name = :server_name
            ORDER BY banned_at DESC
            LIMIT :limit
            """),
            {"server_name": server_name, "limit": limit},
        ).mappings().all()

    # Group by ban_group_id
    grouped: dict[str, dict] = {}
    for r in rows:
        d = dict(r)
        if d.get("banned_at"):
            d["banned_at"] = d["banned_at"].astimezone(CENTRAL_TZ).isoformat()
        
        group = d["ban_group_id"]
        if group not in grouped:
            grouped[group] = {
                "ban_group_id": group,
                "username": None,
                "devices": [],
                "reason": d["reason"],
                "banned_at": d["banned_at"]
            }
        
        if d["minecraft_username"]:
            grouped[group]["username"] = d["minecraft_username"]
        if d["device_id"]:
            grouped[group]["devices"].append(d["device_id"])

    return {
        "server_name": server_name,
        "total_bans": len(grouped),
        "bans": list(grouped.values())
    }


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
    - reason: Reason for the ban (defaults to "broke code of conduct")
    
    Automatically bans both the username and all associated devices.
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            banned_items = []
            ban_group_id = secrets.token_urlsafe(16)
            
            # First, find all device_ids associated with this username
            devices = conn.execute(
                text("""
                    SELECT DISTINCT device_id FROM player_keys
                    WHERE minecraft_username = :minecraft_username
                    AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            ).fetchall()
            
            device_ids = [d[0] for d in devices]
            
            # Ban the username
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
                        INSERT INTO bans (ban_group_id, server_name, minecraft_username, reason)
                        VALUES (:ban_group_id, :server_name, :minecraft_username, :reason)
                    """),
                    {
                        "ban_group_id": ban_group_id,
                        "server_name": server_name,
                        "minecraft_username": minecraft_username,
                        "reason": request.reason
                    }
                )
                banned_items.append(f"username '{minecraft_username}'")
            else:
                banned_items.append(f"username '{minecraft_username}' (already banned)")
            
            # Ban all associated devices
            for device_id in device_ids:
                existing = conn.execute(
                    text("""
                        SELECT id FROM bans
                        WHERE server_name = :server_name
                        AND device_id = :device_id
                        AND minecraft_username IS NULL
                    """),
                    {"server_name": server_name, "device_id": device_id}
                ).fetchone()
                
                if not existing:
                    conn.execute(
                        text("""
                            INSERT INTO bans (ban_group_id, server_name, device_id, reason)
                            VALUES (:ban_group_id, :server_name, :device_id, :reason)
                        """),
                        {
                            "ban_group_id": ban_group_id,
                            "server_name": server_name,
                            "device_id": device_id,
                            "reason": request.reason
                        }
                    )
                    banned_items.append(f"device '{device_id}'")
        
        return {
            "ok": True,
            "action": "admin_banned",
            "server_name": server_name,
            "minecraft_username": minecraft_username,
            "banned_devices": device_ids,
            "reason": request.reason,
            "message": f"Banned '{minecraft_username}' and {len(device_ids)} device(s) from server '{server_name}'"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to ban player: {str(e)}")


@router.delete("/v1/admin/servers/{server_name}/players/{minecraft_username}/ban")
def admin_unban_player(
    server_name: str,
    minecraft_username: str,
    _: bool = Depends(require_master_admin),
):
    """
    Unban a player from a specific server.
    Master admin only (requires X-Admin-Key header).
    
    Automatically unbans the username and all associated devices.
    
    Parameters:
    - server_name: Server to unban from
    - minecraft_username: Username to unban
    """
    
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    if not server_name or len(server_name.strip()) == 0:
        raise HTTPException(status_code=400, detail="server_name cannot be empty")
    
    try:
        with engine.begin() as conn:
            # Find the username ban to get the ban_group_id
            username_ban = conn.execute(
                text("""
                    SELECT ban_group_id FROM bans
                    WHERE server_name = :server_name
                    AND minecraft_username = :minecraft_username
                    AND device_id IS NULL
                """),
                {"server_name": server_name, "minecraft_username": minecraft_username}
            ).fetchone()
            
            if not username_ban:
                raise HTTPException(
                    status_code=404,
                    detail=f"No bans found for '{minecraft_username}' on server '{server_name}'"
                )
            
            ban_group_id = username_ban[0]
            
            # Find all device bans with this ban_group_id
            device_bans = conn.execute(
                text("""
                    SELECT device_id FROM bans
                    WHERE ban_group_id = :ban_group_id
                    AND device_id IS NOT NULL
                """),
                {"ban_group_id": ban_group_id}
            ).fetchall()
            
            device_ids = [d[0] for d in device_bans]
            
            # Delete all bans with this ban_group_id
            result = conn.execute(
                text("""
                    DELETE FROM bans
                    WHERE ban_group_id = :ban_group_id
                """),
                {"ban_group_id": ban_group_id}
            )
        
        return {
            "ok": True,
            "action": "admin_unbanned",
            "server_name": server_name,
            "minecraft_username": minecraft_username,
            "associated_devices_unbanned": device_ids,
            "bans_removed": result.rowcount,
            "message": f"Unbanned '{minecraft_username}' and {len(device_ids)} associated device(s) from server '{server_name}'"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to unban player: {str(e)}")
