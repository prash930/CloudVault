from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from backend.database import get_db
from backend.users import schemas, service
from backend.auth.dependencies import require_admin
from backend.users.models import User
from backend.files.audit_service import create_audit_log, get_audit_logs
from backend.files import schemas as file_schemas
from backend.files import service as file_service
from backend.settings import service as settings_service

router = APIRouter(prefix="/admin", tags=["Admin"], dependencies=[Depends(require_admin)])

@router.get("/dashboard", response_model=schemas.DashboardStats)
def get_dashboard(db: Session = Depends(get_db)):
    return service.get_dashboard_stats(db)

@router.get("/users", response_model=schemas.UserListResponse)
def list_users(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    search: Optional[str] = Query(None, min_length=1),
    status: Optional[str] = Query(None),
    db: Session = Depends(get_db),
):
    if status and not service.validate_status(status):
        raise HTTPException(status_code=400, detail="Invalid status filter")
    users, total = service.search_users(db, search=search, status=status, skip=skip, limit=limit)
    return {"users": users, "total": total}

@router.get("/users/pending", response_model=list[schemas.UserAdminView])
def list_pending_users(db: Session = Depends(get_db)):
    return service.get_pending_users(db)

@router.get("/users/{user_id}", response_model=schemas.UserAdminDetail)
def get_user_detail(user_id: int, db: Session = Depends(get_db)):
    detail = service.get_user_admin_detail(db, user_id)
    if not detail:
        raise HTTPException(status_code=404, detail="User not found")
    return detail

@router.post("/users/{user_id}/approve", response_model=schemas.UserAdminView)
def approve_user_endpoint(user_id: int, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_admin)):
    user = service.approve_user(db, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    create_audit_log(
        db=db,
        action="USER_APPROVED",
        admin_id=admin.id,
        user_id=user_id,
        reason="Admin approved user registration",
        ip_address=request.client.host if request.client else None
    )
    return user

@router.post("/users/{user_id}/reject")
def reject_user_endpoint(user_id: int, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_admin)):
    success = service.reject_user(db, user_id)
    if not success:
        raise HTTPException(status_code=404, detail="Pending user not found")
    
    create_audit_log(
        db=db,
        action="USER_REJECTED",
        admin_id=admin.id,
        user_id=user_id,
        reason="Admin rejected user registration",
        ip_address=request.client.host if request.client else None
    )
    return {"message": "User rejected and deleted"}

@router.post("/users/{user_id}/update-status", response_model=schemas.UserAdminView)
def update_user_status_endpoint(user_id: int, req: schemas.UpdateStatusRequest, request: Request, db: Session = Depends(get_db), admin: User = Depends(require_admin)):
    if not service.validate_status(req.status):
        raise HTTPException(
            status_code=400,
            detail=f"Invalid status '{req.status}'. Must be one of: PENDING, ACTIVE, WARNED, SUSPENDED, BANNED"
        )
    
    if req.status in ["WARNED", "SUSPENDED", "BANNED"] and not req.reason:
        raise HTTPException(status_code=400, detail="Reason is required for WARNED, SUSPENDED, or BANNED status")
    
    target_user = service.get_user_by_id(db, user_id)
    if not target_user:
        raise HTTPException(status_code=404, detail="User not found")
    old_status = target_user.status
        
    user = service.update_user_status(db, user_id, req.status, req.reason)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    create_audit_log(
        db=db,
        action="USER_STATUS_CHANGED",
        admin_id=admin.id,
        user_id=user_id,
        reason=req.reason,
        details={"old_status": old_status, "new_status": req.status},
        ip_address=request.client.host if request.client else None
    )
    return user

@router.post("/users/{user_id}/update-quota", response_model=schemas.QuotaChangeResponse)
def update_user_quota_endpoint(
    user_id: int,
    req: schemas.UpdateQuotaRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin)
):
    if req.quota_bytes <= 0:
        raise HTTPException(status_code=400, detail="Quota must be greater than 0")
    
    result = service.update_user_quota(db, user_id, req.quota_bytes)
    if result is None:
        raise HTTPException(status_code=404, detail="User not found")
    
    create_audit_log(
        db=db,
        action="QUOTA_CHANGED",
        admin_id=admin.id,
        user_id=user_id,
        reason=req.reason,
        details={
            "old_quota_bytes": result["old_quota"],
            "new_quota_bytes": result["new_quota"]
        },
        ip_address=request.client.host if request.client else None
    )
    
    warning = result.get("warning")
    message = "Storage quota updated successfully"
    if warning:
        message += ". WARNING: " + warning
    
    return {
        "user_id": user_id,
        "old_quota_bytes": result["old_quota"],
        "new_quota_bytes": result["new_quota"],
        "warning": warning,
        "message": message
    }

@router.get("/audit-logs", response_model=schemas.AuditLogListResponse)
def get_audit_logs_endpoint(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=500),
    action: str = Query(None, description="Filter by action type"),
    user_id: int = Query(None, description="Filter by target user ID"),
    search: Optional[str] = Query(None, description="Search action or reason"),
    db: Session = Depends(get_db)
):
    logs, total = get_audit_logs(
        db,
        skip=skip,
        limit=limit,
        action_filter=action,
        user_id_filter=user_id,
        search=search,
    )
    return {"logs": logs, "total": total}


