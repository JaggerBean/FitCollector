"""Server push notification endpoints (API key required)."""

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text
from zoneinfo import ZoneInfo
from datetime import datetime, timezone

from database import engine
from auth import require_api_key

router = APIRouter()


class PushPayload(BaseModel):
    message: str = Field(..., min_length=1, max_length=240)
    scheduled_at: str = Field(..., min_length=1)
    timezone: str = Field(..., min_length=1)


@router.get("/v1/servers/push")
def list_push_notifications(server_name: str = Depends(require_api_key)):
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
                SELECT id, message, scheduled_at, created_at
                FROM push_notifications
                WHERE server_name = :server
                ORDER BY scheduled_at DESC
                LIMIT 20
            """),
            {"server": server_name},
        ).mappings().all()

    return {"server_name": server_name, "items": list(rows)}


@router.post("/v1/servers/push")
def schedule_push_notification(payload: PushPayload, server_name: str = Depends(require_api_key)):
    try:
        scheduled = datetime.fromisoformat(payload.scheduled_at)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid scheduled_at format")

    try:
        tz = ZoneInfo(payload.timezone)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid timezone")

    if scheduled.tzinfo is None:
        scheduled = scheduled.replace(tzinfo=tz)
    else:
        scheduled = scheduled.astimezone(tz)

    now_local = datetime.now(tz)
    if scheduled <= now_local:
        raise HTTPException(status_code=400, detail="Scheduled time must be in the future")

    scheduled_date = scheduled.date()
    scheduled_utc = scheduled.astimezone(timezone.utc)

    with engine.begin() as conn:
        existing = conn.execute(
            text("""
                SELECT id FROM push_notifications
                WHERE server_name = :server AND scheduled_date = :scheduled_date
                LIMIT 1
            """),
            {"server": server_name, "scheduled_date": scheduled_date},
        ).fetchone()

        if existing:
            raise HTTPException(status_code=409, detail="A notification is already scheduled for that day")

        row = conn.execute(
            text("""
                INSERT INTO push_notifications (server_name, message, scheduled_at, scheduled_date, created_by)
                VALUES (:server, :message, :scheduled_at, :scheduled_date, :created_by)
                RETURNING id, message, scheduled_at, created_at
            """),
            {
                "server": server_name,
                "message": payload.message.strip(),
                "scheduled_at": scheduled_utc,
                "scheduled_date": scheduled_date,
                "created_by": None,
            },
        ).mappings().first()

    return {"server_name": server_name, "item": dict(row)}
