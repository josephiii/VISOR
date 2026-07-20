
from Database import supabase_client
from schemas.UserSchema import SettingsSchema

class UserService:
    
    def get_settings(self, userId: str):
        
        response = supabase_client().table("settings").select("*").eq("user_id", userId).execute()

        if response.data:
            return response.data[0]
        else:
            return None


    def update_settings(self, userId: str, settingsData: SettingsSchema):
        
        # this converts the data from pydantic schema --> string object supabase can read
        data = settingsData.model_dump(mode="json")
        response = supabase_client().table("settings").update(data).eq("user_id", userId).execute()

        if response.data:
            return response.data[0]
        else:
            return None