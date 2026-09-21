"""Security hardening tests for CloudBox backend.

Covers path traversal, JWT edge cases, authorization boundaries,
and input validation.
"""
import pytest
from datetime import datetime, timedelta, timezone
from jose import jwt
from backend.config import settings
from backend.storage.local import LocalStorageProvider
from backend.auth.service import create_access_token


# ============================================================
# PATH TRAVERSAL PREVENTION
# ============================================================

class TestPathTraversal:
    """Verify LocalStorageProvider blocks path traversal attacks."""
    
    def test_relative_path_traversal_blocked(self):
        """Attempting ../../../etc/passwd must raise ValueError."""
        provider = LocalStorageProvider()
        with pytest.raises(ValueError, match="Path traversal"):
            provider._get_safe_path("../../../etc/passwd")
    
    def test_absolute_path_traversal_blocked(self):
        """Absolute paths outside storage root must raise ValueError."""
        provider = LocalStorageProvider()
        with pytest.raises(ValueError, match="Path traversal"):
            provider._get_safe_path("/etc/passwd")
    
    def test_encoded_traversal_blocked(self):
        """URL-encoded traversal attempts must be blocked."""
        provider = LocalStorageProvider()
        with pytest.raises(ValueError, match="Path traversal"):
            provider._get_safe_path("..%2F..%2F..%2Fetc%2Fpasswd")
    
    def test_valid_path_allowed(self):
        """Normal filenames should work fine."""
        provider = LocalStorageProvider()
        result = provider._get_safe_path("user123/photo.jpg")
        assert "user123" in result
        assert "photo.jpg" in result
    
    def test_nested_valid_path_allowed(self):
        """Nested paths within storage root should work."""
        provider = LocalStorageProvider()
        result = provider._get_safe_path("user1/folder/subfolder/doc.pdf")
        assert "doc.pdf" in result


# ============================================================
# JWT TOKEN SECURITY
# ============================================================

class TestJWTSecurity:
    """Verify JWT token validation edge cases."""

    def test_expired_token_rejected(self, client):
        """Expired tokens must be rejected with 401."""
        expired_token = jwt.encode(
            {"sub": "1", "exp": datetime.now(timezone.utc) - timedelta(hours=1)},
            settings.SECRET_KEY,
            algorithm=settings.JWT_ALGORITHM
        )
        response = client.get("/auth/me", headers={"Authorization": f"Bearer {expired_token}"})
        assert response.status_code == 401

    def test_malformed_token_rejected(self, client):
        """Random garbage tokens must be rejected with 401."""
        response = client.get("/auth/me", headers={"Authorization": "Bearer not.a.valid.jwt.token"})
        assert response.status_code == 401

    def test_wrong_secret_token_rejected(self, client):
        """Token signed with wrong secret must be rejected."""
        bad_token = jwt.encode(
            {"sub": "1", "exp": datetime.now(timezone.utc) + timedelta(hours=1)},
            "wrong-secret-key",
            algorithm=settings.JWT_ALGORITHM
        )
        response = client.get("/auth/me", headers={"Authorization": f"Bearer {bad_token}"})
        assert response.status_code == 401

    def test_token_missing_sub_rejected(self, client):
        """Token without 'sub' claim must be rejected."""
        no_sub_token = jwt.encode(
            {"exp": datetime.now(timezone.utc) + timedelta(hours=1)},
            settings.SECRET_KEY,
            algorithm=settings.JWT_ALGORITHM
        )
        response = client.get("/auth/me", headers={"Authorization": f"Bearer {no_sub_token}"})
        assert response.status_code == 401

    def test_token_nonexistent_user_rejected(self, client):
        """Token for a user ID that doesn't exist must be rejected."""
        ghost_token = create_access_token(data={"sub": "999999"})
        response = client.get("/auth/me", headers={"Authorization": f"Bearer {ghost_token}"})
        assert response.status_code == 401


# ============================================================
# SUSPENDED/BANNED USER TOKEN REJECTION  
# ============================================================

