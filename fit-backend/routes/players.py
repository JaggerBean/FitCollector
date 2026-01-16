"""Player registration and authentication endpoints."""

from fastapi import APIRouter, HTTPException
from sqlalchemy import text
from database import engine
from models import PlayerRegistrationRequest, PlayerApiKeyResponse, KeysResponse

router = APIRouter()

@router.get("/v1/players/keys/{device_id}/{minecraft_username}", response_model=KeysResponse)
def get_keys(device_id: str, minecraft_username: str):
    """
    Fetch all API keys for a given device and Minecraft username.
    Returns a mapping of server_name -> player_api_key (hashed).
    """
    try:
        with engine.begin() as conn:
            rows = conn.execute(
                text("""
                    SELECT server_name, key
                    FROM player_keys
                    WHERE device_id = :device_id AND minecraft_username = :minecraft_username AND active = TRUE
                """),
                {"device_id": device_id, "minecraft_username": minecraft_username}
            ).mappings().all()

        servers = {row["server_name"]: row["key"] for row in rows}

        return KeysResponse(
            minecraft_username=minecraft_username,
            device_id=device_id,
            servers=servers
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to fetch keys: {str(e)}")
from utils import generate_opaque_token, hash_token

router = APIRouter()


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
