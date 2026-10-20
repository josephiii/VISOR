# Vision Language Model

The Vision Language Model (VLM) is used to generate scene descriptions or answer questions about an image. It can analyze visual content and infer information in a similar way that a large language model reasons about text.

In the app, "VISOR, what do you see?" takes a photo with the glasses and sends it to `POST /vlm/image_prompt` (`frontend/.../capture/SceneDescriber.kt` → `network/VlmClient.kt`). If the server can't answer, the app falls back to on-device labels.

## Project Structure

The VLM code lives in `backend/`:

### `services/vision_language_models/model_class.py`

Defines the base class for all vision language models. This provides a common interface so that different models can be swapped in without changing the rest of the code.

### `services/vision_language_models/model_[type].py`

Contains the implementation for each supported model.

Current implementations include:

```
from services.vision_language_models.model_qwen import QwenVL
from services.vision_language_models.model_moondream import Moondream
from services.vision_language_models.model_gemma import Gemma
```

### `scripts/try_vlm.py`

Used for testing models locally, without the server. Running it loads a model and describes a few images from `samples/vlm/`.

## Testing

### Testing in Python

From `backend/`:

```
python -m scripts.try_vlm
```

To test a different model, change the model initialization in `scripts/try_vlm.py`:

```
model = QwenVL()  # Replace with another model implementation
```

To test a different image or prompt, edit the image list or the prompt passed to:

```
model.describe_image("samples/vlm/house.jpg", "Describe this image")
```

### Testing through the API

The active model is selected in `services/VLMService.py`:

```
self.vlm: VLModel = QwenVL()
```

Replace `QwenVL()` with any supported model implementation.

Once configured, start the FastAPI server (see [backend/README.md](../../backend/README.md)) and interact with the API endpoints to test the selected model.


## Model Specifics

The project currently supports three Vision Language Models (VLMs).

### QwenVL

- **Model:** `Qwen/Qwen3-VL-2B-Instruct`
- **Parameter Size:** **2 billion parameters**
- **Context Length:** Up to **256K** tokens
- **Purpose:** General-purpose image understanding, visual question answering, OCR, and document/image reasoning.
- **Notes:** Larger variants (4B, 8B, and 32B) are available by changing the model ID. 

### Gemma

- **Model:** `google/gemma-4-E2B-it`
- **Parameter Size:** **2B effective (dense) parameters**
- **Context Length:** **8K** tokens
- **Purpose:** Instruction-tuned multimodal model for image understanding, visual reasoning, and conversational AI.
- **Notes:** Part of the Gemma 4 family. Larger variants include E4B (4B), 26B-A4B (MoE), and 31B.

### Moondream

- **Model:** `moondream/moondream3-preview`
- **Parameter Size:** **9B total parameters (2B active during inference)**
- **Context Length:** **32K** tokens
- **Purpose:** Efficient image captioning, visual question answering, and image reasoning designed for fast local inference.
- **Notes:** Uses a sparse MoE architecture, allowing performance comparable to much larger models while only activating approximately 2B parameters per token. 
