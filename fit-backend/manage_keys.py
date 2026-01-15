#!/usr/bin/env python3
"""
Utility script to manage API keys for FitCollector backend.
Usage:
  Server keys (X-API-Key header):
    python manage_keys.py server add <server_name> [key_length]
    python manage_keys.py server list
    python manage_keys.py server disable <key>
    python manage_keys.py server enable <key>
  
  Player keys (in request body):
    python manage_keys.py player list [server_name]
    python manage_keys.py player list-by-device <device_id> [server_name]
    python manage_keys.py player disable <key>
    python manage_keys.py player enable <key>
"""

import os
import secrets
import sys
from datetime import datetime
from sqlalchemy import create_engine, text

DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///fitcollector.db")
engine = create_engine(DATABASE_URL, future=True)


def generate_api_key(length: int = 32) -> str:
    """Generate a random API key."""
    return secrets.token_urlsafe(length)


def add_server_key(server_name: str, key_length: int = 32) -> str:
    """Add a new API key for a server."""
    api_key = generate_api_key(key_length)
    
    try:
        with engine.begin() as conn:
            conn.execute(
                text("""
                    INSERT INTO api_keys (key, server_name, active)
                    VALUES (:key, :server_name, :active)
                """),
                {"key": api_key, "server_name": server_name, "active": True}
            )
        print(f"✓ Created server API key for '{server_name}':")
        print(f"  Key: {api_key}")
        return api_key
    except Exception as e:
        print(f"✗ Error creating key: {e}")
        return None


def list_server_keys() -> None:
    """List all server API keys."""
    with engine.begin() as conn:
        rows = conn.execute(
            text("""
                SELECT id, server_name, key, active, created_at, last_used
                FROM api_keys
                ORDER BY created_at DESC
            """)
        ).fetchall()
    
    if not rows:
        print("No API keys found.")
        return
    
    print(f"\n{'ID':<5} {'Server Name':<20} {'Active':<7} {'Created':<20} {'Last Used':<20}")
    print("-" * 90)
    for row in rows:
        id_, server_name, key, active, created_at, last_used = row
        key_preview = key[:12] + "..." if key else "N/A"
        active_str = "✓" if active else "✗"
        created_str = created_at.strftime("%Y-%m-%d %H:%M") if created_at else "N/A"
        last_used_str = last_used.strftime("%Y-%m-%d %H:%M") if last_used else "Never"
        
        print(f"{id_:<5} {server_name:<20} {active_str:<7} {created_str:<20} {last_used_str:<20}")


def disable_server_key(api_key: str) -> None:
    """Disable a server API key."""
    try:
        with engine.begin() as conn:
            result = conn.execute(
                text("UPDATE api_keys SET active = FALSE WHERE key = :key"),
                {"key": api_key}
            )
            if result.rowcount == 0:
                print(f"✗ Key not found: {api_key}")
            else:
                print(f"✓ Disabled server key: {api_key[:12]}...")
    except Exception as e:
        print(f"✗ Error disabling key: {e}")


def enable_server_key(api_key: str) -> None:
    """Enable a server API key."""
    try:
        with engine.begin() as conn:
            result = conn.execute(
                text("UPDATE api_keys SET active = TRUE WHERE key = :key"),
                {"key": api_key}
            )
            if result.rowcount == 0:
                print(f"✗ Key not found: {api_key}")
            else:
                print(f"✓ Enabled server key: {api_key[:12]}...")
    except Exception as e:
        print(f"✗ Error enabling key: {e}")


def list_player_keys(server_name: str = None) -> None:
    """List all player keys, optionally filtered by server."""
    with engine.begin() as conn:
        if server_name:
            rows = conn.execute(
                text("""
                    SELECT id, device_id, minecraft_username, server_name, key, active, created_at, last_used
                    FROM player_keys
                    WHERE server_name = :server
                    ORDER BY created_at DESC
                """),
                {"server": server_name}
            ).fetchall()
        else:
            rows = conn.execute(
                text("""
                    SELECT id, device_id, minecraft_username, server_name, key, active, created_at, last_used
                    FROM player_keys
                    ORDER BY created_at DESC
                """)
            ).fetchall()
    
    if not rows:
        print("No player keys found.")
        return
    
    print(f"\n{'ID':<5} {'Device ID':<20} {'Username':<16} {'Server':<20} {'Active':<7} {'Created':<20}")
    print("-" * 120)
    for row in rows:
        id_, device_id, username, server, key, active, created_at, last_used = row
        active_str = "✓" if active else "✗"
        created_str = created_at.strftime("%Y-%m-%d %H:%M") if created_at else "N/A"
        
        print(f"{id_:<5} {device_id:<20} {username:<16} {server:<20} {active_str:<7} {created_str:<20}")


