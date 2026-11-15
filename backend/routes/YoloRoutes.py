import time

from fastapi import APIRouter, HTTPException, File, UploadFile, Response
from PIL import Image, UnidentifiedImageError
from services import HazardWatchFilter
from services.DepthEstService import shared_depth_service
from services.YoloService import YoloService

yolo_router = APIRouter(prefix="/yolo", tags=["yolo"])
service = YoloService()
depth = shared_depth_service()


# Open to anyone for now, like /vlm/image_prompt (see the note in VLMRoutes.py).
# watch=1 is the hazard watch: only confident, nearby hazards, one box each
# (services/HazardWatchFilter.py). X-Process-Time-Ms says how long the server took.
@yolo_router.post("/runHazardDetection")
def runHazardDetection(response: Response, image: UploadFile = File(...), watch: bool = False):
    started = time.perf_counter()
    try:
        picture = Image.open(image.file).convert("RGB")
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="Could not read the image")

    objects = service.runModel(picture)
    if watch:
        config = HazardWatchFilter.YOLOE_WATCH_CONFIG if service.is_yoloe else HazardWatchFilter.WATCH_CONFIG
        objects = HazardWatchFilter.before_depth(objects, picture.size, config)

    if objects:
        # YoloService's objects, each with "depth": how far away it is, in meters.
        objects = depth.getObjectdepths(objects=objects, grid=depth.runModel(picture))
        if watch:
            objects = HazardWatchFilter.after_depth(objects, config)
    # else: nothing to measure, so the depth model is skipped.

    response.headers["X-Process-Time-Ms"] = str(round((time.perf_counter() - started) * 1000))
    return objects
