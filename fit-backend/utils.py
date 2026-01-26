"""Utility functions for token hashing and generation."""

import hashlib
import secrets
import os


def hash_token(token: str) -> str:
    """Hash a token using SHA256."""
    return hashlib.sha256(token.encode()).hexdigest()


def generate_opaque_token(length: int = 32) -> str:
    """Generate a random opaque token."""
    return secrets.token_urlsafe(length)


def generate_salt(length: int = 16) -> str:
    return secrets.token_hex(length)


def hash_password(password: str, salt: str) -> str:
    """Hash password with PBKDF2-HMAC-SHA256."""
    iterations = int(os.getenv("PASSWORD_ITERATIONS", "120000"))
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), iterations)
    return dk.hex()
