"""Step data ingest endpoint."""

from datetime import datetime
from fastapi import APIRouter
from sqlalchemy import text
from zoneinfo import ZoneInfo
from database import engine
from models import IngestPayload
from auth import validate_and_get_server

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


@router.post("/v1/ingest")
def ingest(p: IngestPayload):
    """
    Ingest step data. Requires player to be registered first via /v1/players/register.
    
    Validates player API key and processes step data.
    Auto-updates username if device previously registered with different name.
    """
    
    # Validate player key and get server_name and current_username
    server_name, current_username = validate_and_get_server(p.device_id, p.player_api_key)
    
    # If username changed, auto-update it
    if current_username != p.minecraft_username:
        with engine.begin() as conn:
            conn.execute(
                text("""
                    UPDATE player_keys 
                    SET minecraft_username = :new_username
                    WHERE device_id = :device_id AND server_name = :server_name
                """),
                {
                    "new_username": p.minecraft_username,
                    "device_id": p.device_id,
                    "server_name": server_name
                }
            )
        current_username = p.minecraft_username
    
    server_day = datetime.now(CENTRAL_TZ).date() if not p.day else p.day
    server_ts = datetime.utcnow().isoformat() + "Z" if not p.timestamp else p.timestamp

    with engine.begin() as conn:
        # Check if this device has already submitted a username today
        device_row = conn.execute(
            text("""
                SELECT minecraft_username FROM step_ingest
                WHERE device_id = :device_id AND day = :day
                ORDER BY created_at ASC LIMIT 1
            """),
            {"device_id": p.device_id, "day": server_day}
        ).fetchone()

        if device_row is not None:
            # Device already submitted a username today
            first_username = device_row[0]
            if first_username != p.minecraft_username:
                # Only allow submissions for the first username of the day
                return {
                    "ok": False,
                    "reason": "Device already submitted a different username today",
                    "device_id": p.device_id,
                    "day": server_day,
                    "first_username": first_username
                }

        # Otherwise, proceed with upsert logic for username
        # Check for existing record for this username
        row = conn.execute(
            text("""
                SELECT day, steps_today FROM step_ingest
                WHERE minecraft_username = :minecraft_username
                ORDER BY created_at DESC LIMIT 1
            """),
            {"minecraft_username": p.minecraft_username}
        ).fetchone()

        should_upsert = False
        is_new_day = False
        if row is None:
            # No record for this username, insert
            should_upsert = True
            is_new_day = True
        else:
            prev_day, prev_steps = row
            if str(server_day) != str(prev_day):
                should_upsert = True
                is_new_day = True
            elif int(p.steps_today) > prev_steps:
                should_upsert = True

        if should_upsert:
            # Delete all previous entries for this username
            conn.execute(
                text("""
                    DELETE FROM step_ingest WHERE minecraft_username = :minecraft_username
                """),
                {"minecraft_username": p.minecraft_username}
            )
            # Insert the new record with server_name
            conn.execute(
                text("""
                    INSERT INTO step_ingest (minecraft_username, device_id, day, steps_today, source, server_name)
                    VALUES (:minecraft_username, :device_id, :day, :steps_today, :source, :server_name)
                """),
                {
                    "minecraft_username": p.minecraft_username,
                    "device_id": p.device_id,
                    "day": server_day,
                    "steps_today": int(p.steps_today),
                    "source": p.source,
                    "server_name": server_name,
                },
            )
            return {
                "ok": True,
                "device_id": p.device_id,
                "day": server_day,
                "steps_today": p.steps_today,
                "upserted": True,
                "new_day": is_new_day
            }
        else:
            return {
                "ok": True,
                "device_id": p.device_id,
                "day": server_day,
                "steps_today": p.steps_today,
                "upserted": False,
                "reason": "Not higher than previous for this day"
            }
