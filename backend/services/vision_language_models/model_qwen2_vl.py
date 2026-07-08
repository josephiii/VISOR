from services.vision_language_models.model_class import VLModel

import torch
from transformers import Qwen2VLForConditionalGeneration, AutoProcessor
#from qwen_vl_utils import process_vision_info

# pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
# pip install transformers accelerate
# pip install qwen-vl-utils
# pip install pillow

class Qwen2_VL(VLModel):

    def __init__(self):
        self.MODEL_ID = "Qwen/Qwen2-VL-2B-Instruct"

    def load_model(self):
        self.model = Qwen2VLForConditionalGeneration.from_pretrained(
            self.MODEL_ID,
            device_map= "cpu", #"auto",
            torch_dtype=torch.bfloat16
        )

        self.processor = AutoProcessor.from_pretrained(self.MODEL_ID)

       #return self.model, self.processor

    def describe_image(self, image_path, prompt="Describe this image to me in 2 sentences"):
        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image_path},
                    {"type": "text", "text": "Describe this image to me in 2 sentences"}
                ],
            }
        ]

        inputs = self.processor.apply_chat_template(
            messages,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt"
        ).to(self.model.device)

        generated_ids = self.model.generate(**inputs, max_new_tokens=100)

        generated_ids_trimmed = [
            out_ids[len(in_ids):] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
        ]

        output_text = self.processor.batch_decode(
            generated_ids_trimmed,
            skip_special_tokens=True,
            clean_up_tokenization_spaces=False
        )
        return output_text