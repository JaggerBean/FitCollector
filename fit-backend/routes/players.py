# Player endpoint: check and set claim status for today
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException, Depends, Query
from sqlalchemy import text
from zoneinfo import ZoneInfo
from datetime import datetime, timezone
from database import engine
from models import PlayerRegistrationRequest, PlayerApiKeyResponse, KeyRecoveryRequest
from utils import generate_opaque_token, hash_token
import json
from auth import require_api_key, validate_and_get_server

CENTRAL_TZ = ZoneInfo("America/Chicago")
router = APIRouter()

DEFAULT_REWARDS = [
    {"min_steps": 1000, "label": "Starter", "rewards": ["give {player} minecraft:bread 3"]},
    {"min_steps": 5000, "label": "Walker", "rewards": ["give {player} minecraft:iron_ingot 3"]},
    {"min_steps": 10000, "label": "Legend", "rewards": ["give {player} minecraft:diamond 1"]},
]


def _get_claim_buffer_days(server_name: str) -> int:
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT claim_buffer_days FROM servers WHERE server_name = :server"),
            {"server": server_name},
        ).fetchone()
    if not row or row[0] is None:
        return 1
    return max(0, int(row[0]))

# ...existing code...


@router.get("/v1/players/claim-status/{minecraft_username}")
def get_claim_status_player(
    minecraft_username: str,
    server_name: str = Query(...),
    day: str | None = Query(default=None),
    min_steps: int | None = Query(default=None),
):
    """
    Check if the player has claimed a specific reward tier for a day (app use).
    Defaults to today if day not provided.
    """
    if min_steps is None:
        raise HTTPException(status_code=400, detail="min_steps is required")
    if min_steps < 0:
        raise HTTPException(status_code=400, detail="min_steps must be >= 0")

    today = datetime.now(CENTRAL_TZ).date()
    if day:
        try:
            target_day = datetime.fromisoformat(day).date()
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid day format (YYYY-MM-DD)")
    else:
        target_day = today

    if target_day > today:
        raise HTTPException(status_code=400, detail="Cannot claim future days")

    with engine.begin() as conn:
        row = conn.execute(
            text("""
                SELECT claimed, claimed_at FROM step_claims
                WHERE minecraft_username = :username
                  AND server_name = :server
                  AND day = :day
                  AND min_steps = :min_steps
                LIMIT 1
            """),
            {
                "username": minecraft_username,
                "server": server_name,
                "day": target_day,
                "min_steps": min_steps,
            }
        ).fetchone()
    if row:
        return {"claimed": row[0], "claimed_at": row[1], "day": str(target_day), "min_steps": min_steps}
    else:
        return {"claimed": False, "claimed_at": None, "day": str(target_day), "min_steps": min_steps}


