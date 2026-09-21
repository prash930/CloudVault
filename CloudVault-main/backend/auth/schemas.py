from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, computed_field


class RegisterRequest(BaseModel):
    email: EmailStr
    display_name: str = Field(..., min_length=2, max_length=50)
    password: str = Field(..., min_length=8)

class LoginRequest(BaseModel):
    email: str
    password: str

class UserOut(BaseModel):
    id: int
    email: str
    display_name: str
    role: str
    status: str
    storage_quota_bytes: int
    storage_used_bytes: int
    created_at: datetime
    avatar_1_path: str | None = Field(default=None, exclude=True)
    avatar_2_path: str | None = Field(default=None, exclude=True)

    model_config = ConfigDict(from_attributes=True)

    @computed_field
    @property
    def has_avatar_1(self) -> bool:
        return bool(self.avatar_1_path)

    @computed_field
    @property
    def has_avatar_2(self) -> bool:
        return bool(self.avatar_2_path)


class UpdateProfileRequest(BaseModel):
    display_name: str | None = Field(None, min_length=2, max_length=50)
    email: EmailStr | None = None
    password: str | None = Field(None, min_length=8)

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut

class ForgotPasswordRequest(BaseModel):
    email: EmailStr

class ResetPasswordRequest(BaseModel):
    token: str
    new_password: str = Field(..., min_length=8)

class MessageResponse(BaseModel):
    message: str
