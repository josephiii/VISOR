
# NOT MOUNTED (see main.py). Kept as the starting point for syncing the app's
# accessibility profile across phones; today the profile lives only on the
# device (the app's data/ProfileStore.kt) and nothing calls these routes.
# Before mounting: SettingsSchema has drifted from the app's UserProfile
# (speech rate is now a number, and text size and the voice/glasses
# navigation switches are missing), and the Supabase `settings` table must match.

from fastapi import APIRouter, HTTPException, Depends
from services.UserService import UserService
from dependencies import get_current_user
from schemas.UserSchema import SettingsSchema

user_router = APIRouter(prefix="/user", tags=["user"])
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