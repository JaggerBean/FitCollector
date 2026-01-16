"""Utility functions for token hashing and generation."""

import hashlib
import secrets


def hash_token(token: str) -> str:
    """Hash a token using SHA256."""
    return hashlib.sha256(token.encode()).hexdigest()


def generate_opaque_token(length: int = 32) -> str:
    """Generate a random opaque token."""
    return secrets.token_urlsafe(length)
