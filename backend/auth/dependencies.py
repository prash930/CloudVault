from typing import Optional
from fastapi import Depends, HTTPException, Query, Request, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session
from jose import jwt, JWTError
from backend.database import get_db
from backend.config import settings
from backend.users.models import User
from backend.users.service import get_user_by_id
from backend.auth.service import is_token_blacklisted

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")
admin_oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/admin/login", scheme_name="AdminOAuth2PasswordBearer")

def _validate_raw_token(token: str, db: Session) -> User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if not token or is_token_blacklisted(token):
        raise credentials_exception
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
        user_id: str = payload.get("sub")
        if user_id is None or payload.get("purpose") is not None:
            raise credentials_exception
        user_id_int = int(user_id)
    except (JWTError, TypeError, ValueError):
        raise credentials_exception
        
    user = get_user_by_id(db, user_id_int)
    if user is None:
        raise credentials_exception
    return user

def get_current_user(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)) -> User:
    return _validate_raw_token(token, db)

def get_current_active_user(user: User = Depends(get_current_user)) -> User:
    if user.status in ["PENDING", "SUSPENDED", "BANNED"]:
        raise HTTPException(status_code=403, detail=f"User account is {user.status.lower()}")
    return user

def get_user_from_header_or_query(
    request: Request,
    token: Optional[str] = Query(None),
    db: Session = Depends(get_db),
) -> User:
    auth_header = request.headers.get("Authorization")
    raw_token = None
    if auth_header and auth_header.startswith("Bearer "):
        raw_token = auth_header[7:].strip()
    elif token:
        raw_token = token.strip()
    
    if not raw_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return _validate_raw_token(raw_token, db)

def get_current_active_user_flexible(user: User = Depends(get_user_from_header_or_query)) -> User:
    if user.status in ["PENDING", "SUSPENDED", "BANNED"]:
        raise HTTPException(status_code=403, detail=f"User account is {user.status.lower()}")
    return user


def get_current_admin_user(token: str = Depends(admin_oauth2_scheme), db: Session = Depends(get_db)) -> User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate admin credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if is_token_blacklisted(token):
        raise credentials_exception
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.JWT_ALGORITHM])
        user_id: str = payload.get("sub")
        if user_id is None or payload.get("purpose") is not None:
            raise credentials_exception
        user_id_int = int(user_id)
    except (JWTError, TypeError, ValueError):
        raise credentials_exception

    user = get_user_by_id(db, user_id_int)
    if user is None:
        raise credentials_exception
    return user

def require_admin(user: User = Depends(get_current_admin_user)) -> User:
    if user.status != "ACTIVE":
        raise HTTPException(status_code=403, detail=f"Admin account is not active ({user.status.lower()})")
    if user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Not enough permissions")
    return user
