from datetime import datetime, timezone
from typing import Any, Optional

from sqlalchemy.orm import Session

from backend.config import settings
from backend.settings import service as settings_service
from backend.storage.models import (
    TelegramConnection,
    TelegramStoredObject,
)
from backend.storage.telegram import (
    TelegramAPIError,
    TelegramNotConnectedError,
    verify_telegram_credentials,
)
from backend.storage.token_crypto import encrypt_secret


def get_telegram_connection(
    db: Session,
) -> Optional[TelegramConnection]:
    return (
        db.query(TelegramConnection)
        .filter(TelegramConnection.id == 1)
        .first()
    )


def is_telegram_configured() -> bool:
    return bool(
        settings.TELEGRAM_BOT_TOKEN
        and settings.TELEGRAM_STORAGE_CHAT_ID
    )


def is_telegram_connected(db: Session) -> bool:
    row = get_telegram_connection(db)

    if (
        row
        and row.encrypted_bot_token
        and row.chat_id
    ):
        return True

    return is_telegram_configured()


def connect_telegram_drive(
    db: Session,
    bot_token: str,
    chat_id: str,
) -> TelegramConnection:

    token = (bot_token or "").strip()
    chat = str(chat_id or "").strip()

    if not token or not chat:
        raise ValueError(
            "Bot token and storage chat ID are required"
        )

    try:
        profile = verify_telegram_credentials(
            token,
            chat,
        )
    except (
        TelegramAPIError,
        TelegramNotConnectedError,
    ) as exc:
        raise ValueError(str(exc)) from exc

    now = datetime.now(timezone.utc)

    connection = get_telegram_connection(db)

    if not connection:
        connection = TelegramConnection(id=1)
        db.add(connection)

    connection.bot_username = profile["bot_username"]
    connection.chat_id = profile["chat_id"]
    connection.encrypted_bot_token = encrypt_secret(
        token
    )
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

    current = settings_service.get_setting(
        db,
        "storage_provider",
        settings.STORAGE_PROVIDER,
    )

    if current == "telegram_drive":
        settings_service.update_settings(
            db,
            {"storage_provider": "local"},
        )


def get_telegram_usage_bytes(db: Session) -> int:
    from sqlalchemy import func

    return int(
        db.query(
            func.coalesce(
                func.sum(
                    TelegramStoredObject.size_bytes
                ),
                0,
            )
        ).scalar()
        or 0
    )


def get_public_status(
    db: Session,
) -> dict[str, Any]:

    runtime = settings_service.get_runtime_settings(db)

    provider = runtime.get(
        "storage_provider",
        settings.STORAGE_PROVIDER,
    )

    telegram_row = get_telegram_connection(db)

    telegram_connected = is_telegram_connected(db)

    telegram_username = (
        telegram_row.bot_username
        if telegram_row
        and telegram_row.bot_username
        else None
    )

    telegram_chat = (
        telegram_row.chat_id
        if telegram_row
        and telegram_row.chat_id
        else (
            settings.TELEGRAM_STORAGE_CHAT_ID
            or None
        )
    )

    status = {
        "storage_provider": provider,
        "telegram_drive": {
            "configured": (
                is_telegram_configured()
                or telegram_connected
            ),
            "connected": telegram_connected,
            "bot_username": telegram_username,
            "chat_id": (
                telegram_chat
                if telegram_connected
                else None
            ),
            "connected_at": (
                telegram_row.connected_at.isoformat()
                if telegram_row
                and telegram_row.connected_at
                else None
            ),
            "status": (
                "connected"
                if telegram_connected
                else "disconnected"
            ),
            "used_space": (
                get_telegram_usage_bytes(db)
                if telegram_connected
                else 0
            ),
            "note": (
                "Files are stored as chunked Telegram "
                "documents in your storage chat."
            ),
        },
    }

    return status


def validate_provider_switch(
    db: Session,
    provider: str,
) -> None:

    if (
        provider == "telegram_drive"
        and not is_telegram_connected(db)
    ):
        raise ValueError(
            "Connect Telegram Drive before selecting "
            "it as the storage provider"
        )