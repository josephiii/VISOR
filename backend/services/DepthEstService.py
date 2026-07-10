import torch
from transformers import AutoImageProcessor, AutoModelForDepthEstimation
from PIL import Image

class Depth:
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

        self.model = AutoModelForDepthEstimation.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf", torch_dtype=self.dtype ).to(self.device).eval()
        self.image_processor = AutoImageProcessor.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf")
    
    
    def compressImage(self):
        try:
            x=0
        except Exception as error:
            print(error)
        return x
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
            return error