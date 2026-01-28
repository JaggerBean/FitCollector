"""Utility functions for token hashing and generation."""

import hashlib
import secrets
import os
import smtplib
from email.message import EmailMessage
from typing import Optional


def hash_token(token: str) -> str:
    """Hash a token using SHA256."""
    return hashlib.sha256(token.encode()).hexdigest()


def generate_opaque_token(length: int = 32) -> str:
    """Generate a random opaque token."""
    return secrets.token_urlsafe(length)


def generate_invite_code(length: int = 8) -> str:
    """Generate a short invite code for private servers."""
    return secrets.token_hex(max(1, length // 2))


def generate_salt(length: int = 16) -> str:
    return secrets.token_hex(length)


def hash_password(password: str, salt: str) -> str:
    """Hash password with PBKDF2-HMAC-SHA256."""
    iterations = int(os.getenv("PASSWORD_ITERATIONS", "120000"))
    dk = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), iterations)
    return dk.hex()


def _truthy_env(value: Optional[str], default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _get_smtp_config() -> Optional[dict]:
    user = os.getenv("GMAIL_USER") or os.getenv("SMTP_USER") or os.getenv("MAIL_USER")
    pwd = os.getenv("GMAIL_PASS") or os.getenv("SMTP_PASS") or os.getenv("MAIL_PASS")
    if not user or not pwd:
        return None

    mail_from = os.getenv("SMTP_FROM") or os.getenv("MAIL_FROM") or user
    server = os.getenv("SMTP_SERVER", "smtp.gmail.com")
    port = int(os.getenv("SMTP_PORT", "587"))
    starttls = _truthy_env(os.getenv("SMTP_STARTTLS"), True)
    ssl_tls = _truthy_env(os.getenv("SMTP_SSL_TLS"), False)
    if starttls and ssl_tls:
        ssl_tls = False

    return {
        "user": user,
        "password": pwd,
        "from": mail_from,
        "server": server,
        "port": port,
        "starttls": starttls,
        "ssl_tls": ssl_tls,
    }


def send_api_key_email(to_email: str, server_name: str, api_key: str, message: str) -> bool:
    config = _get_smtp_config()
    if config is None:
        return False

    msg = EmailMessage()
    msg["Subject"] = "Your StepCraft API Key"
    msg["From"] = config["from"]
    msg["To"] = to_email
    msg.set_content(
        f"Thank you for registering your server: {server_name}\n"
        f"Your API Key: {api_key}\n"
        f"{message}"
    )

    if config["ssl_tls"]:
        with smtplib.SMTP_SSL(config["server"], config["port"]) as smtp:
            smtp.login(config["user"], config["password"])
            smtp.send_message(msg)
    else:
        with smtplib.SMTP(config["server"], config["port"]) as smtp:
            if config["starttls"]:
                smtp.starttls()
            smtp.login(config["user"], config["password"])
            smtp.send_message(msg)
    return True
