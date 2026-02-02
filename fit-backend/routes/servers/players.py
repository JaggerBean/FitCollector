"""Server player management endpoints."""

from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy import text
from zoneinfo import ZoneInfo
from datetime import datetime, timedelta, timezone
from database import engine
from auth import require_server_access

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()

DEFAULT_REWARDS = [
    {"min_steps": 1000, "label": "Starter", "item_id": "minecraft:bread", "rewards": ["give {player} minecraft:bread 3"]},
    {"min_steps": 5000, "label": "Walker", "item_id": "minecraft:iron_ingot", "rewards": ["give {player} minecraft:iron_ingot 3"]},
    {"min_steps": 10000, "label": "Legend", "item_id": "minecraft:diamond", "rewards": ["give {player} minecraft:diamond 1"]},
]

# Server endpoint: check claim status for a day (defaults to today)
@router.get("/v1/servers/players/{minecraft_username}/claim-status")
def get_claim_status_server(
    minecraft_username: str,
    day: str | None = Query(default=None),
    min_steps: int | None = Query(default=None),
    server_name: str = Depends(require_server_access),
):
    """
    Check if the player has claimed their reward for a specific day.
    Defaults to today if day is not provided.
    """
    if min_steps is None:
        raise HTTPException(status_code=400, detail="min_steps is required")
    if min_steps < 0:
        raise HTTPException(status_code=400, detail="min_steps must be >= 0")

    target_day = _resolve_claim_day(day, server_name)
    resolved_username = _resolve_username(minecraft_username, server_name)
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT claimed, claimed_at FROM step_claims
                WHERE minecraft_username = :username AND server_name = :server AND day = :day AND min_steps = :min_steps
                LIMIT 1
            """),
            {
                "username": resolved_username,
                "server": server_name,
                "day": target_day,
                "min_steps": min_steps,
            }
        ).fetchone()
    if row:
        return {"claimed": row[0], "claimed_at": row[1], "day": str(target_day), "min_steps": min_steps}
    else:
        return {"claimed": False, "claimed_at": None, "day": str(target_day), "min_steps": min_steps}

@router.post("/v1/servers/players/{minecraft_username}/claim-reward")
def claim_reward_server(
    minecraft_username: str,
    day: str | None = Query(default=None),
    min_steps: int | None = Query(default=None),
    server_name: str = Depends(require_server_access),
):
    """
    Mark the player's reward as claimed for a specific day.
    Defaults to today if day is not provided.
    """
    if min_steps is None:
        raise HTTPException(status_code=400, detail="min_steps is required")
    if min_steps < 0:
        raise HTTPException(status_code=400, detail="min_steps must be >= 0")

    target_day = _resolve_claim_day(day, server_name)
    resolved_username = _resolve_username(minecraft_username, server_name)
    now = datetime.now(timezone.utc)
    with engine.begin() as conn:
        existing = conn.execute(
            text("""
                SELECT claimed, claimed_at FROM step_claims
                WHERE minecraft_username = :username AND server_name = :server AND day = :day AND min_steps = :min_steps
                LIMIT 1
            """),
            {
                "username": resolved_username,
                "server": server_name,
                "day": target_day,
                "min_steps": min_steps,
            }
        ).fetchone()

        if existing and existing[0]:
            return {
                "claimed": True,
                "claimed_at": existing[1],
                "already_claimed": True,
                "day": str(target_day),
                "min_steps": min_steps,
            }

        conn.execute(
            text("""
                INSERT INTO step_claims (minecraft_username, server_name, day, min_steps, claimed, claimed_at)
                VALUES (:username, :server, :day, :min_steps, TRUE, :claimed_at)
                ON CONFLICT (minecraft_username, server_name, day, min_steps)
                DO UPDATE SET claimed = TRUE, claimed_at = :claimed_at
            """),
            {
                "username": resolved_username,
                "server": server_name,
                "day": target_day,
                "min_steps": min_steps,
                "claimed_at": now,
            }
        )
    return {"claimed": True, "claimed_at": now.isoformat(), "day": str(target_day), "min_steps": min_steps}

@router.get("/v1/servers/players")
def get_server_players(
    limit: int = 1000,
    server_name: str = Depends(require_server_access),
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
    day: str | None = Query(default=None),
    server_name: str = Depends(require_server_access),
):
    """
    Get yesterday's step count for a player on this server.
    If day is provided, uses that day instead.
    Requires server API key.
    """
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    target_day = _resolve_claim_day(day, server_name) if day else yesterday
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT steps_today FROM step_ingest
                WHERE minecraft_username = :username AND server_name = :server AND day = :day
                LIMIT 1
            """),
            {"username": minecraft_username, "server": server_name, "day": target_day}
        ).fetchone()
    if row:
        steps_value = row[0]
        # Keep legacy key for existing clients, but prefer steps_today for new callers.
        return {
            "minecraft_username": minecraft_username,
            "server_name": server_name,
            "day": str(target_day),
            "steps_today": steps_value,
            "steps_yesterday": steps_value,
        }
    else:
        raise HTTPException(status_code=404, detail=f"No step record found for {minecraft_username} on {str(target_day)}.")


