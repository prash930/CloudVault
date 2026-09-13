from datetime import datetime, timedelta, timezone
from typing import Any, Optional
from urllib.parse import urlencode

import httpx
from googleapiclient.discovery import build
from google.oauth2.credentials import Credentials
from sqlalchemy.orm import Session

from backend.auth.service import create_access_token
from backend.config import settings
from backend.settings import service as settings_service
from backend.storage.google_drive import DRIVE_SCOPES, MIME_FOLDER, _create_folder
from backend.storage.models import GoogleDriveConnection, TelegramConnection, TelegramStoredObject
from backend.storage.telegram import TelegramAPIError, verify_telegram_credentials
from backend.storage.token_crypto import decrypt_secret, encrypt_secret

GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
OAUTH_STATE_PURPOSE = "google_drive_oauth"

GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
OAUTH_STATE_PURPOSE = "google_drive_oauth"


def _redirect_uri() -> str:
    return settings.GOOGLE_OAUTH_REDIRECT_URI or "http://127.0.0.1:8000/admin/storage/google/callback"


def _admin_ui_settings_url(suffix: str = "") -> str:
    base = settings.ADMIN_UI_BASE_URL.rstrip("/")
    return f"{base}/settings{suffix}"


def is_google_configured() -> bool:
    return bool(settings.GOOGLE_CLIENT_ID and settings.GOOGLE_CLIENT_SECRET)


def get_connection(db: Session) -> Optional[GoogleDriveConnection]:
    return db.query(GoogleDriveConnection).filter(GoogleDriveConnection.id == 1).first()


def is_connected(db: Session) -> bool:
    connection = get_connection(db)
    return bool(connection and connection.encrypted_refresh_token)


def build_oauth_state(admin_id: int) -> str:
    return create_access_token(
        data={"purpose": OAUTH_STATE_PURPOSE, "admin_id": admin_id},
        expires_delta=timedelta(minutes=15),
    )


