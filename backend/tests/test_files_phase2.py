from backend.auth.service import create_access_token, hash_password
from backend.files.models import AuditLog, FileRecord
from backend.users.models import User


def make_user(db, email, quota=None, status="ACTIVE", role="USER"):
    user = User(
        email=email,
        display_name=email.split("@")[0],
        hashed_password=hash_password("testpass123"),
        role=role,
        status=status,
    )
    if quota is not None:
        user.storage_quota_bytes = quota
    db.add(user)
    db.commit()
    db.refresh(user)
    return user, create_access_token(data={"sub": str(user.id)})


def auth(token):
    return {"Authorization": f"Bearer {token}"}


def upload(client, token, name="photo.jpg", data=b"hello", parent=None):
    params = {}
    if parent is not None:
        params["parent_folder_id"] = parent
    return client.post(
        "/files/upload",
        headers=auth(token),
        params=params,
        files={"file": (name, data, "application/octet-stream")},
    )


def test_upload_success_and_storage_accounting(client, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    user, token = make_user(test_db, "upload@test.com")

    response = upload(client, token, "photo.jpg", b"family")

    assert response.status_code == 200
    data = response.json()
    assert data["filename"] == "photo.jpg"
    assert data["size_bytes"] == 6
    test_db.refresh(user)
    assert user.storage_used_bytes == 6


def test_quota_exceeded_rejected(client, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    _, token = make_user(test_db, "quota@test.com", quota=3)

    response = upload(client, token, "big.pdf", b"1234")

    assert response.status_code == 413
    assert "quota" in response.json()["detail"].lower()
    assert test_db.query(FileRecord).count() == 0


def test_cross_user_view_download_rename_move_delete_blocked(client, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    _, token_a = make_user(test_db, "a@test.com")
    _, token_b = make_user(test_db, "b@test.com")
    file_id = upload(client, token_a, "a.txt", b"secret").json()["id"]

    assert client.get(f"/files/{file_id}", headers=auth(token_b)).status_code == 404
    assert client.get(f"/files/{file_id}/download", headers=auth(token_b)).status_code == 404
    assert client.post(f"/files/{file_id}/rename", headers=auth(token_b), json={"filename": "x.txt"}).status_code == 404
    assert client.post(f"/files/{file_id}/move", headers=auth(token_b), json={"parent_folder_id": None}).status_code == 404
    assert client.post(f"/files/{file_id}/trash", headers=auth(token_b)).status_code == 404
    assert client.delete(f"/files/{file_id}", headers=auth(token_b)).status_code == 404


def test_folder_ownership_and_cycle_prevention(client, test_db):
    _, token_a = make_user(test_db, "folder-a@test.com")
    _, token_b = make_user(test_db, "folder-b@test.com")
    parent = client.post("/files/folders", headers=auth(token_a), json={"name": "Parent"}).json()
    child = client.post(
        "/files/folders",
        headers=auth(token_a),
        json={"name": "Child", "parent_folder_id": parent["id"]},
    ).json()

    blocked = client.post(
        "/files/folders",
        headers=auth(token_b),
        json={"name": "Bad", "parent_folder_id": parent["id"]},
    )
    assert blocked.status_code == 404

    self_move = client.post(
        f"/files/{parent['id']}/move",
        headers=auth(token_a),
        json={"parent_folder_id": parent["id"]},
    )
    descendant_move = client.post(
        f"/files/{parent['id']}/move",
        headers=auth(token_a),
        json={"parent_folder_id": child["id"]},
    )
    assert self_move.status_code == 400
    assert descendant_move.status_code == 400


def test_path_traversal_filename_blocked(client, test_db):
    _, token = make_user(test_db, "path@test.com")

    response = upload(client, token, "../escape.txt", b"nope")

    assert response.status_code == 400


def test_trash_restore_and_permanent_delete_accounting(client, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    user, token = make_user(test_db, "trash@test.com")
    file_id = upload(client, token, "trash.txt", b"abc").json()["id"]

    assert client.post(f"/files/{file_id}/trash", headers=auth(token)).status_code == 200
    test_db.refresh(user)
    assert user.storage_used_bytes == 3
    trash = client.get("/files/trash", headers=auth(token)).json()
    assert trash["total"] == 1

    restored = client.post(f"/files/{file_id}/restore", headers=auth(token))
    assert restored.status_code == 200
    assert restored.json()["is_trashed"] is False

    assert client.delete(f"/files/{file_id}", headers=auth(token)).status_code == 400
    assert client.post(f"/files/{file_id}/trash", headers=auth(token)).status_code == 200
    assert client.delete(f"/files/{file_id}", headers=auth(token)).status_code == 200
    test_db.refresh(user)
    assert user.storage_used_bytes == 0


def test_search_isolation(client, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    _, token_a = make_user(test_db, "search-a@test.com")
    _, token_b = make_user(test_db, "search-b@test.com")
    upload(client, token_a, "shared-name.txt", b"a")
    upload(client, token_b, "shared-name.txt", b"b")

    result = client.get("/files/search?q=shared", headers=auth(token_a)).json()

    assert result["total"] == 1
    assert result["items"][0]["filename"] == "shared-name.txt"


def test_admin_file_authorization_and_audit(client, admin_token, user_token, test_db, tmp_path, monkeypatch):
    monkeypatch.setattr("backend.config.settings.STORAGE_ROOT_DIR", str(tmp_path))
    user = test_db.query(User).filter(User.email == "user@test.com").first()
    file_id = upload(client, user_token, "admin.txt", b"admin").json()["id"]

    assert client.get(f"/admin/users/{user.id}/files", headers=auth(user_token)).status_code == 403
    metadata = client.get(f"/admin/files/{file_id}", headers=auth(admin_token))

    assert metadata.status_code == 200
    audit = test_db.query(AuditLog).filter(AuditLog.action == "ADMIN_FILE_METADATA_VIEWED").first()
    assert audit is not None
    assert audit.file_id == file_id


def test_suspended_user_cannot_upload(client, test_db):
    _, token = make_user(test_db, "suspended-upload@test.com", status="SUSPENDED")

    response = upload(client, token, "blocked.txt", b"blocked")

    assert response.status_code == 403
