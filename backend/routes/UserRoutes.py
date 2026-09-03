
from fastapi import APIRouter, HTTPException, Depends
from services.UserService import UserService
from dependencies import get_current_user
from schemas.UserSchema import SettingsSchema

user_router = APIRouter(prefix="/user", tags=["user"], dependencies=[Depends(get_current_user)])
service = UserService()


@user_router.get("/settings")
def get_settings(userId: str = Depends(get_current_user)):
    try:
        response = service.get_settings(userId)

    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="Fetch settings failed")

    if not response:
        raise HTTPException(status_code=404, detail="Settings not found")
    
    return response

@user_router.put("/settings")
def update_settings(settingsData: SettingsSchema, userId: str = Depends(get_current_user)):
    try:
        response = service.update_settings(userId, settingsData)
    
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="update settings failed")

    if not response:
        raise HTTPException(status_code=404, detail="Settings not found")
    
    return response