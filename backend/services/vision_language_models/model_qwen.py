from services.vision_language_models.model_class import VLModel

import torch
from transformers import (
    AutoModelForImageTextToText,
    AutoProcessor
)


class QwenVL(VLModel):

    def __init__(self):
        self.MODEL_ID = "Qwen/Qwen3-VL-2B-Instruct" # can swap to 2, 4, and 8

    def load_model(self):

        self.model = AutoModelForImageTextToText.from_pretrained(
            self.MODEL_ID,
            device_map="cpu",
            dtype=torch.bfloat16
        )

        self.processor = AutoProcessor.from_pretrained(
            self.MODEL_ID
        )


    def describe_image(
        self,
        image_path,
        prompt="Describe this image to me in 2 sentences"
    ):

        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "image": image_path
                    },
                    {
                        "type": "text",
                        "text": prompt
                    }
                ]
            }
        ]

        inputs = self.processor.apply_chat_template(
            messages,
            tokenize=True,
            add_generation_prompt=True,
            return_tensors="pt",
            return_dict=True
        )

        inputs = inputs.to(self.model.device)


        with torch.inference_mode():

            output_ids = self.model.generate(
                **inputs,
                max_new_tokens=128
            )


        generated = [
            out[len(inp):]
            for inp, out in zip(
                inputs.input_ids,
                output_ids
            )
        ]

        return self.processor.batch_decode(
            generated,
            skip_special_tokens=True
        )[0]