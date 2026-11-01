
from pydantic import BaseModel, EmailStr

class RegisterSchema(BaseModel):
    username: str
    email: EmailStr
    password: str

class LoginSchema(BaseModel):
    email: EmailStr
    password: str

class VerifySchema(BaseModel):
    email: EmailStr
    code: str

class ResendSchema(BaseModel):
    email: EmailStr