def list_player_keys_by_device(device_id: str, server_name: str = None) -> None:
    """List all player keys for a specific device."""
    with engine.begin() as conn:
        if server_name:
            rows = conn.execute(
                text("""
                    SELECT id, device_id, minecraft_username, server_name, key, active, created_at, last_used
                    FROM player_keys
                    WHERE device_id = :device_id AND server_name = :server
                """),
                {"device_id": device_id, "server": server_name}
            ).fetchall()
        else:
            rows = conn.execute(
                text("""
                    SELECT id, device_id, minecraft_username, server_name, key, active, created_at, last_used
                    FROM player_keys
                    WHERE device_id = :device_id
                """),
                {"device_id": device_id}
            ).fetchall()
    
    if not rows:
        print(f"No player keys found for device: {device_id}")
        return
    
    print(f"\nPlayer Keys for Device: {device_id}")
    print(f"\n{'Username':<16} {'Server':<20} {'Player Key':<40} {'Active':<7} {'Last Used':<20}")
    print("-" * 110)
    for row in rows:
        id_, device_id, username, server, key, active, created_at, last_used = row
        active_str = "✓" if active else "✗"
        last_used_str = last_used.strftime("%Y-%m-%d %H:%M") if last_used else "Never"
        
        print(f"{username:<16} {server:<20} {key:<40} {active_str:<7} {last_used_str:<20}")


def disable_player_key(api_key: str) -> None:
    """Disable a player API key."""
    try:
        with engine.begin() as conn:
            result = conn.execute(
                text("UPDATE player_keys SET active = FALSE WHERE key = :key"),
                {"key": api_key}
            )
            if result.rowcount == 0:
                print(f"✗ Key not found: {api_key}")
            else:
                print(f"✓ Disabled player key: {api_key[:12]}...")
    except Exception as e:
        print(f"✗ Error disabling key: {e}")


def enable_player_key(api_key: str) -> None:
    """Enable a player API key."""
    try:
        with engine.begin() as conn:
            result = conn.execute(
                text("UPDATE player_keys SET active = TRUE WHERE key = :key"),
                {"key": api_key}
            )
            if result.rowcount == 0:
                print(f"✗ Key not found: {api_key}")
            else:
                print(f"✓ Enabled player key: {api_key[:12]}...")
    except Exception as e:
        print(f"✗ Error enabling key: {e}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    
    command = sys.argv[1].lower()
    
    # Server key management
    if command == "server":
        if len(sys.argv) < 3:
            print("Usage: python manage_keys.py server <add|list|disable|enable> [args]")
            sys.exit(1)
        
        subcommand = sys.argv[2].lower()
        
        if subcommand == "add":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py server add <server_name> [key_length]")
                sys.exit(1)
            server_name = sys.argv[3]
            key_length = int(sys.argv[4]) if len(sys.argv) > 4 else 32
            add_server_key(server_name, key_length)
        
        elif subcommand == "list":
            list_server_keys()
        
        elif subcommand == "disable":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py server disable <key>")
                sys.exit(1)
            disable_server_key(sys.argv[3])
        
        elif subcommand == "enable":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py server enable <key>")
                sys.exit(1)
            enable_server_key(sys.argv[3])
        
        else:
            print(f"Unknown server command: {subcommand}")
            sys.exit(1)
    
    # Player key management
    elif command == "player":
        if len(sys.argv) < 3:
            print("Usage: python manage_keys.py player <list|list-by-device|disable|enable> [args]")
            sys.exit(1)
        
        subcommand = sys.argv[2].lower()
        
        if subcommand == "list":
            server_name = sys.argv[3] if len(sys.argv) > 3 else None
            list_player_keys(server_name)
        
        elif subcommand == "list-by-device":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py player list-by-device <device_id> [server_name]")
                sys.exit(1)
            device_id = sys.argv[3]
            server_name = sys.argv[4] if len(sys.argv) > 4 else None
            list_player_keys_by_device(device_id, server_name)
        
        elif subcommand == "disable":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py player disable <key>")
                sys.exit(1)
            disable_player_key(sys.argv[3])
        
        elif subcommand == "enable":
            if len(sys.argv) < 4:
                print("Usage: python manage_keys.py player enable <key>")
                sys.exit(1)
            enable_player_key(sys.argv[3])
        
        else:
            print(f"Unknown player command: {subcommand}")
            sys.exit(1)
