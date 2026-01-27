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
                SELECT server_name, owner_email, server_address, server_version, created_at
                FROM servers
                WHERE owner_user_id = :user_id
                ORDER BY created_at DESC
            """),
            {"user_id": user["id"]}
        ).mappings().all()

    return {"servers": list(rows)}
