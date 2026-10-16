from fastapi import APIRouter, UploadFile, File 
from services.yoloService import YoloService
from services.DepthEstService import Depth
from PIL import Image
from util import dservice

yoloRouter = APIRouter(prefix="/yolo", tags=["yolo"])
service = YoloService()


@yoloRouter.post("/runHazardDetection")
def runHazardDetection(image: UploadFile= File(...)):
    image = Image.open(image.file).convert("RGB")
    objects = service.runModel(image)
    #getDepths(objects)
    grid=dservice.runModel(image)
    objects = dservice.getObjectdepths(objects=objects,grid=grid)
    #return objects w depth
    return objects