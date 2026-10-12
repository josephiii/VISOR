"""Run a vision language model on a few sample images, without the server.

From backend/:
    python -m scripts.try_vlm

Swap the model below (QwenVL, Gemma, Moondream) to compare them. The model the
API actually serves is chosen in services/VLMService.py.
"""

from services.vision_language_models.model_qwen import QwenVL
from services.vision_language_models.model_moondream import Moondream  # noqa: F401
from services.vision_language_models.model_gemma import Gemma  # noqa: F401


def main():
    model = QwenVL()
    model.load_model()
    for image in ["samples/vlm/puppy.png", "samples/vlm/house.jpg", "samples/vlm/manhole.jpg"]:
        print(image, "->", model.describe_image(image, "Describe this image"))
    model.unload_model()


if __name__ == "__main__":
    main()
