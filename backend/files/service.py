import mimetypes
import os
import re
import uuid
from datetime import datetime, timezone
from typing import Optional

from fastapi import HTTPException, UploadFile
from sqlalchemy import or_
from sqlalchemy.orm import Session

from backend.config import settings
from backend.files.models import FileRecord
from backend.files.schemas import format_bytes
from backend.storage.factory import get_storage_provider
from backend.storage.base import StorageProvider
from backend.users.models import User
from backend.users.service import get_user_by_id, update_storage_used

SAFE_NAME_RE = re.compile(r"^[^\\/:*?\"<>|\x00-\x1f]+$")
BLOCKED_EXTENSIONS = {
    ".bat", ".cmd", ".com", ".dll", ".exe", ".js", ".msi", ".ps1", ".scr", ".sh", ".vbs"
}
ALLOWED_SORTS = {
    "name": FileRecord.filename,
    "date": FileRecord.updated_at,
    "size": FileRecord.size_bytes,
    "type": FileRecord.mime_type,
}


def get_provider_for_record(db: Session, record: FileRecord) -> StorageProvider:
    return get_storage_provider(db, record.storage_provider or "local")


def get_storage_usage(db: Session, user_id: int) -> dict:
    user = get_user_by_id(db, user_id)
    if not user:
        return {
            "used_bytes": 0,
            "quota_bytes": 0,
            "used_formatted": "0.0 B",
            "quota_formatted": "0.0 B",
            "usage_percentage": 0.0
        }

    used = user.storage_used_bytes
    quota = user.storage_quota_bytes
    pct = (used / quota * 100.0) if quota > 0 else 0.0

    return {
        "used_bytes": used,
        "quota_bytes": quota,
        "used_formatted": format_bytes(used),
        "quota_formatted": format_bytes(quota),
        "usage_percentage": round(pct, 2)
    }


def validate_filename(filename: str) -> str:
    cleaned = os.path.basename((filename or "").strip())
    if not cleaned or cleaned in {".", ".."} or cleaned != filename.strip():
        raise HTTPException(status_code=400, detail="Invalid filename")
    if not SAFE_NAME_RE.match(cleaned):
        raise HTTPException(status_code=400, detail="Invalid filename")
    extension = os.path.splitext(cleaned)[1].lower()
    if extension in BLOCKED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="File type is not allowed")
    return cleaned


def validate_parent_folder(db: Session, user_id: int, parent_folder_id: Optional[int]) -> Optional[FileRecord]:
    if parent_folder_id is None:
        return None
    folder = db.query(FileRecord).filter(
        FileRecord.id == parent_folder_id,
        FileRecord.user_id == user_id,
        FileRecord.is_folder.is_(True),
        FileRecord.is_trashed.is_(False),
    ).first()
    if not folder:
        raise HTTPException(status_code=404, detail="Folder not found")
    return folder


def get_owned_record(
    db: Session,
    user_id: int,
    file_id: int,
    include_trashed: bool = False,
) -> FileRecord:
    query = db.query(FileRecord).filter(FileRecord.id == file_id, FileRecord.user_id == user_id)
    if not include_trashed:
        query = query.filter(FileRecord.is_trashed.is_(False))
    record = query.first()
    if not record:
        raise HTTPException(status_code=404, detail="File or folder not found")
    return record


def get_record_for_admin(db: Session, file_id: int) -> FileRecord:
    record = db.query(FileRecord).filter(FileRecord.id == file_id).first()
    if not record:
        raise HTTPException(status_code=404, detail="File or folder not found")
    return record


def list_files(
    db: Session,
    user_id: int,
    parent_folder_id: Optional[int],
    search: Optional[str],
    sort: str,
    direction: str,
    trashed: bool = False,
):
    if parent_folder_id is not None:
        validate_parent_folder(db, user_id, parent_folder_id)

    query = db.query(FileRecord).filter(
        FileRecord.user_id == user_id,
        FileRecord.is_trashed.is_(trashed),
    )
    if parent_folder_id is None:
        query = query.filter(FileRecord.parent_folder_id.is_(None))
    else:
        query = query.filter(FileRecord.parent_folder_id == parent_folder_id)
    if search:
        query = query.filter(FileRecord.filename.ilike(f"%{search}%"))

    sort_col = ALLOWED_SORTS.get(sort, FileRecord.filename)
    if direction.lower() == "desc":
        sort_col = sort_col.desc()
    else:
        sort_col = sort_col.asc()
    query = query.order_by(FileRecord.is_folder.desc(), sort_col)
    items = query.all()
    return {"items": items, "total": len(items)}


