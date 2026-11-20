from pathlib import Path

from ultralytics import YOLO

# backend/weights/, wherever the server is started from.
WEIGHTS_DIR = Path(__file__).resolve().parent.parent / "weights"

# The detector, best first; the first file present is used.
#  - yoloe_visor.pt: YOLOE fine-tuned on VISOR's own hazard photos. Its classes
#    are the ones it was trained on.
#  - yoloe-26s-seg.pt: YOLOE as Ultralytics ships it, told what to look for
#    with HAZARD_WORDS.
#  - yolo11m.pt, yolo11n.pt: the 80 COCO classes, until a YOLOE file is added.
YOLOE_FINETUNED = WEIGHTS_DIR / "yoloe_visor.pt"
YOLOE_BASE = WEIGHTS_DIR / "yoloe-26s-seg.pt"
CANDIDATES = [YOLOE_FINETUNED, YOLOE_BASE, WEIGHTS_DIR / "yolo11m.pt", WEIGHTS_DIR / "yolo11n.pt"]

# What the base YOLOE model is told to look for: things in the way while
# walking, things that move toward you, and the drops YOLO11 has no word for.
# Curated: a vague word causes false alarms, and a missing one is never found.
# The app says each name aloud as it is written here ("open manhole, 5 feet ahead").
HAZARD_WORDS = [
    "person", "bicycle", "car", "motorcycle", "bus", "truck", "scooter", "stroller", "dog",
    "stairs", "curb", "open manhole", "hole in the ground", "pole", "bollard",
    "traffic cone", "barrier", "fence", "wet floor sign", "fire hydrant", "trash can",
    "bench", "chair", "table", "couch", "bed", "door", "glass door", "tree",
]


def pick_weights(candidates=CANDIDATES):
    """The first of [candidates] that exists, or the last (yolo11n.pt) when none does."""
    return next((path for path in candidates if path.exists()), candidates[-1])


class YoloService:
    """
    Object detection: YOLOE, which can be told by name what to find (stairs,
    curb, open manhole, pole...), or YOLO11 and its 80 everyday COCO classes
    until a YOLOE file is in backend/weights/. Hazard detection
    (routes/YoloRoutes.py) adds how far away each object is with DepthEstService.

    TODO: merge separate boxes that make up one object. A park bench with a table
    between two seats can come back as three benches; merge boxes by how much
    they overlap and how close together they are.
    """

    def __init__(self):
        weights = pick_weights()
        self.weights = weights.name
        self.is_yoloe = weights.name.startswith("yoloe")
        print(f"YoloService: using {weights.name}")

        if self.is_yoloe:
            from ultralytics import YOLOE  # Ultralytics 8.4+, for the YOLOE-26 weights.

            self.model = YOLOE(str(weights))
            if weights == YOLOE_BASE:
                # Text prompts: what to look for, by name. A fine-tuned model
                # already knows its classes and keeps them.
                self.model.set_classes(HAZARD_WORDS, self.model.get_text_pe(HAZARD_WORDS))
        else:
            self.model = YOLO(str(weights))

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
