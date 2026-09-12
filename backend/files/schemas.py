from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime
from typing import Optional

def format_bytes(bytes_val: int) -> str:
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if bytes_val < 1024.0:
            return f"{bytes_val:.1f} {unit}"
        bytes_val /= 1024.0
    return f"{bytes_val:.1f} PB"

class StorageUsageResponse(BaseModel):
    used_bytes: int
    quota_bytes: int
    used_formatted: str
    quota_formatted: str
    usage_percentage: float

class FileOut(BaseModel):
    id: int
    filename: str
    original_filename: str
    mime_type: Optional[str] = None
    size_bytes: int
    is_folder: bool
    parent_folder_id: Optional[int] = None
    is_trashed: bool
    moderation_status: str
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

class FileListResponse(BaseModel):
    items: list[FileOut]
    total: int

class CreateFolderRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)
    parent_folder_id: Optional[int] = None

class RenameRequest(BaseModel):
    filename: str = Field(..., min_length=1, max_length=255)

class MoveRequest(BaseModel):
    parent_folder_id: Optional[int] = None

class FileDetailsResponse(FileOut):
    storage_object_id: Optional[str] = None

class MessageResponse(BaseModel):
    message: str
