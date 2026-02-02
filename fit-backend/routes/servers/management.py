"""Server management endpoints for server owners."""

from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy import text
from typing import Optional, Literal
from datetime import datetime, timezone, timedelta

from database import engine
from auth import require_server_access, require_master_admin
from fastapi.responses import JSONResponse

router = APIRouter()


class UpdateServerSettingsRequest(BaseModel):
    max_players: Optional[int] = Field(None, ge=1, le=10000, description="Maximum players allowed (NULL = unlimited)")


class TogglePrivacyRequest(BaseModel):
    is_private: bool


class InactivePruneSettingsRequest(BaseModel):
    enabled: bool = False
    max_inactive_days: int | None = Field(default=None, ge=1)
    mode: Literal["deactivate", "wipe"] = "deactivate"


class ClaimWindowSettingsRequest(BaseModel):
    claim_buffer_days: int = Field(1, ge=0, le=365)


@router.get("/v1/servers/info")
def get_server_info(server_name: str = Depends(require_server_access)):
    """
    Get your server's information and settings.
    Requires server API key (X-API-Key header).
    """
    try:
        with engine.begin() as conn:
            server = conn.execute(
                text("""
                    SELECT 
                        k.server_name,
                        k.max_players,
                        k.created_at,
                        k.last_used,
                        k.active,
                        s.is_private,
                        s.invite_code,
                        s.claim_buffer_days
                    FROM api_keys k
                    JOIN servers s ON s.server_name = k.server_name
                    WHERE k.server_name = :server_name
                """),
                {"server_name": server_name}
            ).mappings().fetchone()
            
            if not server:
                raise HTTPException(status_code=404, detail="Server not found")
            
            # Get current player count
            player_count = conn.execute(
                text("""
                    SELECT COUNT(DISTINCT minecraft_username) 
                    FROM player_keys 
                    WHERE server_name = :server_name AND active = TRUE
                """),
                {"server_name": server_name}
            ).scalar()
            
            result = dict(server)
            result["current_players"] = player_count
            result["slots_available"] = None if result["max_players"] is None else result["max_players"] - player_count
            
            return result
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get server info: {str(e)}")


