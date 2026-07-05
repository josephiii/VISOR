from model_class import VLModel

import torch
from transformers import Qwen2VLForConditionalGeneration, AutoProcessor
#from qwen_vl_utils import process_vision_info

# pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
# pip install transformers accelerate
# pip install qwen-vl-utils
# pip install pillow

MODEL_ID = "Qwen/Qwen2-VL-2B-Instruct"
image_path = "images.jpg"

class Qwen2_VL(VLModel):
    def __init__(self):
        pass

    def load_model(self):
        model = Qwen2VLForConditionalGeneration.from_pretrained(
            MODEL_ID,
            device_map="auto",
            torch_dtype=torch.bfloat16
        )

        processor = AutoProcessor.from_pretrained(MODEL_ID)

        return model, processor

    def describe_image(self, model, processor, image_path, prompt="Describe this image to me in 2 sentences"):
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image_path},
                    {"type": "text", "text": "Describe this image to me in 2 sentences"}
                ],
            }
        ]

        inputs = processor.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt"
        ).to(model.device)

        output = model.generate(**inputs, max_new_tokens=100)
        return output