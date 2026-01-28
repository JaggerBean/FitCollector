"""Authentication and authorization functions."""

import os
from fastapi import HTTPException, Header, Query
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


def require_user(
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token")
) -> dict:
    """Validate user session token and return user info."""
    token = None
    if x_user_token:
        token = x_user_token.strip()
    elif authorization and authorization.lower().startswith("bearer "):
        token = authorization.split(" ", 1)[1].strip()

    if not token:
        raise HTTPException(status_code=401, detail="Missing user token")

    token_hash = hash_token(token)
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT u.id, u.email, u.name
                FROM user_sessions s
                JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = :token_hash
                LIMIT 1
            """),
            {"token_hash": token_hash}
        ).fetchone()

        if not row:
            raise HTTPException(status_code=401, detail="Invalid user token")

        conn.execute(
            text("UPDATE user_sessions SET last_used = NOW() WHERE token_hash = :token_hash"),
            {"token_hash": token_hash}
        )

    return {"id": row[0], "email": row[1], "name": row[2]}


def require_server_access(
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    authorization: str | None = Header(default=None, alias="Authorization"),
    x_user_token: str | None = Header(default=None, alias="X-User-Token"),
    server: str | None = Query(default=None),
    server_name: str | None = Query(default=None),
) -> str:
    if x_api_key:
        try:
            return require_api_key(x_api_key)
        except HTTPException:
            if not authorization and not x_user_token:
                raise

    user = require_user(authorization=authorization, x_user_token=x_user_token)
    selected = (server or server_name or "").strip()
    if not selected:
        raise HTTPException(status_code=400, detail="Missing server")

    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id FROM servers WHERE server_name = :server AND owner_user_id = :user_id"),
            {"server": selected, "user_id": user["id"]}
        ).fetchone()
        if not row:
            raise HTTPException(status_code=403, detail="Not authorized for this server")

    return selected


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