@router.get("/v1/servers/players/{minecraft_username}/day-steps")
def get_day_steps_server(
    minecraft_username: str,
    day: str = Query(...),
    server_name: str = Depends(require_server_access),
):
    """
    Get step count for a player on a specific day (within claim window).
    Requires server API key.
    """
    target_day = _resolve_claim_day(day, server_name)
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT steps_today FROM step_ingest
                WHERE minecraft_username = :username AND server_name = :server AND day = :day
                LIMIT 1
            """),
            {"username": minecraft_username, "server": server_name, "day": target_day}
        ).fetchone()
    if row:
        return {"minecraft_username": minecraft_username, "server_name": server_name, "day": str(target_day), "steps": row[0]}
    raise HTTPException(status_code=404, detail=f"No step record found for {minecraft_username} on {str(target_day)}.")


@router.get("/v1/servers/players/{minecraft_username}/claim-available")
def get_claim_available(
    minecraft_username: str,
    debug: bool = Query(default=False),
    server_name: str = Depends(require_server_access),
):
    """
    List all claimable reward tiers within the claim window for a player.
    Returns items with day + min_steps + label (one entry per tier per day).
    """
    today = datetime.now(CENTRAL_TZ).date()
    buffer_days = _get_claim_buffer_days(server_name)
    days = [today - timedelta(days=offset) for offset in range(buffer_days + 1)]
    resolved_username = _resolve_username(minecraft_username, server_name)

    debug_info = {
        "server_name": server_name,
        "resolved_username": resolved_username,
        "buffer_days": buffer_days,
        "days": [str(d) for d in days],
        "tiers": [],
        "steps_by_day": {},
        "claimed_by_day": {},
    }

    with engine.begin() as conn:
        tiers = conn.execute(
            text("""
                SELECT min_steps, label, item_id
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).mappings().all()

        if debug:
            debug_info["tiers"] = [
                {"min_steps": t["min_steps"], "label": t["label"]}
                for t in tiers
            ]

        if not tiers:
            tiers = [{"min_steps": r["min_steps"], "label": r["label"], "item_id": r.get("item_id")} for r in DEFAULT_REWARDS]
            if debug:
                debug_info["tiers"] = [
                    {"min_steps": t["min_steps"], "label": t["label"], "item_id": t.get("item_id")}
                    for t in tiers
                ]

        items = []
        for day in days:
            day_str = str(day)
            steps_row = conn.execute(
                text("""
                    SELECT steps_today FROM step_ingest
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                    LIMIT 1
                """),
                {"username": resolved_username, "server": server_name, "day": day_str},
            ).fetchone()

            if not steps_row:
                if debug:
                    debug_info["steps_by_day"][str(day)] = None
                continue

            steps = steps_row[0]
            if debug:
                debug_info["steps_by_day"][str(day)] = steps
            eligible = [tier for tier in tiers if steps >= tier["min_steps"]]
            if not eligible:
                continue

            claimed_rows = conn.execute(
                text("""
                    SELECT min_steps FROM step_claims
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                      AND claimed = TRUE
                """),
                {"username": resolved_username, "server": server_name, "day": day_str},
            ).fetchall()
            claimed_set = {row[0] for row in claimed_rows}
            if debug:
                debug_info["claimed_by_day"][str(day)] = sorted(claimed_set)

            for tier in eligible:
                if tier["min_steps"] in claimed_set:
                    continue
                items.append(
                    {
                        "day": str(day),
                        "min_steps": tier["min_steps"],
                        "label": tier["label"],
                        "item_id": tier.get("item_id"),
                    }
                )

    return {"server_name": server_name, "items": items, "debug": debug_info} if debug else {"server_name": server_name, "items": items}


