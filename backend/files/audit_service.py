from sqlalchemy.orm import Session
from backend.files.models import AuditLog
from typing import Optional
import json


def create_audit_log(
    db: Session,
    action: str,
    admin_id: Optional[int] = None,
    user_id: Optional[int] = None,
    file_id: Optional[int] = None,
    reason: Optional[str] = None,
    details: Optional[dict] = None,
    ip_address: Optional[str] = None
) -> AuditLog:
    """Create an audit log entry for admin actions.
    
    All sensitive admin actions must be logged including:
    - Quota changes (admin_id, user_id, old_quota, new_quota, reason)
    - Status changes (admin_id, user_id, old_status, new_status, reason)
    - User approvals/rejections
    - File access/management
    """
    details_str = json.dumps(details) if details else None
    
    log = AuditLog(
        admin_id=admin_id,
        user_id=user_id,
        file_id=file_id,
        action=action,
        reason=reason,
        details=details_str,
        ip_address=ip_address
    )
    db.add(log)
    db.commit()
    db.refresh(log)
    return log


def get_audit_logs(
    db: Session,
    skip: int = 0,
    limit: int = 50,
    action_filter: Optional[str] = None,
    user_id_filter: Optional[int] = None,
    search: Optional[str] = None,
):
    """Retrieve audit logs with optional filtering."""
    from sqlalchemy import or_

    query = db.query(AuditLog)
    
    if action_filter:
        query = query.filter(AuditLog.action == action_filter)
    if user_id_filter:
        query = query.filter(AuditLog.user_id == user_id_filter)
    if search:
        term = f"%{search.strip()}%"
        query = query.filter(or_(AuditLog.action.ilike(term), AuditLog.reason.ilike(term)))
    
    total = query.count()
    logs = query.order_by(AuditLog.created_at.desc()).offset(skip).limit(limit).all()
    
    return logs, total