@router.get("/v1/servers/claim-window")
def get_claim_window(server_name: str = Depends(require_server_access)):
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT claim_buffer_days
                FROM servers
                WHERE server_name = :server
            """),
            {"server": server_name},
        ).fetchone()

    if not row:
        raise HTTPException(status_code=404, detail="Server not found")

    return {
        "server_name": server_name,
        "claim_buffer_days": row[0] if row[0] is not None else 1,
    }


@router.put("/v1/servers/claim-window")
def update_claim_window(
    payload: ClaimWindowSettingsRequest,
    server_name: str = Depends(require_server_access),
):
    with engine.begin() as conn:
        conn.execute(
            text("""
                UPDATE servers
                SET claim_buffer_days = :days
                WHERE server_name = :server
            """),
            {"days": payload.claim_buffer_days, "server": server_name},
        )

    return {
        "server_name": server_name,
        "claim_buffer_days": payload.claim_buffer_days,
    }


@router.post("/v1/servers/toggle-privacy")
def toggle_server_privacy(request: TogglePrivacyRequest, server_name: str = Depends(require_server_access)):
    """
    Toggle server privacy (public/private) and regenerate invite code if switching to private.
    Requires server API key (X-API-Key header).
    """
    from utils import generate_invite_code
    try:
        with engine.begin() as conn:
            server = conn.execute(
                text("SELECT is_private, invite_code FROM servers WHERE server_name = :server_name"),
                {"server_name": server_name}
            ).mappings().fetchone()

            if not server:
                raise HTTPException(status_code=404, detail=f"Server '{server_name}' not found")

            invite_code = server["invite_code"]
            if request.is_private and (not server["is_private"] or not invite_code):
                invite_code = generate_invite_code()

            conn.execute(
                text("""
                    UPDATE servers
                    SET is_private = :is_private,
                        invite_code = :invite_code
                    WHERE server_name = :server_name
                """),
                {
                    "is_private": request.is_private,
                    "invite_code": invite_code if request.is_private else invite_code,
                    "server_name": server_name,
                }
            )

        return {"ok": True, "is_private": request.is_private, "invite_code": invite_code if request.is_private else None}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to toggle privacy: {str(e)}")


@router.patch("/v1/admin/servers/{server_name}/settings")
def admin_update_server_settings(
    server_name: str,
    request: UpdateServerSettingsRequest,
    _: bool = Depends(require_master_admin)
):
    """
    Update a server's settings (master admin only).
    Requires master admin key (X-Admin-Key header).
    
    Settings:
    - max_players: Maximum number of unique players allowed to register (null = unlimited)
    
    Use this to set player limits based on the server owner's payment plan.
    
    Note: Changing max_players to a number lower than current players will not kick existing players,
    but will prevent new registrations until players drop below the limit.
    """
    try:
        with engine.begin() as conn:
            # Verify server exists
            server_exists = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name"),
                {"server_name": server_name}
            ).fetchone()
            
            if not server_exists:
                raise HTTPException(status_code=404, detail=f"Server '{server_name}' not found")
            
            # Get current player count
            current_players = conn.execute(
                text("""
                    SELECT COUNT(DISTINCT minecraft_username) 
                    FROM player_keys 
                    WHERE server_name = :server_name AND active = TRUE
                """),
                {"server_name": server_name}
            ).scalar()
            
            # Update max_players
            if request.max_players is not None:
                conn.execute(
                    text("""
                        UPDATE api_keys
                        SET max_players = :max_players
                        WHERE server_name = :server_name
                    """),
                    {"max_players": request.max_players, "server_name": server_name}
                )
                
                warning = None
                if request.max_players < current_players:
                    warning = f"Warning: You set max_players to {request.max_players}, but server currently has {current_players} registered players. New registrations will be blocked until players are removed."
                
                return {
                    "ok": True,
                    "server_name": server_name,
                    "max_players": request.max_players,
                    "current_players": current_players,
                    "slots_available": request.max_players - current_players,
                    "warning": warning,
                    "message": f"Updated server settings for '{server_name}'"
                }
            else:
                # Set to unlimited
                conn.execute(
                    text("""
                        UPDATE api_keys
                        SET max_players = NULL
                        WHERE server_name = :server_name
                    """),
                    {"server_name": server_name}
                )
                
                return {
                    "ok": True,
                    "server_name": server_name,
                    "max_players": None,
                    "current_players": current_players,
                    "message": f"Set '{server_name}' to unlimited players"
                }
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to update server settings: {str(e)}")


@router.get("/v1/servers/players/list")
def list_server_players(
    server_name: str = Depends(require_server_access),
    limit: int = 100,
    offset: int = 0,
    q: str | None = None
):
    """
    List all registered players on your server.
    Requires server API key (X-API-Key header).
    
    Returns paginated list of players with their registration info.
    """
    try:
        with engine.begin() as conn:
            query_params = {"server_name": server_name, "limit": limit, "offset": offset}
            query_filter = ""
            if q:
                query_filter = "AND minecraft_username ILIKE :q"
                query_params["q"] = f"%{q}%"

            players = conn.execute(
                text(f"""
                    SELECT
                        minecraft_username,
                        COUNT(DISTINCT device_id) AS device_count,
                        MAX(created_at) AS created_at,
                        MAX(last_used) AS last_used,
                        BOOL_OR(active) AS active
                    FROM player_keys
                    WHERE server_name = :server_name
                      {query_filter}
                    GROUP BY minecraft_username
                    ORDER BY MAX(created_at) DESC
                    LIMIT :limit OFFSET :offset
                """),
                query_params,
            ).mappings().all()

            total = conn.execute(
                text(f"""
                    SELECT COUNT(DISTINCT minecraft_username)
                    FROM player_keys
                    WHERE server_name = :server_name
                      {query_filter}
                """),
                query_params,
            ).scalar()
            
            return {
                "server_name": server_name,
                "total_players": total,
                "players": [dict(p) for p in players],
                "limit": limit,
                "offset": offset
            }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to list players: {str(e)}")


@router.get("/v1/servers/inactive-prune")
def get_inactive_prune_settings(server_name: str = Depends(require_server_access)):
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT inactive_prune_enabled, inactive_prune_days, inactive_prune_mode
                FROM servers
                WHERE server_name = :server
            """),
            {"server": server_name},
        ).fetchone()

    if not row:
        raise HTTPException(status_code=404, detail="Server not found")

    return {
        "server_name": server_name,
        "enabled": bool(row[0]) if row[0] is not None else False,
        "max_inactive_days": row[1],
        "mode": row[2] or "deactivate",
    }


@router.put("/v1/servers/inactive-prune")
def update_inactive_prune_settings(
    payload: InactivePruneSettingsRequest,
    server_name: str = Depends(require_server_access),
):
    if payload.enabled and payload.max_inactive_days is None:
        raise HTTPException(status_code=400, detail="max_inactive_days is required when enabled")

    with engine.begin() as conn:
        conn.execute(
            text("""
                UPDATE servers
                SET inactive_prune_enabled = :enabled,
                    inactive_prune_days = :days,
                    inactive_prune_mode = :mode
                WHERE server_name = :server
            """),
            {
                "enabled": payload.enabled,
                "days": payload.max_inactive_days,
                "mode": payload.mode,
                "server": server_name,
            },
        )

    return {
        "server_name": server_name,
        "enabled": payload.enabled,
        "max_inactive_days": payload.max_inactive_days,
        "mode": payload.mode,
    }


