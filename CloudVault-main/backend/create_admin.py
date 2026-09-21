import getpass
import os
from sqlalchemy.orm import Session

from backend.database import SessionLocal
from backend.users.models import User
from backend.auth.service import hash_password


def create_admin():
    db: Session = SessionLocal()

    try:
        print("--- CloudBox Admin Creation ---")

        email = os.getenv("ADMIN_EMAIL") or input("Email: ").strip()
        display_name = (
            os.getenv("ADMIN_DISPLAY_NAME")
            or input("Display Name: ").strip()
        )
        password = os.getenv("ADMIN_PASSWORD")

        if not password:
            while True:
                password = getpass.getpass(
                    "Password (min 8 chars): "
                )
                if len(password) >= 8:
                    break

                print("Password too short.")

        if len(password) < 8:
            print(
                "Error: ADMIN_PASSWORD must be "
                "at least 8 characters."
            )
            return

        # Check whether admin already exists
        existing_user = (
            db.query(User)
            .filter(User.email == email)
            .first()
        )

        if existing_user:
            # Synchronize existing admin with Render env variables
            existing_user.hashed_password = hash_password(password)
            existing_user.display_name = display_name
            existing_user.role = "ADMIN"
            existing_user.status = "ACTIVE"

            db.commit()

            print(
                f"Success! Existing admin {email} "
                "updated and password reset."
            )
            return

        # Create admin if it doesn't exist
        admin_user = User(
            email=email,
            display_name=display_name,
            hashed_password=hash_password(password),
            role="ADMIN",
            status="ACTIVE"
        )

        db.add(admin_user)
        db.commit()
        db.refresh(admin_user)

        print(f"Success! Admin user {email} created.")

    finally:
        db.close()


if __name__ == "__main__":
    create_admin()
