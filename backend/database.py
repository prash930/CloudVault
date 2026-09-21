from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base, Session
from backend.config import settings

# For SQLite, need check_same_thread=False
connect_args = {}
if settings.DATABASE_URL.startswith("sqlite"):
    connect_args = {"check_same_thread": False}

engine = create_engine(settings.DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

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
