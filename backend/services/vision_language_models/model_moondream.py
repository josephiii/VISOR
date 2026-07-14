from services.vision_language_models.model_class import VLModel

import torch
from PIL import Image

from transformers import AutoModelForCausalLM


class Moondream(VLModel):

    def __init__(self):
        self.MODEL_ID = "moondream/moondream3-preview"


    def load_model(self):

        self.model = AutoModelForCausalLM.from_pretrained(
            self.MODEL_ID,
            trust_remote_code=True,
            dtype=torch.bfloat16,
            device_map="auto"
        )


    def describe_image(
        self,
        image_path,
        prompt="Describe this image to me in 2 sentences"
    ):

        image = Image.open(image_path).convert("RGB")


        with torch.inference_mode():

            answer = self.model.query(
                image,
                prompt
            )


        return answer#["reasoning"]["text"] test later