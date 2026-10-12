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
        for obj in objects:
            #Get df ox x,y
            x1, y1, x2, y2 = map(int, obj["coords"])
            rawObjectDepth = grid[y1:y2, x1:x2]
            #Get 25% depth
            objdepth = np.percentile(rawObjectDepth, 25)
            obj["depth"] = float(objdepth)
        return objects
