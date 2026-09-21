from sqlalchemy import Column, Integer, String, BigInteger, DateTime
from datetime import datetime, timezone
from backend.database import Base
from backend.config import settings

class User(Base):
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    display_name = Column(String(100), nullable=False)
    hashed_password = Column(String(255), nullable=False)
    role = Column(String(20), default="USER")
    status = Column(String(20), default="ACTIVE")
    storage_quota_bytes = Column(BigInteger, default=settings.DEFAULT_QUOTA_BYTES)
    storage_used_bytes = Column(BigInteger, default=0)
    avatar_1_path = Column(String(500), nullable=True)
    avatar_2_path = Column(String(500), nullable=True)
    last_login_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), onupdate=lambda: datetime.now(timezone.utc))
