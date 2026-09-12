import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from backend.database import Base, get_db
from backend.main import app
# Import all models so they register with Base metadata
from backend.users.models import User
from backend.files.models import FileRecord, AuditLog
from backend.storage.models import GoogleDriveConnection, TelegramConnection, TelegramStoredObject
from backend.auth.service import hash_password, create_access_token, blacklisted_tokens

# Use a file-based SQLite for tests (in-memory with shared cache)
SQLALCHEMY_DATABASE_URL = "sqlite:///file:testdb?mode=memory&cache=shared&uri=true"

test_engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False}
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)


@pytest.fixture(autouse=True)
def clear_blacklist():
    """Clear the token blacklist before each test."""
    blacklisted_tokens.clear()
    yield
    blacklisted_tokens.clear()


@pytest.fixture(scope="session", autouse=True)
def setup_database():
    """Create all tables once for the test session."""
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)


@pytest.fixture()
def test_db():
    """Provide a database session for each test with rollback cleanup."""
    connection = test_engine.connect()
    transaction = connection.begin()
    session = Session(bind=connection)
    
    yield session
    
    session.close()
    transaction.rollback()
    connection.close()


@pytest.fixture()
def client(test_db):
    def override_get_db():
        yield test_db

    app.dependency_overrides[get_db] = override_get_db
    # Disable rate limiter for tests
    from backend.middleware.rate_limit import limiter
    limiter.enabled = False
    with TestClient(app) as c:
        yield c
    limiter.enabled = True
    app.dependency_overrides.clear()


@pytest.fixture()
def admin_token(test_db):
    admin = User(
        email="admin@test.com",
        display_name="Admin",
        hashed_password=hash_password("adminpass"),
        role="ADMIN",
        status="ACTIVE"
    )
    test_db.add(admin)
    test_db.commit()
    test_db.refresh(admin)
    return create_access_token(data={"sub": str(admin.id)})


@pytest.fixture()
def user_token(test_db):
    user = User(
        email="user@test.com",
        display_name="User",
        hashed_password=hash_password("userpass123"),
        role="USER",
        status="ACTIVE"
    )
    test_db.add(user)
    test_db.commit()
    test_db.refresh(user)
    return create_access_token(data={"sub": str(user.id)})
