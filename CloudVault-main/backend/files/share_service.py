import secrets
from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy.orm import Session

from backend.config import settings
from backend.files.models import FileRecord, FileShare
from backend.files.service import get_owned_record
from backend.users.models import User


def _share_url(token: str) -> str:
    return f"{settings.FRONTEND_BASE_URL.rstrip('/')}/s/{token}"


def serialize_share(share: FileShare, record: FileRecord | None = None) -> dict:
    return {
        "id": share.id,
        "file_id": share.file_id,
        "token": share.token,
        "shared_with_email": share.shared_with_email,
        "is_link": share.is_link,
        "share_url": _share_url(share.token),
        "created_at": share.created_at,
        "filename": record.filename if record else None,
        "mime_type": record.mime_type if record else None,
        "size_bytes": record.size_bytes if record else None,
        "is_folder": record.is_folder if record else None,
    }


def share_with_email(db: Session, user: User, file_id: int, email: str) -> dict:
    record = get_owned_record(db, user.id, file_id)
    if record.is_folder:
        raise HTTPException(status_code=400, detail="Folders cannot be shared yet")
    normalized = email.strip().lower()
    if normalized == user.email.lower():
        raise HTTPException(status_code=400, detail="You already own this file")
    existing = (
        db.query(FileShare)
        .filter(
            FileShare.file_id == file_id,
            FileShare.owner_id == user.id,
            FileShare.shared_with_email == normalized,
        )
        .first()
    )
    if existing:
        return serialize_share(existing, record)
    share = FileShare(
        file_id=file_id,
        owner_id=user.id,
        token=secrets.token_urlsafe(12),
        shared_with_email=normalized,
        is_link=False,
        created_at=datetime.now(timezone.utc),
    )
    db.add(share)
    db.commit()
    db.refresh(share)
    return serialize_share(share, record)


def create_share_link(db: Session, user: User, file_id: int) -> dict:
    record = get_owned_record(db, user.id, file_id)
    if record.is_folder:
        raise HTTPException(status_code=400, detail="Folders cannot be shared yet")
    existing = (
        db.query(FileShare)
        .filter(
            FileShare.file_id == file_id,
            FileShare.owner_id == user.id,
            FileShare.is_link.is_(True),
        )
        .first()
    )
    if existing:
        return serialize_share(existing, record)
    share = FileShare(
        file_id=file_id,
        owner_id=user.id,
        token=secrets.token_urlsafe(12),
        shared_with_email=None,
        is_link=True,
        created_at=datetime.now(timezone.utc),
    )
    db.add(share)
    db.commit()
    db.refresh(share)
    return serialize_share(share, record)


def list_shared_with_me(db: Session, user: User) -> dict:
    shares = (
        db.query(FileShare)
        .filter(FileShare.shared_with_email == user.email.lower())
        .order_by(FileShare.created_at.desc())
        .all()
    )
    items = []
    for share in shares:
        record = db.query(FileRecord).filter(FileRecord.id == share.file_id).first()
        if record and not record.is_trashed:
            items.append(serialize_share(share, record))
    return {"items": items, "total": len(items)}


def get_share_by_token(db: Session, token: str) -> tuple[FileShare, FileRecord]:
    share = db.query(FileShare).filter(FileShare.token == token).first()
    if not share:
        raise HTTPException(status_code=404, detail="Share not found")
    record = db.query(FileRecord).filter(FileRecord.id == share.file_id).first()
    if not record or record.is_trashed:
        raise HTTPException(status_code=404, detail="File is no longer available")
    return share, record
