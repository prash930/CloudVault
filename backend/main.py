from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from slowapi.errors import RateLimitExceeded
from slowapi import _rate_limit_exceeded_handler

from backend.config import settings
from backend.database import SessionLocal, create_all_tables
from backend.middleware.rate_limit import limiter
from backend.auth.router import router as auth_router
from backend.users.router import router as users_router
from backend.files.router import router as files_router
from backend.storage.router import router as storage_router

import os
from contextlib import asynccontextmanager
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
ADMIN_UI_DIR = PROJECT_ROOT / "admin-dashboard" / "dist"


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Create database tables
    create_all_tables()

    # Create storage directory
    os.makedirs(settings.STORAGE_ROOT_DIR, exist_ok=True)

    db = SessionLocal()

    try:
        from backend.users.service import ensure_minimum_user_quota
        from backend.users.models import User
        from backend.auth.service import hash_password

        ensure_minimum_user_quota(db)
        db.commit()

        # Create or update admin account
        if settings.ADMIN_EMAIL and settings.ADMIN_PASSWORD:

            admin = (
                db.query(User)
                .filter(User.email == settings.ADMIN_EMAIL)
                .first()
            )

            if admin:
                admin.hashed_password = hash_password(
                    settings.ADMIN_PASSWORD
                )
                admin.display_name = settings.ADMIN_DISPLAY_NAME
                admin.role = "ADMIN"
                admin.status = "ACTIVE"

            else:
                admin = User(
                    email=settings.ADMIN_EMAIL,
                    display_name=settings.ADMIN_DISPLAY_NAME,
                    hashed_password=hash_password(
                        settings.ADMIN_PASSWORD
                    ),
                    role="ADMIN",
                    status="ACTIVE"
                )

                db.add(admin)

            db.commit()

    finally:
        db.close()

    yield


app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Backend for CloudBox - Private Family Cloud Storage",
    lifespan=lifespan
)


app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


app.state.limiter = limiter
app.add_exception_handler(
    RateLimitExceeded,
    _rate_limit_exceeded_handler
)


app.include_router(auth_router)
app.include_router(users_router)
app.include_router(files_router)
app.include_router(storage_router)


@app.get("/s/{token}")
def public_share_info(token: str):
    from backend.files import share_service
    from backend.database import SessionLocal

    db = SessionLocal()
    try:
        share, record = share_service.get_share_by_token(db, token)
        return share_service.serialize_share(share, record)
    finally:
        db.close()


@app.get("/s/{token}/download")
async def public_share_download(token: str, request: Request, inline: bool = False):
    from backend.files import share_service, service
    from backend.files.router import build_range_streaming_response
    from backend.database import SessionLocal

    db = SessionLocal()
    try:
        share, record = share_service.get_share_by_token(db, token)
        if record.is_folder:
            raise HTTPException(status_code=400, detail="Folders cannot be downloaded directly")
        provider = service.get_provider_for_record(db, record)
        return await build_range_streaming_response(
            request=request,
            provider=provider,
            object_id=record.storage_object_id,
            filename=record.filename,
            mime_type=record.mime_type,
            inline=inline,
        )
    finally:
        db.close()


@app.get("/")
def read_root():
    return {
        "app": settings.APP_NAME,
        "version": "1.0.0",
        "status": "running"
    }


@app.get("/health")
def health_check():
    return {"status": "ok"}


if ADMIN_UI_DIR.exists():

    assets_dir = ADMIN_UI_DIR / "assets"

    if assets_dir.exists():
        app.mount(
            "/admin-ui/assets",
            StaticFiles(directory=assets_dir),
            name="admin-ui-assets"
        )

    @app.get("/admin-ui")
    @app.get("/admin-ui/{full_path:path}")
    def serve_admin_ui(full_path: str = ""):

        index_file = ADMIN_UI_DIR / "index.html"
        requested = ADMIN_UI_DIR / full_path

        if full_path and requested.is_file():
            return FileResponse(requested)

        return FileResponse(index_file)
