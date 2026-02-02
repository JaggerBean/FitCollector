"""Player push notification endpoints."""

from fastapi import APIRouter, HTTPException
from sqlalchemy import text

from auth import validate_and_get_server
from database import engine
from models import PushSendRequest, PushTokenRegistrationRequest, PushTokenUnregisterRequest
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


@router.post("/v1/players/push/register-device")
def register_push_device(request: PushTokenRegistrationRequest):
    server_name, _ = validate_and_get_server(request.device_id, request.player_api_key)
    try:
        with engine.begin() as conn:
            conn.execute(
                text(
                    """
                    INSERT INTO push_device_tokens (device_id, server_name, platform, token, sandbox)
                    VALUES (:device_id, :server_name, :platform, :token, :sandbox)
                    ON CONFLICT (device_id, server_name, platform, token, sandbox)
                    DO UPDATE SET updated_at = NOW()
                    """
                ),
                {
                    "device_id": request.device_id,
                    "server_name": server_name,
                    "platform": request.platform,
                    "token": request.apns_token,
                    "sandbox": request.sandbox,
                },
            )
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to register push token: {str(e)}")


@router.post("/v1/players/push/unregister-device")
def unregister_push_device(request: PushTokenUnregisterRequest):
    server_name, _ = validate_and_get_server(request.device_id, request.player_api_key)
    try:
        with engine.begin() as conn:
            if request.apns_token:
                result = conn.execute(
                    text(
                        """
                        DELETE FROM push_device_tokens
                        WHERE device_id = :device_id
                          AND server_name = :server_name
                          AND platform = :platform
                          AND token = :token
                        """
                    ),
                    {
                        "device_id": request.device_id,
                        "server_name": server_name,
                        "platform": request.platform,
                        "token": request.apns_token,
                    },
                )
            else:
                result = conn.execute(
                    text(
                        """
                        DELETE FROM push_device_tokens
                        WHERE device_id = :device_id
                          AND server_name = :server_name
                          AND platform = :platform
                        """
                    ),
                    {
                        "device_id": request.device_id,
                        "server_name": server_name,
                        "platform": request.platform,
                    },
                )
        return {"status": "ok", "deleted": result.rowcount}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to unregister push token: {str(e)}")


@router.post("/v1/players/push/send")
def send_push_notification(request: PushSendRequest):
    server_name, _ = validate_and_get_server(request.device_id, request.player_api_key)
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
                    WHERE device_id = :device_id
                      AND server_name = :server_name
                    ORDER BY updated_at DESC
                    """
                ),
                {
                    "device_id": request.device_id,
                    "server_name": server_name,
                },
            ).fetchall()

            if not rows:
                rows = conn.execute(
                    text(
                        """
                        SELECT token, sandbox, platform
                        FROM push_device_tokens
                        WHERE device_id = :device_id
                        ORDER BY updated_at DESC
                        """
                    ),
                    {"device_id": request.device_id},
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
        for token, _sandbox, platform in eligible:
            try:
                if str(platform or "").lower() == "android":
                    send_fcm_push(token, request.title, request.body, request.data)
                else:
                    send_push(token, request.title, request.body, request.data)
            except (Unregistered, BadDeviceToken):
                with engine.begin() as conn:
                    conn.execute(
                        text(
                            """
                            DELETE FROM push_device_tokens
                            WHERE device_id = :device_id AND token = :token
                            """
                        ),
                        {"device_id": request.device_id, "token": token},
                    )
                failures += 1
            except FirebaseError as e:
                if is_unregistered_fcm_error(e):
                    with engine.begin() as conn:
                        conn.execute(
                            text(
                                """
                                DELETE FROM push_device_tokens
                                WHERE device_id = :device_id AND token = :token
                                """
                            ),
                            {"device_id": request.device_id, "token": token},
                        )
                    failures += 1
                    continue
                raise HTTPException(status_code=502, detail=f"FCM error: {format_fcm_exception(e)}")
            except APNsException as e:
                raise HTTPException(status_code=502, detail=f"APNs error: {format_apns_exception(e)}")

        return {
            "status": "sent",
            "tokens": len(eligible),
            "failed": failures,
        }

    except FcmConfigError as e:
        raise HTTPException(status_code=500, detail=f"FCM config error: {str(e)}")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to send push: {str(e)}")
