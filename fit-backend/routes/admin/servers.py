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
            return JSONResponse(content={
                "servers": [
                    {
                        "server_name": s["server_name"],
                        "api_key_hash": s["key"],
                        "active": s["active"],
                        "created_at": s["created_at"],
                        "last_used": s["last_used"]
                    } for s in servers
                ]
            })
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to list servers: {str(e)}")
