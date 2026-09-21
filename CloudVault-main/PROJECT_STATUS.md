# CloudBox Family - Project Status

> **Last Updated:** 2026-07-22 (Admin Dashboard completion pass)  
> **Phase:** 2 backend + Android + **Admin Dashboard (implemented)**  
> **Next Phase:** 3 - Production hardening, sharing, and storage-provider expansion (not Google Drive / payments yet)

---

## Phase 1 Status

**Verified intact.** All Phase 1 functionality remains in place and covered by automated tests.

- FastAPI auth, JWT login/logout, account status enforcement, admin approval, user management, quota management, audit logs, storage abstraction, rate limiting, and storage usage endpoints.
- Account statuses: `PENDING`, `ACTIVE`, `WARNED`, `SUSPENDED`, `BANNED`.
- Default 50 GB quota; admin can set individual quotas (50 GB, 100 GB, 200 GB, 500 GB, 1 TB, custom).
- Android Kotlin + Jetpack Compose + Material 3 shell with auth, token storage, Retrofit/OkHttp, navigation, and storage display.

---

## Phase 2 Status

**Verified via automated backend tests.** End-to-end Android ↔ backend flow on a physical device is **not yet manually tested** on this machine.

### Backend File Management (verified by tests)

- Authenticated file upload through `/files/upload`.
- Streaming local storage writes through `StorageProvider.store_stream`.
- Authenticated streaming downloads through `/files/{file_id}/download`.
- Opaque storage object IDs backed by `LocalStorageProvider`.
- Secure filename validation, path traversal prevention, blocked executable/script extensions, maximum upload size config.
- Server-side quota enforcement; storage accounting increases on upload and decreases only on permanent deletion.
- Files in Trash continue counting toward quota.

### Folders (verified by tests)

- Hierarchical folder creation, upload into folders, listing/opening by parent folder ID.
- Ownership validation, cycle prevention (cannot move folder into itself or descendant).

### My Files / Trash / Search (verified by tests)

- List, search, sort, rename, move, move-to-trash, restore, permanent delete.
- Search isolation — users only see their own files/folders.

### Privacy, Security, Admin (verified by tests)

- Cross-user IDOR blocked for view/download/rename/move/delete/trash/search.
- Suspended/banned/pending users blocked from protected file APIs.
- Admin file access via `/admin/users/{user_id}/files`, `/admin/files/{file_id}`, download, trash, permanent delete.
- Admin actions audited.

### Android (built; device testing pending)

- `FilesApi`, `FileRepository`, upload via system file picker, download, folders, search, sort, trash, profile storage display.
- Tabs: Home, My Files, Recent, Transfers, Trash, Profile.

---

## Admin Dashboard Status

**Implemented and verified** (React + Vite + TypeScript).

| Area | Status | Backend routes |
|------|--------|----------------|
| Admin login | Done | `POST /auth/admin/login`, `GET /auth/me`, `POST /auth/logout` |
| Dashboard overview | Done | `GET /admin/dashboard` |
| Pending approvals | Done | `GET /admin/users/pending`, approve/reject |
| User management | Done | list/search, status, quota presets + custom |
| Files / storage (per user) | Done | list, folders, trash, preview, download, trash/restore/delete |
| Audit logs | Done | `GET /admin/audit-logs` (filters + pagination) |
| System settings | Done | `GET/PUT /admin/settings` |

**What was missing before this pass (now added):** `main.tsx`, `App.tsx`, routing + auth guard, `AuditLogs.tsx`, `Settings.tsx`, global `index.css`, production `dist/` build, Node.js LTS on this machine for builds.

**How to run (production-style — UI served by FastAPI):**

```powershell
cd D:\software\cloudbox_Backup\admin-dashboard
npm install
npm run build

cd D:\software\cloudbox_Backup
python -m uvicorn backend.main:app --host 127.0.0.1 --port 8000
```

