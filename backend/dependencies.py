
from fastapi import Depends, HTTPException
from Database import supabase_client
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

security = HTTPBearer()

def get_current_user(credentials: HTTPAuthorizationCredentials = Depends(security)):
    try:
        response = supabase_client().auth.get_user(credentials.credentials)

        if response is None or response.user is None:
            raise HTTPException(status_code=401, detail="Invalid or expired token")
        return response.user.id
    
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")