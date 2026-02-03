"""Server push notification endpoints (API key required)."""

from fastapi import APIRouter, HTTPException, Depends, Header
from pydantic import BaseModel, Field
from sqlalchemy import text
from zoneinfo import ZoneInfo
from datetime import datetime, timezone

from database import engine
from auth import require_server_access
from audit import log_audit_event, maybe_get_user
from apns_service import (
    ApnsConfigError,
    APNsException,
    Unregistered,
    apns_use_sandbox,
    format_apns_exception,
    send_push,
)
from apns2.errors import BadDeviceToken
from fcm_service import (
    FcmConfigError,
    FirebaseError,
    format_fcm_exception,
    is_unregistered_fcm_error,
    send_fcm_push,
)

router = APIRouter()


class PushPayload(BaseModel):
    message: str = Field(..., min_length=1, max_length=240)
    scheduled_at: str = Field(..., min_length=1)
    timezone: str = Field(..., min_length=1)


class PushSendPayload(BaseModel):
    title: str = Field(..., min_length=1, max_length=64)
    body: str = Field(..., min_length=1, max_length=256)
    data: dict | None = None


@router.get("/v1/servers/push")
def list_push_notifications(server_name: str = Depends(require_server_access)):
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
def schedule_push_notification(
    payload: PushPayload,
    server_name: str = Depends(require_server_access),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
):
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

    user = maybe_get_user(authorization=authorization, x_user_token=x_user_token)
    log_audit_event(
        server_name=server_name,
        actor_user_id=user["id"] if user else None,
        action="push_scheduled",
        summary="Scheduled push notification",
        details={
            "message": payload.message.strip(),
            "scheduled_at": scheduled_utc.isoformat(),
            "timezone": payload.timezone,
        },
    )
    return {"server_name": server_name, "item": dict(row)}


@router.post("/v1/servers/push/send")
def send_push_now(
    payload: PushSendPayload,
    server_name: str = Depends(require_server_access),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
):
    target_sandbox: bool | None = None
    try:
        target_sandbox = apns_use_sandbox()
    except ApnsConfigError:
        target_sandbox = None

    try:
        with engine.begin() as conn:
            rows = conn.execute(
                text(
                    """
                    SELECT token, sandbox, platform
                    FROM push_device_tokens
                    WHERE server_name = :server
                    ORDER BY updated_at DESC
                    """
                ),
                {"server": server_name},
            ).fetchall()

        eligible = [
            r for r in rows
            if (str(r[2] or "").lower() == "android")
            or (
                str(r[2] or "").lower() == "ios"
                and target_sandbox is not None
                and bool(r[1]) == target_sandbox
            )
        ]
        if not eligible:
            raise HTTPException(status_code=404, detail="No push tokens registered for this environment")

        failures = 0
        title = payload.title
        if server_name and server_name.lower() not in title.lower():
            title = f"{title} • {server_name}"
        data = dict(payload.data or {})
        data.setdefault("server_name", server_name)
        for token, _sandbox, platform in eligible:
            try:
                if str(platform or "").lower() == "android":
                    send_fcm_push(token, title, payload.body, data)
                else:
                    send_push(token, title, payload.body, data)
            except (Unregistered, BadDeviceToken):
                with engine.begin() as conn:
                    conn.execute(
                        text(
                            """
                            DELETE FROM push_device_tokens
                            WHERE server_name = :server AND token = :token
                            """
                        ),
                        {"server": server_name, "token": token},
                    )
                failures += 1
            except FirebaseError as e:
                if is_unregistered_fcm_error(e):
                    with engine.begin() as conn:
                        conn.execute(
                            text(
                                """
                                DELETE FROM push_device_tokens
                                WHERE server_name = :server AND token = :token
                                """
                            ),
                            {"server": server_name, "token": token},
                        )
                    failures += 1
                    continue
                raise HTTPException(status_code=502, detail=f"FCM error: {format_fcm_exception(e)}")
            except APNsException as e:
                raise HTTPException(status_code=502, detail=f"APNs error: {format_apns_exception(e)}")

        user = maybe_get_user(authorization=authorization, x_user_token=x_user_token)
        log_audit_event(
            server_name=server_name,
            actor_user_id=user["id"] if user else None,
            action="push_sent_now",
            summary="Sent push notification",
            details={
                "title": title,
                "body": payload.body,
                "tokens": len(eligible),
                "failed": failures,
            },
        )
        return {"status": "sent", "tokens": len(eligible), "failed": failures}

    except FcmConfigError as e:
        raise HTTPException(status_code=500, detail=f"FCM config error: {str(e)}")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to send push: {str(e)}")
