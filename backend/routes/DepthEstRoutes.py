from fastapi import APIRouter, HTTPException, File, UploadFile
from util import dservice
from PIL import Image


depth_router= APIRouter(prefix="/depth", tags=["depth"])


@depth_router.post("")
def beginDepthEst(image : UploadFile = File(...)):
    #Given an image file and coordinates of objects in the file find the depths
    image = Image.open(image.file).convert("RGB")
    if image is None:
        raise HTTPException(status_code=400, detail="Missing image/frame")
    response = dservice.runModel(image)
    return "Success", response
    