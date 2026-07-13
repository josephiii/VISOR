from services.vision_language_models.model_class import VLModel

import torch
from PIL import Image

from transformers import (
    AutoModelForCausalLM,
    AutoProcessor
)


class Moondream(VLModel):

    def __init__(self):
        self.MODEL_ID = "moondream/moondream3-preview"


    def load_model(self):

        self.model = AutoModelForCausalLM.from_pretrained(
            self.MODEL_ID,
            trust_remote_code=True,
            dtype=torch.bfloat16,
            device_map="cpu"
        )

        self.processor = AutoProcessor.from_pretrained(
            self.MODEL_ID,
            trust_remote_code=True
        )


    def describe_image(
        self,
        image_path,
        prompt="Describe this image to me in 2 sentences"
    ):

        image = Image.open(image_path).convert("RGB")


        inputs = self.processor(
            images=image,
            text=prompt,
            return_tensors="pt"
        )


        inputs = inputs.to(self.model.device)


        with torch.inference_mode():

            output_ids = self.model.generate(
                **inputs,
                max_new_tokens=128
            )


        return self.processor.decode(
            output_ids[0],
            skip_special_tokens=True
        )