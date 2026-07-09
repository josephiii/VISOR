
from Database import supabase_client
from fastapi import HTTPException


class AuthService:

    def register(self, username: str, email: str, password: str):
        database = supabase_client()

        try:
            response = database.auth.sign_up({
                "email": email,
                "password": password,
                "options": {"data": {"username": username}}
            })
        except Exception:
            raise HTTPException(status_code=400, detail="Registration Failed")
        
        return response


    def login(self, email: str, password: str):
        database = supabase_client()

        try:
            response = database.auth.sign_in_with_password({
                "email": email,
                "password": password
            })
        except Exception:
            raise HTTPException(status_code=401, detail="Invalid email or password")
        
        return response
    
    
    def delete_account(self, token: str):
        database = supabase_client()
        
        try:
            user = database.auth.get_user(token)
        except Exception:
            raise HTTPException(status_code=401, detail="Invalid or expired user token")
        
        if user is None or user.user is None:
            raise HTTPException(status_code=401, detail="Invalid or expired user token")
        
        database.auth.admin.delete_user(user.user.id)

        