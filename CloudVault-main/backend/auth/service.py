import bcrypt
import hashlib
import base64
from datetime import datetime, timedelta, timezone
from typing import Optional
from jose import JWTError, jwt
from backend.config import settings


def _prepare_password(password: str) -> bytes:
    """Pre-hash the password with SHA-256 to handle bcrypt's 72-byte limit.
    
    bcrypt truncates at 72 bytes silently (or raises in bcrypt 5.0+).
    By pre-hashing with SHA-256 → base64 (44 chars), we ensure any length
    password is properly handled while maintaining security.
    """
    sha256_hash = hashlib.sha256(password.encode("utf-8")).digest()
    return base64.b64encode(sha256_hash)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    prepared = _prepare_password(plain_password)
    return bcrypt.checkpw(prepared, hashed_password.encode("utf-8"))


def hash_password(password: str) -> str:
    prepared = _prepare_password(password)
    salt = bcrypt.gensalt()
    hashed = bcrypt.hashpw(prepared, salt)
    return hashed.decode("utf-8")

# In-memory token blacklist for Phase 1
blacklisted_tokens = set()

def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.now(timezone.utc) + expires_delta
    else:
        expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, settings.SECRET_KEY, algorithm=settings.JWT_ALGORITHM)
    return encoded_jwt

def blacklist_token(token: str):
    blacklisted_tokens.add(token)

def is_token_blacklisted(token: str) -> bool:
    return token in blacklisted_tokens


def create_password_reset_token(user_id: int) -> str:
    return create_access_token(
        data={"sub": str(user_id), "purpose": "password_reset"},
        expires_delta=timedelta(minutes=settings.PASSWORD_RESET_EXPIRE_MINUTES),
    )


def verify_password_reset_token(token: str) -> Optional[int]:
    if is_token_blacklisted(token):
        return None
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
        if payload.get("purpose") != "password_reset":
            return None
        return int(payload["sub"])
    except (JWTError, KeyError, TypeError, ValueError):
        return None