def search_files(db: Session, user_id: int, query_text: str, sort: str, direction: str):
    query = db.query(FileRecord).filter(
        FileRecord.user_id == user_id,
        FileRecord.is_trashed.is_(False),
        FileRecord.filename.ilike(f"%{query_text}%"),
    )
    sort_col = ALLOWED_SORTS.get(sort, FileRecord.filename)
    sort_col = sort_col.desc() if direction.lower() == "desc" else sort_col.asc()
    items = query.order_by(FileRecord.is_folder.desc(), sort_col).all()
    return {"items": items, "total": len(items)}


def create_folder(db: Session, user_id: int, name: str, parent_folder_id: Optional[int]) -> FileRecord:
    safe_name = validate_filename(name)
    validate_parent_folder(db, user_id, parent_folder_id)
    folder = FileRecord(
        user_id=user_id,
        filename=safe_name,
        original_filename=safe_name,
        mime_type="inode/directory",
        size_bytes=0,
        parent_folder_id=parent_folder_id,
        is_folder=True,
        moderation_status="APPROVED",
    )
    db.add(folder)
    db.commit()
    db.refresh(folder)
    return folder


def assert_quota_allows(user: User, incoming_size: int, max_upload_bytes: Optional[int] = None) -> None:
    cap = max_upload_bytes if max_upload_bytes is not None else settings.MAX_UPLOAD_BYTES
    if incoming_size < 0:
        raise HTTPException(status_code=400, detail="Invalid upload size")
    if incoming_size > cap:
        raise HTTPException(status_code=413, detail="File exceeds maximum upload size")
    if (user.storage_used_bytes or 0) + incoming_size > user.storage_quota_bytes:
        raise HTTPException(status_code=413, detail="Storage quota exceeded")


async def create_upload(
    db: Session,
    user: User,
    upload: UploadFile,
    parent_folder_id: Optional[int],
) -> FileRecord:
    filename = validate_filename(upload.filename or "upload")
    validate_parent_folder(db, user.id, parent_folder_id)

    from backend.settings import service as settings_service
    from backend.storage.service import is_telegram_connected
    runtime = settings_service.get_runtime_settings(db)
    active_provider = runtime.get("storage_provider", "local")

    # For telegram_drive: if not connected, reject the upload with HTTP 503.
    # Admins must connect Telegram Drive via Admin Settings before uploads work.
    if active_provider == "telegram_drive" and not is_telegram_connected(db):
        raise HTTPException(
            status_code=503,
            detail="Telegram Drive is not connected. Please connect it from Admin Settings before uploading files.",
        )

    if active_provider == "google_drive":
        from backend.storage.service import validate_provider_switch

        try:
            validate_provider_switch(db, active_provider)
        except ValueError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        provider = get_storage_provider(db, active_provider)
        try:
            object_id, actual_size = await provider.store_stream_for_user(
                user.id,
                upload.file,
                upload.content_type or "",
            )
        except Exception as exc:
            raise HTTPException(
                status_code=502,
                detail=f"{active_provider} upload failed: {exc}",
            ) from exc
    elif active_provider == "telegram_drive":
        provider = get_storage_provider(db, active_provider)
        try:
            object_id, actual_size = await provider.store_stream_for_user(
                user.id,
                upload.file,
                upload.content_type or "",
            )
        except Exception as exc:
            raise HTTPException(
                status_code=502,
                detail=f"Telegram Drive upload failed: {exc}",
            ) from exc
    else:
        object_id = f"user-{user.id}/{uuid.uuid4().hex}"
        provider = get_storage_provider(db, "local")
        await provider.store_stream(object_id, upload.file, upload.content_type or "")
        actual_size = await provider.get_file_size(object_id)

    try:
        assert_quota_allows(user, actual_size, runtime["max_upload_bytes"])
    except HTTPException:
        await provider.delete_file(object_id)
        raise

    mime_type = upload.content_type or mimetypes.guess_type(filename)[0] or "application/octet-stream"
    record = FileRecord(
        user_id=user.id,
        filename=filename,
        original_filename=filename,
        mime_type=mime_type,
        size_bytes=actual_size,
        storage_object_id=object_id,
        storage_provider=active_provider,
        parent_folder_id=parent_folder_id,
        is_folder=False,
        moderation_status="APPROVED",
    )
    db.add(record)
    update_storage_used(db, user.id, actual_size)
    db.commit()
    db.refresh(record)
    return record


def rename_record(db: Session, user_id: int, file_id: int, filename: str) -> FileRecord:
    record = get_owned_record(db, user_id, file_id)
    record.filename = validate_filename(filename)
    record.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(record)
    return record


