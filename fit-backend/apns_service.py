"""APNs push notification helper."""

from __future__ import annotations

import os
import collections
import collections.abc
from functools import lru_cache
from typing import Any

# Compatibility shim for Python 3.12+ where collections.Iterable/Mapping were removed.
if not hasattr(collections, "Iterable"):
    collections.Iterable = collections.abc.Iterable  # type: ignore[attr-defined]
if not hasattr(collections, "Mapping"):
    collections.Mapping = collections.abc.Mapping  # type: ignore[attr-defined]

from apns2.client import APNsClient
from apns2.payload import Payload
from apns2.errors import APNsException, Unregistered


class ApnsConfigError(RuntimeError):
    pass


def _load_config() -> dict[str, Any]:
    cert_path = os.getenv("APNS_CERT_PATH")
    topic = os.getenv("APNS_TOPIC")
    if not cert_path:
        raise ApnsConfigError("APNS_CERT_PATH is not set")
    if not topic:
        raise ApnsConfigError("APNS_TOPIC is not set")

    use_sandbox = os.getenv("APNS_USE_SANDBOX", "true").strip().lower() in {"1", "true", "yes"}
    password = os.getenv("APNS_CERT_PASSWORD") or None
    return {
        "cert_path": cert_path,
        "topic": topic,
        "use_sandbox": use_sandbox,
        "password": password,
    }


@lru_cache
def get_apns_config() -> dict[str, Any]:
    return _load_config()


@lru_cache
def get_apns_client() -> APNsClient:
    config = get_apns_config()
    return APNsClient(
        config["cert_path"],
        use_sandbox=config["use_sandbox"],
        password=config["password"],
    )


def apns_use_sandbox() -> bool:
    return bool(get_apns_config()["use_sandbox"])


def send_push(token: str, title: str, body: str, data: dict | None = None) -> None:
    config = get_apns_config()
    client = get_apns_client()
    payload = Payload(
        alert={"title": title, "body": body},
        sound="default",
        badge=1,
        custom=data or {},
    )
    client.send_notification(token, payload, config["topic"])


__all__ = [
    "ApnsConfigError",
    "APNsException",
    "Unregistered",
    "apns_use_sandbox",
    "send_push",
]
