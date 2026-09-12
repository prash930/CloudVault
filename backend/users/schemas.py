from pydantic import BaseModel, Field, ConfigDict
from datetime import datetime
from typing import List, Optional

class UserPublic(BaseModel):
    id: int
    email: str
    display_name: str
    role: str
    status: str
    storage_quota_bytes: int
    storage_used_bytes: int
    created_at: datetime
    
    model_config = ConfigDict(from_attributes=True)

class UserAdminView(UserPublic):
    updated_at: datetime
    last_login_at: Optional[datetime] = None
    hashed_password: Optional[str] = None

class UserAdminDetail(UserAdminView):
    file_count: int = 0

class UpdateStatusRequest(BaseModel):
    status: str
    reason: Optional[str] = None

class UserListResponse(BaseModel):
    users: List[UserAdminView]
    total: int

class RecentUploadSummary(BaseModel):
    id: int
    filename: str
    user_id: int
    user_email: str
    size_bytes: int
    mime_type: Optional[str] = None
    created_at: datetime

class RecentAuditSummary(BaseModel):
    id: int
    action: str
    admin_id: Optional[int] = None
    user_id: Optional[int] = None
    file_id: Optional[int] = None
    reason: Optional[str] = None
    created_at: datetime

class DashboardStats(BaseModel):
    total_users: int
    active_users: int
    pending_users: int
    warned_users: int
    suspended_users: int
    banned_users: int
    total_files: int
    total_storage_used_bytes: int
    total_quota_bytes: int
    recent_uploads: List[RecentUploadSummary]
    recent_admin_actions: List[RecentAuditSummary]

class UpdateQuotaRequest(BaseModel):
    quota_bytes: int
    reason: Optional[str] = None

class QuotaChangeResponse(BaseModel):
    user_id: int
    old_quota_bytes: int
    new_quota_bytes: int
    warning: Optional[str] = None
    message: str

class AuditLogResponse(BaseModel):
    id: int
    admin_id: Optional[int] = None
    user_id: Optional[int] = None
    file_id: Optional[int] = None
    action: str
    reason: Optional[str] = None
    details: Optional[str] = None
    ip_address: Optional[str] = None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

class AuditLogListResponse(BaseModel):
    logs: List[AuditLogResponse]
    total: int

class AdminSettingsResponse(BaseModel):
    default_quota_bytes: int
    require_registration_approval: bool
    max_upload_bytes: int
    trash_retention_days: int
    storage_provider: str = "local"

class AdminSettingsUpdateRequest(BaseModel):
    default_quota_bytes: Optional[int] = Field(None, gt=0)
    require_registration_approval: Optional[bool] = None
    max_upload_bytes: Optional[int] = Field(None, gt=0)
    trash_retention_days: Optional[int] = Field(None, ge=0)
    storage_provider: Optional[str] = None

class MessageResponse(BaseModel):
    message: str