class TestAccountStatusEnforcement:
    """Verify that suspended/banned users are blocked even with valid tokens."""

    def test_suspended_user_token_blocked(self, client, test_db):
        """A user with a valid token but SUSPENDED status must get 403."""
        from backend.users.models import User
        from backend.auth.service import hash_password
        
        user = User(
            email="suspended_security@test.com",
            display_name="Suspended",
            hashed_password=hash_password("testpass123"),
            role="USER",
            status="SUSPENDED"
        )
        test_db.add(user)
        test_db.commit()
        test_db.refresh(user)
        
        token = create_access_token(data={"sub": str(user.id)})
        response = client.get("/files/storage-usage", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 403
        assert "suspended" in response.json()["detail"].lower()

    def test_banned_user_token_blocked(self, client, test_db):
        """A user with a valid token but BANNED status must get 403."""
        from backend.users.models import User
        from backend.auth.service import hash_password
        
        user = User(
            email="banned_security@test.com",
            display_name="Banned",
            hashed_password=hash_password("testpass123"),
            role="USER",
            status="BANNED"
        )
        test_db.add(user)
        test_db.commit()
        test_db.refresh(user)
        
        token = create_access_token(data={"sub": str(user.id)})
        response = client.get("/files/storage-usage", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 403
        assert "banned" in response.json()["detail"].lower()

    def test_pending_user_token_blocked(self, client, test_db):
        """A user with PENDING status must get 403 on protected endpoints."""
        from backend.users.models import User
        from backend.auth.service import hash_password
        
        user = User(
            email="pending_security@test.com",
            display_name="Pending",
            hashed_password=hash_password("testpass123"),
            role="USER",
            status="PENDING"
        )
        test_db.add(user)
        test_db.commit()
        test_db.refresh(user)
        
        token = create_access_token(data={"sub": str(user.id)})
        response = client.get("/files/storage-usage", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 403


# ============================================================
# INPUT VALIDATION
# ============================================================

class TestInputValidation:
    """Verify input validation and edge cases."""

    def test_register_invalid_email_format(self, client):
        """Registration with invalid email format must be rejected."""
        response = client.post("/auth/register", json={
            "email": "not-an-email",
            "display_name": "Test",
            "password": "strongpassword"
        })
        assert response.status_code == 422

    def test_register_empty_display_name(self, client):
        """Registration with empty display name must be rejected."""
        response = client.post("/auth/register", json={
            "email": "valid@test.com",
            "display_name": "",
            "password": "strongpassword"
        })
        assert response.status_code == 422

    def test_register_display_name_too_long(self, client):
        """Registration with display name > 50 chars must be rejected."""
        response = client.post("/auth/register", json={
            "email": "valid@test.com",
            "display_name": "A" * 51,
            "password": "strongpassword"
        })
        assert response.status_code == 422

    def test_negative_quota_rejected(self, client, admin_token):
        """Negative quota values must be rejected."""
        response = client.post(
            "/admin/users/1/update-quota",
            headers={"Authorization": f"Bearer {admin_token}"},
            json={"quota_bytes": -100}
        )
        assert response.status_code == 400

    def test_admin_approve_nonexistent_user(self, client, admin_token):
        """Approving a non-existent user returns 404."""
        response = client.post(
            "/admin/users/99999/approve",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 404


# ============================================================
# AUTHORIZATION BOUNDARY TESTS
# ============================================================

class TestAuthorizationBoundaries:
    """Verify authorization boundaries are enforced."""

    def test_user_cannot_approve_users(self, client, user_token):
        """Normal users cannot approve other users."""
        response = client.post(
            "/admin/users/1/approve",
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert response.status_code == 403

    def test_user_cannot_reject_users(self, client, user_token):
        """Normal users cannot reject other users."""
        response = client.post(
            "/admin/users/1/reject",
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert response.status_code == 403

    def test_user_cannot_change_status(self, client, user_token):
        """Normal users cannot change user statuses."""
        response = client.post(
            "/admin/users/1/update-status",
            headers={"Authorization": f"Bearer {user_token}"},
            json={"status": "BANNED", "reason": "hacking attempt"}
        )
        assert response.status_code == 403

    def test_no_auth_header_returns_401(self, client):
        """Missing Authorization header returns 401."""
        for path in ["/auth/me", "/files/storage-usage", "/admin/dashboard"]:
            response = client.get(path)
            assert response.status_code == 401, f"Expected 401 for {path}"

    def test_empty_bearer_token_returns_401(self, client):
        """Empty bearer token returns 401."""
        response = client.get("/auth/me", headers={"Authorization": "Bearer "})
        assert response.status_code == 401
