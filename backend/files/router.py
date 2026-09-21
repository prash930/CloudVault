from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request, UploadFile, File
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from backend.auth.dependencies import get_current_active_user, get_current_active_user_flexible
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


import re

RANGE_HEADER_RE = re.compile(r"^bytes=(\d*)-(\d*)$")


async def build_range_streaming_response(
    request: Request,
    provider,
    object_id: str,
    filename: str,
    mime_type: Optional[str] = None,
    inline: bool = False,
):
    total_size = await provider.get_file_size(object_id)
    range_header = request.headers.get("Range")
    disposition_type = "inline" if inline else "attachment"

    if range_header:
        match = RANGE_HEADER_RE.match(range_header.strip())
        if match:
            start_str, end_str = match.groups()
            if start_str and end_str:
                start = int(start_str)
                end = int(end_str)
            elif start_str:
                start = int(start_str)
                end = total_size - 1
            elif end_str:
                start = max(0, total_size - int(end_str))
                end = total_size - 1
            else:
                start = 0
                end = total_size - 1

            if total_size > 0 and (start >= total_size or end >= total_size or start > end):
                raise HTTPException(
                    status_code=416,
                    detail="Requested Range Not Satisfiable",
                    headers={"Content-Range": f"bytes */{total_size}"},
                )

            content_length = max(0, end - start + 1) if total_size > 0 else 0
            headers = {
                "Content-Disposition": f'{disposition_type}; filename="{filename}"',
                "Content-Range": f"bytes {start}-{end}/{total_size}",
                "Accept-Ranges": "bytes",
                "Content-Length": str(content_length),
            }
            return StreamingResponse(
                provider.stream_file_range(object_id, start, end),
                status_code=206,
                media_type=mime_type or "application/octet-stream",
                headers=headers,
            )

    headers = {
        "Content-Disposition": f'{disposition_type}; filename="{filename}"',
        "Accept-Ranges": "bytes",
        "Content-Length": str(total_size),
    }
    return StreamingResponse(
        provider.stream_file(object_id),
        status_code=200,
        media_type=mime_type or "application/octet-stream",
        headers=headers,
    )


@router.get("/{file_id}/download")
async def download_file_endpoint(
    file_id: int,
    request: Request,
    inline: bool = Query(False),
    current_user: User = Depends(get_current_active_user_flexible),
    db: Session = Depends(get_db),
):
    record = service.get_owned_record(db, current_user.id, file_id)
    if record.is_folder:
        raise HTTPException(status_code=400, detail="Folders cannot be downloaded directly")
    provider = service.get_provider_for_record(db, record)
    if not await provider.file_exists(record.storage_object_id):
        raise HTTPException(status_code=404, detail="File bytes not found on storage")
    return await build_range_streaming_response(
        request=request,
        provider=provider,
        object_id=record.storage_object_id,
        filename=record.filename,
        mime_type=record.mime_type,
        inline=inline,
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
