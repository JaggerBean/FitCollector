import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

# Example API key for server endpoints
API_KEY = "testkey"

ROUTES = [
    ("GET", "/v1/players/claim-status/nonexistent_user", {}),
    ("POST", "/v1/players/register", {"json": {"minecraft_username": "testuser", "device_id": "testdevice", "server_name": "testserver"}}),
    ("POST", "/v1/players/recover-key", {"json": {"minecraft_username": "testuser", "device_id": "testdevice", "server_name": "testserver"}}),
    ("GET", "/v1/servers/available", {}),
    ("GET", "/v1/servers/players/nonexistent_user/claim-status", {"headers": {"X-API-Key": API_KEY}}),
    ("POST", "/v1/servers/players/nonexistent_user/claim-reward", {"headers": {"X-API-Key": API_KEY}}),
    ("GET", "/v1/servers/players", {"headers": {"X-API-Key": API_KEY}}),
    ("GET", "/v1/servers/players/nonexistent_user/yesterday-steps", {"headers": {"X-API-Key": API_KEY}}),
    ("DELETE", "/v1/servers/players/nonexistent_user", {"headers": {"X-API-Key": API_KEY}}),
    ("POST", "/v1/ingest", {"json": {"minecraft_username": "testuser", "device_id": "testdevice", "steps_today": 1000, "player_api_key": "testplayerkey"}}),
    ("GET", "/health", {}),
]

def test_all_routes():
    for method, path, opts in ROUTES:
        if method == "GET":
            response = client.get(path, **opts)
        elif method == "POST":
            response = client.post(path, **opts)
        elif method == "DELETE":
            response = client.delete(path, **opts)
        else:
            continue
        # Print for manual review; in real tests, assert expected status and response
        print(f"{method} {path} -> {response.status_code} {response.json()}")
