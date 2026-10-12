from fastapi import APIRouter, HTTPException, File, UploadFile
from PIL import Image, UnidentifiedImageError
from services.DepthEstService import DepthEstService

depth_router = APIRouter(prefix="/depth", tags=["depth"])
service = DepthEstService()


@depth_router.post("")
def beginDepthEst(image: UploadFile = File(...)):
    # Next step: also take the coordinates of objects in the image and return
    # each one's depth (DepthEstService.getObjectdepths).
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
