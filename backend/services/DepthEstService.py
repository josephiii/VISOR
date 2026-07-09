import torch
from transformers import AutoImageProcessor, AutoModelForDepthEstimation, pipe
from PIL import Image

class Depth:
    def compressImage():
        try:
            x=0
        except Exception as error:
            print(error)
        return x
    def runModel(image):
        """
        This functions runs the pre trained model. For the EC2 instance items from model should be on Cuda to use the GPU 
        """
        try:
            x=0
            #Load and create model instance
            model = AutoModelForDepthEstimation.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf", device_map="auto", torch_dtype=torch.float16 ).eval()
            #Load image (call compressImage())
            image_processor = AutoImageProcessor.from_pretrained("depth-anything/Depth-Anything-V2-Metric-Indoor-Small-hf")

            #Run only the inference part of the model
            inputs= image_processor(images=image, return_tensors="pt").to( device=model.device, dtype=torch.float16)
            with torch.no_grad():   
                outputs= model(**inputs)
            
            result = image_processor.post_process_depth_estimation(
            outputs,
            target_sizes=[(image.height, image.width)],
            )
            return result[0]["predicted_depth"].float().cpu().numpy()
            
        except Exception as error:
            print(error)
        return x
    