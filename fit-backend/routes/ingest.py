"""Step data ingest endpoint."""

from datetime import datetime
from fastapi import APIRouter, HTTPException
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
    Checks for bans before allowing submission.
    """
    
    # Validate player key and get server_name and current_username
    server_name, current_username = validate_and_get_server(p.device_id, p.player_api_key)
    
    # Check if player is banned (by username or device)
    with engine.begin() as conn:
        ban_check = conn.execute(
            text("""
                SELECT id, reason FROM bans
                WHERE server_name = :server_name
                AND (
                    minecraft_username = :minecraft_username
                    OR device_id = :device_id
                )
                LIMIT 1
            """),
            {
                "server_name": server_name,
                "minecraft_username": p.minecraft_username,
                "device_id": p.device_id
            }
        ).fetchone()
        
        if ban_check:
            reason = ban_check[1] if ban_check[1] else "No reason provided"
            raise HTTPException(
                status_code=403,
                detail=f"You are banned from server '{server_name}'. Reason: {reason}"
            )
    
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

        # Otherwise, proceed with upsert logic for username, server, and day
        # Check for existing record for this username, server, and day
        row = conn.execute(
            text("""
                SELECT day, steps_today FROM step_ingest
                WHERE minecraft_username = :minecraft_username
                  AND server_name = :server_name
                  AND day = :day
                ORDER BY created_at DESC LIMIT 1
            """),
            {
                "minecraft_username": p.minecraft_username,
                "server_name": server_name,
                "day": server_day
            }
        ).fetchone()

        should_upsert = False
        is_new_day = False
        if row is None:
            # No record for this username/server/day, insert
            should_upsert = True
            is_new_day = True
        else:
            prev_day, prev_steps = row
            # prev_day should always equal server_day here, but keep logic for safety
            if str(server_day) != str(prev_day):
                should_upsert = True
                is_new_day = True
            elif int(p.steps_today) > prev_steps:
                should_upsert = True

        if should_upsert:
            # Use upsert to atomically handle race conditions from multiple devices
            # This ensures only the highest step count is stored for a username/server/day
            conn.execute(
                text("""
                    INSERT INTO step_ingest (minecraft_username, device_id, day, steps_today, source, server_name)
                    VALUES (:minecraft_username, :device_id, :day, :steps_today, :source, :server_name)
                    ON CONFLICT (minecraft_username, server_name, day)
                    WHERE minecraft_username IS NOT NULL AND server_name IS NOT NULL
                    DO UPDATE SET
                        steps_today = EXCLUDED.steps_today,
                        device_id = EXCLUDED.device_id,
                        source = EXCLUDED.source,
                        created_at = NOW()
                    WHERE EXCLUDED.steps_today > step_ingest.steps_today
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
