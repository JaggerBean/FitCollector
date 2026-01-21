"""Server player management endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from zoneinfo import ZoneInfo
from datetime import datetime, timedelta
from database import engine
from auth import require_api_key

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()

# Server endpoint: check and set claim status for yesterday
@router.get("/v1/servers/players/{minecraft_username}/claim-status")
def get_claim_status_server(minecraft_username: str, server_name: str = Depends(require_api_key)):
    """
    Check if the player has claimed their reward for yesterday (server/mod use).
    """
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT claimed, claimed_at FROM step_claims
                WHERE minecraft_username = :username AND server_name = :server AND day = :yesterday
                LIMIT 1
            """),
            {"username": minecraft_username, "server": server_name, "yesterday": yesterday}
        ).fetchone()
    if row:
        return {"claimed": row[0], "claimed_at": row[1]}
    else:
        return {"claimed": False, "claimed_at": None}

@router.post("/v1/servers/players/{minecraft_username}/claim-reward")
def claim_reward_server(minecraft_username: str, server_name: str = Depends(require_api_key)):
    """
    Mark the player's reward as claimed for yesterday (server/mod use).
    """
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    with engine.begin() as conn:
        conn.execute(
            text("""
                INSERT INTO step_claims (minecraft_username, server_name, day, claimed, claimed_at)
                VALUES (:username, :server, :yesterday, TRUE, NOW())
                ON CONFLICT (minecraft_username, server_name, day)
                DO UPDATE SET claimed = TRUE, claimed_at = NOW()
            """),
            {"username": minecraft_username, "server": server_name, "yesterday": yesterday}
        )
    return {"claimed": True, "claimed_at": datetime.now().isoformat()}

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


@router.get("/v1/servers/players/{minecraft_username}/yesterday-steps")
def get_yesterday_steps_server(
    minecraft_username: str,
    server_name: str = Depends(require_api_key),
):
    """
    Get yesterday's step count for a player on this server.
    Requires server API key.
    """
    from datetime import datetime, timedelta
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT steps_today FROM step_ingest
                WHERE minecraft_username = :username AND server_name = :server AND day = :yesterday
                LIMIT 1
            """),
            {"username": minecraft_username, "server": server_name, "yesterday": yesterday}
        ).fetchone()
    if row:
        return {"minecraft_username": minecraft_username, "server_name": server_name, "day": str(yesterday), "steps_yesterday": row[0]}
    else:
        return {"minecraft_username": minecraft_username, "server_name": server_name, "day": str(yesterday), "steps_yesterday": 0}

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
