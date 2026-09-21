import asyncio
import io
import json
import uuid
from typing import Any, AsyncIterator, BinaryIO, Optional

import httpx
from sqlalchemy.orm import Session

from backend.config import settings
from backend.storage.base import StorageProvider
from backend.storage.models import TelegramConnection, TelegramStoredObject
from backend.storage.token_crypto import decrypt_secret

TELEGRAM_API_BASE = "https://api.telegram.org"
# Stay under Bot API sendDocument (50 MB) and getFile (20 MB) limits.
CHUNK_SIZE = 19 * 1024 * 1024


class TelegramNotConnectedError(RuntimeError):
    pass


class TelegramAPIError(RuntimeError):
    pass


def _get_connection_row(db: Session) -> Optional[TelegramConnection]:
    return db.query(TelegramConnection).filter(TelegramConnection.id == 1).first()


def get_telegram_credentials(db: Session) -> tuple[str, str]:
    row = _get_connection_row(db)
    token = ""
    chat_id = ""
    if row and row.encrypted_bot_token and row.chat_id:
        token = decrypt_secret(row.encrypted_bot_token)
        chat_id = str(row.chat_id)
    if not token:
        token = (settings.TELEGRAM_BOT_TOKEN or "").strip()
    if not chat_id:
        chat_id = str(settings.TELEGRAM_STORAGE_CHAT_ID or "").strip()
    if not token or not chat_id:
        raise TelegramNotConnectedError(
            "Telegram Drive is not connected. Set TELEGRAM_BOT_TOKEN and "
            "TELEGRAM_STORAGE_CHAT_ID, or connect from Admin Settings."
        )
    return token, chat_id


def telegram_api(token: str, method: str, payload: Optional[dict] = None, files: Optional[dict] = None) -> Any:
    url = f"{TELEGRAM_API_BASE}/bot{token}/{method}"
    with httpx.Client(timeout=120.0) as client:
        if files:
            response = client.post(url, data=payload or {}, files=files)
        else:
            response = client.post(url, json=payload or {})
        try:
            body = response.json()
        except ValueError as exc:
            raise TelegramAPIError(f"Telegram returned a non-JSON response ({response.status_code})") from exc
        if not body.get("ok"):
            raise TelegramAPIError(body.get("description") or f"Telegram API error ({response.status_code})")
        return body.get("result")


def download_telegram_file(token: str, file_path: str) -> bytes:
    url = f"{TELEGRAM_API_BASE}/file/bot{token}/{file_path}"
    with httpx.Client(timeout=120.0) as client:
        response = client.get(url)
        response.raise_for_status()
        return response.content


def verify_telegram_credentials(token: str, chat_id: str) -> dict:
    me = telegram_api(token, "getMe")
    telegram_api(token, "getChat", {"chat_id": chat_id})
    return {
        "bot_username": me.get("username") or me.get("first_name") or "telegram-bot",
        "bot_id": str(me.get("id") or ""),
        "chat_id": str(chat_id),
    }


def _load_parts(record: TelegramStoredObject) -> list[dict]:
    try:
        parts = json.loads(record.parts_json or "[]")
    except json.JSONDecodeError:
        return []
    return parts if isinstance(parts, list) else []


