"""Audit logging helpers."""

from __future__ import annotations

import json
from typing import Any

from fastapi import Header
from sqlalchemy import text

from auth import require_user
from database import engine


def maybe_get_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
) -> dict | None:
    try:
        return require_user(authorization=authorization, x_user_token=x_user_token)
    except Exception:
        return None


def log_audit_event(
    server_name: str,
    actor_user_id: int | None,
    action: str,
    summary: str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    payload = json.dumps(details or {})
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                INSERT INTO audit_logs (server_name, actor_user_id, action, summary, details_json)
                VALUES (:server_name, :actor_user_id, :action, :summary, :details_json)
                """
            ),
            {
                "server_name": server_name,
                "actor_user_id": actor_user_id,
                "action": action,
                "summary": summary,
                "details_json": payload,
            },
        )

