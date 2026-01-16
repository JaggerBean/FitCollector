from datetime import datetime, date
from typing import Optional

from fastapi import FastAPI, HTTPException, Depends
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text
from fastapi.middleware.cors import CORSMiddleware
import os
import secrets
import hashlib
from fastapi import Header
from zoneinfo import ZoneInfo
from datetime import timezone


CENTRAL_TZ = ZoneInfo("America/Chicago")


DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///fitcollector.db")
MASTER_ADMIN_KEY = os.getenv("MASTER_ADMIN_KEY", "change-me-in-production")  # Master admin key
engine = create_engine(DATABASE_URL, future=True)


# Token hashing utilities for opaque tokens
def hash_token(token: str) -> str:
    """Hash a token using SHA256."""
    return hashlib.sha256(token.encode()).hexdigest()


def generate_opaque_token(length: int = 32) -> str:
    """Generate a random opaque token."""
    return secrets.token_urlsafe(length)

app = FastAPI(title="FitCollector Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

def require_api_key(x_api_key: str | None = Header(default=None, alias="X-API-Key")):
    """Validate API key and return the server name."""
    if not x_api_key:
        raise HTTPException(status_code=401, detail="Missing API key")
    
    with engine.begin() as conn:
        key_row = conn.execute(
            text("SELECT server_name FROM api_keys WHERE key = :key AND active = TRUE"),
            {"key": x_api_key}
        ).fetchone()
        
        if not key_row:
            raise HTTPException(status_code=401, detail="Invalid API key")
        
        # Update last_used timestamp
        conn.execute(
            text("UPDATE api_keys SET last_used = NOW() WHERE key = :key"),
            {"key": x_api_key}
        )
    
    return key_row[0]


def require_master_admin(x_admin_key: str | None = Header(default=None, alias="X-Admin-Key")):
    """Validate master admin key. Only you should have this."""
    if not x_admin_key:
        raise HTTPException(status_code=401, detail="Missing admin key")
    
    if x_admin_key != MASTER_ADMIN_KEY:
        raise HTTPException(status_code=401, detail="Invalid admin key")
    
    return True

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

        # 3) Migration: add server_name if missing
        conn.execute(text("""
        ALTER TABLE step_ingest
        ADD COLUMN IF NOT EXISTS server_name TEXT;
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

        # 4) Create api_keys table for per-server authentication
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS api_keys (
            id BIGSERIAL PRIMARY KEY,
            key TEXT UNIQUE NOT NULL,
            server_name TEXT NOT NULL,
            active BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMPTZ DEFAULT NOW(),
            last_used TIMESTAMPTZ
        );
        """))

        # Create index for faster lookups
        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_api_keys_key
        ON api_keys(key);
        """))

        # 5) Create player_keys table for per-player authentication
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS player_keys (
            id BIGSERIAL PRIMARY KEY,
            key TEXT UNIQUE NOT NULL,
            device_id TEXT NOT NULL,
            minecraft_username TEXT NOT NULL,
            server_name TEXT NOT NULL,
            active BOOLEAN DEFAULT TRUE,
            created_at TIMESTAMPTZ DEFAULT NOW(),
            last_used TIMESTAMPTZ,
            UNIQUE(device_id, server_name)
        );
        """))

        # Create indexes for player key lookups
        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_player_keys_key
        ON player_keys(key);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_player_keys_device_server
        ON player_keys(device_id, server_name);
        """))




def require_api_key(x_api_key: str | None = Header(default=None, alias="X-API-Key")):
    """
    Validate server API key (opaque token).
    Returns server_name on success.
    """
    if not x_api_key:
        raise HTTPException(status_code=401, detail="Missing API key")
    
    key_hash = hash_token(x_api_key)
    
    with engine.begin() as conn:
        key_row = conn.execute(
            text("SELECT server_name FROM api_keys WHERE key = :key_hash AND active = TRUE"),
            {"key_hash": key_hash}
        ).fetchone()
        
        if not key_row:
            raise HTTPException(status_code=401, detail="Invalid API key")
        
        # Update last_used timestamp
        conn.execute(
            text("UPDATE api_keys SET last_used = NOW() WHERE key = :key_hash"),
            {"key_hash": key_hash}
        )
    
    return key_row[0]


def get_or_create_player_key(device_id: str, minecraft_username: str, server_name: str) -> str:
    """
    Get existing player key or create new one.
    Auto-updates username if device previously registered with different name.
    """
    with engine.begin() as conn:
        # Check if device_id has a key for this server
        existing = conn.execute(
            text("""
                SELECT key, minecraft_username FROM player_keys
                WHERE device_id = :device_id AND server_name = :server
                LIMIT 1
            """),
            {"device_id": device_id, "server": server_name}
        ).fetchone()
        
        if existing:
            key, old_username = existing
            
            # If username changed, update it
            if old_username != minecraft_username:
                conn.execute(
                    text("""
                        UPDATE player_keys 
                        SET minecraft_username = :new_username
                        WHERE key = :key
                    """),
                    {"new_username": minecraft_username, "key": key}
                )
            
            return key
        
        # First time: create new player key
        new_key = secrets.token_urlsafe(32)
        conn.execute(
            text("""
                INSERT INTO player_keys (key, device_id, minecraft_username, server_name, active)
                VALUES (:key, :device_id, :username, :server, TRUE)
            """),
            {
                "key": new_key,
                "device_id": device_id,
                "username": minecraft_username,
                "server": server_name
            }
        )
        return new_key


def validate_player_key(device_id: str, player_api_key: str, server_name: str) -> str:
    """
    Validate player key and return current minecraft_username.
    Updates last_used timestamp.
    """
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT minecraft_username FROM player_keys
                WHERE key = :key 
                  AND device_id = :device_id
                  AND server_name = :server
                  AND active = TRUE
            """),
            {
                "key": player_api_key,
                "device_id": device_id,
                "server": server_name
            }
        ).fetchone()
        
        if not row:
            raise HTTPException(
                status_code=401, 
                detail="Invalid player API key"
            )
        
        # Update last_used timestamp
        conn.execute(
            text("UPDATE player_keys SET last_used = NOW() WHERE key = :key"),
            {"key": player_api_key}
        )
    
    return row[0]


