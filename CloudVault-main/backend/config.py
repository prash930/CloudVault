from pydantic_settings import BaseSettings, SettingsConfigDict
from pathlib import Path

from pydantic import model_validator

BASE_DIR = Path(__file__).resolve().parent

DEV_SECRET_KEY = "CHANGE-THIS-TO-A-RANDOM-SECRET-KEY-IN-PRODUCTION"


class Settings(BaseSettings):
    # App
    APP_NAME: str = "CloudBox"
    DEBUG: bool = True

    # Database
    DATABASE_URL: str = "sqlite:///./cloudbox.db"

    # Admin
    ADMIN_EMAIL: str = ""
    ADMIN_DISPLAY_NAME: str = "Admin"
    ADMIN_PASSWORD: str = ""

    # JWT
    SECRET_KEY: str = DEV_SECRET_KEY
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440

    # Password reset email
    SMTP_HOST: str = ""
    SMTP_PORT: int = 587
    SMTP_USERNAME: str = ""
    SMTP_PASSWORD: str = ""
    SMTP_FROM_EMAIL: str = "noreply@cloudbox.local"
    SMTP_USE_TLS: bool = True
    PASSWORD_RESET_EXPIRE_MINUTES: int = 30
    FRONTEND_BASE_URL: str = "http://localhost:8000"

    # Storage
    STORAGE_PROVIDER: str = "local"
    STORAGE_ROOT_DIR: str = "./storage_data"
    MAX_UPLOAD_BYTES: int = 10 * 1024 * 1024 * 1024

    # Quotas (default: 50 GB)
    DEFAULT_QUOTA_BYTES: int = 50 * 1024 * 1024 * 1024

    # Telegram Drive
    TELEGRAM_BOT_TOKEN: str = ""
    TELEGRAM_STORAGE_CHAT_ID: str = ""
    TRASH_RETENTION_DAYS: int = 30

    # Rate Limiting
    RATE_LIMIT_LOGIN: str = "5/minute"
    RATE_LIMIT_REGISTER: str = "3/hour"
    RATE_LIMIT_GENERAL: str = "60/minute"

    # CORS
    CORS_ORIGINS: list[str] = ["*"]

    @model_validator(mode="after")
    def _validate_secret_key(self):
        if not self.DEBUG and (
            not self.SECRET_KEY or self.SECRET_KEY == DEV_SECRET_KEY
        ):
            raise ValueError(
                "SECRET_KEY must be set to a strong random value "
                "when DEBUG is false"
            )
        return self

    model_config = SettingsConfigDict(
        env_file=(BASE_DIR / ".env", BASE_DIR.parent / ".env"),
        case_sensitive=True,
        extra="ignore"
    )


settings = Settings()
