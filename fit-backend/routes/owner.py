"""Owner-scoped endpoints for managing servers and rewards."""

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from sqlalchemy import text
import json
from datetime import datetime, timezone

from database import engine
from auth import require_user

router = APIRouter()


class RewardTier(BaseModel):
    min_steps: int = Field(..., ge=0)
    label: str = Field(..., min_length=1)
    rewards: list[str] = Field(default_factory=list)


class RewardsPayload(BaseModel):
    tiers: list[RewardTier] = Field(default_factory=list)


class PushPayload(BaseModel):
    message: str = Field(..., min_length=1, max_length=240)
    scheduled_at: str = Field(..., min_length=1)


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


@router.get("/v1/owner/servers/{server_name}/push")
def list_push_notifications(server_name: str, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
                SELECT id, message, scheduled_at, created_at
                FROM push_notifications
                WHERE server_name = :server
                ORDER BY scheduled_at DESC
                LIMIT 20
            """),
            {"server": server_name},
        ).mappings().all()

    return {"server_name": server_name, "items": list(rows)}


@router.post("/v1/owner/servers/{server_name}/push")
def schedule_push_notification(server_name: str, payload: PushPayload, user=Depends(require_user)):
    _ensure_owner(server_name, user["id"])
    try:
        scheduled = datetime.fromisoformat(payload.scheduled_at)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid scheduled_at format")

    if scheduled.tzinfo is None:
        scheduled = scheduled.replace(tzinfo=timezone.utc)
    scheduled_date = scheduled.date()

    with engine.begin() as conn:
        existing = conn.execute(
            text("""
                SELECT id FROM push_notifications
                WHERE server_name = :server AND scheduled_date = :scheduled_date
                LIMIT 1
            """),
            {"server": server_name, "scheduled_date": scheduled_date},
        ).fetchone()

        if existing:
            raise HTTPException(status_code=409, detail="A notification is already scheduled for that day")

        row = conn.execute(
            text("""
                INSERT INTO push_notifications (server_name, message, scheduled_at, scheduled_date, created_by)
                VALUES (:server, :message, :scheduled_at, :scheduled_date, :created_by)
                RETURNING id, message, scheduled_at, created_at
            """),
            {
                "server": server_name,
                "message": payload.message.strip(),
                "scheduled_at": scheduled,
                "scheduled_date": scheduled_date,
                "created_by": user["id"],
            },
        ).mappings().first()

    return {"server_name": server_name, "item": dict(row)}


def _ensure_owner(server_name: str, user_id: int) -> None:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
            {"server": server_name, "user_id": user_id}
        ).fetchone()
        if not row:
            raise HTTPException(status_code=403, detail="Not authorized for this server")
