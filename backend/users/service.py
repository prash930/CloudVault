from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import func, or_
from sqlalchemy.orm import Session

from backend.files.models import AuditLog, FileRecord
from backend.users.models import User

def get_user_by_email(db: Session, email: str) -> Optional[User]:
    return db.query(User).filter(User.email == email).first()

def get_user_by_id(db: Session, user_id: int) -> Optional[User]:
    return db.query(User).filter(User.id == user_id).first()

def create_user(
    db: Session,
    email: str,
    display_name: str,
    hashed_password: str,
    *,
    status: str = "ACTIVE",
    storage_quota_bytes: Optional[int] = None,
) -> User:
    from backend.settings import service as settings_service

    if storage_quota_bytes is None:
        storage_quota_bytes = settings_service.get_runtime_settings(db)["default_quota_bytes"]

    db_user = User(
        email=email,
        display_name=display_name,
        hashed_password=hashed_password,
        status=status,
        role="USER",
        storage_quota_bytes=storage_quota_bytes,
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user


def ensure_minimum_user_quota(db: Session) -> None:
    """Give every user at least the configured default quota."""
    from backend.config import settings
    from backend.settings import service as settings_service

    floor = settings.DEFAULT_QUOTA_BYTES
    runtime = settings_service.get_runtime_settings(db)
    if int(runtime.get("default_quota_bytes") or 0) < floor:
        settings_service.update_settings(db, {"default_quota_bytes": floor})
    db.query(User).filter(User.storage_quota_bytes < floor).update(
        {User.storage_quota_bytes: floor},
        synchronize_session=False,
    )
    db.commit()

def record_login(db: Session, user: User) -> User:
    user.last_login_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(user)
    return user

def approve_user(db: Session, user_id: int) -> Optional[User]:
    user = get_user_by_id(db, user_id)
    if user:
        user.status = "ACTIVE"
        db.commit()
        db.refresh(user)
    return user

def reject_user(db: Session, user_id: int) -> bool:
    user = get_user_by_id(db, user_id)
    if user and user.status == "PENDING":
        db.delete(user)
        db.commit()
        return True
    return False

def update_user_status(db: Session, user_id: int, new_status: str, reason: str = None) -> Optional[User]:
    user = get_user_by_id(db, user_id)
    if user:
        user.status = new_status
        db.commit()
        db.refresh(user)
    return user

def reset_user_password(db: Session, user_id: int, new_password: str) -> Optional[User]:
    from backend.auth.service import hash_password

    user = get_user_by_id(db, user_id)
    if user:
        user.hashed_password = hash_password(new_password)
        db.commit()
        db.refresh(user)
    return user

def search_users(
    db: Session,
    *,
    search: Optional[str] = None,
    status: Optional[str] = None,
    skip: int = 0,
    limit: int = 100,
):
    query = db.query(User)
    if search:
        term = f"%{search.strip()}%"
        query = query.filter(or_(User.email.ilike(term), User.display_name.ilike(term)))
    if status:
        query = query.filter(User.status == status)
    total = query.count()
    users = query.order_by(User.created_at.desc()).offset(skip).limit(limit).all()
    return users, total

def get_all_users(db: Session, skip: int = 0, limit: int = 100):
    users, _ = search_users(db, skip=skip, limit=limit)
    return users

def get_users_with_files(db: Session) -> list[dict]:
    from backend.files.models import FileRecord

    users = db.query(User).order_by(User.display_name.asc()).all()
    result = []
    for user in users:
        data = {c.name: getattr(user, c.name) for c in user.__table__.columns}
        data.pop("hashed_password", None)
        data.pop("avatar_1_path", None)
        data.pop("avatar_2_path", None)

        files = (
            db.query(FileRecord)
            .filter(
                FileRecord.user_id == user.id,
                FileRecord.is_trashed.is_(False),
                FileRecord.parent_folder_id.is_(None),
            )
            .order_by(FileRecord.is_folder.desc(), FileRecord.filename.asc())
            .all()
        )
        file_list = []
        for record in files:
            file_list.append({
                "id": record.id,
                "filename": record.filename,
                "original_filename": record.original_filename,
                "mime_type": record.mime_type,
                "size_bytes": record.size_bytes or 0,
                "is_folder": record.is_folder,
                "parent_folder_id": record.parent_folder_id,
                "is_trashed": record.is_trashed,
                "moderation_status": record.moderation_status,
                "created_at": record.created_at,
                "updated_at": record.updated_at,
            })
        result.append({**data, "file_count": len([f for f in file_list if not f["is_folder"]]), "files": file_list})

    return result

def get_pending_users(db: Session):
    return db.query(User).filter(User.status == "PENDING").order_by(User.created_at.asc()).all()

def get_user_file_count(db: Session, user_id: int) -> int:
    return db.query(FileRecord).filter(
        FileRecord.user_id == user_id,
        FileRecord.is_folder.is_(False),
        FileRecord.is_trashed.is_(False),
    ).count()

def get_user_admin_detail(db: Session, user_id: int) -> Optional[dict]:
    user = get_user_by_id(db, user_id)
    if not user:
        return None
    data = {c.name: getattr(user, c.name) for c in user.__table__.columns}
    data.pop("hashed_password", None)
    return {
        **data,
        "file_count": get_user_file_count(db, user_id),
    }

def get_dashboard_stats(db: Session) -> dict:
    total_users = db.query(User).count()
    active = db.query(User).filter(User.status == "ACTIVE").count()
    pending = db.query(User).filter(User.status == "PENDING").count()
    warned = db.query(User).filter(User.status == "WARNED").count()
    suspended = db.query(User).filter(User.status == "SUSPENDED").count()
    banned = db.query(User).filter(User.status == "BANNED").count()

    total_used = db.query(func.sum(User.storage_used_bytes)).scalar() or 0
    total_quota = db.query(func.sum(User.storage_quota_bytes)).scalar() or 0
    total_files = db.query(FileRecord).filter(
        FileRecord.is_folder.is_(False),
        FileRecord.is_trashed.is_(False),
    ).count()

    recent_files = (
        db.query(FileRecord, User.email)
        .join(User, User.id == FileRecord.user_id)
        .filter(FileRecord.is_folder.is_(False))
        .order_by(FileRecord.created_at.desc())
        .limit(10)
        .all()
    )
    recent_uploads = [
        {
            "id": record.id,
            "filename": record.filename,
            "user_id": record.user_id,
            "user_email": email,
            "size_bytes": record.size_bytes or 0,
            "mime_type": record.mime_type,
            "created_at": record.created_at,
        }
        for record, email in recent_files
    ]

    recent_logs = (
        db.query(AuditLog)
        .order_by(AuditLog.created_at.desc())
        .limit(10)
        .all()
    )
    recent_admin_actions = [
        {
            "id": log.id,
            "action": log.action,
            "admin_id": log.admin_id,
            "user_id": log.user_id,
            "file_id": log.file_id,
            "reason": log.reason,
            "created_at": log.created_at,
        }
        for log in recent_logs
    ]

    return {
        "total_users": total_users,
        "active_users": active,
        "pending_users": pending,
        "warned_users": warned,
        "suspended_users": suspended,
        "banned_users": banned,
        "total_files": total_files,
        "total_storage_used_bytes": total_used,
        "total_quota_bytes": total_quota,
        "recent_uploads": recent_uploads,
        "recent_admin_actions": recent_admin_actions,
    }

def update_storage_used(db: Session, user_id: int, bytes_delta: int) -> Optional[User]:
    user = get_user_by_id(db, user_id)
    if user:
        user.storage_used_bytes = max(0, (user.storage_used_bytes or 0) + bytes_delta)
        db.commit()
        db.refresh(user)
    return user

def check_quota(db: Session, user_id: int, file_size: int) -> bool:
    user = get_user_by_id(db, user_id)
    if not user:
        return False
    return (user.storage_used_bytes + file_size) <= user.storage_quota_bytes


VALID_STATUSES = {"PENDING", "ACTIVE", "WARNED", "SUSPENDED", "BANNED"}


def validate_status(status: str) -> bool:
    return status in VALID_STATUSES


def update_user_quota(db: Session, user_id: int, new_quota_bytes: int) -> Optional[dict]:
    user = get_user_by_id(db, user_id)
    if not user:
        return None

    old_quota = user.storage_quota_bytes
    warning = None

    if new_quota_bytes < user.storage_used_bytes:
        warning = (
            f"New quota ({new_quota_bytes} bytes) is below current usage "
            f"({user.storage_used_bytes} bytes). Existing files will NOT be deleted. "
            f"Additional uploads will be blocked until usage falls below the new quota."
        )

    user.storage_quota_bytes = new_quota_bytes
    db.commit()
    db.refresh(user)

    return {
        "old_quota": old_quota,
        "new_quota": new_quota_bytes,
        "warning": warning,
        "user": user
    }
