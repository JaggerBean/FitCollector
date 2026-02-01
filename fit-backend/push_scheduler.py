import os
import time
import logging
from datetime import datetime, timezone
from sqlalchemy import text

from database import engine
from apns_service import ApnsConfigError, APNsException, Unregistered, apns_use_sandbox, send_push

logger = logging.getLogger("push_scheduler")


def run_push_scheduler() -> None:
    enabled = os.getenv("ENABLE_PUSH_SCHEDULER", "true").lower() in {"1", "true", "yes"}
    if not enabled:
        logger.info("Push scheduler disabled")
        return

    interval = int(os.getenv("PUSH_SCHEDULER_INTERVAL_SECONDS", "30"))
    logger.info("Push scheduler active (interval=%ss)", interval)

    while True:
        try:
            run_push_once()
        except Exception:
            logger.exception("Push scheduler run failed")
        time.sleep(interval)


def run_push_once() -> None:
    try:
        target_sandbox = apns_use_sandbox()
    except ApnsConfigError as e:
        logger.error("APNs config error: %s", e)
        return

    now = datetime.now(timezone.utc)
    limit = int(os.getenv("PUSH_SCHEDULER_BATCH", "200"))
    default_title = os.getenv("PUSH_DEFAULT_TITLE", "StepCraft")

    with engine.begin() as conn:
        rows = conn.execute(
            text(
                """
                SELECT
                    pn.id,
                    pn.server_name,
                    pn.message,
                    pn.scheduled_at,
                    pdt.device_id,
                    pdt.token,
                    pdt.sandbox,
                    COALESCE(pk.minecraft_username, 'unknown') AS minecraft_username
                FROM push_notifications pn
                JOIN push_device_tokens pdt
                  ON pdt.server_name = pn.server_name
                LEFT JOIN player_keys pk
                  ON pk.device_id = pdt.device_id
                 AND pk.server_name = pn.server_name
                 AND pk.active = TRUE
                LEFT JOIN push_deliveries pd
                  ON pd.notification_id = pn.id
                 AND pd.device_id = pdt.device_id
                WHERE pn.scheduled_at <= :now
                  AND pd.id IS NULL
                  AND pdt.sandbox = :sandbox
                ORDER BY pn.scheduled_at ASC
                LIMIT :limit
                """
            ),
            {"now": now, "sandbox": target_sandbox, "limit": limit},
        ).mappings().all()

    if not rows:
        return

    for row in rows:
        token = row["token"]
        device_id = row["device_id"]
        server_name = row["server_name"]
        username = row["minecraft_username"]

        payload_data = {
            "server_name": server_name,
            "notification_id": row["id"],
            "scheduled_at": row["scheduled_at"].isoformat() if row["scheduled_at"] else None,
        }

        try:
            send_push(token, default_title, row["message"], payload_data)
        except Unregistered:
            logger.info("Unregistered token for device %s on %s; removing", device_id, server_name)
            with engine.begin() as conn:
                conn.execute(
                    text(
                        """
                        DELETE FROM push_device_tokens
                        WHERE device_id = :device_id AND token = :token
                        """
                    ),
                    {"device_id": device_id, "token": token},
                )
            continue
        except APNsException as e:
            logger.warning("APNs error for %s/%s: %s", server_name, device_id, e)
            continue
        except Exception:
            logger.exception("Push send failed for %s/%s", server_name, device_id)
            continue

        with engine.begin() as conn:
            conn.execute(
                text(
                    """
                    INSERT INTO push_deliveries (notification_id, device_id, minecraft_username, server_name)
                    VALUES (:notification_id, :device_id, :username, :server)
                    ON CONFLICT (notification_id, device_id) DO NOTHING
                    """
                ),
                {
                    "notification_id": row["id"],
                    "device_id": device_id,
                    "username": username,
                    "server": server_name,
                },
            )


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    run_push_scheduler()