def is_descendant(db: Session, user_id: int, folder_id: int, possible_descendant_id: int) -> bool:
    current = db.query(FileRecord).filter(
        FileRecord.id == possible_descendant_id,
        FileRecord.user_id == user_id,
        FileRecord.is_folder.is_(True),
    ).first()
    while current and current.parent_folder_id is not None:
        if current.parent_folder_id == folder_id:
            return True
        current = db.query(FileRecord).filter(
            FileRecord.id == current.parent_folder_id,
            FileRecord.user_id == user_id,
            FileRecord.is_folder.is_(True),
        ).first()
    return False


def move_record(db: Session, user_id: int, file_id: int, parent_folder_id: Optional[int]) -> FileRecord:
    record = get_owned_record(db, user_id, file_id)
    validate_parent_folder(db, user_id, parent_folder_id)
    if record.is_folder:
        if parent_folder_id == record.id:
            raise HTTPException(status_code=400, detail="Cannot move a folder into itself")
        if parent_folder_id is not None and is_descendant(db, user_id, record.id, parent_folder_id):
            raise HTTPException(status_code=400, detail="Cannot move a folder into its descendant")
    record.parent_folder_id = parent_folder_id
    record.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(record)
    return record


def set_trash_recursive(db: Session, user_id: int, record: FileRecord, trashed: bool) -> None:
    record.is_trashed = trashed
    record.trashed_at = datetime.now(timezone.utc) if trashed else None
    record.updated_at = datetime.now(timezone.utc)
    if record.is_folder:
        children = db.query(FileRecord).filter(
            FileRecord.user_id == user_id,
            FileRecord.parent_folder_id == record.id,
        ).all()
        for child in children:
            set_trash_recursive(db, user_id, child, trashed)


def move_to_trash(db: Session, user_id: int, file_id: int) -> FileRecord:
    record = get_owned_record(db, user_id, file_id)
    set_trash_recursive(db, user_id, record, True)
    db.commit()
    db.refresh(record)
    return record


def restore_from_trash(db: Session, user_id: int, file_id: int) -> FileRecord:
    record = get_owned_record(db, user_id, file_id, include_trashed=True)
    if not record.is_trashed:
        return record
    if record.parent_folder_id is not None:
        parent = db.query(FileRecord).filter(
            FileRecord.id == record.parent_folder_id,
            FileRecord.user_id == user_id,
            FileRecord.is_folder.is_(True),
            FileRecord.is_trashed.is_(False),
        ).first()
        if not parent:
            record.parent_folder_id = None
    set_trash_recursive(db, user_id, record, False)
    db.commit()
    db.refresh(record)
    return record


async def permanently_delete(db: Session, user_id: int, file_id: int) -> None:
    record = get_owned_record(db, user_id, file_id, include_trashed=True)
    records = collect_descendants(db, user_id, record)
    bytes_removed = 0
    for item in records:
        if not item.is_folder and item.storage_object_id:
            provider = get_provider_for_record(db, item)
            await provider.delete_file(item.storage_object_id)
            bytes_removed += item.size_bytes or 0
    for item in records:
        db.delete(item)
    update_storage_used(db, user_id, -bytes_removed)
    db.commit()


def collect_descendants(db: Session, user_id: int, record: FileRecord) -> list[FileRecord]:
    records = [record]
    if record.is_folder:
        children = db.query(FileRecord).filter(
            FileRecord.user_id == user_id,
            FileRecord.parent_folder_id == record.id,
        ).all()
        for child in children:
            records.extend(collect_descendants(db, user_id, child))
    return records


def admin_list_user_files(
    db: Session,
    user_id: int,
    *,
    parent_folder_id: Optional[int] = None,
    search: Optional[str] = None,
    sort: str = "name",
    direction: str = "asc",
    trashed: Optional[bool] = None,
):
    if parent_folder_id is not None:
        validate_parent_folder(db, user_id, parent_folder_id)

    query = db.query(FileRecord).filter(FileRecord.user_id == user_id)
    if trashed is None:
        query = query.filter(FileRecord.is_trashed.is_(False))
    else:
        query = query.filter(FileRecord.is_trashed.is_(trashed))

    if trashed is not True:
        if parent_folder_id is None and not search:
            query = query.filter(FileRecord.parent_folder_id.is_(None))
        elif parent_folder_id is not None:
            query = query.filter(FileRecord.parent_folder_id == parent_folder_id)

    if search:
        query = query.filter(FileRecord.filename.ilike(f"%{search}%"))

    sort_col = ALLOWED_SORTS.get(sort, FileRecord.filename)
    sort_col = sort_col.desc() if direction.lower() == "desc" else sort_col.asc()
    items = query.order_by(FileRecord.is_folder.desc(), sort_col).all()
    return {"items": items, "total": len(items)}
