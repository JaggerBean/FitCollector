"""User-owned servers list endpoint (Bearer token required)."""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import text

from database import engine
from auth import require_user
from audit import log_audit_event

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


@router.post("/v1/servers/{server_name}/pause")
def pause_server(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        _backfill_api_owner(conn, server_name, user["id"])
        result = conn.execute(
            text("""
                UPDATE api_keys
                SET active = FALSE
                WHERE server_name = :server
                  AND owner_user_id = :user_id
            """),
            {"server": server_name, "user_id": user["id"]},
        )
    log_audit_event(
        server_name=server_name,
        actor_user_id=user["id"],
        action="server_paused",
        summary="Paused server (API key deactivated)",
        details={"keys_deactivated": result.rowcount},
    )
    return {
        "ok": True,
        "server_name": server_name,
        "keys_deactivated": result.rowcount,
        "message": f"Server '{server_name}' paused.",
    }


@router.post("/v1/servers/{server_name}/resume")
def resume_server(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        _backfill_api_owner(conn, server_name, user["id"])
        key_row = conn.execute(
            text("""
                SELECT id
                FROM api_keys
                WHERE server_name = :server
                  AND owner_user_id = :user_id
                ORDER BY created_at DESC
                LIMIT 1
            """),
            {"server": server_name, "user_id": user["id"]},
        ).fetchone()
        if not key_row:
            raise HTTPException(status_code=404, detail="No API key found for this server")

        conn.execute(
            text("""
                UPDATE api_keys
                SET active = FALSE
                WHERE server_name = :server
                  AND owner_user_id = :user_id
            """),
            {"server": server_name, "user_id": user["id"]},
        )
        conn.execute(
            text("UPDATE api_keys SET active = TRUE WHERE id = :id"),
            {"id": key_row[0]},
        )

    log_audit_event(
        server_name=server_name,
        actor_user_id=user["id"],
        action="server_resumed",
        summary="Resumed server (API key activated)",
    )
    return {
        "ok": True,
        "server_name": server_name,
        "message": f"Server '{server_name}' resumed.",
    }


@router.delete("/v1/servers/{server_name}")
def delete_server(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    try:
        with engine.begin() as conn:
            _backfill_api_owner(conn, server_name, user["id"])
            conn.execute(
                text("DELETE FROM push_deliveries WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM push_notifications WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM push_device_tokens WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM step_claims WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM step_ingest WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM player_keys WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM bans WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM key_recovery_audit WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM server_rewards WHERE server_name = :server"),
                {"server": server_name},
            )
            conn.execute(
                text("DELETE FROM api_keys WHERE server_name = :server"),
                {"server": server_name},
            )
            result = conn.execute(
                text("DELETE FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
                {"server": server_name, "user_id": user["id"]},
            )
            if result.rowcount == 0:
                raise HTTPException(status_code=404, detail=f"Server '{server_name}' not found")

        log_audit_event(
            server_name=server_name,
            actor_user_id=user["id"],
            action="server_deleted",
            summary="Deleted server and all data",
        )
        return {"ok": True, "message": f"Server '{server_name}' deleted."}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete server: {str(e)}")


def _ensure_owner(server_name: str, user_id: int) -> None:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
            {"server": server_name, "user_id": user_id},
        ).fetchone()
        if not row:
            raise HTTPException(status_code=403, detail="Not authorized for this server")


def _backfill_api_owner(conn, server_name: str, user_id: int) -> None:
    conn.execute(
        text("""
            UPDATE api_keys
            SET owner_user_id = :user_id
            WHERE owner_user_id IS NULL
              AND server_name = :server
        """),
        {"user_id": user_id, "server": server_name},
    )
