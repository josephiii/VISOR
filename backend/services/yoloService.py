from ultralytics import YOLO
from pathlib import Path
import os
 
class YoloService:
    def __init__(self):
        self.model =  YOLO("yolo11n.pt") # n for nano, s small, m medium, l large, x xl
    
    def fineTuneModel(self):
        #freeze backbone train top layers + new classes
        #unfreeze and train backbone to adapt to classes
        return
    
    def runModel(self, image:str):
        res = self.model(image)
        return res
    def MergeClasses():
        '''
        There are cases where the model returns separate objects when those separat objects make up one object. For example, 
        a park bench that has a table and a bench on each side could possibly return 3 different benches. In such cases,
        this function merges these objects into one object by determining how much their boxes overlap, and how close
        they are in proximity. 
        '''
##TEST
'''
if __name__=="__main__":
    path = (Path(__file__).resolve())
    path = os.path.join(path, "..", "..", "tests")
    print(os.path.abspath(path))
    service= YoloService()
    res = service.runModel(os.path.abspath(os.path.join(path, "qwen2-vl/images.jpg")))
    r = res[0]
    r.save("out.jpg")
    for box in r.boxes:
        cls_id = int(box.cls[0])
        label  = r.names[cls_id]
        conf   = float(box.conf[0])
        xyxy   = box.xyxy[0].tolist()   # [x1, y1, x2, y2] pixel corners
        print(f"{label}  {conf:.2f}  {xyxy}")
'''