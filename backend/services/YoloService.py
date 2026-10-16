from pathlib import Path

from ultralytics import YOLO

# backend/weights/, wherever the server is started from.
WEIGHTS = Path(__file__).resolve().parent.parent / "weights" / "yolo11n.pt"


class YoloService:
    """
    Object detection with YOLO11 (the 80 everyday COCO classes: person, car,
    chair, ...). Hazard detection (routes/YoloRoutes.py) adds how far away each
    object is with DepthEstService.

    TODO: fine-tune on the hazards VISOR's users need to hear about, as laid out
    in documentation/research/yolo-research.md: freeze the backbone and train the
    top layers on the new classes, then unfreeze the backbone to adapt it.
    TODO: merge separate boxes that make up one object. A park bench with a table
    between two seats can come back as three benches; merge boxes by how much
    they overlap and how close together they are.
    """

    def __init__(self):
        # n for nano; s small, m medium, l large and x extra large are slower.
        self.model = YOLO(str(WEIGHTS))

    def runModel(self, image):
        """
        Finds the objects in an image (a PIL image or a path). Each comes back as
        { object, classID, coords: [x1, y1, x2, y2] in pixels, conf }.
        """
        result = self.model(image, verbose=False)[0]
        return [
            {
                "object": result.names[int(box.cls)],
                "classID": int(box.cls),
                "coords": box.xyxy.tolist()[0],
                "conf": float(box.conf),
            }
            for box in result.boxes
        ]
