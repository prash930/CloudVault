from sqlalchemy.orm import Session

from backend.config import settings
from backend.settings import service as settings_service
from backend.storage.base import StorageProvider
from backend.storage.local import LocalStorageProvider


def get_storage_provider(db: Session, provider_name: str | None = None) -> StorageProvider:
    name = provider_name or settings_service.get_setting(
        db, "storage_provider", settings.STORAGE_PROVIDER
    )
    if name == "google_drive":
        from backend.storage.google_drive import GoogleDriveStorageProvider

        return GoogleDriveStorageProvider(db)
    if name == "telegram_drive":
        from backend.storage.service import is_telegram_connected
        from backend.storage.telegram import TelegramStorageProvider

        if is_telegram_connected(db):
            return TelegramStorageProvider(db)
        return LocalStorageProvider()
    return LocalStorageProvider()