@router.get("/v1/players/claim-available")
def get_claim_available_player(
    device_id: str = Query(...),
    player_api_key: str = Query(...),
    debug: bool = Query(default=False),
):
    """
    List all claimable reward tiers within the claim window for the authenticated player.
    Returns items with day + min_steps + label (one entry per tier per day).
    """
    server_name, minecraft_username = validate_and_get_server(device_id, player_api_key)
    today = datetime.now(CENTRAL_TZ).date()
    buffer_days = _get_claim_buffer_days(server_name)
    days = [today - timedelta(days=offset) for offset in range(buffer_days + 1)]

    debug_info = {
        "server_name": server_name,
        "minecraft_username": minecraft_username,
        "buffer_days": buffer_days,
        "days": [str(d) for d in days],
        "tiers": [],
        "steps_by_day": {},
        "claimed_by_day": {},
    }

    with engine.begin() as conn:
        tiers = conn.execute(
            text("""
                SELECT min_steps, label
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).mappings().all()

        if debug:
            debug_info["tiers"] = [
                {"min_steps": t["min_steps"], "label": t["label"]}
                for t in tiers
            ]

        if not tiers:
            return {"server_name": server_name, "items": [], "debug": debug_info} if debug else {"server_name": server_name, "items": []}

        items = []
        for day in days:
            day_str = str(day)
            steps_row = conn.execute(
                text("""
                    SELECT steps_today FROM step_ingest
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                    LIMIT 1
                """),
                {"username": minecraft_username, "server": server_name, "day": day_str},
            ).fetchone()

            if not steps_row:
                if debug:
                    debug_info["steps_by_day"][day_str] = None
                continue

            steps = steps_row[0]
            if debug:
                debug_info["steps_by_day"][day_str] = steps

            eligible = [tier for tier in tiers if steps >= tier["min_steps"]]
            if not eligible:
                continue

            claimed_rows = conn.execute(
                text("""
                    SELECT min_steps FROM step_claims
                    WHERE minecraft_username = :username
                      AND server_name = :server
                      AND CAST(day AS TEXT) = :day
                      AND claimed = TRUE
                """),
                {"username": minecraft_username, "server": server_name, "day": day_str},
            ).fetchall()
            claimed_set = {row[0] for row in claimed_rows}
            if debug:
                debug_info["claimed_by_day"][day_str] = sorted(claimed_set)

            for tier in eligible:
                if tier["min_steps"] in claimed_set:
                    continue
                items.append(
                    {
                        "day": day_str,
                        "min_steps": tier["min_steps"],
                        "label": tier["label"],
                    }
                )

    return {"server_name": server_name, "items": items, "debug": debug_info} if debug else {"server_name": server_name, "items": items}


@router.get("/v1/players/push/next")
def get_next_push_notification(
    minecraft_username: str = Query(...),
    device_id: str = Query(...),
    server_name: str = Query(...),
    player_api_key: str = Query(...),
):
    """
    Fetch the next scheduled push message for a server and mark it delivered for this device.
    Requires the player_api_key for authorization.
    """
    token_hash = hash_token(player_api_key)
    with engine.begin() as conn:
        valid = conn.execute(
            text("""
                SELECT id FROM player_keys
                WHERE device_id = :device_id
                  AND minecraft_username = :username
                  AND server_name = :server
                  AND active = TRUE
                  AND key = :key_hash
                LIMIT 1
            """),
            {
                "device_id": device_id,
                "username": minecraft_username,
                "server": server_name,
                "key_hash": token_hash,
            },
        ).fetchone()

        if not valid:
            raise HTTPException(status_code=403, detail="Invalid player API key")

        row = conn.execute(
            text("""
                SELECT pn.id, pn.message, pn.scheduled_at
                FROM push_notifications pn
                LEFT JOIN push_deliveries pd
                  ON pd.notification_id = pn.id
                 AND pd.device_id = :device_id
                WHERE pn.server_name = :server
                  AND pn.scheduled_at <= :now
                  AND pd.id IS NULL
                ORDER BY pn.scheduled_at ASC
                LIMIT 1
            """),
            {"server": server_name, "device_id": device_id, "now": datetime.now(timezone.utc)},
        ).mappings().first()

        if not row:
            return {"message": None}

        conn.execute(
            text("""
                INSERT INTO push_deliveries (notification_id, device_id, minecraft_username, server_name)
                VALUES (:notification_id, :device_id, :username, :server)
            """),
            {
                "notification_id": row["id"],
                "device_id": device_id,
                "username": minecraft_username,
                "server": server_name,
            },
        )

    return {
        "message": row["message"],
        "server_name": server_name,
        "scheduled_at": row["scheduled_at"],
        "id": row["id"],
    }


@router.get("/v1/players/rewards")
def get_player_rewards(
    device_id: str = Query(...),
    server_name: str = Query(...),
    player_api_key: str = Query(...),
):
    """
    Fetch reward tiers for a server using a player's API key.
    """
    token_hash = hash_token(player_api_key)
    with engine.begin() as conn:
        valid = conn.execute(
            text("""
                SELECT id FROM player_keys
                WHERE device_id = :device_id
                  AND server_name = :server
                  AND active = TRUE
                  AND key = :key_hash
                LIMIT 1
            """),
            {
                "device_id": device_id,
                "server": server_name,
                "key_hash": token_hash,
            },
        ).fetchone()

        if not valid:
            raise HTTPException(status_code=403, detail="Invalid player API key")

        rows = conn.execute(
            text("""
                SELECT min_steps, label, rewards_json
                FROM server_rewards
                WHERE server_name = :server
                ORDER BY min_steps ASC, position ASC
            """),
            {"server": server_name},
        ).fetchall()

    if not rows:
        return {"server_name": server_name, "tiers": DEFAULT_REWARDS, "is_default": True}

    tiers = []
    for row in rows:
        try:
            rewards = json.loads(row[2]) if row[2] else []
        except Exception:
            rewards = []
        tiers.append({"min_steps": row[0], "label": row[1], "rewards": rewards})

    return {"server_name": server_name, "tiers": tiers, "is_default": False}

"""Player registration and authentication endpoints."""

@router.get("/v1/servers/available")
def get_available_servers(invite_code: str | None = Query(None)):
    """
    Get all available servers that users can register to.
    
    This endpoint is public (no authentication required).
    Returns a list of all active servers in the system.
    """
    try:
        with engine.begin() as conn:
            public_rows = conn.execute(
                text("""
                    SELECT s.server_name, s.created_at
                    FROM servers s
                    JOIN api_keys k ON k.server_name = s.server_name
                    WHERE k.active = TRUE
                      AND COALESCE(s.is_private, FALSE) = FALSE
                    ORDER BY s.server_name ASC
                """)
            ).mappings().all()

            servers = [dict(row) for row in public_rows]

            if invite_code:
                private_row = conn.execute(
                    text("""
                        SELECT s.server_name, s.created_at
                        FROM servers s
                        JOIN api_keys k ON k.server_name = s.server_name
                        WHERE k.active = TRUE
                          AND s.invite_code = :invite_code
                        LIMIT 1
                    """),
                    {"invite_code": invite_code}
                ).mappings().first()

                if private_row:
                    existing_names = {s["server_name"] for s in servers}
                    if private_row["server_name"] not in existing_names:
                        servers.append(dict(private_row))
        
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
                text("""
                    SELECT k.id, k.max_players, s.is_private, s.invite_code
                    FROM api_keys k
                    JOIN servers s ON s.server_name = k.server_name
                    WHERE k.server_name = :server_name AND k.active = TRUE
                """),
                {"server_name": request.server_name}
            ).fetchone()
            
            if not server_info:
                raise HTTPException(status_code=404, detail=f"Server '{request.server_name}' not found. Register with a valid server name.")
            
            max_players = server_info[1]  # Can be NULL (unlimited) or an integer
            is_private = bool(server_info[2])
            server_invite = server_info[3]

            if is_private and request.invite_code != server_invite:
                raise HTTPException(
                    status_code=403,
                    detail="Invalid invite code for this private server."
                )
            
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
                    SELECT steps_today FROM step_ingest
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