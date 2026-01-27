"""Server rewards configuration endpoints."""

from fastapi import APIRouter, Depends, HTTPException, Header, Query
from pydantic import BaseModel, Field
from sqlalchemy import text
import json
from database import engine
from auth import require_api_key, require_user

router = APIRouter()


def _ensure_owner(server_name: str, user_id: int) -> None:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
            {"server": server_name, "user_id": user_id}
        ).fetchone()
        if not row:
            raise HTTPException(status_code=403, detail="Not authorized for this server")


def require_server_access(
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
    server: str | None = Query(default=None),
) -> str:
    if x_api_key:
        try:
            return require_api_key(x_api_key)
        except HTTPException:
            if not authorization and not x_user_token:
                raise

    user = require_user(authorization=authorization, x_user_token=x_user_token)
    if not server:
        raise HTTPException(status_code=400, detail="Missing server")
    _ensure_owner(server, user["id"])
    return server


class RewardTier(BaseModel):
    min_steps: int = Field(..., ge=0)
    label: str = Field(..., min_length=1)
    rewards: list[str] = Field(default_factory=list)


class RewardsPayload(BaseModel):
    tiers: list[RewardTier] = Field(default_factory=list)


DEFAULT_REWARDS = [
    RewardTier(min_steps=1000, label="Starter", rewards=["give {player} minecraft:bread 3"]),
    RewardTier(min_steps=5000, label="Walker", rewards=["give {player} minecraft:iron_ingot 3"]),
    RewardTier(min_steps=10000, label="Legend", rewards=["give {player} minecraft:diamond 1"]),
]


@router.get("/v1/servers/rewards")
def get_server_rewards(server_name: str = Depends(require_server_access)):
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
        return {
            "server_name": server_name,
            "tiers": [tier.model_dump() for tier in DEFAULT_REWARDS],
            "is_default": True,
        }

    tiers = []
    for row in rows:
        rewards = []
        try:
            rewards = json.loads(row[2]) if row[2] else []
        except Exception:
            rewards = []
        tiers.append({"min_steps": row[0], "label": row[1], "rewards": rewards})

    return {"server_name": server_name, "tiers": tiers, "is_default": False}


@router.post("/v1/servers/rewards/default")
def seed_default_rewards(server_name: str = Depends(require_server_access)):
    with engine.begin() as conn:
        conn.execute(
            text("DELETE FROM server_rewards WHERE server_name = :server"),
            {"server": server_name},
        )

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

    return {
        "server_name": server_name,
        "tiers": [tier.model_dump() for tier in DEFAULT_REWARDS],
        "is_default": False,
    }


@router.put("/v1/servers/rewards")
def replace_rewards(payload: RewardsPayload, server_name: str = Depends(require_server_access)):
    if not payload.tiers:
        with engine.begin() as conn:
            conn.execute(
                text("DELETE FROM server_rewards WHERE server_name = :server"),
                {"server": server_name},
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

    return {"server_name": server_name, "tiers": [tier.model_dump() for tier in payload.tiers]}