def validate_and_get_server(device_id: str, player_api_key: str) -> tuple[str, str]:
    """
    Validate user token (opaque token) and return (server_name, minecraft_username).
    Scoped: can only access their own data.
    Updates last_used timestamp.
    """
    token_hash = hash_token(player_api_key)
    
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT server_name, minecraft_username FROM player_keys
                WHERE key = :key_hash 
                  AND device_id = :device_id
                  AND active = TRUE
            """),
            {
                "key_hash": token_hash,
                "device_id": device_id
            }
        ).fetchone()
        
        if not row:
            raise HTTPException(
                status_code=401, 
                detail="Invalid user token or device mismatch"
            )
        
        # Update last_used timestamp
        conn.execute(
            text("UPDATE player_keys SET last_used = NOW() WHERE key = :key_hash"),
            {"key_hash": token_hash}
        )
    
    return row[0], row[1]  # server_name, minecraft_username


@app.on_event("startup")
def on_startup():
    init_db()


class IngestPayload(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    steps_today: int = Field(..., ge=0, le=500_000)
    player_api_key: str = Field(..., min_length=20)  # Required - must register first
    day: Optional[str] = None
    source: Optional[str] = "health_connect"
    timestamp: Optional[str] = None


class PlayerRegistrationRequest(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    server_name: str = Field(..., min_length=3, max_length=50)


class PlayerApiKeyResponse(BaseModel):
    player_api_key: str
    minecraft_username: str
    device_id: str
    server_name: str
    message: str = "Save this key securely. You'll need it for all future submissions."


class ServerRegistrationRequest(BaseModel):
    server_name: str = Field(..., min_length=3, max_length=50)
    owner_name: str = Field(..., min_length=2, max_length=100)
    owner_email: str = Field(..., min_length=5, max_length=255)
    server_address: Optional[str] = Field(None, min_length=5, max_length=255)
    server_version: Optional[str] = None


class ApiKeyResponse(BaseModel):
    api_key: str
    server_name: str
    message: str = "Store this key securely. You won't be able to see it again!"


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/v1/players/register")
def register_player(request: PlayerRegistrationRequest):
    """
    Register a new player and get their player API key.
    
    This endpoint is public (no authentication required).
    Players call this once with their device_id, username, and server name.
    Returns a unique API key to use for all future step submissions.
    """
    
    # Generate unique player API key
    player_api_key = secrets.token_urlsafe(32)
    
    try:
        with engine.begin() as conn:
            # Check if server exists
            server_exists = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name AND active = TRUE"),
                {"server_name": request.server_name}
            ).fetchone()
            
            if not server_exists:
                raise HTTPException(status_code=404, detail=f"Server '{request.server_name}' not found. Register with a valid server name.")
            
            # Check if this device already has a key for this server
            existing_key = conn.execute(
                text("""
                    SELECT key FROM player_keys 
                    WHERE device_id = :device_id AND server_name = :server_name AND active = TRUE
                """),
                {"device_id": request.device_id, "server_name": request.server_name}
            ).fetchone()
            
            if existing_key:
                raise HTTPException(
                    status_code=409, 
                    detail=f"Device already registered for '{request.server_name}'. Use existing key or contact admin to reset."
                )
            
            # Generate opaque token and hash it before storing
            plaintext_token = generate_opaque_token()
            token_hash = hash_token(plaintext_token)
            
            # Insert new player token (hashed)
            conn.execute(
                text("""
                    INSERT INTO player_keys (key, device_id, minecraft_username, server_name, active)
                    VALUES (:key_hash, :device_id, :username, :server, TRUE)
                """),
                {
                    "key_hash": token_hash,
                    "device_id": request.device_id,
                    "username": request.minecraft_username,
                    "server": request.server_name
                }
            )
        
        # Return the plaintext token ONLY on creation (never again)
        return PlayerApiKeyResponse(
            player_api_key=plaintext_token,
            minecraft_username=request.minecraft_username,
            device_id=request.device_id,
            server_name=request.server_name,
            message="Save this token securely in your app. You'll need it for all future step submissions. You cannot retrieve it if lost."
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to register player: {str(e)}")


@app.post("/v1/servers/register")
def register_server(request: ServerRegistrationRequest):
    """
    Register a new Minecraft server and get an API key (opaque token).
    
    This endpoint is public (no authentication required) to allow new server owners to register.
    The API key returned should be stored securely by the server owner.
    Can only access data from this server.
    """
    
    # Generate opaque token and hash it
    plaintext_key = generate_opaque_token()
    key_hash = hash_token(plaintext_key)
    
    try:
        with engine.begin() as conn:
            # Check if server name already exists
            existing = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name AND active = TRUE"),
                {"server_name": request.server_name}
            ).fetchone()
            
            if existing:
                raise HTTPException(status_code=409, detail=f"Server name '{request.server_name}' already registered")
            
            # Insert new API key (hashed)
            conn.execute(
                text("""
                    INSERT INTO api_keys (key, server_name, active)
                    VALUES (:key_hash, :server_name, :active)
                """),
                {"key_hash": key_hash, "server_name": request.server_name, "active": True}
            )
            
            # TODO: Store additional server metadata (owner_name, owner_email, server_address, etc.)
            # For now, we're just using server_name in the api_keys table
            # You might want to create a separate 'servers' table for this metadata
            
            # TODO: Send email to owner_email with the API key
            # Example: send_email(request.owner_email, plaintext_key, request.server_name)
        
        # Return the plaintext key ONLY on creation (never again)
        return ApiKeyResponse(
            api_key=plaintext_key,
            server_name=request.server_name,
            message="Store this key securely in your server config. You won't be able to see it again!"
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to register server: {str(e)}")


@app.get("/v1/servers/info")
def get_server_info(server_name: str = Depends(require_api_key)):
    """Get info about the authenticated server."""
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT server_name, active, created_at, last_used
                FROM api_keys
                WHERE server_name = :server_name
                LIMIT 1
            """),
            {"server_name": server_name}
        ).mappings().first()
    
    if not row:
        raise HTTPException(status_code=404, detail="Server not found")
    
    d = dict(row)
    if d.get("created_at"):
        d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
    if d.get("last_used"):
        d["last_used"] = d["last_used"].astimezone(CENTRAL_TZ).isoformat()
    
    return d


