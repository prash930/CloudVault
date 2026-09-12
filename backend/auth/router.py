from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from backend.database import get_db
from backend.auth import schemas, service
from backend.auth.dependencies import get_current_user, oauth2_scheme
from backend.users.service import get_user_by_email, create_user, record_login
from backend.settings import service as settings_service
from backend.middleware.rate_limit import limiter

router = APIRouter(prefix="/auth", tags=["Authentication"])

@router.post("/register", response_model=schemas.MessageResponse)
@limiter.limit("3/hour")
def register(request: Request, user_data: schemas.RegisterRequest, db: Session = Depends(get_db)):
    user = get_user_by_email(db, user_data.email)
    if user:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    hashed_password = service.hash_password(user_data.password)
    runtime = settings_service.get_runtime_settings(db)
    initial_status = "PENDING" if runtime["require_registration_approval"] else "ACTIVE"
    create_user(db, user_data.email, user_data.display_name, hashed_password, status=initial_status)
    
    if initial_status == "PENDING":
        return {"message": "Registration successful. Please wait for admin approval."}
    return {"message": "Registration successful. You can sign in now."}

@router.post("/login", response_model=schemas.TokenResponse)
@limiter.limit("5/minute")
def login(request: Request, form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = get_user_by_email(db, form_data.username)
    if not user or not service.verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    if user.status in ["BANNED", "SUSPENDED"]:
        raise HTTPException(status_code=403, detail=f"User account is {user.status.lower()}")
    elif user.status == "PENDING":
        raise HTTPException(status_code=403, detail="Account is pending admin approval")
        
    record_login(db, user)
    access_token = service.create_access_token(data={"sub": str(user.id)})
    return {"access_token": access_token, "token_type": "bearer", "user": user}

@router.post("/admin/login", response_model=schemas.TokenResponse)
@limiter.limit("5/minute")
def admin_login(request: Request, form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    user = get_user_by_email(db, form_data.username)
    if not user or not service.verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    if user.role != "ADMIN":
        raise HTTPException(status_code=403, detail="Admin access required")
    if user.status in ["BANNED", "SUSPENDED", "PENDING"]:
        raise HTTPException(status_code=403, detail=f"Admin account is not active ({user.status.lower()})")
    record_login(db, user)
    access_token = service.create_access_token(data={"sub": str(user.id)})
    return {"access_token": access_token, "token_type": "bearer", "user": user}

@router.post("/logout")
def logout(token: str = Depends(oauth2_scheme)):
    service.blacklist_token(token)
    return {"message": "Successfully logged out"}

@router.post("/forgot-password", response_model=schemas.MessageResponse)
def forgot_password(req: schemas.ForgotPasswordRequest):
    # Phase 1: Just log and return success
    print(f"Password reset requested for {req.email}")
    return {"message": "If that email is in our system, we have sent a reset link."}

@router.get("/me", response_model=schemas.UserOut)
def read_users_me(current_user = Depends(get_current_user)):
    return current_user
