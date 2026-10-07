from fastapi import Http, APIRouter, UploadFile, File 
from services.yoloService import yolo

yoloRouter = APIRouter(prefix="yolo", tags=["yolo"])
service = yolo()


@yoloRouter.post("/runHazardDetection")
def runHazardDetection(image: UploadFile= File(...)):
    objects = service.runModel(image)
    #getDepths(objects)
    #return objects w depth
    return objects