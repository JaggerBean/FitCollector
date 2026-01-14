from datetime import datetime, date
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from fastapi.middleware.cors import CORSMiddleware
import os
from fastapi import Header
from zoneinfo import ZoneInfo
from datetime import timezone


CENTRAL_TZ = ZoneInfo("America/Chicago")


DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///fitcollector.db")
engine = create_engine(DATABASE_URL, future=True)
API_KEY = os.getenv("FIT_API_KEY", "dev-secret-change-me")
# engine = create_engine("sqlite:///fitcollector.db", future=True)

app = FastAPI(title="FitCollector Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

def require_api_key(x_api_key: str | None):
    if x_api_key != API_KEY:
        raise HTTPException(status_code=401, detail="Invalid API key")

def init_db() -> None:
    with engine.begin() as conn:
        # 1) Create table if it doesn't exist (original schema is fine)
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS step_ingest (
            id BIGSERIAL PRIMARY KEY,
            device_id TEXT NOT NULL,
            day DATE NOT NULL,
            steps_today BIGINT NOT NULL,
            source TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        """))

        # 2) Migration: add minecraft_username if missing
        conn.execute(text("""
        ALTER TABLE step_ingest
        ADD COLUMN IF NOT EXISTS minecraft_username TEXT;
        """))

        # Optional: backfill old rows so you can later enforce NOT NULL
        conn.execute(text("""
        UPDATE step_ingest
        SET minecraft_username = COALESCE(minecraft_username, device_id)
        WHERE minecraft_username IS NULL;
        """))

        # 3) Indexes (now safe)
        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_step_ingest_device_day
        ON step_ingest(device_id, day);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_step_ingest_mc_day
        ON step_ingest(minecraft_username, day);
        """))




@app.on_event("startup")
def on_startup():
    init_db()


class IngestPayload(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    steps_today: int = Field(..., ge=0, le=500_000)
    day: Optional[str] = None
    source: Optional[str] = "health_connect"
    timestamp: Optional[str] = None


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/v1/ingest")
def ingest(p: IngestPayload, x_api_key: str | None = Header(default=None, alias="X-API-Key")):
    require_api_key(x_api_key)
    server_day = server_day = datetime.now(CENTRAL_TZ).date() if not p.day else p.day
    server_ts = datetime.utcnow().isoformat() + "Z" if not p.timestamp else p.timestamp

    with engine.begin() as conn:
        # Check if this device has already submitted a username today
        device_row = conn.execute(
            text("""
                SELECT minecraft_username FROM step_ingest
                WHERE device_id = :device_id AND day = :day
                ORDER BY created_at ASC LIMIT 1
            """),
            {"device_id": p.device_id, "day": server_day}
        ).fetchone()

        if device_row is not None:
            # Device already submitted a username today
            first_username = device_row[0]
            if first_username != p.minecraft_username:
                # Only allow submissions for the first username of the day
                return {"ok": False, "reason": "Device already submitted a different username today", "device_id": p.device_id, "day": server_day, "first_username": first_username}

        # Otherwise, proceed with upsert logic for username
        # Check for existing record for this username
        row = conn.execute(
            text("""
                SELECT day, steps_today FROM step_ingest
                WHERE minecraft_username = :minecraft_username
                ORDER BY created_at DESC LIMIT 1
            """),
            {"minecraft_username": p.minecraft_username}
        ).fetchone()

        should_upsert = False
        is_new_day = False
        if row is None:
            # No record for this username, insert
            should_upsert = True
            is_new_day = True
        else:
            prev_day, prev_steps = row
            if str(server_day) != str(prev_day):
                should_upsert = True
                is_new_day = True
            elif int(p.steps_today) > prev_steps:
                should_upsert = True

        if should_upsert:
            # Delete all previous entries for this username
            conn.execute(
                text("""
                    DELETE FROM step_ingest WHERE minecraft_username = :minecraft_username
                """),
                {"minecraft_username": p.minecraft_username}
            )
            # Insert the new record
            conn.execute(
                text("""
                    INSERT INTO step_ingest (minecraft_username, device_id, day, steps_today, source)
                    VALUES (:minecraft_username, :device_id, :day, :steps_today, :source)
                """),
                {
                    "minecraft_username": p.minecraft_username,
                    "device_id": p.device_id,
                    "day": server_day,
                    "steps_today": int(p.steps_today),
                    "source": p.source,
                },
            )
            return {"ok": True, "device_id": p.device_id, "day": server_day, "steps_today": p.steps_today, "upserted": True, "new_day": is_new_day}
        else:
            return {"ok": True, "device_id": p.device_id, "day": server_day, "steps_today": p.steps_today, "upserted": False, "reason": "Not higher than previous for this day"}

@app.get("/v1/latest/{device_id}")
def latest(device_id: str, x_api_key: str | None = Header(default=None, alias="X-API-Key")):
    require_api_key(x_api_key)

    with engine.begin() as conn:
        row = conn.execute(
            text("""
            SELECT
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                created_at
            FROM step_ingest
            WHERE device_id = :device_id
            ORDER BY created_at DESC
            LIMIT 1
            """),
            {"device_id": device_id},
        ).mappings().first()

    if not row:
        raise HTTPException(status_code=404, detail="No data for device_id")

    d = dict(row)
    if d.get("created_at"):
        d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
    return d



@app.get("/v1/admin/all")
def admin_all(
    limit: int = 100,
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
):
    require_api_key(x_api_key)

    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                id,
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                created_at
            FROM step_ingest
            ORDER BY created_at DESC
            LIMIT :limit
            """),
            {"limit": limit},
        ).mappings().all()

    # ✅ Convert RowMapping → dict and convert created_at to CST
    out: list[dict] = []
    for r in rows:
        d = dict(r)
        if d.get("created_at"):
            d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
        out.append(d)

    return out

