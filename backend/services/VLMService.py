# services/VLMService.py
import tempfile
import os

from services.vision_language_models.model_class import VLModel
from services.vision_language_models.model_qwen2_vl import Qwen2_VL

class VLMService:
    def __init__(self):
        self.vlm: VLModel = Qwen2_VL()  # SELECT DESIRED MODEL # ALSO ADD A SETTINGS FILE TO SELECT FROM THERE
        self.vlm.load_model()

    def answer_question(self, prompt: str, image_bytes: bytes):
        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as tmp:
            tmp.write(image_bytes)
            tmp_path = tmp.name

        try:
            return self.vlm.describe_image(tmp_path, prompt)
        finally:
            os.remove(tmp_path)