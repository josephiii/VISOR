from fastapi import APIRouter, HTTPException, File, UploadFile
from PIL import Image, UnidentifiedImageError
from services.DepthEstService import shared_depth_service
from services.YoloService import YoloService

yolo_router = APIRouter(prefix="/yolo", tags=["yolo"])
service = YoloService()
depth = shared_depth_service()


# Open to anyone for now, like /vlm/image_prompt (see the note in VLMRoutes.py).
@yolo_router.post("/runHazardDetection")
def runHazardDetection(image: UploadFile = File(...)):
    try:
        picture = Image.open(image.file).convert("RGB")
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="Could not read the image")

    objects = service.runModel(picture)
    if not objects:
        return []  # Nothing to measure: skip the depth model.

    # YoloService's objects, each with "depth": how far away it is, in meters.
    return depth.getObjectdepths(objects=objects, grid=depth.runModel(picture))