def verify_oauth_state(state: str, db: Optional[Session] = None) -> int:
    from jose import JWTError, jwt
    from backend.users.service import get_user_by_id

    try:
        payload = jwt.decode(state, settings.SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
    except JWTError as exc:
        raise ValueError("Invalid OAuth state") from exc
    if payload.get("purpose") != OAUTH_STATE_PURPOSE:
        raise ValueError("Invalid OAuth state purpose")
    admin_id = payload.get("admin_id")
    if admin_id is None:
        raise ValueError("Missing admin in OAuth state")

    admin_id_int = int(admin_id)
    if db is not None:
        user = get_user_by_id(db, admin_id_int)
        if not user or user.role != "ADMIN" or user.status in ["PENDING", "SUSPENDED", "BANNED"]:
            raise ValueError("Admin user in OAuth state is invalid or inactive")

    return admin_id_int



def build_authorization_url(state: str) -> str:
    params = {
        "client_id": settings.GOOGLE_CLIENT_ID,
        "redirect_uri": _redirect_uri(),
        "response_type": "code",
        "scope": " ".join(DRIVE_SCOPES + ["openid", "email"]),
        "access_type": "offline",
        "prompt": "select_account consent",
        "state": state,
    }
    return f"{GOOGLE_AUTH_URL}?{urlencode(params)}"


def _credentials_from_refresh(refresh_token: str) -> Credentials:
    return Credentials(
        token=None,
        refresh_token=refresh_token,
        token_uri=GOOGLE_TOKEN_URL,
        client_id=settings.GOOGLE_CLIENT_ID,
        client_secret=settings.GOOGLE_CLIENT_SECRET,
        scopes=DRIVE_SCOPES,
    )


def _initialize_drive_folders(refresh_token: str) -> tuple[str, str]:
    credentials = _credentials_from_refresh(refresh_token)
    service = build("drive", "v3", credentials=credentials, cache_discovery=False)
    root_folder_id = _create_folder(service, "root", "CloudBox")
    users_folder_id = _create_folder(service, root_folder_id, "users")
    return root_folder_id, users_folder_id


def exchange_code_and_connect(db: Session, code: str) -> GoogleDriveConnection:
    payload = {
        "code": code,
        "client_id": settings.GOOGLE_CLIENT_ID,
        "client_secret": settings.GOOGLE_CLIENT_SECRET,
        "redirect_uri": _redirect_uri(),
        "grant_type": "authorization_code",
    }
    with httpx.Client(timeout=30.0) as client:
        token_response = client.post(GOOGLE_TOKEN_URL, data=payload)
        token_response.raise_for_status()
        token_data = token_response.json()

        refresh_token = token_data.get("refresh_token")
        if not refresh_token:
            raise ValueError("Google did not return a refresh token. Try disconnecting and reconnecting.")

        access_token = token_data["access_token"]
        userinfo = client.get(
            GOOGLE_USERINFO_URL,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        userinfo.raise_for_status()
        profile = userinfo.json()

    root_folder_id, users_folder_id = _initialize_drive_folders(refresh_token)
    now = datetime.now(timezone.utc)
    connection = get_connection(db)
    if not connection:
        connection = GoogleDriveConnection(id=1)
        db.add(connection)

    connection.account_email = profile.get("email")
    connection.account_id = profile.get("id")
    connection.encrypted_refresh_token = encrypt_secret(refresh_token)
    connection.root_folder_id = root_folder_id
    connection.users_folder_id = users_folder_id
    connection.connected_at = now
    connection.updated_at = now
    db.commit()
    db.refresh(connection)
    return connection


def disconnect(db: Session) -> None:
    connection = get_connection(db)
    if connection:
        connection.account_email = None
        connection.account_id = None
        connection.encrypted_refresh_token = None
        connection.root_folder_id = None
        connection.users_folder_id = None
        connection.connected_at = None
        connection.updated_at = datetime.now(timezone.utc)
        db.commit()


def get_telegram_connection(db: Session) -> Optional[TelegramConnection]:
    return db.query(TelegramConnection).filter(TelegramConnection.id == 1).first()


def is_telegram_configured() -> bool:
    return bool(settings.TELEGRAM_BOT_TOKEN and settings.TELEGRAM_STORAGE_CHAT_ID)


def is_telegram_connected(db: Session) -> bool:
    row = get_telegram_connection(db)
    if row and row.encrypted_bot_token and row.chat_id:
        return True
    return is_telegram_configured()


def connect_telegram_drive(db: Session, bot_token: str, chat_id: str) -> TelegramConnection:
    token = (bot_token or "").strip()
    chat = str(chat_id or "").strip()
    if not token or not chat:
        raise ValueError("Bot token and storage chat ID are required")
    try:
        profile = verify_telegram_credentials(token, chat)
    except (TelegramAPIError, TelegramNotConnectedError) as exc:
        raise ValueError(str(exc)) from exc

    now = datetime.now(timezone.utc)
    connection = get_telegram_connection(db)
    if not connection:
        connection = TelegramConnection(id=1)
        db.add(connection)
    connection.bot_username = profile["bot_username"]
    connection.chat_id = profile["chat_id"]
    connection.encrypted_bot_token = encrypt_secret(token)
    connection.connected_at = now
    connection.updated_at = now
    db.commit()
    db.refresh(connection)
    return connection


def disconnect_telegram(db: Session) -> None:
    connection = get_telegram_connection(db)
    if connection:
        connection.bot_username = None
        connection.chat_id = None
        connection.encrypted_bot_token = None
        connection.connected_at = None
        connection.updated_at = datetime.now(timezone.utc)
        db.commit()
    current = settings_service.get_setting(db, "storage_provider", settings.STORAGE_PROVIDER)
    if current == "telegram_drive":
        settings_service.update_settings(db, {"storage_provider": "local"})


def get_telegram_usage_bytes(db: Session) -> int:
    from sqlalchemy import func

    return int(db.query(func.coalesce(func.sum(TelegramStoredObject.size_bytes), 0)).scalar() or 0)


def get_public_status(db: Session) -> dict[str, Any]:
    runtime = settings_service.get_runtime_settings(db)
    provider = runtime.get("storage_provider", settings.STORAGE_PROVIDER)
    telegram_row = get_telegram_connection(db)
    telegram_connected = is_telegram_connected(db)
    telegram_username = telegram_row.bot_username if telegram_row and telegram_row.bot_username else None
    telegram_chat = telegram_row.chat_id if telegram_row and telegram_row.chat_id else (
        settings.TELEGRAM_STORAGE_CHAT_ID or None
    )

    status = {
        "storage_provider": provider if provider != "google_drive" else "local",
        "telegram_drive": {
            "configured": is_telegram_configured() or telegram_connected,
            "connected": telegram_connected,
            "bot_username": telegram_username,
            "chat_id": telegram_chat if telegram_connected else None,
            "connected_at": (
                telegram_row.connected_at.isoformat()
                if telegram_row and telegram_row.connected_at
                else None
            ),
            "status": "connected" if telegram_connected else "disconnected",
            "used_space": get_telegram_usage_bytes(db) if telegram_connected else 0,
            "note": "Files are stored as chunked Telegram documents in your storage chat.",
        },
    }

    return status


def validate_provider_switch(db: Session, provider: str) -> None:
    if provider == "google_drive":
        raise ValueError("Google Drive is disabled")
    if provider == "telegram_drive" and not is_telegram_connected(db):
        raise ValueError("Connect Telegram Drive before selecting it as the storage provider")
