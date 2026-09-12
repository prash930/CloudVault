from typing import Any

from sqlalchemy.orm import Session

from backend.config import settings
from backend.settings.models import AppSetting

SETTING_KEYS = {
    "default_quota_bytes": int,
    "require_registration_approval": bool,
    "max_upload_bytes": int,
    "trash_retention_days": int,
    "storage_provider": str,
}


def _parse_value(raw: str, value_type: type) -> Any:
    if value_type is bool:
        return raw.strip().lower() in {"1", "true", "yes", "on"}
    return value_type(raw)


def _serialize_value(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def get_default_settings() -> dict[str, Any]:
    return {
        "default_quota_bytes": settings.DEFAULT_QUOTA_BYTES,
        "require_registration_approval": False,
        "max_upload_bytes": settings.MAX_UPLOAD_BYTES,
        "trash_retention_days": getattr(settings, "TRASH_RETENTION_DAYS", 30),
        "storage_provider": settings.STORAGE_PROVIDER,
    }


def get_runtime_settings(db: Session) -> dict[str, Any]:
    defaults = get_default_settings()
    rows = db.query(AppSetting).all()
    for row in rows:
        if row.key in SETTING_KEYS:
            defaults[row.key] = _parse_value(row.value, SETTING_KEYS[row.key])
    return defaults


def get_setting(db: Session, key: str, default: Any = None) -> Any:
    runtime = get_runtime_settings(db)
    return runtime.get(key, default)


def update_settings(db: Session, updates: dict[str, Any]) -> dict[str, Any]:
    for key, value in updates.items():
        if key not in SETTING_KEYS:
            continue
        parsed = SETTING_KEYS[key](value) if not isinstance(value, SETTING_KEYS[key]) else value
        row = db.query(AppSetting).filter(AppSetting.key == key).first()
        serialized = _serialize_value(parsed)
        if row:
            row.value = serialized
        else:
            db.add(AppSetting(key=key, value=serialized))
    db.commit()
    return get_runtime_settings(db)
