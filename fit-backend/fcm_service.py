"""FCM push notification helper."""

from __future__ import annotations

import os
from functools import lru_cache
from typing import Any

import firebase_admin
from firebase_admin import credentials, messaging
from firebase_admin.exceptions import FirebaseError


class FcmConfigError(RuntimeError):
    pass


def _to_data_map(data: dict[str, Any] | None) -> dict[str, str]:
    if not data:
        return {}
    mapped: dict[str, str] = {}
    for key, value in data.items():
        if value is None:
            continue
        mapped[str(key)] = str(value)
    return mapped


@lru_cache
def get_fcm_app() -> firebase_admin.App:
    creds_path = os.getenv("FCM_SERVICE_ACCOUNT_PATH")
    if not creds_path:
        raise FcmConfigError("FCM_SERVICE_ACCOUNT_PATH is not set")

    try:
        return firebase_admin.get_app()
    except ValueError:
        cred = credentials.Certificate(creds_path)
        return firebase_admin.initialize_app(cred)


def format_fcm_exception(exc: Exception) -> str:
    parts: list[str] = [type(exc).__name__, str(exc)]
    code = getattr(exc, "code", None)
    if code:
        parts.append(f"code={code}")
    cause = getattr(exc, "__cause__", None)
    if cause:
        parts.append(f"cause={type(cause).__name__}: {cause}")
    return " | ".join(p for p in parts if p)


def is_unregistered_fcm_error(exc: Exception) -> bool:
    msg = format_fcm_exception(exc).lower()
    return (
        "registration-token-not-registered" in msg
        or "requested entity was not found" in msg
        or "unregistered" in msg
    )


def send_fcm_push(token: str, title: str, body: str, data: dict[str, Any] | None = None) -> None:
    app = get_fcm_app()
    channel_id = os.getenv("ANDROID_PUSH_CHANNEL_ID", "stepcraft_push")

    message = messaging.Message(
        token=token,
        notification=messaging.Notification(title=title, body=body),
        data=_to_data_map(data),
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                sound="default",
                channel_id=channel_id,
            ),
        ),
    )
    messaging.send(message, app=app)


__all__ = [
    "FcmConfigError",
    "FirebaseError",
    "format_fcm_exception",
    "is_unregistered_fcm_error",
    "send_fcm_push",
]
