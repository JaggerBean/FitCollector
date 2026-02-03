"""Server rewards configuration endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Header
from pydantic import BaseModel, Field
from sqlalchemy import text
import json
from database import engine
from auth import require_server_access
from audit import log_audit_event, maybe_get_user

router = APIRouter()


class RewardTier(BaseModel):
    min_steps: int = Field(..., ge=0)
    label: str = Field(..., min_length=1)
    item_id: str | None = None
    rewards: list[str] = Field(default_factory=list)


class RewardsPayload(BaseModel):
    tiers: list[RewardTier] = Field(default_factory=list)


DEFAULT_REWARDS = [
    RewardTier(min_steps=1000, label="Starter", item_id="minecraft:bread", rewards=["give {player} minecraft:bread 3"]),
    RewardTier(min_steps=5000, label="Walker", item_id="minecraft:iron_ingot", rewards=["give {player} minecraft:iron_ingot 3"]),
    RewardTier(min_steps=10000, label="Legend", item_id="minecraft:diamond", rewards=["give {player} minecraft:diamond 1"]),
]


@router.get("/v1/servers/rewards")
def get_server_rewards(server_name: str = Depends(require_server_access)):
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
                SELECT min_steps, label, item_id, rewards_json
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).fetchall()

    if not rows:
        return {
            "server_name": server_name,
            "tiers": [tier.model_dump() for tier in DEFAULT_REWARDS],
            "is_default": True,
        }

    tiers = []
    for row in rows:
        rewards = []
        try:
            rewards = json.loads(row[3]) if row[3] else []
        except Exception:
            rewards = []
        tiers.append({"min_steps": row[0], "label": row[1], "item_id": row[2], "rewards": rewards})

    return {"server_name": server_name, "tiers": tiers, "is_default": False}


@router.post("/v1/servers/rewards/default")
def seed_default_rewards(
    server_name: str = Depends(require_server_access),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
):
    with engine.begin() as conn:
        conn.execute(
            text("DELETE FROM server_rewards WHERE server_name = :server"),
            {"server": server_name},
        )

        for idx, tier in enumerate(DEFAULT_REWARDS):
            conn.execute(
                text("""
                    INSERT INTO server_rewards (server_name, min_steps, label, item_id, rewards_json, position)
                    VALUES (:server, :min_steps, :label, :item_id, :rewards_json, :position)
                """),
                {
                    "server": server_name,
                    "min_steps": tier.min_steps,
                    "label": tier.label,
                    "item_id": tier.item_id,
                    "rewards_json": json.dumps(tier.rewards),
                    "position": idx,
                },
            )

    user = maybe_get_user(authorization=authorization, x_user_token=x_user_token)
    log_audit_event(
        server_name=server_name,
        actor_user_id=user["id"] if user else None,
        action="rewards_reset_default",
        summary="Reset rewards to default tiers",
    )
    return {
        "server_name": server_name,
        "tiers": [tier.model_dump() for tier in DEFAULT_REWARDS],
        "is_default": False,
    }


@router.put("/v1/servers/rewards")
def replace_rewards(
    payload: RewardsPayload,
    server_name: str = Depends(require_server_access),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
):
    if not payload.tiers:
        with engine.begin() as conn:
            conn.execute(
                text("DELETE FROM server_rewards WHERE server_name = :server"),
                {"server": server_name},
            )
        user = maybe_get_user(authorization=authorization, x_user_token=x_user_token)
        log_audit_event(
            server_name=server_name,
            actor_user_id=user["id"] if user else None,
            action="rewards_cleared",
            summary="Cleared all reward tiers",
        )
        return {"server_name": server_name, "tiers": []}

    for tier in payload.tiers:
        if tier.min_steps < 0:
            raise HTTPException(status_code=400, detail="min_steps must be >= 0")
        if not tier.label.strip():
            raise HTTPException(status_code=400, detail="label cannot be empty")

    with engine.begin() as conn:
        conn.execute(
            text("DELETE FROM server_rewards WHERE server_name = :server"),
            {"server": server_name},
        )

        for idx, tier in enumerate(payload.tiers):
            conn.execute(
                text("""
                    INSERT INTO server_rewards (server_name, min_steps, label, item_id, rewards_json, position)
                    VALUES (:server, :min_steps, :label, :item_id, :rewards_json, :position)
                """),
                {
                    "server": server_name,
                    "min_steps": tier.min_steps,
                    "label": tier.label,
                    "item_id": tier.item_id,
                    "rewards_json": json.dumps(tier.rewards),
                    "position": idx,
                },
            )

    user = maybe_get_user(authorization=authorization, x_user_token=x_user_token)
    log_audit_event(
        server_name=server_name,
        actor_user_id=user["id"] if user else None,
        action="rewards_updated",
        summary=f"Updated {len(payload.tiers)} reward tier(s)",
        details={"tiers": [tier.model_dump() for tier in payload.tiers]},
    )
    return {"server_name": server_name, "tiers": [tier.model_dump() for tier in payload.tiers]}
