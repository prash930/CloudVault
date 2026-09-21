from abc import ABC, abstractmethod
from typing import AsyncIterator, BinaryIO, Optional
import os

class StorageProvider(ABC):
    """Abstract storage provider interface.
    
    Implementations can be swapped without changing the rest of the app.
    Supported: local, telegram_drive
    """
    
    @abstractmethod
    async def store_file(self, object_id: str, data: bytes, content_type: str = "") -> str:
        """Store file data. Returns the storage path/key."""
        pass

    async def store_stream(self, object_id: str, source: BinaryIO, content_type: str = "") -> str:
        """Store file data from a stream. Returns the storage key."""
        pass
    
    @abstractmethod
    async def retrieve_file(self, object_id: str) -> bytes:
        """Retrieve file data by object ID."""
        pass

    async def stream_file(self, object_id: str) -> AsyncIterator[bytes]:
        """Yield file data by object ID without loading the entire file into memory."""
        pass

    async def stream_file_range(self, object_id: str, start: int, end: int) -> AsyncIterator[bytes]:
        """Yield file data for the byte range [start, end] inclusive."""
        total = await self.get_file_size(object_id)
        if start < 0:
            start = 0
        if end >= total:
            end = total - 1
        if start > end:
            return
        data = await self.retrieve_file(object_id)
        chunk_size = 1024 * 1024
        sliced = data[start : end + 1]
        for offset in range(0, len(sliced), chunk_size):
            yield sliced[offset : offset + chunk_size]

    
    @abstractmethod
    async def delete_file(self, object_id: str) -> bool:
        """Delete a file. Returns True if successful."""
        pass
    
    @abstractmethod
    async def file_exists(self, object_id: str) -> bool:
        """Check if a file exists in storage."""
        pass
    
    @abstractmethod
    async def get_file_size(self, object_id: str) -> int:
        """Get file size in bytes."""
        pass
