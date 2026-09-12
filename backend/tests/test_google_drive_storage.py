import base64
from unittest.mock import MagicMock, patch

import pytest

from backend.auth.service import create_access_token, hash_password
from backend.settings.models import AppSetting
from backend.storage.models import GoogleDriveConnection, UserDriveFolder
from backend.storage.service import (
    build_authorization_url,
    disconnect,
    exchange_code_and_connect,
    get_public_status,
    is_connected,
    validate_provider_switch,
)
from backend.storage.token_crypto import decrypt_secret, encrypt_secret
from backend.users.models import User


def test_token_crypto_roundtrip():
    secret = "refresh-token-value-123"
    encrypted = encrypt_secret(secret)
    assert encrypted != secret
    assert decrypt_secret(encrypted) == secret


def test_storage_status_disconnected(test_db):
    status = get_public_status(test_db)
    # Default provider is local; Telegram Drive is configured via Admin Settings in production.
    assert status["storage_provider"] == "local"
    assert status["google_drive"]["connected"] is False
    assert status["google_drive"]["account_email"] is None


def test_validate_provider_switch_requires_connection(test_db):
    with pytest.raises(ValueError, match="Connect Google Drive"):
        validate_provider_switch(test_db, "google_drive")


def test_validate_provider_switch_allows_local(test_db):
    validate_provider_switch(test_db, "local")


def test_build_authorization_url(monkeypatch):
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_ID", "client-id")
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_SECRET", "client-secret")
    url = build_authorization_url("state-token")
    assert "accounts.google.com" in url
    assert "client_id=client-id" in url
    assert "state=state-token" in url
    assert "drive.file" in url
    assert "prompt=select_account" in url or "prompt=select_account+consent" in url or "prompt=consent" not in url


def test_disconnect_clears_connection(test_db):
    connection = GoogleDriveConnection(
        id=1,
        account_email="admin@gmail.com",
        account_id="123",
        encrypted_refresh_token=encrypt_secret("refresh"),
        root_folder_id="root-id",
        users_folder_id="users-id",
    )
    test_db.add(connection)
    test_db.commit()

    disconnect(test_db)
    test_db.refresh(connection)

    assert connection.encrypted_refresh_token is None
    assert connection.account_email is None
    assert not is_connected(test_db)


@patch("backend.storage.service._initialize_drive_folders")
@patch("backend.storage.service.httpx.Client")
def test_exchange_code_and_connect(mock_client_cls, mock_init_folders, test_db, monkeypatch):
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_ID", "client-id")
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_SECRET", "client-secret")
    mock_init_folders.return_value = ("root-folder", "users-folder")

    token_response = MagicMock()
    token_response.raise_for_status.return_value = None
    token_response.json.return_value = {
        "access_token": "access-token",
        "refresh_token": "refresh-token",
    }

    userinfo_response = MagicMock()
    userinfo_response.raise_for_status.return_value = None
    userinfo_response.json.return_value = {"email": "drive@gmail.com", "id": "google-user"}

    mock_client = MagicMock()
    mock_client.__enter__.return_value = mock_client
    mock_client.post.return_value = token_response
    mock_client.get.return_value = userinfo_response
    mock_client_cls.return_value = mock_client

    connection = exchange_code_and_connect(test_db, "auth-code")

    assert connection.account_email == "drive@gmail.com"
    assert connection.root_folder_id == "root-folder"
    assert decrypt_secret(connection.encrypted_refresh_token) == "refresh-token"
    assert is_connected(test_db)


@patch("backend.storage.google_drive.ensure_user_drive_folder", return_value="folder-1")
@patch("backend.storage.google_drive._drive_service")
def test_google_drive_upload_and_delete(mock_drive_service, _mock_folder, test_db, monkeypatch):
    monkeypatch.setattr("backend.config.settings.GOOGLE_CLIENT_ID", "client-id")
    monkeypatch.setattr("backend.config.settings.GOOGLE_CLIENT_SECRET", "client-secret")

    connection = GoogleDriveConnection(
        id=1,
        encrypted_refresh_token=encrypt_secret("refresh"),
        users_folder_id="users-id",
    )
    test_db.add(connection)
    test_db.add(AppSetting(key="storage_provider", value="google_drive"))
    test_db.commit()

    service = MagicMock()
    created = service.files.return_value.create.return_value.execute
    created.return_value = {"id": "drive-file-1", "size": "5"}
    deleted = service.files.return_value.delete.return_value.execute
    size = service.files.return_value.get.return_value.execute
    size.return_value = {"size": "5", "trashed": False}
    mock_drive_service.return_value = service

    from backend.storage.google_drive import GoogleDriveStorageProvider
    import io

    provider = GoogleDriveStorageProvider(test_db)
    file_id, uploaded_size = _run_async(
        provider.store_stream_for_user(1, io.BytesIO(b"hello"), "text/plain")
    )
    assert file_id == "drive-file-1"
    assert uploaded_size == 5

    exists = _run_async(provider.file_exists("drive-file-1"))
    assert exists is True

    deleted_ok = _run_async(provider.delete_file("drive-file-1"))
    assert deleted_ok is True
    deleted.assert_called_once()


def _run_async(coro):
    import asyncio

    return asyncio.run(coro)


def test_storage_provider_api_requires_admin(client, user_token):
    response = client.get("/admin/storage/status", headers={"Authorization": f"Bearer {user_token}"})
    assert response.status_code == 403


