
from fastapi import APIRouter
from services.AuthService import AuthService
from schemas.AuthSchema import RegisterSchema, LoginSchema

auth_router = APIRouter(prefix="/auth", tags=["auth"])
service = AuthService()

## MAKE SURE TO ADD PROPER ERROR RESPONSES FOR LOGIN AND RESGISTER!!
@auth_router.post("/register")
async def register(userData: RegisterSchema):
    response = service.register(userData)

    if not response:
        return "Error" # <---- HERE
    return response

@auth_router.post("/login")
async def login(userData: LoginSchema):
    
    response = service.login(userData)

    if not response:
        return "Error" # <---- HERE
    return response

