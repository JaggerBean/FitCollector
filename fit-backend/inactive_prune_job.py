import os
import time
import logging
from datetime import datetime, timezone, timedelta
from zoneinfo import ZoneInfo
from sqlalchemy import text

from database import engine

logger = logging.getLogger("inactive_prune")


def run_daily_scheduler() -> None:
    enabled = os.getenv("ENABLE_INACTIVE_PRUNE_SCHEDULER", "false").lower() in {"1", "true", "yes"}
    if not enabled:
        logger.info("Inactive prune scheduler disabled")
        return

    run_time = os.getenv("INACTIVE_PRUNE_DAILY_TIME", "03:00")
    tz_name = os.getenv("INACTIVE_PRUNE_TIMEZONE", "UTC")
    tz = _get_timezone(tz_name)

    logger.info("Inactive prune scheduler active at %s %s", run_time, tz.key)

    while True:
        try:
            sleep_seconds = _seconds_until_next_run(run_time, tz)
            time.sleep(sleep_seconds)
            run_inactive_prune_once()
        except Exception:
            logger.exception("Inactive prune run failed")
            time.sleep(60)


def run_inactive_prune_once() -> None:
    with engine.begin() as conn:
        servers = conn.execute(
            text("""
                SELECT server_name, inactive_prune_days, inactive_prune_mode
                FROM servers
                WHERE inactive_prune_enabled = TRUE
                  AND inactive_prune_days IS NOT NULL
                  AND inactive_prune_days > 0
            """)
        ).mappings().all()

    if not servers:
        logger.info("Inactive prune: no servers enabled")
        return

    for server in servers:
        server_name = server["server_name"]
        days = server["inactive_prune_days"]
        mode = server["inactive_prune_mode"] or "deactivate"
        cutoff = datetime.now(timezone.utc) - timedelta(days=days)

        with engine.begin() as conn:
            rows = conn.execute(
                text("""
                    SELECT
                        pk.id,
                        pk.minecraft_username,
                        pk.device_id,
                        pk.created_at,
                        MAX(sc.claimed_at) AS last_claimed_at
                    FROM player_keys pk
                    LEFT JOIN step_claims sc
                      ON sc.server_name = pk.server_name
                     AND sc.minecraft_username = pk.minecraft_username
                     AND sc.claimed = TRUE
                    WHERE pk.server_name = :server
                      AND pk.active = TRUE
                    GROUP BY pk.id, pk.minecraft_username, pk.device_id, pk.created_at
                """),
                {"server": server_name},
            ).mappings().all()

            candidates = []
            for row in rows:
                last_claimed = _coerce_dt(row["last_claimed_at"])
                created_at = _coerce_dt(row["created_at"])
                last_activity = last_claimed or created_at
                if last_activity and last_activity < cutoff:
                    candidates.append(row)

            if not candidates:
                logger.info("Inactive prune: no candidates for %s", server_name)
                continue

            if mode == "deactivate":
                deactivated = 0
                for row in candidates:
                    result = conn.execute(
                        text("UPDATE player_keys SET active = FALSE WHERE id = :id"),
                        {"id": row["id"]},
                    )
                    deactivated += result.rowcount
                logger.info(
                    "Inactive prune: deactivated %s players for %s",
                    deactivated,
                    server_name,
                )
                continue

            deleted = {
                "player_keys": 0,
                "step_ingest": 0,
                "step_claims": 0,
                "push_deliveries": 0,
                "bans": 0,
            }
            for row in candidates:
                username = row["minecraft_username"]
                deleted["player_keys"] += conn.execute(
                    text("DELETE FROM player_keys WHERE id = :id"),
                    {"id": row["id"]},
                ).rowcount
                deleted["step_ingest"] += conn.execute(
                    text("""
                        DELETE FROM step_ingest
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                deleted["step_claims"] += conn.execute(
                    text("""
                        DELETE FROM step_claims
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                deleted["push_deliveries"] += conn.execute(
                    text("""
                        DELETE FROM push_deliveries
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                deleted["bans"] += conn.execute(
                    text("""
                        DELETE FROM bans
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount

            logger.info(
                "Inactive prune: wiped %s players for %s (%s)",
                len(candidates),
                server_name,
                deleted,
            )


def _coerce_dt(value) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        dt = value
    elif isinstance(value, str):
        try:
            dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except Exception:
            return None
    else:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def _get_timezone(name: str) -> timezone | ZoneInfo:
    try:
        return ZoneInfo(name)
    except Exception:
        logger.warning("Invalid timezone '%s', defaulting to UTC", name)
        return timezone.utc


def _seconds_until_next_run(run_time: str, tz: timezone | ZoneInfo) -> float:
    try:
        hour_str, minute_str = run_time.split(":", 1)
        hour = int(hour_str)
        minute = int(minute_str)
    except Exception:
        logger.warning("Invalid INACTIVE_PRUNE_DAILY_TIME '%s', defaulting to 03:00", run_time)
        hour, minute = 3, 0

    now = datetime.now(tz)
    target = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
    if target <= now:
        target = target + timedelta(days=1)
    return max(1.0, (target - now).total_seconds())


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    run_daily_scheduler()
