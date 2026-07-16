
from fastapi import APIRouter, HTTPException
from services.UserService import UserService
from schemas.UserSchema import SettingsSchema

user_router = APIRouter(prefix="/user", tags=["user"])
service = UserService()


@user_router.get("/settings")
def get_settings(userId: str):
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
def update_settings(userId: str, settingsData: SettingsSchema):
    try:
        response = service.update_settings(
            userId, settingsData.username, settingsData.visionType, settingsData.visionDescription, 
            settingsData.severity, settingsData.speechRate, settingsData.verbosity, settingsData.appHighContrast
        )
    
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=500, detail="update settings failed")

    if not response:
        raise HTTPException(status_code=404, detail="Settings not found")
    
    return response