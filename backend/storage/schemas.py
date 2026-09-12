from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, Field


class GoogleDriveStatus(BaseModel):
    configured: bool
    connected: bool
    account_email: Optional[str] = None
    account_id: Optional[str] = None
    connected_at: Optional[datetime] = None
    status: Literal["connected", "disconnected"]
    total_space: Optional[int] = None
    used_space: Optional[int] = None
    available_space: Optional[int] = None


class TelegramDriveStatus(BaseModel):
    configured: bool
    connected: bool
    bot_username: Optional[str] = None
    chat_id: Optional[str] = None
    connected_at: Optional[datetime] = None
    status: Literal["connected", "disconnected"]
    used_space: Optional[int] = None
    note: Optional[str] = None


class StorageStatusResponse(BaseModel):
    storage_provider: str
    google_drive: GoogleDriveStatus
    telegram_drive: TelegramDriveStatus


class GoogleConnectResponse(BaseModel):
    authorization_url: str


class TelegramConnectRequest(BaseModel):
    bot_token: str = Field(..., min_length=10)
    chat_id: str = Field(..., min_length=1)


class StorageProviderUpdateRequest(BaseModel):
    storage_provider: Literal["local", "google_drive", "telegram_drive"] = Field(...)


class MessageResponse(BaseModel):
    message: str
