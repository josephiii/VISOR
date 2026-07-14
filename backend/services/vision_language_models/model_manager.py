import os
os.environ["HF_HOME"] = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache", "huggingface")

from model_qwen import QwenVL
from model_moondream import Moondream
from model_gemma import Gemma

def main():
    model = Gemma()
    model.load_model()
    text : str = model.describe_image("images.jpg", "Describe this image")
    print(text)
    model.unload_model()

if __name__ == "__main__":
    main()