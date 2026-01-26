"""Authentication endpoints for web users."""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr, Field
from sqlalchemy import text

from database import engine
from utils import generate_opaque_token, hash_token, generate_salt, hash_password
from auth import require_user
from fastapi import Depends

router = APIRouter()


class RegisterRequest(BaseModel):
    email: EmailStr
    name: str = Field(..., min_length=2, max_length=100)
    password: str = Field(..., min_length=8, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=8, max_length=128)


@router.post("/v1/auth/register")
def register_user(request: RegisterRequest):
    with engine.begin() as conn:
        existing = conn.execute(
            text("SELECT id FROM users WHERE email = :email"),
            {"email": request.email.lower()}
        ).fetchone()
        if existing:
            raise HTTPException(status_code=409, detail="Email already registered")

        salt = generate_salt()
        pwd_hash = hash_password(request.password, salt)
        conn.execute(
            text("""
                INSERT INTO users (email, name, password_hash, password_salt)
                VALUES (:email, :name, :password_hash, :password_salt)
            """),
            {
                "email": request.email.lower(),
                "name": request.name,
                "password_hash": pwd_hash,
                "password_salt": salt,
            }
        )

    return {"ok": True, "message": "Account created"}


@router.post("/v1/auth/login")
def login_user(request: LoginRequest):
    with engine.begin() as conn:
        row = conn.execute(
            text("SELECT id, password_hash, password_salt FROM users WHERE email = :email"),
            {"email": request.email.lower()}
        ).fetchone()
        if not row:
            raise HTTPException(status_code=401, detail="Invalid credentials")

        user_id, pwd_hash, salt = row[0], row[1], row[2]
        if hash_password(request.password, salt) != pwd_hash:
            raise HTTPException(status_code=401, detail="Invalid credentials")

        token = generate_opaque_token()
        token_hash = hash_token(token)
        conn.execute(
            text("""
                INSERT INTO user_sessions (user_id, token_hash, last_used)
                VALUES (:user_id, :token_hash, NOW())
            """),
            {"user_id": user_id, "token_hash": token_hash}
        )

    return {"token": token}


@router.get("/v1/auth/me")
def me(user=Depends(require_user)):
    return {"id": user["id"], "email": user["email"], "name": user["name"]}
