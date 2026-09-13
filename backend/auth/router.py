from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.responses import HTMLResponse
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from backend.database import get_db
from backend.auth import schemas, service
from backend.auth.dependencies import get_current_user, oauth2_scheme
from backend.users.service import get_user_by_email, get_user_by_id, create_user, record_login
from backend.auth.email_service import send_password_reset_email
from backend.config import settings
from backend.middleware.rate_limit import limiter

router = APIRouter(prefix="/auth", tags=["Authentication"])

@router.post("/register", response_model=schemas.MessageResponse)
@limiter.limit("3/hour")
def register(request: Request, user_data: schemas.RegisterRequest, db: Session = Depends(get_db)):
    user = get_user_by_email(db, user_data.email)
    if user:
        raise HTTPException(status_code=400, detail="Email already registered")
    
    hashed_password = service.hash_password(user_data.password)
    create_user(db, user_data.email, user_data.display_name, hashed_password, status="ACTIVE")
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
@limiter.limit("3/hour")
def forgot_password(request: Request, req: schemas.ForgotPasswordRequest, db: Session = Depends(get_db)):
    user = get_user_by_email(db, req.email)
    if user:
        token = service.create_password_reset_token(user.id)
        reset_link = f"{settings.FRONTEND_BASE_URL.rstrip('/')}/auth/reset-password?token={token}"
        try:
            send_password_reset_email(user.email, reset_link)
        except Exception:
            # Do not expose delivery failures or account existence to the caller.
            pass
    return {"message": "If that email is in our system, we have sent a reset link."}


@router.get("/reset-password", response_class=HTMLResponse)
@limiter.limit("5/minute")
def reset_password_page(request: Request):
    return """<!doctype html>
<html><head><meta charset=\"utf-8\"><title>Reset password</title>
<style>body{font-family:system-ui;max-width:400px;margin:4rem auto;padding:1rem}input,button{box-sizing:border-box;width:100%;padding:.7rem;margin:.35rem 0}#message{min-height:1.5rem}</style>
</head><body><h1>Reset password</h1><form id=\"form\"><input id=\"password\" type=\"password\" minlength=\"8\" placeholder=\"New password\" required><input id=\"confirm\" type=\"password\" minlength=\"8\" placeholder=\"Confirm new password\" required><button>Reset password</button></form><p id=\"message\"></p>
<script>const token=new URLSearchParams(location.search).get('token'),form=document.getElementById('form'),message=document.getElementById('message');form.addEventListener('submit',async e=>{e.preventDefault();const password=document.getElementById('password').value;if(password!==document.getElementById('confirm').value){message.textContent='Passwords do not match.';return}const response=await fetch('/auth/reset-password',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:token,new_password:password})});const body=await response.json();message.textContent=body.message||body.detail||'Unable to reset password.'})</script>
</body></html>"""


@router.post("/reset-password", response_model=schemas.MessageResponse)
@limiter.limit("5/minute")
def reset_password(request: Request, req: schemas.ResetPasswordRequest, db: Session = Depends(get_db)):
    user_id = service.verify_password_reset_token(req.token)
    if user_id is None:
        raise HTTPException(status_code=400, detail="Invalid or expired password reset token")
    user = get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.hashed_password = service.hash_password(req.new_password)
    db.commit()
    service.blacklist_token(req.token)
    return {"message": "Password reset successful. You can sign in now."}

@router.get("/me", response_model=schemas.UserOut)
def read_users_me(current_user = Depends(get_current_user)):
    return current_user