@router.get("/settings", response_model=schemas.AdminSettingsResponse)
def get_admin_settings(db: Session = Depends(get_db)):
    return settings_service.get_runtime_settings(db)


@router.put("/settings", response_model=schemas.AdminSettingsResponse)
def update_admin_settings(
    req: schemas.AdminSettingsUpdateRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    updates = req.model_dump(exclude_unset=True)
    if not updates:
        raise HTTPException(status_code=400, detail="No settings provided")
    if "storage_provider" in updates:
        from backend.storage.service import validate_provider_switch

        try:
            validate_provider_switch(db, updates["storage_provider"])
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
    old_settings = settings_service.get_runtime_settings(db)
    new_settings = settings_service.update_settings(db, updates)
    create_audit_log(
        db=db,
        action="ADMIN_SETTINGS_CHANGED",
        admin_id=admin.id,
        reason="Admin updated system settings",
        details={"old": old_settings, "new": new_settings},
        ip_address=request.client.host if request.client else None,
    )
    return new_settings


@router.get("/users/{user_id}/files", response_model=file_schemas.FileListResponse)
def admin_list_user_files(
    user_id: int,
    parent_folder_id: Optional[int] = Query(None),
    search: Optional[str] = Query(None, min_length=1),
    sort: str = Query("name", pattern="^(name|date|size|type)$"),
    direction: str = Query("asc", pattern="^(asc|desc)$"),
    trashed: Optional[bool] = Query(None),
    db: Session = Depends(get_db),
):
    if not service.get_user_by_id(db, user_id):
        raise HTTPException(status_code=404, detail="User not found")
    return file_service.admin_list_user_files(
        db,
        user_id,
        parent_folder_id=parent_folder_id,
        search=search,
        sort=sort,
        direction=direction,
        trashed=trashed,
    )


@router.get("/files/{file_id}", response_model=file_schemas.FileDetailsResponse)
def admin_get_file_metadata(
    file_id: int,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    record = file_service.get_record_for_admin(db, file_id)
    create_audit_log(
        db=db,
        action="ADMIN_FILE_METADATA_VIEWED",
        admin_id=admin.id,
        user_id=record.user_id,
        file_id=record.id,
        reason="Admin viewed file metadata",
        ip_address=request.client.host if request.client else None,
    )
    return record


@router.get("/files/{file_id}/download")
async def admin_download_file(
    file_id: int,
    request: Request,
    inline: bool = Query(False),
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    record = file_service.get_record_for_admin(db, file_id)
    if record.is_folder:
        raise HTTPException(status_code=400, detail="Folders cannot be downloaded directly")
    if record.is_trashed:
        raise HTTPException(status_code=400, detail="Cannot download trashed files")
    create_audit_log(
        db=db,
        action="ADMIN_FILE_DOWNLOADED",
        admin_id=admin.id,
        user_id=record.user_id,
        file_id=record.id,
        reason="Admin downloaded user file",
        ip_address=request.client.host if request.client else None,
    )
    provider = file_service.get_provider_for_record(db, record)
    disposition = "inline" if inline else "attachment"
    headers = {"Content-Disposition": f'{disposition}; filename="{record.filename}"'}
    return StreamingResponse(
        provider.stream_file(record.storage_object_id),
        media_type=record.mime_type or "application/octet-stream",
        headers=headers,
    )


@router.post("/files/{file_id}/trash", response_model=file_schemas.FileOut)
def admin_trash_file(
    file_id: int,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    record = file_service.get_record_for_admin(db, file_id)
    result = file_service.move_to_trash(db, record.user_id, record.id)
    create_audit_log(
        db=db,
        action="ADMIN_FILE_TRASHED",
        admin_id=admin.id,
        user_id=record.user_id,
        file_id=record.id,
        reason="Admin moved user file to trash",
        ip_address=request.client.host if request.client else None,
    )
    return result


@router.post("/files/{file_id}/restore", response_model=file_schemas.FileOut)
def admin_restore_file(
    file_id: int,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    record = file_service.get_record_for_admin(db, file_id)
    result = file_service.restore_from_trash(db, record.user_id, record.id)
    create_audit_log(
        db=db,
        action="ADMIN_FILE_RESTORED",
        admin_id=admin.id,
        user_id=record.user_id,
        file_id=record.id,
        reason="Admin restored user file from trash",
        ip_address=request.client.host if request.client else None,
    )
    return result


@router.delete("/files/{file_id}", response_model=file_schemas.MessageResponse)
async def admin_permanently_delete_file(
    file_id: int,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    record = file_service.get_record_for_admin(db, file_id)
    user_id = record.user_id
    create_audit_log(
        db=db,
        action="ADMIN_FILE_PERMANENTLY_DELETED",
        admin_id=admin.id,
        user_id=user_id,
        file_id=record.id,
        reason="Admin permanently deleted user file",
        ip_address=request.client.host if request.client else None,
    )
    await file_service.permanently_delete(db, user_id, file_id)
    return {"message": "File permanently deleted by admin"}
