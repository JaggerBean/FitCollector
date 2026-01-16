"""Server player management endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from zoneinfo import ZoneInfo

from database import engine
from auth import require_api_key

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


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
                # Check if player exists at all
                exists = conn.execute(
                    text("""
                        SELECT COUNT(*) as count FROM step_ingest
                        WHERE minecraft_username = :minecraft_username
                    """),
                    {"minecraft_username": minecraft_username}
                ).fetchone()
                
                if exists[0] == 0:
                    raise HTTPException(
                        status_code=404,
                        detail=f"Player '{minecraft_username}' not found in any server"
                    )
                
                # Delete all data for this player across all servers
                result = conn.execute(
                    text("""
                        DELETE FROM step_ingest
                        WHERE minecraft_username = :minecraft_username
                    """),
                    {"minecraft_username": minecraft_username}
                )
                rows_deleted = result.rowcount
                
                return {
                    "ok": True,
                    "action": "deleted_all",
                    "minecraft_username": minecraft_username,
                    "rows_deleted": rows_deleted,
                    "message": f"All data for '{minecraft_username}' deleted across all servers"
                }
            else:
                # Check if player exists on this specific server
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
