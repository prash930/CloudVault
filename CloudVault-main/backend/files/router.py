from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from backend.auth.dependencies import get_current_active_user
from backend.database import get_db
from backend.files import schemas, service, share_service
from backend.users.models import User

router = APIRouter(prefix="/files", tags=["Files"])


@router.get("/storage-usage", response_model=schemas.StorageUsageResponse)
def get_storage_usage_endpoint(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.get_storage_usage(db, current_user.id)


@router.get("", response_model=schemas.FileListResponse)
def list_files_endpoint(
    parent_folder_id: Optional[int] = Query(None),
    search: Optional[str] = Query(None, min_length=1),
    sort: str = Query("name", pattern="^(name|date|size|type)$"),
    direction: str = Query("asc", pattern="^(asc|desc)$"),
    category: Optional[str] = Query(None, pattern="^(images|videos|music|documents|emails|passwords)$"),
    recursive: bool = Query(False),
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.list_files(db, current_user.id, parent_folder_id, search, sort, direction, category=category, recursive=recursive)


@router.get("/search", response_model=schemas.FileListResponse)
def search_files_endpoint(
    q: str = Query(..., min_length=1),
    sort: str = Query("name", pattern="^(name|date|size|type)$"),
    direction: str = Query("asc", pattern="^(asc|desc)$"),
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.search_files(db, current_user.id, q, sort, direction)


@router.get("/trash", response_model=schemas.FileListResponse)
def list_trash_endpoint(
    sort: str = Query("date", pattern="^(name|date|size|type)$"),
    direction: str = Query("desc", pattern="^(asc|desc)$"),
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.list_files(db, current_user.id, None, None, sort, direction, trashed=True)


@router.get("/shared", response_model=schemas.ShareListResponse)
def list_shared_files(
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return share_service.list_shared_with_me(db, current_user)


@router.post("/{file_id}/share", response_model=schemas.ShareOut)
def share_file_with_email(
    file_id: int,
    req: schemas.ShareEmailRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return share_service.share_with_email(db, current_user, file_id, req.email)


@router.post("/{file_id}/share-link", response_model=schemas.ShareOut)
def create_file_share_link(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return share_service.create_share_link(db, current_user, file_id)


@router.post("/folders", response_model=schemas.FileOut)
def create_folder_endpoint(
    req: schemas.CreateFolderRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.create_folder(db, current_user.id, req.name, req.parent_folder_id)


@router.post("/upload", response_model=schemas.FileOut)
async def upload_file_endpoint(
    file: UploadFile = File(...),
    parent_folder_id: Optional[int] = Query(None),
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return await service.create_upload(db, current_user, file, parent_folder_id)


@router.get("/{file_id}", response_model=schemas.FileOut)
def get_file_endpoint(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.get_owned_record(db, current_user.id, file_id, include_trashed=True)


@router.get("/{file_id}/download")
async def download_file_endpoint(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    record = service.get_owned_record(db, current_user.id, file_id)
    if record.is_folder:
        raise HTTPException(status_code=400, detail="Folders cannot be downloaded directly")
    provider = service.get_provider_for_record(db, record)
    headers = {"Content-Disposition": f'attachment; filename="{record.filename}"'}
    return StreamingResponse(
        provider.stream_file(record.storage_object_id),
        media_type=record.mime_type or "application/octet-stream",
        headers=headers,
    )


@router.post("/{file_id}/rename", response_model=schemas.FileOut)
def rename_file_endpoint(
    file_id: int,
    req: schemas.RenameRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.rename_record(db, current_user.id, file_id, req.filename)


@router.post("/{file_id}/move", response_model=schemas.FileOut)
def move_file_endpoint(
    file_id: int,
    req: schemas.MoveRequest,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.move_record(db, current_user.id, file_id, req.parent_folder_id)


@router.post("/{file_id}/trash", response_model=schemas.FileOut)
def move_to_trash_endpoint(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.move_to_trash(db, current_user.id, file_id)


@router.post("/{file_id}/restore", response_model=schemas.FileOut)
def restore_file_endpoint(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    return service.restore_from_trash(db, current_user.id, file_id)


@router.delete("/{file_id}", response_model=schemas.MessageResponse)
async def permanently_delete_endpoint(
    file_id: int,
    current_user: User = Depends(get_current_active_user),
    db: Session = Depends(get_db),
):
    record = service.get_owned_record(db, current_user.id, file_id, include_trashed=True)
    if not record.is_trashed:
        raise HTTPException(status_code=400, detail="Move file to Trash before permanent deletion")
    await service.permanently_delete(db, current_user.id, file_id)
    return {"message": "File permanently deleted"}
