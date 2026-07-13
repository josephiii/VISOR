from model_qwen import QwenVL
from model_moondream import Moondream

def main():
    model = Moondream()
    model.load_model()
    text : str = model.describe_image("images.jpg", "Describe this image")
    print(text)
    model.unload_model()

if __name__ == "__main__":
    main()