"""Server registration and info endpoints."""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from zoneinfo import ZoneInfo

from database import engine
from models import ServerRegistrationRequest, ApiKeyResponse
from utils import generate_opaque_token, hash_token, generate_invite_code
from auth import require_server_access, require_user

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
    invite_code = None
    
    try:
        with engine.begin() as conn:
            # Check if server name already exists
            existing = conn.execute(
                text("SELECT id FROM api_keys WHERE server_name = :server_name AND active = TRUE"),
                {"server_name": request.server_name}
            ).fetchone()
            
            if existing:
                raise HTTPException(status_code=409, detail=f"Server name '{request.server_name}' already registered")

            if request.is_private:
                provided = (request.invite_code or "").strip() or None
                if provided:
                    exists = conn.execute(
                        text("SELECT id FROM servers WHERE invite_code = :invite_code"),
                        {"invite_code": provided}
                    ).fetchone()
                    if exists:
                        raise HTTPException(status_code=409, detail="Invite code already in use. Choose another.")
                    invite_code = provided
                else:
                    for _ in range(5):
                        candidate = generate_invite_code()
                        exists = conn.execute(
                            text("SELECT id FROM servers WHERE invite_code = :invite_code"),
                            {"invite_code": candidate}
                        ).fetchone()
                        if not exists:
                            invite_code = candidate
                            break
                    if not invite_code:
                        raise HTTPException(status_code=500, detail="Failed to generate unique invite code. Try again.")
            
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
                    INSERT INTO servers (
                        server_name,
                        owner_user_id,
                        owner_name,
                        owner_email,
                        server_address,
                        server_version,
                        is_private,
                        invite_code
                    )
                    VALUES (
                        :server_name,
                        :owner_user_id,
                        :owner_name,
                        :owner_email,
                        :server_address,
                        :server_version,
                        :is_private,
                        :invite_code
                    )
                """),
                {
                    "server_name": request.server_name,
                    "owner_user_id": user["id"],
                    "owner_name": request.owner_name,
                    "owner_email": request.owner_email,
                    "server_address": request.server_address,
                    "server_version": request.server_version,
                    "is_private": request.is_private,
                    "invite_code": invite_code,
                }
            )
            
            # TODO: Send email to owner_email with the API key
            # Example: send_email(request.owner_email, plaintext_key, request.server_name)
        
        # Return the plaintext key ONLY on creation (never again)
        return ApiKeyResponse(
            api_key=plaintext_key,
            server_name=request.server_name,
            message="Store this key securely in your server config. You won't be able to see it again!",
            is_private=request.is_private,
            invite_code=invite_code,
        )
    
    except HTTPException:
        raise
    except IntegrityError as e:
        msg = str(e)
        if "invite_code" in msg or "idx_servers_invite_code" in msg:
            raise HTTPException(status_code=409, detail="Invite code already in use. Choose another.")
        raise HTTPException(status_code=500, detail="Failed to register server due to a database constraint.")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to register server: {str(e)}")
