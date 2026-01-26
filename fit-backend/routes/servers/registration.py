"""Server registration and info endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from zoneinfo import ZoneInfo

from database import engine
from models import ServerRegistrationRequest, ApiKeyResponse
from utils import generate_opaque_token, hash_token
from auth import require_api_key, require_user

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()


@router.post("/v1/servers/register")
def register_server(request: ServerRegistrationRequest, user=Depends(require_user)) -> ApiKeyResponse:
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
                    INSERT INTO api_keys (key, server_name, active, owner_user_id)
                    VALUES (:key_hash, :server_name, :active, :owner_user_id)
                """),
                {
                    "key_hash": key_hash,
                    "server_name": request.server_name,
                    "active": True,
                    "owner_user_id": user["id"],
                }
            )

            conn.execute(
                text("""
                    INSERT INTO servers (server_name, owner_user_id, owner_name, owner_email, server_address, server_version)
                    VALUES (:server_name, :owner_user_id, :owner_name, :owner_email, :server_address, :server_version)
                """),
                {
                    "server_name": request.server_name,
                    "owner_user_id": user["id"],
                    "owner_name": request.owner_name,
                    "owner_email": request.owner_email,
                    "server_address": request.server_address,
                    "server_version": request.server_version,
                }
            )
            
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


@router.get("/v1/servers/info")
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
