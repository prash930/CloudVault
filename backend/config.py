from pydantic_settings import BaseSettings, SettingsConfigDict
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

class Settings(BaseSettings):
    # App
    APP_NAME: str = "CloudBox"
    DEBUG: bool = True
    
    # Database
    DATABASE_URL: str = "sqlite:///./cloudbox.db"
    
    # JWT
    SECRET_KEY: str = "CHANGE-THIS-TO-A-RANDOM-SECRET-KEY-IN-PRODUCTION"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440  # 24 hours
    
    # Storage
    STORAGE_PROVIDER: str = "local"  # telegram_drive, local, google_drive — set to telegram_drive via Admin Settings in production
    STORAGE_ROOT_DIR: str = "./storage_data"
    MAX_UPLOAD_BYTES: int = 10 * 1024 * 1024 * 1024  # 10 GB per upload safety cap
    
    # Quotas
    DEFAULT_QUOTA_BYTES: int = 1024 * 1024 * 1024 * 1024  # 1 TB per user by default (no user quota questioning)
    
    # Telegram Drive (Bot API). Prefer Admin Settings connect; .env is a fallback.
    TELEGRAM_BOT_TOKEN: str = ""
    TELEGRAM_STORAGE_CHAT_ID: str = ""
    TRASH_RETENTION_DAYS: int = 30
    
    # Rate Limiting
    RATE_LIMIT_LOGIN: str = "5/minute"
    RATE_LIMIT_REGISTER: str = "3/hour"
    RATE_LIMIT_GENERAL: str = "60/minute"
    
    # CORS
    CORS_ORIGINS: list[str] = ["*"]  # Restrict in production
    
    # Google Drive OAuth (set in .env — never commit secrets)
    GOOGLE_CLIENT_ID: str = ""
    GOOGLE_CLIENT_SECRET: str = ""
    GOOGLE_OAUTH_REDIRECT_URI: str = "http://127.0.0.1:8000/admin/storage/google/callback"
    ADMIN_UI_BASE_URL: str = "http://localhost:5173/admin-ui"
    
    model_config = SettingsConfigDict(
        env_file=(BASE_DIR / ".env", BASE_DIR.parent / ".env"),
        case_sensitive=True,
        extra="ignore"
    )

settings = Settings()

