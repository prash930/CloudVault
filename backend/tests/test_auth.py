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
