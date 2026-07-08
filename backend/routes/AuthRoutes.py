
from fastapi import APIRouter, HTTPException
from services.AuthService import AuthService
from schemas.AuthSchema import RegisterSchema, LoginSchema

router = APIRouter()
service = AuthService()

@router.post("/register")
def register(userData: RegisterSchema):
    try:
        response = service.register(userData.username, userData.email, userData.password)

    except Exception as error:
        raise HTTPException(status_code=400, detail=error)
    
    if not response.user:
        raise HTTPException(status_code=400, detail="Registration Failed")
    
    return {"user_id": response.user.id}

@router.post("/login")
def login(userData: LoginSchema):
    try:
        response = service.login(userData.email, userData.password)
    
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid Email or Password")
    
    if not response.session:
        raise HTTPException(status_code=401, detail="Session Error")
    
    return {"access_token": response.session.access_token}

