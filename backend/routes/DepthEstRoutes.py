from fastapi import APIRouter, HTTPException
from services.DepthEstService import Depth

depth_router= APIRouter(prefix="/depth", tags=["depth"])
service= Depth()

@depth_router.post("")
async def beginDepthEst(image):
    if not image:
        raise HTTPException(status_code=400, detail="Missing image/frame")
    response = await service.runModel(image)
    if response:
        return
    else:
        raise HTTPException(status_code=500, detail="Error running depth estimation model") 
    