from fastapi import APIRouter, HTTPException, File, UploadFile
from PIL import Image, UnidentifiedImageError
from services.DepthEstService import shared_depth_service

depth_router = APIRouter(prefix="/depth", tags=["depth"])
service = shared_depth_service()


@depth_router.post("")
def beginDepthEst(image: UploadFile = File(...)):
    # The whole depth map. For the depth of each object in the image, see
    # /yolo/runHazardDetection (YoloRoutes.py).
    try:
        picture = Image.open(image.file).convert("RGB")
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="Could not read the image")

    depth_map = service.runModel(picture)

    # Depth in meters per pixel, rows top to bottom (a numpy array is not JSON).
    return {
        "width": picture.width,
        "height": picture.height,
        "depth": depth_map.astype(float).round(3).tolist(),
    }
