"""Owner-scoped endpoints for managing servers and rewards."""

from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy import text
import json
from datetime import datetime, timezone, timedelta
from zoneinfo import ZoneInfo
from typing import Literal

from database import engine
from auth import require_user

router = APIRouter()


class RewardTier(BaseModel):
    min_steps: int = Field(..., ge=0)
    label: str = Field(..., min_length=1)
    rewards: list[str] = Field(default_factory=list)


class RewardsPayload(BaseModel):
    tiers: list[RewardTier] = Field(default_factory=list)


class InactivePruneSettings(BaseModel):
    enabled: bool = False
    max_inactive_days: int | None = Field(default=None, ge=1)
    mode: Literal["deactivate", "wipe"] = "deactivate"




DEFAULT_REWARDS = [
    RewardTier(min_steps=1000, label="Starter", rewards=["give {player} minecraft:bread 3"]),
    RewardTier(min_steps=5000, label="Walker", rewards=["give {player} minecraft:iron_ingot 3"]),
    RewardTier(min_steps=10000, label="Legend", rewards=["give {player} minecraft:diamond 1"]),
]


@router.get("/v1/owner/servers")
def list_owned_servers(user=Depends(require_user)):
    with engine.begin() as conn:
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


@router.get("/v1/owner/servers/{server_name}/rewards")
def get_rewards(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
                SELECT min_steps, label, rewards_json
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).fetchall()

    if not rows:
        return {"server_name": server_name, "tiers": [t.model_dump() for t in DEFAULT_REWARDS], "is_default": True}

    tiers = []
    for row in rows:
        try:
            rewards = json.loads(row[2]) if row[2] else []
        except Exception:
            rewards = []
        tiers.append({"min_steps": row[0], "label": row[1], "rewards": rewards})

    return {"server_name": server_name, "tiers": tiers, "is_default": False}


@router.post("/v1/owner/servers/{server_name}/rewards/default")
def seed_default(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        conn.execute(text("DELETE FROM server_rewards WHERE server_name = :server"), {"server": server_name})
        for idx, tier in enumerate(DEFAULT_REWARDS):
            conn.execute(
                text("""
                    INSERT INTO server_rewards (server_name, min_steps, label, rewards_json, position)
                    VALUES (:server, :min_steps, :label, :rewards_json, :position)
                """),
                {
                    "server": server_name,
                    "min_steps": tier.min_steps,
                    "label": tier.label,
                    "rewards_json": json.dumps(tier.rewards),
                    "position": idx,
                },
            )

    return {"server_name": server_name, "tiers": [t.model_dump() for t in DEFAULT_REWARDS], "is_default": False}


@router.put("/v1/owner/servers/{server_name}/rewards")
def replace_rewards(server_name: str, payload: RewardsPayload, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        conn.execute(text("DELETE FROM server_rewards WHERE server_name = :server"), {"server": server_name})
        for idx, tier in enumerate(payload.tiers):
            conn.execute(
                text("""
                    INSERT INTO server_rewards (server_name, min_steps, label, rewards_json, position)
                    VALUES (:server, :min_steps, :label, :rewards_json, :position)
                """),
                {
                    "server": server_name,
                    "min_steps": tier.min_steps,
                    "label": tier.label,
                    "rewards_json": json.dumps(tier.rewards),
                    "position": idx,
                },
            )

    return {"server_name": server_name, "tiers": [t.model_dump() for t in payload.tiers]}


@router.get("/v1/owner/servers/{server_name}/inactive-prune")
def get_inactive_prune_settings(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
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


@router.put("/v1/owner/servers/{server_name}/inactive-prune")
def update_inactive_prune_settings(
    server_name: str,
    payload: InactivePruneSettings,
    user=Depends(require_user),
):
    _ensure_owner(server_name, user["id"])

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


@router.post("/v1/owner/servers/{server_name}/inactive-prune/run")
def run_inactive_prune(
    server_name: str,
    dry_run: bool = Query(False),
    user=Depends(require_user),
):
    _ensure_owner(server_name, user["id"])

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


def _ensure_owner(server_name: str, user_id: int) -> None:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
            {"server": server_name, "user_id": user_id}
        ).fetchone()
        if not row:
            raise HTTPException(status_code=403, detail="Not authorized for this server")


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
