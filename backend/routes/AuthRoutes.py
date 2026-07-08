
from fastapi import APIRouter, HTTPException
from services.AuthService import AuthService
from schemas.AuthSchema import RegisterSchema, LoginSchema

auth_router = APIRouter(prefix="/auth", tags=["auth"])
service = AuthService()

## MAKE SURE TO ADD PROPER ERROR RESPONSES FOR LOGIN AND RESGISTER!!
@auth_router.post("/register")
async def register(userData: RegisterSchema):
    response = service.register(userData)

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

