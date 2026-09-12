def test_admin_dashboard(client, admin_token):
    response = client.get("/admin/dashboard", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    data = response.json()
    assert "total_users" in data
    assert "active_users" in data
    assert "pending_users" in data

def test_admin_list_users(client, admin_token):
    response = client.get("/admin/users", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    data = response.json()
    assert "users" in data
    assert isinstance(data["users"], list)

def test_admin_list_pending(client, admin_token):
    response = client.get("/admin/users/pending", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)

def test_admin_approve_user(client, admin_token, test_db):
    from backend.users.models import User
    from backend.auth.service import hash_password
    pending = User(
        email="pending1@test.com",
        display_name="Pending",
        hashed_password=hash_password("testpass1"),
        role="USER",
        status="PENDING"
    )
    test_db.add(pending)
    test_db.commit()
    test_db.refresh(pending)
    
    response = client.post(f"/admin/users/{pending.id}/approve", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    assert response.json()["status"] == "ACTIVE"

def test_admin_reject_user(client, admin_token, test_db):
    from backend.users.models import User
    from backend.auth.service import hash_password
    pending = User(
        email="pending2@test.com",
        display_name="Pending 2",
        hashed_password=hash_password("testpass2"),
        role="USER",
        status="PENDING"
    )
    test_db.add(pending)
    test_db.commit()
    test_db.refresh(pending)
    
    response = client.post(f"/admin/users/{pending.id}/reject", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    
    # Ensure it's deleted
    assert test_db.query(User).filter(User.id == pending.id).first() is None

def test_admin_update_status(client, admin_token, test_db):
    from backend.users.models import User
    from backend.auth.service import hash_password
    user = User(
        email="updateme@test.com",
        display_name="Update Me",
        hashed_password=hash_password("testpass3"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    # Test valid update
    response = client.post(
        f"/admin/users/{user.id}/update-status",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"status": "WARNED", "reason": "Violation"}
    )
    assert response.status_code == 200
    assert response.json()["status"] == "WARNED"
    
    # Test missing reason
    response = client.post(
        f"/admin/users/{user.id}/update-status",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"status": "SUSPENDED"}
    )
    assert response.status_code == 400

def test_admin_update_status_invalid(client, admin_token, test_db):
    """Test that invalid status values are rejected."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    user = User(
        email="invalidstatus@test.com",
        display_name="Invalid Status",
        hashed_password=hash_password("testpass4"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    response = client.post(
        f"/admin/users/{user.id}/update-status",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"status": "INVALID_STATUS", "reason": "test"}
    )
    assert response.status_code == 400
    assert "Invalid status" in response.json()["detail"]

def test_non_admin_cannot_access(client, user_token):
    response = client.get("/admin/dashboard", headers={"Authorization": f"Bearer {user_token}"})
    assert response.status_code == 403

def test_storage_usage(client, user_token):
    response = client.get("/files/storage-usage", headers={"Authorization": f"Bearer {user_token}"})
    assert response.status_code == 200
    data = response.json()
    assert "used_bytes" in data
    assert "quota_bytes" in data


# ============================================================
# ADMIN STORAGE QUOTA MANAGEMENT TESTS
# ============================================================

def test_admin_update_quota(client, admin_token, test_db):
    """Admin can change a user's storage quota."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    user = User(
        email="quotauser@test.com",
        display_name="Quota User",
        hashed_password=hash_password("testpass5"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    # Change quota to 100 GB
    new_quota = 100 * 1024 * 1024 * 1024  # 100 GB
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": new_quota, "reason": "User requested upgrade"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["new_quota_bytes"] == new_quota
    assert data["warning"] is None
    assert "updated successfully" in data["message"]

def test_admin_update_quota_predefined_values(client, admin_token, test_db):
    """Test predefined quota values: 50GB, 100GB, 200GB, 500GB, 1TB."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    
    predefined_quotas = {
        "50 GB": 50 * 1024 * 1024 * 1024,
        "100 GB": 100 * 1024 * 1024 * 1024,
        "200 GB": 200 * 1024 * 1024 * 1024,
        "500 GB": 500 * 1024 * 1024 * 1024,
        "1 TB": 1024 * 1024 * 1024 * 1024,
    }
    
    user = User(
        email="predefined_quota@test.com",
        display_name="Predefined Quota",
        hashed_password=hash_password("testpass6"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    for label, quota_bytes in predefined_quotas.items():
        response = client.post(
            f"/admin/users/{user.id}/update-quota",
            headers={"Authorization": f"Bearer {admin_token}"},
            json={"quota_bytes": quota_bytes, "reason": f"Set to {label}"}
        )
        assert response.status_code == 200, f"Failed to set quota to {label}"
        assert response.json()["new_quota_bytes"] == quota_bytes

def test_admin_update_quota_below_usage(client, admin_token, test_db):
    """Reducing quota below current usage returns a warning but does NOT delete files."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    
    user = User(
        email="quotabelow@test.com",
        display_name="Quota Below",
        hashed_password=hash_password("testpass7"),
        role="USER",
        status="ACTIVE",
        storage_used_bytes=30 * 1024 * 1024 * 1024  # 30 GB used
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    # Try to reduce quota to 10 GB (below the 30 GB usage)
    new_quota = 10 * 1024 * 1024 * 1024  # 10 GB
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": new_quota, "reason": "Testing quota reduction"}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["warning"] is not None
    assert "below current usage" in data["warning"]
    assert data["new_quota_bytes"] == new_quota
    
    # Verify files are NOT deleted - user's storage_used_bytes should remain unchanged
    test_db.refresh(user)
    assert user.storage_used_bytes == 30 * 1024 * 1024 * 1024
    assert user.storage_quota_bytes == new_quota

def test_admin_update_quota_invalid_zero(client, admin_token, test_db):
    """Quota must be greater than 0."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    
    user = User(
        email="quotazero@test.com",
        display_name="Quota Zero",
        hashed_password=hash_password("testpass8"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": 0}
    )
    assert response.status_code == 400
    assert "greater than 0" in response.json()["detail"]

def test_admin_update_quota_nonexistent_user(client, admin_token):
    """Updating quota for a non-existent user returns 404."""
    response = client.post(
        "/admin/users/99999/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": 50 * 1024 * 1024 * 1024}
    )
    assert response.status_code == 404

def test_admin_update_quota_takes_effect_immediately(client, admin_token, test_db):
    """Increasing a quota should take effect immediately."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    
    user = User(
        email="quotaimmediate@test.com",
        display_name="Quota Immediate",
        hashed_password=hash_password("testpass9"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    new_quota = 200 * 1024 * 1024 * 1024  # 200 GB
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": new_quota, "reason": "Upgrade"}
    )
    assert response.status_code == 200
    
    # Verify the change took effect in the database
    test_db.refresh(user)
    assert user.storage_quota_bytes == new_quota


# ============================================================
# AUTHORIZATION TESTS - Normal user cannot change quotas
# ============================================================

def test_user_cannot_update_own_quota(client, user_token, test_db):
    """A normal user CANNOT update their own storage quota via API."""
    from backend.users.models import User
    user = test_db.query(User).filter(User.email == "user@test.com").first()
    assert user is not None
    
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {user_token}"},
        json={"quota_bytes": 999 * 1024 * 1024 * 1024, "reason": "I want more storage"}
    )
    assert response.status_code == 403, "Normal user must NOT be able to change their own quota"

def test_user_cannot_update_other_user_quota(client, user_token):
    """A normal user CANNOT update another user's quota."""
    response = client.post(
        "/admin/users/1/update-quota",
        headers={"Authorization": f"Bearer {user_token}"},
        json={"quota_bytes": 100 * 1024 * 1024 * 1024}
    )
    assert response.status_code == 403

def test_user_cannot_access_admin_endpoints(client, user_token):
    """Normal user cannot access any admin endpoints."""
    endpoints = [
        ("GET", "/admin/dashboard"),
        ("GET", "/admin/users"),
        ("GET", "/admin/users/pending"),
        ("GET", "/admin/audit-logs"),
    ]
    for method, path in endpoints:
        if method == "GET":
            response = client.get(path, headers={"Authorization": f"Bearer {user_token}"})
        assert response.status_code == 403, f"User should get 403 on {method} {path}"

def test_unauthenticated_cannot_access_admin(client):
    """Unauthenticated requests cannot access admin endpoints."""
    response = client.get("/admin/dashboard")
    assert response.status_code == 401

def test_unauthenticated_cannot_update_quota(client):
    """Unauthenticated request cannot update quota."""
    response = client.post(
        "/admin/users/1/update-quota",
        json={"quota_bytes": 100 * 1024 * 1024 * 1024}
    )
    assert response.status_code == 401


# ============================================================
# AUDIT LOG TESTS
# ============================================================

def test_quota_change_creates_audit_log(client, admin_token, test_db):
    """Every quota change must be recorded in audit logs."""
    from backend.users.models import User
    from backend.files.models import AuditLog
    from backend.auth.service import hash_password
    
    user = User(
        email="auditquota@test.com",
        display_name="Audit Quota",
        hashed_password=hash_password("testpass10"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    
    old_quota = user.storage_quota_bytes
    new_quota = 500 * 1024 * 1024 * 1024  # 500 GB
    
    response = client.post(
        f"/admin/users/{user.id}/update-quota",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"quota_bytes": new_quota, "reason": "Testing audit"}
    )
    assert response.status_code == 200
    
    # Verify audit log was created
    audit = test_db.query(AuditLog).filter(
        AuditLog.action == "QUOTA_CHANGED",
        AuditLog.user_id == user.id
    ).first()
    assert audit is not None
    assert audit.reason == "Testing audit"
    assert audit.admin_id is not None

def test_admin_get_audit_logs(client, admin_token):
    """Admin can retrieve audit logs."""
    response = client.get(
        "/admin/audit-logs",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "logs" in data
    assert "total" in data
    assert isinstance(data["logs"], list)

def test_admin_get_audit_logs_filtered(client, admin_token):
    """Admin can filter audit logs by action type."""
    response = client.get(
        "/admin/audit-logs?action=QUOTA_CHANGED",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert response.status_code == 200
    data = response.json()
    for log in data["logs"]:
        assert log["action"] == "QUOTA_CHANGED"

def test_user_cannot_access_audit_logs(client, user_token):
    """Normal user cannot access audit logs."""
    response = client.get(
        "/admin/audit-logs",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    assert response.status_code == 403
