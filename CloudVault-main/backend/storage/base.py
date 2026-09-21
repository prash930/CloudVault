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