@router.get("/v1/servers/players/{minecraft_username}/claim-status-list")
def get_claim_status_list(
    minecraft_username: str,
    server_name: str = Depends(require_server_access),
):
    """
    List claim status for all eligible reward tiers within the claim window for a player.
    Only returns tiers the player is eligible to claim (per day in window).
    """
    today = datetime.now(CENTRAL_TZ).date()
    buffer_days = _get_claim_buffer_days(server_name)
    days = [today - timedelta(days=offset) for offset in range(buffer_days + 1)]
    resolved_username = _resolve_username(minecraft_username, server_name)

    with engine.begin() as conn:
        tiers = conn.execute(
            text("""
                SELECT min_steps, label, item_id
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).mappings().all()

        if not tiers:
            tiers = [{"min_steps": r["min_steps"], "label": r["label"], "item_id": r.get("item_id")} for r in DEFAULT_REWARDS]

        items = []
        for day in days:
            day_str = str(day)
            steps_row = conn.execute(
                text("""
                    SELECT steps_today FROM step_ingest
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                    LIMIT 1
                """),
                {"username": resolved_username, "server": server_name, "day": day_str},
            ).fetchone()

            if not steps_row:
                continue

            steps = steps_row[0]
            eligible = [tier for tier in tiers if steps >= tier["min_steps"]]
            if not eligible:
                continue

            claimed_rows = conn.execute(
                text("""
                    SELECT min_steps, claimed, claimed_at FROM step_claims
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                """),
                {"username": resolved_username, "server": server_name, "day": day_str},
            ).fetchall()

            claimed_map = {row[0]: (bool(row[1]), row[2]) for row in claimed_rows}

            for tier in eligible:
                claimed, claimed_at = claimed_map.get(tier["min_steps"], (False, None))
                items.append(
                    {
                        "day": day_str,
                        "min_steps": tier["min_steps"],
                        "label": tier["label"],
                        "item_id": tier.get("item_id"),
                        "claimed": claimed,
                        "claimed_at": claimed_at,
                    }
                )

    return {"server_name": server_name, "items": items}

@router.delete("/v1/servers/players/{minecraft_username}")
def delete_player(
    minecraft_username: str,
    all: bool = False,
    server_name: str = Depends(require_server_access),
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


def _resolve_claim_day(day: str | None, server_name: str):
    today = datetime.now(CENTRAL_TZ).date()
    if day:
        try:
            target_day = datetime.fromisoformat(day).date()
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid day format (YYYY-MM-DD)")
    else:
        target_day = today

    if target_day > today:
        raise HTTPException(status_code=400, detail="Cannot claim future days")

    buffer_days = _get_claim_buffer_days(server_name)
    earliest = today - timedelta(days=buffer_days)
    if target_day < earliest:
        raise HTTPException(status_code=400, detail="Day is outside claim window")

    return target_day


def _get_claim_buffer_days(server_name: str) -> int:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT claim_buffer_days FROM servers WHERE server_name = :server"),
            {"server": server_name},
        ).fetchone()
    if not row or row[0] is None:
        return 1
    return max(0, int(row[0]))


def _resolve_username(minecraft_username: str, server_name: str) -> str:
    if not minecraft_username:
        return minecraft_username
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT minecraft_username FROM step_ingest
                WHERE server_name = :server
                  AND LOWER(minecraft_username) = LOWER(:username)
                ORDER BY day DESC
                LIMIT 1
            """),
            {"server": server_name, "username": minecraft_username},
        ).fetchone()
    return row[0] if row else minecraft_username


