from datetime import datetime, date
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from fastapi.middleware.cors import CORSMiddleware
import os
import os
from fastapi import Header


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
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS step_ingest (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            device_id TEXT NOT NULL,
            day TEXT NOT NULL,
            steps_today INTEGER NOT NULL,
            source TEXT,
            created_at TEXT NOT NULL
        );
        """))
        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_step_ingest_device_day
        ON step_ingest(device_id, day);
        """))


@app.on_event("startup")
def on_startup():
    init_db()


class IngestPayload(BaseModel):
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
    server_day = date.today().isoformat() if not p.day else p.day
    server_ts = datetime.utcnow().isoformat() + "Z" if not p.timestamp else p.timestamp

    with engine.begin() as conn:
        conn.execute(
            text("""
            INSERT INTO step_ingest (device_id, day, steps_today, source, created_at)
            VALUES (:device_id, :day, :steps_today, :source, :created_at)
            """),
            {
                "device_id": p.device_id,
                "day": server_day,
                "steps_today": int(p.steps_today),
                "source": p.source,
                "created_at": server_ts,
            },
        )

    return {"ok": True, "device_id": p.device_id, "day": server_day, "steps_today": p.steps_today}

@app.get("/v1/latest/{device_id}")
def latest(device_id: str, x_api_key: str | None = Header(default=None, alias="X-API-Key")):
    require_api_key(x_api_key)

    with engine.begin() as conn:
        row = conn.execute(
            text("""
            SELECT device_id, day, steps_today, source, created_at
            FROM step_ingest
            WHERE device_id = :device_id
            ORDER BY id DESC
            LIMIT 1
            """),
            {"device_id": device_id},
        ).mappings().first()

    if not row:
        raise HTTPException(status_code=404, detail="No data for device_id")

    return dict(row)