@app.get("/v1/servers/players")
def get_server_players(
    limit: int = 1000,
    server_name: str = Depends(require_api_key),
):
    """
    Get all player data for this server.
    Requires server API key. Returns all step submissions scoped to this server.
    """
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
            SELECT
                minecraft_username,
                device_id,
                day::text AS day,
                steps_today,
                source,
                created_at
            FROM step_ingest
            WHERE server_name = :server_name
            ORDER BY minecraft_username, day DESC
            LIMIT :limit
            """),
            {"server_name": server_name, "limit": limit},
        ).mappings().all()

    # Convert timestamps to ISO format in server timezone
    out: list[dict] = []
    for r in rows:
        d = dict(r)
        if d.get("created_at"):
            d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
        out.append(d)

    return {
        "server_name": server_name,
        "player_count": len(set(row["minecraft_username"] for row in out)),
        "total_records": len(out),
        "data": out
    }


@app.post("/v1/ingest")
def ingest(p: IngestPayload):
    """
    Ingest step data. Requires player to be registered first via /v1/players/register.
    
    Validates player API key and processes step data.
    Auto-updates username if device previously registered with different name.
    """
    
    # Validate player key and get server_name and current_username
    server_name, current_username = validate_and_get_server(p.device_id, p.player_api_key)
    
    # If username changed, auto-update it
    if current_username != p.minecraft_username:
        with engine.begin() as conn:
            conn.execute(
                text("""
                    UPDATE player_keys 
                    SET minecraft_username = :new_username
                    WHERE key = :key
                """),
                {"new_username": p.minecraft_username, "key": player_api_key}
            )
        current_username = p.minecraft_username
    
    server_day = datetime.now(CENTRAL_TZ).date() if not p.day else p.day
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
                response = {"ok": False, "reason": "Device already submitted a different username today", "device_id": p.device_id, "day": server_day, "first_username": first_username}
                if is_first_submission:
                    response["player_api_key"] = player_api_key
                return response

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
            # Insert the new record with server_name
            conn.execute(
                text("""
                    INSERT INTO step_ingest (minecraft_username, device_id, day, steps_today, source, server_name)
                    VALUES (:minecraft_username, :device_id, :day, :steps_today, :source, :server_name)
                """),
                {
                    "minecraft_username": p.minecraft_username,
                    "device_id": p.device_id,
                    "day": server_day,
                    "steps_today": int(p.steps_today),
                    "source": p.source,
                    "server_name": server_name,
                },
            )
            return {"ok": True, "device_id": p.device_id, "day": server_day, "steps_today": p.steps_today, "upserted": True, "new_day": is_new_day}
        else:
            return {"ok": True, "device_id": p.device_id, "day": server_day, "steps_today": p.steps_today, "upserted": False, "reason": "Not higher than previous for this day"}

@app.get("/v1/latest/{device_id}")
def latest(device_id: str, server_name: str = Depends(require_api_key)):
    # server_name is validated by require_api_key dependency

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
    limit: int = 1000,
    _: bool = Depends(require_master_admin),
):
    # Master admin only - restricted access
    # Shows ALL data from ALL servers grouped by server

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
                server_name,
                created_at
            FROM step_ingest
            ORDER BY server_name, created_at DESC
            LIMIT :limit
            """),
            {"limit": limit},
        ).mappings().all()

    # Group by server_name
    grouped: dict[str, list] = {}
    for r in rows:
        d = dict(r)
        if d.get("created_at"):
            d["created_at"] = d["created_at"].astimezone(CENTRAL_TZ).isoformat()
        
        srv = d["server_name"]
        if srv not in grouped:
            grouped[srv] = []
        grouped[srv].append(d)

    return grouped

