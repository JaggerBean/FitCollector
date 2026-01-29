import os
import logging
from datetime import datetime, timedelta
from sqlalchemy import create_engine, text

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///fitcollector.db")
IS_SQLITE = DATABASE_URL.startswith("sqlite")
engine = create_engine(DATABASE_URL, future=True)


"""Database schema definitions and initialization."""

def init_db() -> None:
    """Initialize database schema with all migrations."""
    with engine.begin() as conn:
        # Create step_claims table for reward claim tracking
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS step_claims (
            id BIGSERIAL PRIMARY KEY,
            minecraft_username TEXT NOT NULL,
            server_name TEXT NOT NULL,
            day DATE NOT NULL,
            min_steps BIGINT NOT NULL DEFAULT 0,
            claimed BOOLEAN NOT NULL DEFAULT FALSE,
            claimed_at TIMESTAMPTZ,
            UNIQUE(minecraft_username, server_name, day, min_steps)
        );
        """))

        # Migration: add min_steps if missing
        conn.execute(text("""
        ALTER TABLE step_claims
        ADD COLUMN IF NOT EXISTS min_steps BIGINT NOT NULL DEFAULT 0;
        """))

        # Migration: drop legacy unique constraint (per-day) and add per-tier unique index
        if not IS_SQLITE:
            conn.execute(text("""
            ALTER TABLE step_claims
            DROP CONSTRAINT IF EXISTS step_claims_minecraft_username_server_name_day_key;
            """))

            conn.execute(text("""
            DROP INDEX IF EXISTS idx_step_claims_unique_user_server_day;
            """))

        # SQLite requires table rebuild to drop unique constraints
        if IS_SQLITE:
            cols = [row[1] for row in conn.execute(text("PRAGMA table_info(step_claims)"))]
            if cols:
                conn.execute(text("ALTER TABLE step_claims RENAME TO step_claims_old"))
                conn.execute(text("""
                CREATE TABLE step_claims (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    minecraft_username TEXT NOT NULL,
                    server_name TEXT NOT NULL,
                    day DATE NOT NULL,
                    min_steps BIGINT NOT NULL DEFAULT 0,
                    claimed BOOLEAN NOT NULL DEFAULT FALSE,
                    claimed_at TIMESTAMPTZ,
                    UNIQUE(minecraft_username, server_name, day, min_steps)
                );
                """))
                if "min_steps" in cols:
                    conn.execute(text("""
                    INSERT INTO step_claims (id, minecraft_username, server_name, day, min_steps, claimed, claimed_at)
                    SELECT id, minecraft_username, server_name, day, min_steps, claimed, claimed_at
                    FROM step_claims_old
                    """))
                else:
                    conn.execute(text("""
                    INSERT INTO step_claims (id, minecraft_username, server_name, day, min_steps, claimed, claimed_at)
                    SELECT id, minecraft_username, server_name, day, 0, claimed, claimed_at
                    FROM step_claims_old
                    """))
                conn.execute(text("DROP TABLE step_claims_old"))

        conn.execute(text("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_step_claims_unique_user_server_day_tier
        ON step_claims(minecraft_username, server_name, day, min_steps);
        """))
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

        # 5b) Migration: add owner_user_id if missing
        conn.execute(text("""
        ALTER TABLE api_keys
        ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_api_keys_key
        ON api_keys(key);
        """))

        # 5c) Create servers table for metadata + ownership
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS servers (
            id BIGSERIAL PRIMARY KEY,
            server_name TEXT UNIQUE NOT NULL,
            owner_user_id BIGINT NOT NULL,
            owner_name TEXT NOT NULL,
            owner_email TEXT NOT NULL,
            server_address TEXT,
            server_version TEXT,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_servers_owner
        ON servers(owner_user_id);
        """))

        # 5d) Migration: add privacy + invite code to servers
        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS is_private BOOLEAN DEFAULT FALSE;
        """))

        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS invite_code TEXT;
        """))

        # 5e) Migration: add inactivity cleanup settings
        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS inactive_prune_enabled BOOLEAN DEFAULT FALSE;
        """))

        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS inactive_prune_days INTEGER;
        """))

        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS inactive_prune_mode TEXT DEFAULT 'deactivate';
        """))

        # 5f) Migration: add claim buffer settings (days back allowed to claim)
        conn.execute(text("""
        ALTER TABLE servers
        ADD COLUMN IF NOT EXISTS claim_buffer_days INTEGER DEFAULT 1;
        """))

        conn.execute(text("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_servers_invite_code
        ON servers(invite_code)
        WHERE invite_code IS NOT NULL;
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

        # 10) Create users and sessions for web login
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS users (
            id BIGSERIAL PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            name TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS user_sessions (
            id BIGSERIAL PRIMARY KEY,
            user_id BIGINT NOT NULL,
            token_hash TEXT UNIQUE NOT NULL,
            created_at TIMESTAMPTZ DEFAULT NOW(),
            last_used TIMESTAMPTZ
        );
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_user_sessions_user
        ON user_sessions(user_id);
        """))

        # 9) Create server_rewards table for reward tiers
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS server_rewards (
            id BIGSERIAL PRIMARY KEY,
            server_name TEXT NOT NULL,
            min_steps BIGINT NOT NULL,
            label TEXT NOT NULL,
            rewards_json TEXT NOT NULL,
            position INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_server_rewards_server
        ON server_rewards(server_name);
        """))

        # 11) Create push_notifications table for scheduled server messages
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS push_notifications (
            id BIGSERIAL PRIMARY KEY,
            server_name TEXT NOT NULL,
            message TEXT NOT NULL,
            scheduled_at TIMESTAMPTZ NOT NULL,
            scheduled_date DATE NOT NULL,
            created_by BIGINT,
            created_at TIMESTAMPTZ DEFAULT NOW()
        );
        """))

        conn.execute(text("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_push_notifications_server_day
        ON push_notifications(server_name, scheduled_date);
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_push_notifications_server_time
        ON push_notifications(server_name, scheduled_at);
        """))

        # 12) Track deliveries per device to avoid duplicate push messages
        conn.execute(text("""
        CREATE TABLE IF NOT EXISTS push_deliveries (
            id BIGSERIAL PRIMARY KEY,
            notification_id BIGINT NOT NULL,
            device_id TEXT NOT NULL,
            minecraft_username TEXT NOT NULL,
            server_name TEXT NOT NULL,
            delivered_at TIMESTAMPTZ DEFAULT NOW(),
            UNIQUE(notification_id, device_id)
        );
        """))

        conn.execute(text("""
        CREATE INDEX IF NOT EXISTS idx_push_deliveries_device
        ON push_deliveries(device_id, server_name);
        """))