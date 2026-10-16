"""Run hazard detection on one image, without the server: YOLO finds the
objects and the depth model says how far away each one is, as
POST /yolo/runHazardDetection does.

From backend/:
    python -m scripts.try_yolo [image]

The first run downloads and caches the depth model. Writes the image with
YOLO's boxes drawn on it to yolo_out.jpg in the current folder.
"""

import sys

from PIL import Image

from services.DepthEstService import DepthEstService
from services.YoloService import YoloService


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "samples/depth/room.jpg"
    image = Image.open(path).convert("RGB")
    yolo = YoloService()
    depth = DepthEstService()
    print("Models loaded.")

    objects = yolo.runModel(image)
    if not objects:
        print("No objects found.")
    else:
        depth.getObjectdepths(objects, depth.runModel(image))
        for obj in sorted(objects, key=lambda o: o["depth"]):
            print(f'{obj["object"]}: {obj["depth"]:.2f} m (confidence {obj["conf"]:.2f})')

    yolo.model(image, verbose=False)[0].save(filename="yolo_out.jpg")
    print("Wrote yolo_out.jpg")


if __name__ == "__main__":
    main()
