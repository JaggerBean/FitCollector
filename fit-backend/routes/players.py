# Player endpoint: check and set claim status for today
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy import text
from zoneinfo import ZoneInfo
from database import engine
from models import PlayerRegistrationRequest, PlayerApiKeyResponse, KeyRecoveryRequest
from utils import generate_opaque_token, hash_token
from auth import require_api_key

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()

# ...existing code...


@router.get("/v1/players/claim-status/{minecraft_username}")
def get_claim_status_player(minecraft_username: str, server_name: str = Query(...)):
    """
    Check if the player has claimed their reward for yesterday (app use).
    Uses Central Time timezone to match server logic.
    """
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT claimed, claimed_at FROM step_claims
                WHERE minecraft_username = :username AND server_name = :server AND day = :yesterday
                LIMIT 1
            """),
            {"username": minecraft_username, "server": server_name, "yesterday": yesterday}
        ).fetchone()
    if row:
        return {"claimed": row[0], "claimed_at": row[1]}
    else:
        return {"claimed": False, "claimed_at": None}

"""Player registration and authentication endpoints."""

@router.get("/v1/servers/available")
def get_available_servers():
    """
    Get all available servers that users can register to.
    
    This endpoint is public (no authentication required).
    Returns a list of all active servers in the system.
    """
    try:
        with engine.begin() as conn:
            rows = conn.execute(
                text("""
                    SELECT server_name, created_at
                    FROM api_keys
                    WHERE active = TRUE
                    ORDER BY server_name ASC
                """)
            ).mappings().all()
        
        servers = [dict(row) for row in rows]
        
        return {
            "total_servers": len(servers),
            "servers": servers
        }
    
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch available servers: {str(e)}")


@router.post("/v1/players/register")
def register_player(request: PlayerRegistrationRequest) -> PlayerApiKeyResponse:
    """
    Register a new player and get their player API key.
    
    This endpoint is public (no authentication required).
    Players call this once with their device_id, username, and server name.
    Returns a unique API key to use for all future step submissions.
    """
    
    try:
        with engine.begin() as conn:
            # Check if server exists and get max_players limit
            server_info = conn.execute(
                text("SELECT id, max_players FROM api_keys WHERE server_name = :server_name AND active = TRUE"),
                {"server_name": request.server_name}
            ).fetchone()
            
            if not server_info:
                raise HTTPException(status_code=404, detail=f"Server '{request.server_name}' not found. Register with a valid server name.")
            
            max_players = server_info[1]  # Can be NULL (unlimited) or an integer
            
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
            
            # Check if server has reached player limit
            if max_players is not None:
                current_player_count = conn.execute(
                    text("""
                        SELECT COUNT(DISTINCT minecraft_username) 
                        FROM player_keys 
                        WHERE server_name = :server_name AND active = TRUE
                    """),
                    {"server_name": request.server_name}
                ).scalar()
                
                if current_player_count >= max_players:
                    raise HTTPException(
                        status_code=403,
                        detail=f"Server '{request.server_name}' has reached its maximum player limit ({max_players} players). Contact the server admin."
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

@router.post("/v1/players/recover-key")
def recover_key(request: KeyRecoveryRequest) -> PlayerApiKeyResponse:
    """
    Recover/reset a player API key for a specific server.
    
    This endpoint allows players to get a new API key if they've lost their original one.
    The device_id and minecraft_username combo identifies the player's identity.
    Returns a new plaintext key that must be saved immediately.
    """
    try:
        with engine.begin() as conn:
            # Check if this player is registered for this server
            existing_key = conn.execute(
                text("""
                    SELECT id FROM player_keys 
                    WHERE device_id = :device_id 
                      AND minecraft_username = :minecraft_username
                      AND server_name = :server_name 
                      AND active = TRUE
                """),
                {
                    "device_id": request.device_id,
                    "minecraft_username": request.minecraft_username,
                    "server_name": request.server_name
                }
            ).fetchone()
            
            if not existing_key:
                raise HTTPException(
                    status_code=404,
                    detail=f"No active registration found for this device/username on '{request.server_name}'"
                )
            
            # Generate new plaintext token and hash it
            plaintext_token = generate_opaque_token()
            token_hash = hash_token(plaintext_token)
            
            # Update the existing key with the new hashed token
            conn.execute(
                text("""
                    UPDATE player_keys 
                    SET key = :new_key_hash
                    WHERE device_id = :device_id 
                      AND minecraft_username = :minecraft_username
                      AND server_name = :server_name
                """),
                {
                    "new_key_hash": token_hash,
                    "device_id": request.device_id,
                    "minecraft_username": request.minecraft_username,
                    "server_name": request.server_name
                }
            )
            
            # Log the recovery event for audit purposes
            conn.execute(
                text("""
                    INSERT INTO key_recovery_audit (device_id, minecraft_username, server_name)
                    VALUES (:device_id, :minecraft_username, :server_name)
                """),
                {
                    "device_id": request.device_id,
                    "minecraft_username": request.minecraft_username,
                    "server_name": request.server_name
                }
            )
        
        # Return the new plaintext token
        return PlayerApiKeyResponse(
            player_api_key=plaintext_token,
            minecraft_username=request.minecraft_username,
            device_id=request.device_id,
            server_name=request.server_name,
            message="Your old API key has been revoked. Save this new token securely. You'll need it for all future step submissions."
        )
    
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to recover key: {str(e)}")

@router.get("/v1/players/steps-yesterday")
def get_steps_yesterday(minecraft_username: str = Query(...), player_api_key: str = Query(...)):
    """
    Get the number of steps the player had yesterday.
    Requires minecraft_username and player_api_key (plaintext).
    """
    from auth import hash_token
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo
    CENTRAL_TZ = ZoneInfo("America/Chicago")
    yesterday = (datetime.now(CENTRAL_TZ) - timedelta(days=1)).date()
    try:
        # Authenticate player by username and key
        token_hash = hash_token(player_api_key)
        with engine.begin() as conn:
            row = conn.execute(
                text("""
                    SELECT server_name FROM player_keys
                    WHERE minecraft_username = :username
                      AND key = :key_hash
                      AND active = TRUE
                    LIMIT 1
                """),
                {
                    "username": minecraft_username,
                    "key_hash": token_hash
                }
            ).fetchone()
            if not row:
                raise HTTPException(status_code=401, detail="Invalid username or API key")
            server_name = row[0]
            steps_row = conn.execute(
                text("""
                    SELECT steps FROM step_ingest
                    WHERE minecraft_username = :username
                      AND server_name = :server_name
                      AND day = :yesterday
                    LIMIT 1
                """),
                {
                    "username": minecraft_username,
                    "server_name": server_name,
                    "yesterday": yesterday
                }
            ).fetchone()
        steps = steps_row[0] if steps_row else 0
        return {"minecraft_username": minecraft_username, "server_name": server_name, "steps_yesterday": steps, "day": str(yesterday)}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch steps: {str(e)}")