- **Browser:** [http://127.0.0.1:8000/admin-ui/](http://127.0.0.1:8000/admin-ui/)

**How to run (development — hot reload, API proxied to backend):**

```powershell
# Terminal 1 — backend
cd D:\software\cloudbox_Backup
python -m uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000

# Terminal 2 — Vite
cd D:\software\cloudbox_Backup\admin-dashboard
npm run dev
```

- **Browser:** [http://localhost:5173/admin-ui/](http://localhost:5173/admin-ui/)

**Admin account:** First-time setup (interactive):

```powershell
cd D:\software\cloudbox_Backup
python backend/create_admin.py
```

Use the email and password you choose at the prompts. Only users with `role=ADMIN` and `status=ACTIVE` can use `/auth/admin/login`.

---

## Test Results

Command (2026-07-22 Admin Dashboard completion):

```powershell
$env:TMP = "D:\software\cloudbox_Backup\.tmp"
$env:TEMP = "D:\software\cloudbox_Backup\.tmp"
cd D:\software\cloudbox_Backup
python -m pytest backend/tests/ -q
```

Result:

- **66 passed**
- **0 failed**
- 3 warnings (slowapi deprecation)

**Note:** On Windows, set `TMP`/`TEMP` to a writable project directory if `%TEMP%\pytest-of-*` is locked.

**Admin UI build:** `npm run build` in `admin-dashboard/` — success (Vite 6).

| Suite | Tests |
|-------|-------|
| `test_auth.py` | 10 |
| `test_users.py` | 24 |
| `test_security.py` | 23 |
| `test_files_phase2.py` | 9 |

There are **no automated frontend tests** for the admin dashboard yet.

---

## Bugs Fixed During Verification

1. **Android:** Missing `gradle/wrapper/gradle-wrapper.jar` — downloaded for Gradle 8.9.
2. **Android:** Missing launcher icon (`mipmap/ic_launcher`) — added `drawable/ic_launcher.xml`, updated manifest.
3. **Android:** `HomeScreen.kt` compile error — `Modifier.align` used outside `Column`; wrapped `FilesView` content in `Column`.
4. **Environment:** Installed JDK 17 (Temurin), Android Studio, Android SDK (platform 35, build-tools 35.0.0/34.0.0), created `android/local.properties`.
5. **Admin Dashboard:** Missing app shell entry (`main.tsx` / `App.tsx`), Audit Logs and Settings pages, and CSS — added; dashboard builds and loads at `/admin-ui/`.
6. **Environment:** Installed Node.js LTS (24.x) for `npm run build`.

---

## Android Build Status

**APK BUILD: SUCCESS**

Command:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location D:\software\cloudbox_Backup\android
.\gradlew.bat assembleDebug
```

Output:

- **Path:** `D:\software\cloudbox_Backup\android\app\build\outputs\apk\debug\app-debug.apk`
- **Size:** ~18.4 MB
- **Build:** Gradle 8.9, AGP 8.7.3, Kotlin 2.1.0

---

## Android 15 Compatibility

| Setting | Value |
|---------|-------|
| `compileSdk` | 35 (Android 15) |
| `targetSdk` | 35 |
| `minSdk` | 26 |

- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE` only — no broad storage permissions; uploads use the system file picker (`GetContent`).
- **Cleartext HTTP:** Enabled for local dev (`usesCleartextTraffic=true`). Use HTTPS in production.

---

## Development Environment (this machine)

| Component | Status |
|-----------|--------|
| Python 3.14 + pytest | Installed; tests pass |
| Node.js LTS | Installed (`C:\Program Files\nodejs`) |
| JDK 17 (Temurin) | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |
| Android Studio | Installed |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` (platform 35, build-tools) |
| `JAVA_HOME` / `ANDROID_HOME` | Set in build session; **add to system/user env vars for convenience** |

---

## Backend Connection for Phone Testing

The API base URL is configured in `android/app/build.gradle.kts` as `BuildConfig.BASE_URL`.

| Target | BASE_URL | Backend command |
|--------|----------|-----------------|
| **Android emulator** | `http://10.0.2.2:8000` (default) | `uvicorn backend.main:app --reload --host 127.0.0.1 --port 8000` |
| **Physical phone (same Wi‑Fi/LAN)** | `http://YOUR_PC_LAN_IP:8000` | `uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000` |

Steps for physical device:

1. Find PC LAN IP: `ipconfig` → e.g. `192.168.1.42`
2. Edit `buildConfigField("BASE_URL", ...)` in `android/app/build.gradle.kts` to `http://192.168.1.42:8000`
3. Rebuild APK: `.\gradlew.bat assembleDebug`
4. Allow port 8000 through Windows Firewall if needed
5. Start backend from project root: `python -m uvicorn backend.main:app --host 0.0.0.0 --port 8000`
6. Create admin (first time): `python backend/create_admin.py`

Do **not** use `localhost` on a physical phone — it refers to the phone itself.

---

## Known Limitations

1. Local and Telegram Drive storage; Google Drive was removed from the codebase (not used).
2. SQLite dev database; no Alembic migrations yet.
3. Token blacklist in-memory.
4. Forgot password is a placeholder.
5. Android UI not manually verified on device in this pass.
6. `Recent` and `Transfers` tabs are present but may show limited/placeholder behavior.
7. Rebuild required after changing `BASE_URL` (compile-time `BuildConfig`).
8. Pytest on Windows may need writable `TMP`/`TEMP` if `%TEMP%\pytest-of-*` is locked.
9. Admin dashboard has no E2E/browser automation tests; manual smoke test recommended after each UI change.
10. Re-run `npm run build` in `admin-dashboard/` before deploying UI changes via FastAPI static serve.

---

## Remaining Work (explicitly out of scope for this pass)

**Not started (per project plan):** Payments, and remaining Phase 3 production hardening items beyond what exists today.

**Completed:**
- Telegram Drive Integration: Completed, integrated with abstraction layer, verified by automated unit tests.
- Google Drive: Removed entirely from the codebase (unused).

**Still unfinished / recommended next:**

1. Manual end-to-end phone test: REGISTER → ADMIN APPROVAL → LOGIN → UPLOAD → VIEW → DOWNLOAD → TRASH/RESTORE → QUOTA.
2. Backend tests for `POST /auth/admin/login` and `GET/PUT /admin/settings` (APIs exist; not in pytest suite yet).
3. Frontend unit/E2E tests for admin dashboard.
4. Alembic migrations, persistent token blacklist, production CORS lockdown, HTTPS for admin UI in production.
5. Optional: serve admin UI on LAN (`0.0.0.0`) with firewall rules documented for family admins on other PCs.

---

## Exact Next Recommended Phase

**Phase 3: Production hardening and family-cloud polish**

- Alembic migrations, WorkManager background transfers, token blacklist persistence, production CORS, optional sharing — **only when explicitly requested**.
