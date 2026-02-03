"""Audit log endpoints for server owners."""

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import text
import json

from auth import require_user
from database import engine

router = APIRouter()


@router.get("/v1/servers/audit")
def list_audit_events(
    server: str | None = Query(default=None),
    action: str | None = Query(default=None),
    limit: int = Query(default=200, ge=1, le=1000),
    user=Depends(require_user),
):
    params = {"user_id": user["id"], "limit": limit}
    server_filter = ""
    action_filter = ""

    if server:
        server_filter = "AND a.server_name = :server"
        params["server"] = server

    if action:
        action_filter = "AND a.action = :action"
        params["action"] = action

    with engine.begin() as conn:
        rows = conn.execute(
            text(
                f"""
                SELECT
                    a.id,
                    a.server_name,
                    a.actor_user_id,
                    a.action,
                    a.summary,
                    a.details_json,
                    a.created_at,
                    u.email AS actor_email
                FROM audit_logs a
                JOIN servers s
                  ON s.server_name = a.server_name
                 AND s.owner_user_id = :user_id
                LEFT JOIN users u
                  ON u.id = a.actor_user_id
                WHERE 1=1
                  {server_filter}
                  {action_filter}
                ORDER BY a.created_at DESC
                LIMIT :limit
                """
            ),
            params,
        ).mappings().all()

    items = []
    for row in rows:
        item = dict(row)
        if item.get("details_json"):
            try:
                item["details"] = json.loads(item["details_json"])
            except Exception:
                item["details"] = {}
        else:
            item["details"] = {}
        item.pop("details_json", None)
        items.append(item)

    return {"items": items, "limit": limit}