@router.post("/v1/servers/inactive-prune/run")
def run_inactive_prune(
    dry_run: bool = Query(False),
    server_name: str = Depends(require_server_access),
):
    with engine.begin() as conn:
        settings = conn.execute(
            text("""
                SELECT inactive_prune_enabled, inactive_prune_days, inactive_prune_mode
                FROM servers
                WHERE server_name = :server
            """),
            {"server": server_name},
        ).fetchone()

        if not settings:
            raise HTTPException(status_code=404, detail="Server not found")

        enabled = bool(settings[0]) if settings[0] is not None else False
        days = settings[1]
        mode = settings[2] or "deactivate"

        if not enabled:
            raise HTTPException(status_code=400, detail="Inactive prune is disabled for this server")
        if days is None or days <= 0:
            raise HTTPException(status_code=400, detail="Invalid max_inactive_days setting")

        rows = conn.execute(
            text("""
                SELECT
                    pk.id,
                    pk.minecraft_username,
                    pk.device_id,
                    pk.created_at,
                    MAX(sc.claimed_at) AS last_claimed_at
                FROM player_keys pk
                LEFT JOIN step_claims sc
                  ON sc.server_name = pk.server_name
                 AND sc.minecraft_username = pk.minecraft_username
                 AND sc.claimed = TRUE
                WHERE pk.server_name = :server
                  AND pk.active = TRUE
                GROUP BY pk.id, pk.minecraft_username, pk.device_id, pk.created_at
            """),
            {"server": server_name},
        ).mappings().all()

        cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        candidates = []
        for row in rows:
            last_claimed = _coerce_dt(row["last_claimed_at"])
            created_at = _coerce_dt(row["created_at"])
            last_activity = last_claimed or created_at
            if last_activity and last_activity < cutoff:
                candidates.append(row)

        if dry_run:
            return {
                "server_name": server_name,
                "dry_run": True,
                "mode": mode,
                "max_inactive_days": days,
                "candidates": [
                    {
                        "minecraft_username": r["minecraft_username"],
                        "device_id": r["device_id"],
                        "last_claimed_at": r["last_claimed_at"],
                        "created_at": r["created_at"],
                    }
                    for r in candidates
                ],
                "total_candidates": len(candidates),
            }

        removed = []
        delete_counts = {
            "player_keys": 0,
            "step_ingest": 0,
            "step_claims": 0,
            "push_deliveries": 0,
            "bans": 0,
        }

        for row in candidates:
            username = row["minecraft_username"]
            if mode == "deactivate":
                result = conn.execute(
                    text("UPDATE player_keys SET active = FALSE WHERE id = :id"),
                    {"id": row["id"]},
                )
                delete_counts["player_keys"] += result.rowcount
            else:
                delete_counts["player_keys"] += conn.execute(
                    text("DELETE FROM player_keys WHERE id = :id"),
                    {"id": row["id"]},
                ).rowcount
                delete_counts["step_ingest"] += conn.execute(
                    text("""
                        DELETE FROM step_ingest
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                delete_counts["step_claims"] += conn.execute(
                    text("""
                        DELETE FROM step_claims
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                delete_counts["push_deliveries"] += conn.execute(
                    text("""
                        DELETE FROM push_deliveries
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount
                delete_counts["bans"] += conn.execute(
                    text("""
                        DELETE FROM bans
                        WHERE server_name = :server
                          AND minecraft_username = :username
                    """),
                    {"server": server_name, "username": username},
                ).rowcount

            removed.append(username)

        return {
            "server_name": server_name,
            "dry_run": False,
            "mode": mode,
            "max_inactive_days": days,
            "removed_players": removed,
            "total_removed": len(removed),
            "records_affected": delete_counts,
        }


@router.delete("/v1/servers/players/{minecraft_username}")
def server_wipe_player(
    minecraft_username: str,
    server_name: str = Depends(require_server_access),
):
    """
    Wipe all data for a specific player on your server only.
    Server owners can use this to reset a player's data and API key.
    Requires server API key (X-API-Key header).
    
    This deletes:
    - Player's API key registration for this server
    - All step data for this player on this server
    - Any bans for this player on this server
    
    After wiping, the player will need to re-register to submit data again.
    """
    if not minecraft_username or len(minecraft_username.strip()) == 0:
        raise HTTPException(status_code=400, detail="minecraft_username cannot be empty")
    
    try:
        with engine.begin() as conn:
            # Delete player's API key for this server
            key_result = conn.execute(
                text("""
                    DELETE FROM player_keys 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
            
            # Delete player's step data for this server
            step_result = conn.execute(
                text("""
                    DELETE FROM step_ingest 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
            
            # Delete any bans for this player on this server
            ban_result = conn.execute(
                text("""
                    DELETE FROM bans 
                    WHERE minecraft_username = :minecraft_username 
                      AND server_name = :server_name
                """),
                {"minecraft_username": minecraft_username, "server_name": server_name}
            )
        
        return {
            "ok": True,
            "action": "server_wiped_player",
            "minecraft_username": minecraft_username,
            "server_name": server_name,
            "player_keys_deleted": key_result.rowcount,
            "step_records_deleted": step_result.rowcount,
            "bans_deleted": ban_result.rowcount,
            "message": f"Wiped all data for '{minecraft_username}' on server '{server_name}'. Player will need to re-register."
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to wipe player data: {str(e)}")


def _coerce_dt(value) -> datetime | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        dt = value
    elif isinstance(value, str):
        try:
            dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
        except Exception:
            return None
    else:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt
