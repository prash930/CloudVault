import asyncio
import io
import uuid
from typing import AsyncIterator, BinaryIO, Optional

from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload, MediaIoBaseUpload
from sqlalchemy.orm import Session

from backend.config import settings
from backend.storage.base import StorageProvider
from backend.storage.models import GoogleDriveConnection, UserDriveFolder
from backend.storage.token_crypto import decrypt_secret

DRIVE_SCOPES = ["https://www.googleapis.com/auth/drive.file"]
MIME_FOLDER = "application/vnd.google-apps.folder"


class GoogleDriveNotConnectedError(RuntimeError):
    pass


def _build_credentials(refresh_token: str) -> Credentials:
    if not settings.GOOGLE_CLIENT_ID or not settings.GOOGLE_CLIENT_SECRET:
        raise GoogleDriveNotConnectedError("Google OAuth client credentials are not configured")
    return Credentials(
        token=None,
        refresh_token=refresh_token,
        token_uri="https://oauth2.googleapis.com/token",
        client_id=settings.GOOGLE_CLIENT_ID,
        client_secret=settings.GOOGLE_CLIENT_SECRET,
        scopes=DRIVE_SCOPES,
    )


def _get_connection(db: Session) -> GoogleDriveConnection:
    connection = db.query(GoogleDriveConnection).filter(GoogleDriveConnection.id == 1).first()
    if not connection or not connection.encrypted_refresh_token:
        raise GoogleDriveNotConnectedError("Google Drive is not connected")
    return connection


def _drive_service(db: Session):
    connection = _get_connection(db)
    credentials = _build_credentials(decrypt_secret(connection.encrypted_refresh_token))
    return build("drive", "v3", credentials=credentials, cache_discovery=False)


def _find_child_folder(service, parent_id: str, name: str) -> Optional[str]:
    query = (
        f"'{parent_id}' in parents and name = '{name}' and "
        f"mimeType = '{MIME_FOLDER}' and trashed = false"
    )
    result = (
        service.files()
        .list(q=query, spaces="drive", fields="files(id)", pageSize=1)
        .execute()
    )
    files = result.get("files", [])
    return files[0]["id"] if files else None


def _create_folder(service, parent_id: str, name: str) -> str:
    existing = _find_child_folder(service, parent_id, name)
    if existing:
        return existing
    metadata = {
        "name": name,
        "mimeType": MIME_FOLDER,
        "parents": [parent_id],
    }
    created = service.files().create(body=metadata, fields="id").execute()
    return created["id"]


def ensure_user_drive_folder(db: Session, user_id: int) -> str:
    mapping = db.query(UserDriveFolder).filter(UserDriveFolder.user_id == user_id).first()
    if mapping:
        return mapping.drive_folder_id

    connection = _get_connection(db)
    if not connection.users_folder_id:
        raise GoogleDriveNotConnectedError("Google Drive users folder is not initialized")

    service = _drive_service(db)
    folder_name = f"user-{user_id}"
    drive_folder_id = _create_folder(service, connection.users_folder_id, folder_name)
    mapping = UserDriveFolder(user_id=user_id, drive_folder_id=drive_folder_id)
    db.add(mapping)
    db.commit()
    db.refresh(mapping)
    return mapping.drive_folder_id


class GoogleDriveStorageProvider(StorageProvider):
    """Stores binary objects in isolated per-user Google Drive folders."""

    def __init__(self, db: Session):
        self.db = db

    async def _run(self, func, *args, **kwargs):
        return await asyncio.to_thread(func, *args, **kwargs)

    def _upload_file_sync(self, user_id: int, source: BinaryIO, content_type: str) -> tuple[str, int]:
        parent_id = ensure_user_drive_folder(self.db, user_id)
        service = _drive_service(self.db)
        file_name = uuid.uuid4().hex
        media = MediaIoBaseUpload(
            source,
            mimetype=content_type or "application/octet-stream",
            resumable=True,
        )
        metadata = {"name": file_name, "parents": [parent_id]}
        created = (
            service.files()
            .create(body=metadata, media_body=media, fields="id,size")
            .execute()
        )
        return created["id"], int(created.get("size") or 0)

    async def store_file(self, object_id: str, data: bytes, content_type: str = "") -> str:
        raise NotImplementedError("Use store_stream with user context for Google Drive uploads")

    async def store_stream_for_user(
        self,
        user_id: int,
        source: BinaryIO,
        content_type: str = "",
    ) -> tuple[str, int]:
        return await self._run(self._upload_file_sync, user_id, source, content_type)

    async def store_stream(self, object_id: str, source: BinaryIO, content_type: str = "") -> str:
        raise NotImplementedError("Google Drive uploads require user context")

    def _download_sync(self, file_id: str) -> bytes:
        service = _drive_service(self.db)
        request = service.files().get_media(fileId=file_id)
        buffer = io.BytesIO()
        downloader = MediaIoBaseDownload(buffer, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()
        return buffer.getvalue()

    async def retrieve_file(self, object_id: str) -> bytes:
        return await self._run(self._download_sync, object_id)

    async def stream_file(self, object_id: str) -> AsyncIterator[bytes]:
        data = await self.retrieve_file(object_id)
        chunk_size = 1024 * 1024
        for offset in range(0, len(data), chunk_size):
            yield data[offset : offset + chunk_size]

    def _delete_sync(self, file_id: str) -> bool:
        service = _drive_service(self.db)
        try:
            service.files().delete(fileId=file_id).execute()
            return True
        except Exception:
            return False

    async def delete_file(self, object_id: str) -> bool:
        return await self._run(self._delete_sync, object_id)

    def _exists_sync(self, file_id: str) -> bool:
        service = _drive_service(self.db)
        try:
            service.files().get(fileId=file_id, fields="id,trashed").execute()
            return True
        except Exception:
            return False

    async def file_exists(self, object_id: str) -> bool:
        return await self._run(self._exists_sync, object_id)

    def _size_sync(self, file_id: str) -> int:
        service = _drive_service(self.db)
        try:
            metadata = service.files().get(fileId=file_id, fields="size,trashed").execute()
            if metadata.get("trashed"):
                return 0
            return int(metadata.get("size") or 0)
        except Exception:
            return 0

    async def get_file_size(self, object_id: str) -> int:
        return await self._run(self._size_sync, object_id)


def get_drive_quota(db: Session) -> Optional[dict]:
    """Retrieve Google Drive account storage limits and usage."""
    try:
        service = _drive_service(db)
        about = service.about().get(fields="storageQuota").execute()
        quota = about.get("storageQuota", {})
        limit = int(quota.get("limit", 0))
        usage = int(quota.get("usage", 0))
        return {
            "total_space": limit,
            "used_space": usage,
            "available_space": max(0, limit - usage) if limit > 0 else 0,
        }
    except Exception:
        return None