class TelegramStorageProvider(StorageProvider):
    """Stores binary objects as chunked documents in a Telegram chat (Telegram Drive)."""

    def __init__(self, db: Session):
        self.db = db

    async def _run(self, func, *args, **kwargs):
        return await asyncio.to_thread(func, *args, **kwargs)

    def _send_chunk_sync(self, token: str, chat_id: str, object_id: str, index: int, chunk: bytes, user_id: int) -> dict:
        filename = f"{object_id}.part{index:04d}"
        caption = f"CloudBox user-{user_id} {object_id} part-{index}"
        result = telegram_api(
            token,
            "sendDocument",
            {
                "chat_id": chat_id,
                "caption": caption[:1024],
                "disable_notification": "true",
            },
            files={"document": (filename, io.BytesIO(chunk), "application/octet-stream")},
        )
        document = result.get("document") or {}
        file_id = document.get("file_id")
        if not file_id:
            raise TelegramAPIError("Telegram did not return a file_id for the uploaded chunk")
        return {
            "index": index,
            "file_id": file_id,
            "message_id": result.get("message_id"),
            "size": len(chunk),
        }

    def _upload_sync(self, user_id: int, source: BinaryIO, content_type: str = "") -> tuple[str, int]:
        token, chat_id = get_telegram_credentials(self.db)
        object_id = uuid.uuid4().hex
        parts: list[dict] = []
        index = 0
        while True:
            chunk = source.read(CHUNK_SIZE)
            if not chunk:
                break
            parts.append(self._send_chunk_sync(token, chat_id, object_id, index, chunk, user_id))
            index += 1

        total = sum(int(part["size"]) for part in parts)
        record = TelegramStoredObject(
            object_id=object_id,
            user_id=user_id,
            parts_json=json.dumps(parts),
            size_bytes=total,
        )
        self.db.add(record)
        self.db.commit()
        return object_id, total

    async def store_file(self, object_id: str, data: bytes, content_type: str = "") -> str:
        raise NotImplementedError("Use store_stream_for_user for Telegram Drive uploads")

    async def store_stream(self, object_id: str, source: BinaryIO, content_type: str = "") -> str:
        raise NotImplementedError("Telegram Drive uploads require user context")

    async def store_stream_for_user(
        self,
        user_id: int,
        source: BinaryIO,
        content_type: str = "",
    ) -> tuple[str, int]:
        return await self._run(self._upload_sync, user_id, source, content_type)

    def _download_sync(self, object_id: str) -> bytes:
        token, _chat_id = get_telegram_credentials(self.db)
        record = self.db.query(TelegramStoredObject).filter(TelegramStoredObject.object_id == object_id).first()
        if not record:
            raise FileNotFoundError()
        buffer = io.BytesIO()
        for part in sorted(_load_parts(record), key=lambda item: int(item.get("index", 0))):
            meta = telegram_api(token, "getFile", {"file_id": part["file_id"]})
            file_path = meta.get("file_path")
            if not file_path:
                raise TelegramAPIError("Telegram did not return a file_path")
            buffer.write(download_telegram_file(token, file_path))
        return buffer.getvalue()

    async def retrieve_file(self, object_id: str) -> bytes:
        return await self._run(self._download_sync, object_id)

    async def stream_file(self, object_id: str) -> AsyncIterator[bytes]:
        data = await self.retrieve_file(object_id)
        chunk_size = 1024 * 1024
        for offset in range(0, len(data), chunk_size):
            yield data[offset : offset + chunk_size]

    def _delete_sync(self, object_id: str) -> bool:
        record = self.db.query(TelegramStoredObject).filter(TelegramStoredObject.object_id == object_id).first()
        if not record:
            return False
        try:
            token, chat_id = get_telegram_credentials(self.db)
        except TelegramNotConnectedError:
            token, chat_id = "", ""
        if token and chat_id:
            for part in _load_parts(record):
                message_id = part.get("message_id")
                if message_id is None:
                    continue
                try:
                    telegram_api(token, "deleteMessage", {"chat_id": chat_id, "message_id": message_id})
                except TelegramAPIError:
                    pass
        self.db.delete(record)
        self.db.commit()
        return True

    async def delete_file(self, object_id: str) -> bool:
        return await self._run(self._delete_sync, object_id)

    async def file_exists(self, object_id: str) -> bool:
        record = self.db.query(TelegramStoredObject).filter(TelegramStoredObject.object_id == object_id).first()
        return record is not None

    async def get_file_size(self, object_id: str) -> int:
        record = self.db.query(TelegramStoredObject).filter(TelegramStoredObject.object_id == object_id).first()
        return int(record.size_bytes) if record else 0
