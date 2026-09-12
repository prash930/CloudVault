from datetime import datetime, timezone

from sqlalchemy import BigInteger, Column, DateTime, ForeignKey, Integer, String, Text

from backend.database import Base


class GoogleDriveConnection(Base):
    """Singleton row storing the admin OAuth connection to Google Drive."""

    __tablename__ = "google_drive_connections"

    id = Column(Integer, primary_key=True, default=1)
    account_email = Column(String(255), nullable=True)
    account_id = Column(String(100), nullable=True)
    encrypted_refresh_token = Column(Text, nullable=True)
    root_folder_id = Column(String(100), nullable=True)
    users_folder_id = Column(String(100), nullable=True)
    connected_at = Column(DateTime, nullable=True)
    updated_at = Column(
        DateTime,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class UserDriveFolder(Base):
    """Maps each CloudBox user to an isolated folder on Google Drive."""

    __tablename__ = "user_drive_folders"

    user_id = Column(Integer, ForeignKey("users.id"), primary_key=True)
    drive_folder_id = Column(String(100), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))


class TelegramConnection(Base):
    """Singleton row storing the admin Telegram bot used as Telegram Drive."""

    __tablename__ = "telegram_connections"

    id = Column(Integer, primary_key=True, default=1)
    bot_username = Column(String(255), nullable=True)
    chat_id = Column(String(64), nullable=True)
    encrypted_bot_token = Column(Text, nullable=True)
    connected_at = Column(DateTime, nullable=True)
    updated_at = Column(
        DateTime,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class TelegramStoredObject(Base):
    """Maps a CloudBox object ID to Telegram message/file chunks."""

    __tablename__ = "telegram_stored_objects"

    object_id = Column(String(64), primary_key=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    parts_json = Column(Text, nullable=False, default="[]")
    size_bytes = Column(BigInteger, nullable=False, default=0)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
