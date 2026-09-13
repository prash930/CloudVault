def test_register_success(client):
    response = client.post("/auth/register", json={
        "email": "newuser@test.com",
        "display_name": "New User",
        "password": "strongpassword"
    })
    assert response.status_code == 200
    assert "Registration successful" in response.json()["message"]

def test_register_duplicate_email(client, test_db):
    """Register same email twice — second attempt should fail."""
    # First register
    client.post("/auth/register", json={
        "email": "dupuser@test.com",
        "display_name": "Dup User",
        "password": "strongpassword"
    })
    # Second register with same email
    response = client.post("/auth/register", json={
        "email": "dupuser@test.com",
        "display_name": "Dup User 2",
        "password": "strongpassword2"
    })
    assert response.status_code == 400
    assert "Email already registered" in response.json()["detail"]

def test_register_weak_password(client):
    response = client.post("/auth/register", json={
        "email": "weak@test.com",
        "display_name": "Weak",
        "password": "weak"
    })
    assert response.status_code == 422

def test_login_pending_user(client):
    """Registration now auto-approves users (ACTIVE). Login should succeed immediately."""
    client.post("/auth/register", json={
        "email": "pendinglogin@test.com",
        "display_name": "Pending Login",
        "password": "strongpassword"
    })
    response = client.post("/auth/login", data={
        "username": "pendinglogin@test.com",
        "password": "strongpassword"
    })
    # Auto-approve is enabled: new registrations are ACTIVE, login succeeds (200)
    assert response.status_code == 200
    assert "access_token" in response.json()

def test_login_active_user(client, user_token):
    # Test logging in an active user (the one created in conftest)
    response = client.post("/auth/login", data={
        "username": "user@test.com",
        "password": "userpass123"
    })
    assert response.status_code == 200
    assert "access_token" in response.json()

def test_login_wrong_password(client, test_db):
    """Create user and try wrong password."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    u = User(
        email="wrongpw@test.com",
        display_name="Wrong PW",
        hashed_password=hash_password("correctpassword"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(u)
    test_db.commit()
    
    response = client.post("/auth/login", data={
        "username": "wrongpw@test.com",
        "password": "wrongpassword"
    })
    assert response.status_code == 401
    
def test_login_banned_user(client, test_db):
    from backend.users.models import User
    from backend.auth.service import hash_password
    banned_user = User(
        email="banned@test.com",
        display_name="Banned",
        hashed_password=hash_password("bannedpass"),
        role="USER",
        status="BANNED"
    )
    test_db.add(banned_user)
    test_db.commit()
    
    response = client.post("/auth/login", data={
        "username": "banned@test.com",
        "password": "bannedpass"
    })
    assert response.status_code == 403
    assert "banned" in response.json()["detail"].lower()

def test_me_authenticated(client, user_token):
    response = client.get("/auth/me", headers={"Authorization": f"Bearer {user_token}"})
    assert response.status_code == 200
    assert response.json()["email"] == "user@test.com"

def test_me_unauthenticated(client):
    response = client.get("/auth/me")
    assert response.status_code == 401

def test_logout(client, test_db):
    """Create a fresh user, login, then logout and verify token is blacklisted."""
    from backend.users.models import User
    from backend.auth.service import hash_password
    u = User(
        email="logoutuser@test.com",
        display_name="Logout User",
        hashed_password=hash_password("logoutpass123"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(u)
    test_db.commit()
    
    # Login to get a fresh token
    login_resp = client.post("/auth/login", data={
        "username": "logoutuser@test.com",
        "password": "logoutpass123"
    })
    assert login_resp.status_code == 200
    token = login_resp.json()["access_token"]
    
    # Logout
    response = client.post("/auth/logout", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    
    # Check that token is blacklisted by trying to hit /me
    response2 = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert response2.status_code == 401


def test_forgot_password_sends_reset_link_for_known_user(client, test_db, monkeypatch):
    from backend.auth import router as auth_router
    from backend.auth.service import hash_password
    from backend.users.models import User

    user = User(
        email="reset@test.com",
        display_name="Reset User",
        hashed_password=hash_password("oldpassword"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    sent = {}
    monkeypatch.setattr(
        auth_router,
        "send_password_reset_email",
        lambda to_email, reset_link: sent.update(email=to_email, link=reset_link),
    )

    response = client.post("/auth/forgot-password", json={"email": user.email})

    assert response.status_code == 200
    assert response.json()["message"] == "If that email is in our system, we have sent a reset link."
    assert sent["email"] == user.email
    assert "/auth/reset-password?token=" in sent["link"]


def test_forgot_password_unknown_email_returns_generic_success(client, monkeypatch):
    from backend.auth import router as auth_router

    sent = []
    monkeypatch.setattr(auth_router, "send_password_reset_email", lambda *args: sent.append(args))

    response = client.post("/auth/forgot-password", json={"email": "unknown@test.com"})

    assert response.status_code == 200
    assert response.json()["message"] == "If that email is in our system, we have sent a reset link."
    assert sent == []


def test_reset_password_happy_path_and_token_is_single_use(client, test_db):
    from backend.auth.service import create_password_reset_token, hash_password
    from backend.users.models import User

    user = User(
        email="reset-happy@test.com",
        display_name="Reset Happy",
        hashed_password=hash_password("oldpassword"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    token = create_password_reset_token(user.id)

    response = client.post("/auth/reset-password", json={"token": token, "new_password": "newpassword123"})

    assert response.status_code == 200
    assert "successful" in response.json()["message"].lower()
    login = client.post("/auth/login", data={"username": user.email, "password": "newpassword123"})
    assert login.status_code == 200

    reused = client.post("/auth/reset-password", json={"token": token, "new_password": "anotherpassword"})
    assert reused.status_code == 400
    assert "invalid or expired" in reused.json()["detail"].lower()


def test_reset_password_rejects_expired_token(client, test_db):
    from datetime import timedelta
    from backend.auth.service import create_access_token, hash_password
    from backend.users.models import User

    user = User(
        email="reset-expired@test.com",
        display_name="Reset Expired",
        hashed_password=hash_password("oldpassword"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    expired_token = create_access_token(
        {"sub": str(user.id), "purpose": "password_reset"},
        expires_delta=timedelta(minutes=-1),
    )

    response = client.post(
        "/auth/reset-password",
        json={"token": expired_token, "new_password": "newpassword123"},
    )

    assert response.status_code == 400
    assert "invalid or expired" in response.json()["detail"].lower()


def test_reset_password_rejects_blacklisted_token(client, test_db):
    from backend.auth.service import blacklist_token, create_password_reset_token, hash_password
    from backend.users.models import User

    user = User(
        email="reset-blacklisted@test.com",
        display_name="Reset Blacklisted",
        hashed_password=hash_password("oldpassword"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    token = create_password_reset_token(user.id)
    blacklist_token(token)

    response = client.post("/auth/reset-password", json={"token": token, "new_password": "newpassword123"})

    assert response.status_code == 400


def test_password_reset_token_cannot_authenticate_user(client, test_db):
    from backend.auth.service import create_password_reset_token, hash_password
    from backend.users.models import User

    user = User(
        email="reset-auth@test.com",
        display_name="Reset Auth",
        hashed_password=hash_password("oldpassword"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)

    response = client.get(
        "/auth/me",
        headers={"Authorization": f"Bearer {create_password_reset_token(user.id)}"},
    )

    assert response.status_code == 401
