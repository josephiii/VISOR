
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
        except Exception as e:
            # Supabase rejects sign-in until the signup code has been entered.
            if "email not confirmed" in str(e).lower():
                raise HTTPException(status_code=403, detail="Email not confirmed")
            raise HTTPException(status_code=401, detail="Invalid email or password")

        return response


    def verify_email(self, email: str, code: str):
        database = supabase_client()

        try:
            response = database.auth.verify_otp({
                "email": email,
                "token": code,
                "type": "signup"
            })
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid or expired code")

        return response


    def resend_code(self, email: str):
        database = supabase_client()

        try:
            database.auth.resend({"type": "signup", "email": email})
        except Exception:
            raise HTTPException(status_code=400, detail="Could not resend code")

    
    def delete_account(self, user_id: str):
        # The caller's token was already checked by dependencies.get_current_user.
        supabase_client().auth.admin.delete_user(user_id)

        