import io
from unittest.mock import patch

import pytest

from backend.auth.service import create_access_token, hash_password
from backend.settings.models import AppSetting
from backend.storage.models import TelegramConnection, TelegramStoredObject
from backend.storage.service import (
    connect_telegram_drive,
    disconnect_telegram,
    get_public_status,
    is_telegram_connected,
    validate_provider_switch,
)
from backend.storage.token_crypto import encrypt_secret
from backend.users.models import User


def _run_async(coro):
    import asyncio

    return asyncio.run(coro)


def test_storage_status_includes_telegram_disconnected(test_db):
    status = get_public_status(test_db)
    assert status["telegram_drive"]["connected"] is False
    assert status["telegram_drive"]["status"] == "disconnected"


def test_validate_provider_switch_requires_telegram_connection(test_db):
    with pytest.raises(ValueError, match="Connect Telegram Drive"):
        validate_provider_switch(test_db, "telegram_drive")


@patch("backend.storage.service.verify_telegram_credentials")
def test_connect_and_disconnect_telegram(mock_verify, test_db):
    mock_verify.return_value = {
        "bot_username": "cloudbox_bot",
        "bot_id": "1",
        "chat_id": "-100123",
    }
    connection = connect_telegram_drive(test_db, "123456:ABCDEF-token", "-100123")
    assert connection.bot_username == "cloudbox_bot"
    assert is_telegram_connected(test_db) is True
    validate_provider_switch(test_db, "telegram_drive")

    disconnect_telegram(test_db)
    assert is_telegram_connected(test_db) is False


def test_set_storage_provider_to_telegram_without_connection(client, admin_token):
    response = client.put(
        "/admin/storage/provider",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"storage_provider": "telegram_drive"},
    )
    assert response.status_code == 400


@patch("backend.storage.service.verify_telegram_credentials")
def test_telegram_connect_api(mock_verify, client, admin_token):
    mock_verify.return_value = {
        "bot_username": "cloudbox_bot",
        "bot_id": "9",
        "chat_id": "111",
    }
    response = client.post(
        "/admin/storage/telegram/connect",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"bot_token": "123456:ABCDEF-token", "chat_id": "111"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["telegram_drive"]["connected"] is True
    assert data["telegram_drive"]["bot_username"] == "cloudbox_bot"


@patch("backend.storage.telegram.telegram_api")
def test_telegram_upload_and_delete(mock_api, test_db, monkeypatch):
    monkeypatch.setattr("backend.storage.telegram.CHUNK_SIZE", 4)
    test_db.add(
        TelegramConnection(
            id=1,
            bot_username="bot",
            chat_id="42",
            encrypted_bot_token=encrypt_secret("123456:ABCDEF-token"),
        )
    )
    test_db.commit()

    sent = {"n": 0}

    def fake_api(token, method, payload=None, files=None):
        if method == "sendDocument":
            sent["n"] += 1
            return {"message_id": sent["n"], "document": {"file_id": f"file-{sent['n']}"}}
        if method == "getFile":
            return {"file_path": "documents/x"}
        if method == "deleteMessage":
            return True
        raise AssertionError(method)

    mock_api.side_effect = fake_api

    from backend.storage.telegram import TelegramStorageProvider, download_telegram_file

    with patch("backend.storage.telegram.download_telegram_file", return_value=b"abcd"):
        provider = TelegramStorageProvider(test_db)
        object_id, size = _run_async(provider.store_stream_for_user(1, io.BytesIO(b"abcdefgh"), "text/plain"))
        assert size == 8
        assert _run_async(provider.file_exists(object_id)) is True
        with patch("backend.storage.telegram.download_telegram_file", side_effect=[b"abcd", b"efgh"]):
            data = _run_async(provider.retrieve_file(object_id))
        assert data == b"abcdefgh"
        assert _run_async(provider.delete_file(object_id)) is True
        assert test_db.query(TelegramStoredObject).filter(TelegramStoredObject.object_id == object_id).first() is None


@patch("backend.storage.telegram.TelegramStorageProvider")
def test_upload_rejects_telegram_when_not_connected(mock_provider_cls, client, test_db):
    test_db.add(AppSetting(key="storage_provider", value="telegram_drive"))
    test_db.commit()
    user = User(
        email="tg-user@test.com",
        display_name="TG User",
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
