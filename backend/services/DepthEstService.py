from functools import cache

import torch
from transformers import AutoImageProcessor, AutoModelForDepthEstimation
import numpy as np
from fastapi import HTTPException


class DepthEstService:
    """
    Metric depth (meters per pixel) with Depth-Anything-V2 (indoor, small).

    TODO: downscale large images (shorter side 518 px, the model's input size)
    before inference to save time on the server.
    """

    def chooseDevice(self):
        """
        Determine the device type to run the model in either dev/production
        """
        if torch.cuda.is_available():
            return "cuda"
        elif torch.backends.mps.is_available():
            return "mps"
        else: return "cpu"

    def __init__(self):
        """
        Load the models once to be used in the other functions
        """
        self.device = self.chooseDevice()
        if self.device == "cuda":
            self.dtype = torch.float16
        else:
            self.dtype = torch.float32

        self.model = AutoModelForDepthEstimation.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf", dtype=self.dtype ).to(self.device).eval()
        self.image_processor = AutoImageProcessor.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf")
    
    
    def runModel(self ,image):
        """
        This functions runs the pre trained model. For the EC2 instance items from model should be on Cuda to use the GPU 
        """
        try:            
            #Run only the inference part of the model
            inputs= self.image_processor(images=image, return_tensors="pt").to(self.device, self.dtype )
            with torch.no_grad():   
                outputs= self.model(**inputs)
            
            result = self.image_processor.post_process_depth_estimation(
            outputs,
            target_sizes=[(image.height, image.width)],
            )
            return result[0]["predicted_depth"].float().cpu().numpy()
            
        except Exception as error:
            print(error)
            raise HTTPException(status_code=500, detail=str(error)) 
        
    def getObjectdepths(self, objects, grid: np.ndarray):
        """
        Adds "depth" (meters) to each of YoloService's objects, from the depth
        map runModel returned for the same image.
        """
        height, width = grid.shape[:2]
        measured = []
        for obj in objects:
            # The object's box, inside the picture (a box can reach a pixel past
            # the edge, and a negative index would count from the other side)
            # and at least one pixel wide and tall: an empty slice has no percentile.
            x1, y1, x2, y2 = map(int, obj["coords"])
            x1 = min(max(x1, 0), width - 1)
            y1 = min(max(y1, 0), height - 1)
            x2 = min(max(x2, x1 + 1), width)
            y2 = min(max(y2, y1 + 1), height)
            # The 25th percentile: the nearer part of the box, not the background behind the object.
            objdepth = float(np.percentile(grid[y1:y2, x1:x2], 25))
            # A distance that isn't a number can't be sent (JSON has no NaN) or said.
            if not np.isfinite(objdepth):
                continue
            obj["depth"] = objdepth
            measured.append(obj)
        return measured


@cache
def shared_depth_service() -> DepthEstService:
    """
    The one DepthEstService the routes share (/depth and hazard detection), so
    the model is loaded once.
    """
    return DepthEstService()
