# VISOR Backend

The FastAPI server behind the VISOR app: accounts (through Supabase), scene descriptions from a vision
language model, and depth estimation.

## Run it

From `backend/` (the team develops with Python 3.14):

```
python -m venv .venv
.venv\Scripts\activate          # Windows; macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env          # macOS/Linux: cp .env.example .env, then fill in the values
uvicorn main:app --host 0.0.0.0 --port 8000
```

The first start downloads the models from Hugging Face (Qwen3-VL-2B and Depth-Anything-V2 small), which
takes a while. Interactive API docs are then at `http://localhost:8000/docs`.

To point the app at your server, set `visor_api_base_url` in `frontend/visor/local.properties`.
For example, `http://10.0.2.2:8000` reaches this machine from the Android emulator. Without it,
the app uses the hosted server.

## Endpoints

| Method | Path | Body | Returns |
| --- | --- | --- | --- |
| POST | `/auth/register` | `{ username, email, password }` | `{ user_id }` |
| POST | `/auth/login` | `{ email, password }` | `{ access_token, refresh_token }` |
| DELETE | `/auth/deleteAccount` | `Authorization: Bearer <access_token>` | `{ message }` |
| POST | `/vlm/image_prompt` | multipart `prompt`, `image` | the answer, as a string |
| POST | `/depth` | multipart `image` | `{ width, height, depth }`, depth in meters per pixel |

## Layout

| Path | What it holds |
| --- | --- |
| `main.py` | The FastAPI app: mounts the routers below. |
| `Database.py`, `dependencies.py` | The Supabase client, and `get_current_user` for routes that need a logged-in user. |
| `routes/` | HTTP endpoints, one file per feature. `UserRoutes.py` (profile sync) is kept but **not mounted** yet; see the note in it. |
| `schemas/` | Request bodies (Pydantic). |
| `services/` | The logic behind each route; `vision_language_models/` holds one class per VLM. |
| `scripts/` | Try a model without the server: `python -m scripts.try_vlm`, `python -m scripts.try_depth`. |
| `samples/` | Test images for the scripts. |
| `notebooks/` | Exploratory notebooks (the earlier Qwen2-VL test). |
| `weights/` | YOLO weights kept for planned custom object recognition. Nothing loads them yet. |
| `other/` | `CustomObjectDetectorTrainer.py`, a draft for training the app's custom object detector (commented out). |

More on the models: [documentation/developer/VLM.md](../documentation/developer/VLM.md).
