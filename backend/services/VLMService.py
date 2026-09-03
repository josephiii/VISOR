# services/VLMService.py
import tempfile
import os

from services.vision_language_models.model_class import VLModel
from services.vision_language_models.model_qwen import QwenVL

from Database import supabase_client
from fastapi import HTTPException

class VLMService:
    def __init__(self):
        self.vlm: VLModel = QwenVL()  # SELECT DESIRED MODEL # ALSO ADD A SETTINGS FILE TO SELECT FROM THERE
        self.vlm.load_model()

    def answer_question(self, token : str, prompt: str, image_bytes: bytes):

        # CHECK TO SE IF USER EXISTS
        # database = supabase_client()
        # try:
        #     response = database.auth.get_user(token)
        # except Exception:
        #     raise HTTPException(status_code=401, detail="Invalid or expired user token")
        #
        # if response.user is None:
        #     raise HTTPException(status_code=401, detail="Invalid or expired user token")
        # user = response.user

        # TEMP FILE
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            tmp.write(image_bytes)
            tmp_path = tmp.name

        # PREFORM VLM SERVICE
        try:
            return self.vlm.describe_image(tmp_path, prompt)
        finally:
            os.remove(tmp_path)