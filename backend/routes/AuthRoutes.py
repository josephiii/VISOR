
from fastapi import Header, APIRouter, HTTPException
from services.AuthService import AuthService
from schemas.AuthSchema import RegisterSchema, LoginSchema

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

@auth_router.delete("/deleteAccount")
def delete_account(authorization: str = Header(...)):
    token = authorization.replace("Bearer ", "")

    try:
        service.delete_account(token)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Account Deletion Failed")
    
    return {"message": "Account Successfully Deleted"}

