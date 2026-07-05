
from Database import supabase_client

class AuthService:

    def register(self, username: str, email: str, password: str):
        
        database = supabase_client()
        return database.auth.sign_up({
            "email": email,
            "password": password,
            "options": {"data": {"username": username}}
        })


    def login(self, email: str, password: str):
        
        database = supabase_client()
        return database.auth.sign_in_with_password({
            "email": email,
            "password": password
        })