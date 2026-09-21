import os
import aiofiles
from typing import AsyncIterator, BinaryIO
from urllib.parse import unquote
from backend.storage.base import StorageProvider
from backend.config import settings

class LocalStorageProvider(StorageProvider):
    def __init__(self):
        self.root_dir = os.path.abspath(settings.STORAGE_ROOT_DIR)
        os.makedirs(self.root_dir, exist_ok=True)
        
    def _get_safe_path(self, object_id: str) -> str:
        # Defense-in-depth: decode URL-encoded characters (e.g. %2F → /)
        # to catch encoded traversal attempts like ..%2F..%2F
        decoded_id = unquote(object_id)
        target = os.path.abspath(os.path.join(self.root_dir, decoded_id))
        if not target.startswith(self.root_dir):
            raise ValueError("Path traversal attempt detected")
        return target

    async def store_file(self, object_id: str, data: bytes, content_type: str = "") -> str:
        safe_path = self._get_safe_path(object_id)
        # Create subdirectories if needed (though typically object_id is flat)
        os.makedirs(os.path.dirname(safe_path), exist_ok=True)
        async with aiofiles.open(safe_path, 'wb') as f:
            await f.write(data)
        return object_id

    async def store_stream(self, object_id: str, source: BinaryIO, content_type: str = "") -> str:
        safe_path = self._get_safe_path(object_id)
        os.makedirs(os.path.dirname(safe_path), exist_ok=True)
        async with aiofiles.open(safe_path, 'wb') as f:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                await f.write(chunk)
        return object_id
        
    async def retrieve_file(self, object_id: str) -> bytes:
        safe_path = self._get_safe_path(object_id)
        if not os.path.exists(safe_path):
            raise FileNotFoundError()
        async with aiofiles.open(safe_path, 'rb') as f:
            return await f.read()

    async def stream_file(self, object_id: str) -> AsyncIterator[bytes]:
        safe_path = self._get_safe_path(object_id)
        if not os.path.exists(safe_path):
            raise FileNotFoundError()
        async with aiofiles.open(safe_path, 'rb') as f:
            while True:
                chunk = await f.read(1024 * 1024)
                if not chunk:
                    break
                yield chunk

    async def stream_file_range(self, object_id: str, start: int, end: int) -> AsyncIterator[bytes]:
        safe_path = self._get_safe_path(object_id)
        if not os.path.exists(safe_path):
            raise FileNotFoundError()
        total_size = os.path.getsize(safe_path)
        if start < 0:
            start = 0
        if end >= total_size:
            end = total_size - 1
        if start > end:
            return
        
        async with aiofiles.open(safe_path, 'rb') as f:
            await f.seek(start)
            bytes_left = end - start + 1
            chunk_size = 1024 * 1024
            while bytes_left > 0:
                to_read = min(chunk_size, bytes_left)
                chunk = await f.read(to_read)
                if not chunk:
                    break
                bytes_left -= len(chunk)
                yield chunk

            
    async def delete_file(self, object_id: str) -> bool:
        safe_path = self._get_safe_path(object_id)
        if os.path.exists(safe_path):
            os.remove(safe_path)
            return True
        return False
        
    async def file_exists(self, object_id: str) -> bool:
        safe_path = self._get_safe_path(object_id)
        return os.path.exists(safe_path)
        
    async def get_file_size(self, object_id: str) -> int:
        safe_path = self._get_safe_path(object_id)
        if os.path.exists(safe_path):
            return os.path.getsize(safe_path)
        return 0
