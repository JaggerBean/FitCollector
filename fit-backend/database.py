"""Database schema definitions and initialization."""

import os
from sqlalchemy import create_engine, text

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///fitcollector.db")
engine = create_engine(DATABASE_URL, future=True)


def init_db() -> None:
    """Initialize database schema with all migrations."""
    with engine.begin() as conn:
        # 1) Create step_ingest table if it doesn't exist
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

        # Backfill old rows so you can later enforce NOT NULL
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

        # 4) Create indexes
        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_step_ingest_device_day
        ON step_ingest(device_id, day);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_step_ingest_mc_day
        ON step_ingest(minecraft_username, day);
        """))

        # 4a) Add unique constraint to prevent duplicate entries from multiple devices
        # This ensures only one entry per username/server/day combination
        conn.execute(text("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_step_ingest_unique_user_server_day
        ON step_ingest(minecraft_username, server_name, day)
        WHERE minecraft_username IS NOT NULL AND server_name IS NOT NULL;
        """))

        # 5) Create api_keys table for per-server authentication
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

        # 5a) Migration: add max_players column if missing (NULL = unlimited)
        conn.execute(text("""
        ALTER TABLE api_keys
        ADD COLUMN IF NOT EXISTS max_players INTEGER;
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_api_keys_key
        ON api_keys(key);
        """))

        # 6) Create player_keys table for per-player authentication
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

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_player_keys_key
        ON player_keys(key);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_player_keys_device_server
        ON player_keys(device_id, server_name);
        """))

        # 7) Create bans table for player bans
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS bans (
            id BIGSERIAL PRIMARY KEY,
            ban_group_id TEXT NOT NULL,
            server_name TEXT NOT NULL,
            minecraft_username TEXT,
            device_id TEXT,
            reason TEXT,
            banned_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        # 7a) Migration: add ban_group_id if missing
        conn.execute(text("""
        ALTER TABLE bans
        ADD COLUMN IF NOT EXISTS ban_group_id TEXT;
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_bans_server_username
        ON bans(server_name, minecraft_username);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_bans_server_device
        ON bans(server_name, device_id);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_bans_ban_group_id
        ON bans(ban_group_id);
        """))
        # 8) Create key_recovery_audit table for tracking key recoveries
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS key_recovery_audit (
            id BIGSERIAL PRIMARY KEY,
            device_id TEXT NOT NULL,
            minecraft_username TEXT NOT NULL,
            server_name TEXT NOT NULL,
            recovered_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_key_recovery_device_server
        ON key_recovery_audit(device_id, server_name);
        """))