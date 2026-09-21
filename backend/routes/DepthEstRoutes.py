from fastapi import APIRouter, HTTPException, File, UploadFile
from services.DepthEstService import Depth
from PIL import Image


depth_router= APIRouter(prefix="/depth", tags=["depth"])
service= Depth()

@depth_router.post("")
def beginDepthEst(image : UploadFile = File(...)):

    image = Image.open(image.file).convert("RGB")
    if image is None:
        raise HTTPException(status_code=400, detail="Missing image/frame")
    response = service.runModel(image)
    return "Success"
    