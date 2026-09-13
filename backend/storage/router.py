from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from backend.auth.dependencies import require_admin
from backend.database import get_db
from backend.files.audit_service import create_audit_log
from backend.settings import service as settings_service
from backend.storage import schemas, service
from backend.users.models import User

router = APIRouter(prefix="/admin/storage", tags=["Admin Storage"])


@router.get("/status", response_model=schemas.StorageStatusResponse)
def get_storage_status(db: Session = Depends(get_db), admin: User = Depends(require_admin)):
    return service.get_public_status(db)


@router.post("/telegram/connect", response_model=schemas.StorageStatusResponse)
def connect_telegram_drive(
    req: schemas.TelegramConnectRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    try:
        service.connect_telegram_drive(db, req.bot_token, req.chat_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    create_audit_log(
        db=db,
        action="TELEGRAM_DRIVE_CONNECTED",
        admin_id=admin.id,
        reason="Admin connected Telegram Drive storage",
        ip_address=request.client.host if request.client else None,
    )
    return service.get_public_status(db)


@router.post("/telegram/disconnect", response_model=schemas.MessageResponse)
def disconnect_telegram_drive(
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    service.disconnect_telegram(db)
    create_audit_log(
        db=db,
        action="TELEGRAM_DRIVE_DISCONNECTED",
        admin_id=admin.id,
        reason="Admin disconnected Telegram Drive storage",
        ip_address=request.client.host if request.client else None,
    )
    return {"message": "Telegram Drive disconnected. Existing Telegram-backed files remain mapped in CloudBox."}


@router.put("/provider", response_model=schemas.StorageStatusResponse)
def set_storage_provider(
    req: schemas.StorageProviderUpdateRequest,
    request: Request,
    db: Session = Depends(get_db),
    admin: User = Depends(require_admin),
):
    try:
        service.validate_provider_switch(db, req.storage_provider)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    old = settings_service.get_setting(db, "storage_provider", "local")
    settings_service.update_settings(db, {"storage_provider": req.storage_provider})
    create_audit_log(
        db=db,
        action="STORAGE_PROVIDER_CHANGED",
        admin_id=admin.id,
        reason=f"Storage provider changed from {old} to {req.storage_provider}",
        details={"old_provider": old, "new_provider": req.storage_provider},
        ip_address=request.client.host if request.client else None,
    )
    return service.get_public_status(db)