def test_storage_provider_api_status(client, admin_token, test_db):
    response = client.get("/admin/storage/status", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    data = response.json()
    assert data["storage_provider"] == "local"  # default provider is local; set telegram_drive via Admin Settings
    assert data["google_drive"]["status"] == "disconnected"


def test_set_storage_provider_to_google_drive_without_connection(client, admin_token):
    response = client.put(
        "/admin/storage/provider",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"storage_provider": "google_drive"},
    )
    assert response.status_code == 400


def test_upload_uses_local_provider_by_default(client, test_db, tmp_path, monkeypatch):
    """When telegram_drive is default but not configured, uploads fall back to local storage."""
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    user = User(
        email="drive-user@test.com",
        display_name="Drive User",
        hashed_password=hash_password("testpass123"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    token = create_access_token(data={"sub": str(user.id)})

    response = client.post(
        "/files/upload",
        headers={"Authorization": f"Bearer {token}"},
        files={"file": ("note.txt", b"local", "text/plain")},
    )
    # telegram_drive is the default provider but is not connected in tests → falls back to local
    assert response.status_code == 200
    from backend.files.models import FileRecord

    record = test_db.query(FileRecord).filter(FileRecord.id == response.json()["id"]).first()
    assert record.storage_provider == "local"


@patch("backend.storage.google_drive.GoogleDriveStorageProvider")
def test_upload_rejects_google_drive_when_not_connected(mock_provider_cls, client, test_db, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", "/tmp/unused")
    test_db.add(AppSetting(key="storage_provider", value="google_drive"))
    test_db.commit()

    user = User(
        email="gdrive-user@test.com",
        display_name="GDrive User",
        hashed_password=hash_password("testpass123"),
        role="USER",
        status="ACTIVE",
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    token = create_access_token(data={"sub": str(user.id)})

    response = client.post(
        "/files/upload",
        headers={"Authorization": f"Bearer {token}"},
        files={"file": ("note.txt", b"cloud", "text/plain")},
    )
    assert response.status_code == 503
    mock_provider_cls.assert_not_called()


@patch("backend.storage.google_drive._drive_service")
def test_get_drive_quota_success(mock_drive_service, test_db):
    service = MagicMock()
    service.about.return_value.get.return_value.execute.return_value = {
        "storageQuota": {"limit": "5000000000000", "usage": "100000000000"}
    }
    mock_drive_service.return_value = service

    from backend.storage.google_drive import get_drive_quota
    quota = get_drive_quota(test_db)
    assert quota is not None
    assert quota["total_space"] == 5000000000000
    assert quota["used_space"] == 100000000000
    assert quota["available_space"] == 4900000000000


@patch("backend.storage.google_drive._drive_service")
def test_get_drive_quota_failure(mock_drive_service, test_db):
    mock_drive_service.side_effect = Exception("Google API error")
    from backend.storage.google_drive import get_drive_quota
    quota = get_drive_quota(test_db)
    assert quota is None


@patch("backend.storage.google_drive.get_drive_quota")
def test_get_public_status_with_quota(mock_get_quota, test_db):
    mock_get_quota.return_value = {
        "total_space": 5000000000000,
        "used_space": 100000000000,
        "available_space": 4900000000000,
    }
    connection = GoogleDriveConnection(
        id=1,
        account_email="drive@gmail.com",
        encrypted_refresh_token=encrypt_secret("refresh"),
    )
    test_db.add(connection)
    test_db.commit()

    from backend.storage.service import get_public_status
    status = get_public_status(test_db)
    assert status["google_drive"]["connected"] is True
    assert status["google_drive"]["total_space"] == 5000000000000
    assert status["google_drive"]["used_space"] == 100000000000
    assert status["google_drive"]["available_space"] == 4900000000000


@patch("backend.storage.service.exchange_code_and_connect")
def test_google_oauth_callback_flow_success(mock_exchange, client, admin_token, test_db, monkeypatch):
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_ID", "client-id")
    monkeypatch.setattr("backend.storage.service.settings.GOOGLE_CLIENT_SECRET", "client-secret")

    # Step 1: Admin calls connect endpoint
    connect_res = client.get("/admin/storage/google/connect", headers={"Authorization": f"Bearer {admin_token}"})
    assert connect_res.status_code == 200
    auth_url = connect_res.json()["authorization_url"]
    assert "state=" in auth_url

    # Extract state from URL
    from urllib.parse import parse_qs, urlparse
    parsed = urlparse(auth_url)
    state = parse_qs(parsed.query)["state"][0]

    # Step 2: Google redirects browser to callback without Authorization header
    callback_res = client.get(
        f"/admin/storage/google/callback?code=mock_code&state={state}",
        follow_redirects=False,
    )
    assert callback_res.status_code == 307 or callback_res.status_code == 302
    assert "storage=connected" in callback_res.headers["location"]
    mock_exchange.assert_called_once_with(test_db, "mock_code")


def test_google_oauth_callback_invalid_state_redirects_with_error(client):
    callback_res = client.get(
        "/admin/storage/google/callback?code=mock_code&state=invalid_state_token",
        follow_redirects=False,
    )
    assert callback_res.status_code == 307 or callback_res.status_code == 302
    assert "storage=error" in callback_res.headers["location"]


