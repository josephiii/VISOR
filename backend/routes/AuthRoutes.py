
from fastapi import APIRouter, Depends, HTTPException
from dependencies import get_current_user
from services.AuthService import AuthService
from schemas.AuthSchema import RegisterSchema, LoginSchema, VerifySchema, ResendSchema

auth_router = APIRouter(prefix="/auth", tags=["auth"])
service = AuthService()

@auth_router.post("/register")
def register(userData: RegisterSchema):
    try:
        response = service.register(userData.username, userData.email, userData.password)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Registration Failed")
    
    if not response.user:
        raise HTTPException(status_code=400, detail="Registration Failed")
    
    return {"user_id": response.user.id}

@auth_router.post("/login")
def login(userData: LoginSchema):
    try:
        response = service.login(userData.email, userData.password)
    
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid Email or Password")
    
    if not response.session:
        raise HTTPException(status_code=401, detail="Session Error")
    
    return {
        "access_token": response.session.access_token,
        "refresh_token": response.session.refresh_token
    }

@auth_router.post("/verify")
def verify(userData: VerifySchema):
    try:
        response = service.verify_email(userData.email, userData.code)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Verification Failed")

    if not response.session:
        raise HTTPException(status_code=400, detail="Invalid or expired code")

    return {
        "access_token": response.session.access_token,
        "refresh_token": response.session.refresh_token
    }

@auth_router.post("/resend")
def resend(userData: ResendSchema):
    try:
        service.resend_code(userData.email)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Could not resend code")

    return {"message": "Code sent"}

@auth_router.delete("/deleteAccount")
def delete_account(user_id: str = Depends(get_current_user)):
    try:
        service.delete_account(user_id)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Account Deletion Failed")
    
    return {"message": "Account Successfully Deleted"}

