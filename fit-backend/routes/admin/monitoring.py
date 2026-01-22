"""Admin monitoring endpoints."""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text
from zoneinfo import ZoneInfo

from database import engine
from auth import require_api_key, require_master_admin

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


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


@router.get("/v1/admin/players/{minecraft_username}/yesterday-steps")
def admin_get_yesterday_steps(
    minecraft_username: str,
    server_name: str,
    device_id: str = None,
    _: bool = Depends(require_master_admin)
):
    """
    Admin: Get yesterday's step count for a player on a server (from player_keys.steps_yesterday).
    Requires master admin key.
    """
    with engine.begin() as conn:
        if device_id:
            row = conn.execute(
                text("""
                    SELECT steps_yesterday FROM player_keys
                    WHERE minecraft_username = :username AND server_name = :server AND device_id = :device_id
                    LIMIT 1
                """),
                {"username": minecraft_username, "server": server_name, "device_id": device_id}
            ).fetchone()
        else:
            row = conn.execute(
                text("""
                    SELECT steps_yesterday FROM player_keys
                    WHERE minecraft_username = :username AND server_name = :server
                    LIMIT 1
                """),
                {"username": minecraft_username, "server": server_name}
            ).fetchone()
    if row:
        return {"minecraft_username": minecraft_username, "server_name": server_name, "steps_yesterday": row[0]}
    else:
        raise HTTPException(404, f"No steps_yesterday found for {minecraft_username}.")