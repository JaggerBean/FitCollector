"""Utility functions for token hashing and generation."""

import hashlib
import secrets
import os
import smtplib
from pathlib import Path
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

        safe_message = message or ""
        logo_path = Path(__file__).resolve().parent / "Logo" / "logo.png"
        logo_bytes = None
        if logo_path.exists():
                logo_bytes = logo_path.read_bytes()

        subject = "Your StepCraft API Key"
        text_body = (
                "Thanks for registering your StepCraft server!\n\n"
                f"Server: {server_name}\n"
                f"API Key: {api_key}\n\n"
                "Next steps (Minecraft server):\n"
                "1) Join the server as an OP.\n"
                "2) Run /stepcraft admin_gui\n"
                "3) Open Settings -> Set API Key and paste:\n"
                f"   /stepcraft set_api_key {api_key}\n"
                "4) Confirm API Key Status shows Working.\n\n"
                "Alternative: edit config/stepcraft.properties and set api_key=<your key>.\n\n"
                f"{safe_message}\n"
        )

        html_body = f"""
        <div style=\"font-family: Inter, Arial, sans-serif; background: #0b0f14; color: #e2e8f0; padding: 32px;\">
            <div style=\"max-width: 640px; margin: 0 auto; background: #111827; border: 1px solid #1f2937; border-radius: 16px; overflow: hidden;\">
                <div style=\"padding: 24px 28px; background: #0f172a; border-bottom: 1px solid #1f2937;\">
                    <div style=\"display: flex; align-items: center; gap: 12px;\">
                        {"<img src=\"cid:stepcraft-logo\" alt=\"StepCraft\" style=\"height: 36px; width: auto;\" />" if logo_bytes else ""}
                        <div style=\"font-size: 18px; font-weight: 700; color: #a7f3d0;\">StepCraft</div>
                    </div>
                    <div style=\"margin-top: 8px; font-size: 14px; color: #cbd5f5;\">Your server API key is ready</div>
                </div>

                <div style=\"padding: 28px;\">
                    <p style=\"margin: 0 0 16px;\">Thanks for registering your StepCraft server! Here are your details:</p>
                    <div style=\"background: #0b1220; border: 1px solid #1f2937; border-radius: 12px; padding: 16px;\">
                        <div style=\"font-size: 13px; color: #94a3b8;\">Server</div>
                        <div style=\"font-size: 16px; font-weight: 600; margin-bottom: 12px;\">{server_name}</div>
                        <div style=\"font-size: 13px; color: #94a3b8;\">API Key</div>
                        <div style=\"font-size: 16px; font-weight: 700; color: #f8fafc; word-break: break-all;\">{api_key}</div>
                    </div>

                    <h3 style=\"margin: 22px 0 8px; font-size: 16px;\">Configure your Minecraft server</h3>
                    <ol style=\"margin: 0; padding-left: 18px; color: #cbd5f5;\">
                        <li>Join the server as an OP.</li>
                        <li>Run <strong>/stepcraft admin_gui</strong>.</li>
                        <li>Open <strong>Settings</strong> → <strong>Set API Key</strong> and paste:</li>
                    </ol>
                    <div style=\"margin: 10px 0 14px; padding: 12px 14px; background: #0b1220; border-radius: 10px; border: 1px dashed #334155; color: #e2e8f0; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;\">
                        /stepcraft set_api_key {api_key}
                    </div>
                    <p style=\"margin: 0 0 14px; color: #cbd5f5;\">Confirm <strong>API Key Status</strong> shows <strong>Working</strong>.</p>

                    <p style=\"margin: 0 0 10px; color: #94a3b8;\">Alternative: edit <strong>config/stepcraft.properties</strong> and set <code>api_key=&lt;your key&gt;</code>.</p>

                    {f"<div style=\"margin-top: 14px; color: #cbd5f5;\">{safe_message}</div>" if safe_message else ""}
                </div>

                <div style=\"padding: 16px 28px; border-top: 1px solid #1f2937; color: #64748b; font-size: 12px;\">
                    If you did not request this, you can ignore this email.
                </div>
            </div>
        </div>
        """

        msg = EmailMessage()
        msg["Subject"] = subject
        msg["From"] = config["from"]
        msg["To"] = to_email
        msg.set_content(text_body)
        msg.add_alternative(html_body, subtype="html")

        if logo_bytes:
                msg.get_payload()[1].add_related(logo_bytes, maintype="image", subtype="png", cid="stepcraft-logo")

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
