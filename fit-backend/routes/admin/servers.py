"""Admin-only endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import JSONResponse
from sqlalchemy import text
from database import engine
from auth import require_master_admin

router = APIRouter()

@router.get("/v1/admin/servers/list")
def admin_list_servers(_: bool = Depends(require_master_admin)):
    """
    List all registered servers and their API keys (master admin only).
    Requires master admin key (X-Admin-Key header).
    """
    try:
        with engine.begin() as conn:
            servers = conn.execute(
                text("""
                    SELECT server_name, key, active, created_at, last_used
                    FROM api_keys
                    ORDER BY created_at DESC
                """),
            ).mappings().all()
            def dt_to_str(dt):
                return dt.isoformat() if dt is not None else None

            return JSONResponse(content={
                "servers": [
                    {
                        "server_name": s["server_name"],
                        "api_key_hash": s["key"],
                        "active": s["active"],
                        "created_at": dt_to_str(s["created_at"]),
                        "last_used": dt_to_str(s["last_used"])
                    } for s in servers
                ]
            })
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to list servers: {str(e)}")

@router.delete("/v1/admin/servers/{server_name}")
def admin_delete_server(server_name: str, _: bool = Depends(require_master_admin)):
    """
    Delete a specific server and all associated API keys and player data (master admin only).
    Requires master admin key (X-Admin-Key header).
    """
    try:
        with engine.begin() as conn:
            # Delete player keys for this server
            conn.execute(
                text("DELETE FROM player_keys WHERE server_name = :server_name"),
                {"server_name": server_name}
            )
            # Delete server API key
            result = conn.execute(
                text("DELETE FROM api_keys WHERE server_name = :server_name"),
                {"server_name": server_name}
            )
            if result.rowcount == 0:
                raise HTTPException(status_code=404, detail=f"Server '{server_name}' not found")
        return {"ok": True, "message": f"Server '{server_name}' and all related data deleted."}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete server: {str(e)}")
