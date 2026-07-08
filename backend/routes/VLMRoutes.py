# routes/VLMRoutes.py
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
from services.VLMService import VLMService

vlm_router = APIRouter(prefix="/vlm", tags=["vlm"])
service = VLMService()

@vlm_router.post("/image_prompt")
async def image_prompt(
    prompt: str = Form(...),
    image: UploadFile = File(...)
):
    image_bytes = await image.read()

    response = service.answer_question(prompt=prompt, image_bytes=image_bytes)

    if not response:
        raise HTTPException(status_code=422, detail="VLM analysis failed")
    return response