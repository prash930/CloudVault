from pydantic_settings import BaseSettings, SettingsConfigDict
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent


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
    SECRET_KEY: str = "CHANGE-THIS-TO-A-RANDOM-SECRET-KEY-IN-PRODUCTION"
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
    FRONTEND_BASE_URL: str = "https://cloudvault-7890806d.fastapicloud.dev"

    # Storage
    STORAGE_PROVIDER: str = "local"
    STORAGE_ROOT_DIR: str = "./storage_data"
    MAX_UPLOAD_BYTES: int = 10 * 1024 * 1024 * 1024

    # Quotas
    DEFAULT_QUOTA_BYTES: int = 1024 * 1024 * 1024 * 1024

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

    model_config = SettingsConfigDict(
        env_file=(BASE_DIR / ".env", BASE_DIR.parent / ".env"),
        case_sensitive=True,
        extra="ignore"
    )


settings = Settings()
