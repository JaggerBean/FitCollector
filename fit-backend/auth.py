"""Authentication and authorization functions."""

import os
from fastapi import HTTPException, Header
from sqlalchemy import text
from utils import hash_token, generate_opaque_token
from database import engine

MASTER_ADMIN_KEY = os.getenv("MASTER_ADMIN_KEY", "change-me-in-production")


def require_api_key(x_api_key: str | None = Header(default=None, alias="X-API-Key")) -> str:
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


def require_master_admin(x_admin_key: str | None = Header(default=None, alias="X-Admin-Key")) -> bool:
    """Validate master admin key. Only you should have this."""
    if not x_admin_key:
        raise HTTPException(status_code=401, detail="Missing admin key")
    
    if x_admin_key != MASTER_ADMIN_KEY:
        raise HTTPException(status_code=401, detail="Invalid admin key")
    
    return True


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
