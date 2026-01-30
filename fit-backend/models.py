"""Pydantic models for request/response schemas."""

from pydantic import BaseModel, Field
from typing import Optional


class IngestPayload(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    steps_today: int = Field(..., ge=0, le=500_000)
    player_api_key: str = Field(..., min_length=20)
    day: Optional[str] = None
    source: Optional[str] = "health_connect"
    timestamp: Optional[str] = None


class PlayerRegistrationRequest(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    server_name: str = Field(..., min_length=3, max_length=50)
    invite_code: Optional[str] = Field(None, min_length=6, max_length=32)


class PlayerApiKeyResponse(BaseModel):
    player_api_key: str
    minecraft_username: str
    device_id: str
    server_name: str
    message: str = "Save this key securely. You'll need it for all future submissions."


class ServerRegistrationRequest(BaseModel):
    server_name: str = Field(..., min_length=3, max_length=50)
    owner_name: str = Field(..., min_length=2, max_length=100)
    owner_email: str = Field(..., min_length=5, max_length=255)
    server_address: Optional[str] = Field(None, min_length=5, max_length=255)
    server_version: Optional[str] = None
    is_private: bool = Field(False, description="If true, server is private and requires invite code")
    invite_code: Optional[str] = Field(None, min_length=6, max_length=32, description="Invite code for private server")


class ReopenServerRequest(BaseModel):
    server_name: str = Field(..., min_length=3, max_length=50)



class KeysResponse(BaseModel):
    minecraft_username: str
    device_id: str
    servers: dict[str, str]  # server_name -> player_api_key


class KeyRecoveryRequest(BaseModel):
    minecraft_username: str = Field(..., min_length=3, max_length=16)
    device_id: str = Field(..., min_length=6, max_length=128)
    server_name: str = Field(..., min_length=3, max_length=50)


class DeviceUsernameResponse(BaseModel):
    minecraft_username: str
    device_id: str
    server_name: str


class ApiKeyResponse(BaseModel):
    api_key: str
    server_name: str
    message: str = "Store this key securely. You won't be able to see it again!"
    is_private: Optional[bool] = None
    invite_code: Optional[str] = None
