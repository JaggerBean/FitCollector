"""User-owned servers list endpoint (Bearer token required)."""

from fastapi import APIRouter, Depends
from sqlalchemy import text

from database import engine
from auth import require_user

router = APIRouter()


@router.get("/v1/servers/owned")
def list_owned_servers(user=Depends(require_user)):
    with engine.begin() as conn:
        # Backfill ownership for legacy rows that only have owner_email
        conn.execute(
            text("""
                UPDATE servers
                SET owner_user_id = :user_id
                WHERE owner_user_id IS NULL
                  AND owner_email = :owner_email
            """),
            {"user_id": user["id"], "owner_email": user["email"]},
        )

        conn.execute(
            text("""
                UPDATE api_keys
                SET owner_user_id = :user_id
                WHERE owner_user_id IS NULL
                  AND server_name IN (
                      SELECT server_name FROM servers WHERE owner_email = :owner_email
                  )
            """),
            {"user_id": user["id"], "owner_email": user["email"]},
        )

        rows = conn.execute(
            text("""
                SELECT 
                    s.server_name,
                    s.owner_email,
                    s.server_address,
                    s.server_version,
                    s.created_at,
                    s.is_private,
                    s.invite_code,
                    COALESCE(MAX(CASE WHEN k.active THEN 1 ELSE 0 END), 0) AS api_active,
                    COUNT(k.id) AS key_count
                FROM servers s
                LEFT JOIN api_keys k
                  ON k.server_name = s.server_name
                 AND k.owner_user_id = s.owner_user_id
                WHERE s.owner_user_id = :user_id
                GROUP BY s.server_name, s.owner_email, s.server_address, s.server_version, s.created_at, s.is_private, s.invite_code
                ORDER BY s.created_at DESC
            """),
            {"user_id": user["id"]}
        ).mappings().all()

    servers = []
    for row in rows:
        item = dict(row)
        item["is_active"] = bool(item.get("api_active"))
        item["is_deleted"] = not item["is_active"]
        item.pop("api_active", None)
        item.pop("key_count", None)
        servers.append(item)

    return {"servers": servers}
