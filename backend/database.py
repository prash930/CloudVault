from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, declarative_base, Session
from backend.config import settings

import time
import logging

logger = logging.getLogger(__name__)

# For SQLite, need check_same_thread=False
connect_args = {}
if settings.DATABASE_URL.startswith("sqlite"):
    connect_args = {"check_same_thread": False}
else:
    connect_args = {"connect_timeout": 10}

engine = create_engine(
    settings.DATABASE_URL,
    connect_args=connect_args,
    pool_pre_ping=True,
    pool_recycle=1800,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def connect_with_retry(retries: int = 5, delay: float = 3.0):
    """Attempt to connect to the database, retrying on transient failures
    (e.g. temporary DNS resolution or network hiccups on the hosting platform)."""
    attempt = 0
    while True:
        attempt += 1
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return
        except Exception as e:
            if attempt >= retries:
                raise
            logger.warning(
                "Database connection attempt %s/%s failed (%s). Retrying in %ss...",
                attempt,
                retries,
                e,
                delay,
            )
            time.sleep(delay)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def create_all_tables():
    # Import all models so they register with Base
    from backend.users import models as user_models
    from backend.files import models as file_models
    from backend.settings import models as settings_models
    from backend.storage import models as storage_models
    connect_with_retry()
    Base.metadata.create_all(bind=engine)
    _apply_sqlite_migrations()


def _apply_sqlite_migrations():
    """Lightweight migrations for existing SQLite databases."""
    if not settings.DATABASE_URL.startswith("sqlite"):
        return
    with engine.begin() as conn:
        columns = {
            row[1]
            for row in conn.exec_driver_sql("PRAGMA table_info(users)").fetchall()
        }
        if "last_login_at" not in columns:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN last_login_at DATETIME")
        if "avatar_1_path" not in columns:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN avatar_1_path VARCHAR(500)")
        if "avatar_2_path" not in columns:
            conn.exec_driver_sql("ALTER TABLE users ADD COLUMN avatar_2_path VARCHAR(500)")

        file_columns = {
            row[1]
            for row in conn.exec_driver_sql("PRAGMA table_info(file_records)").fetchall()
        }
        if "storage_provider" not in file_columns:
            conn.exec_driver_sql(
                "ALTER TABLE file_records ADD COLUMN storage_provider VARCHAR(20) DEFAULT 'local'"
            )
