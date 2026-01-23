"""Admin-only endpoints for player management."""

from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import JSONResponse
from sqlalchemy import text
from database import engine
from auth import require_master_admin

router = APIRouter()

@router.get("/v1/admin/players/all")
def admin_list_players_and_keys(_: bool = Depends(require_master_admin)):
    """
    List all players and their API keys for each server (master admin only).
    Requires master admin key (X-Admin-Key header).
    """
    try:
        with engine.begin() as conn:
            rows = conn.execute(
                text("""
                    SELECT server_name, minecraft_username, device_id, key, active, created_at, last_used
                    FROM player_keys
                    ORDER BY server_name ASC, minecraft_username ASC
                """),
            ).mappings().all()
            def dt_to_str(dt):
                return dt.isoformat() if dt is not None else None
            players = [
                {
                    "server_name": r["server_name"],
                    "minecraft_username": r["minecraft_username"],
                    "device_id": r["device_id"],
                    "api_key_hash": r["key"],
                    "active": r["active"],
                    "created_at": dt_to_str(r["created_at"]),
                    "last_used": dt_to_str(r["last_used"])
                } for r in rows
            ]
            return JSONResponse(content={
                "total": len(players),
                "players": players
            })
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to list players: {str(e)}")
