"""Run the depth model on one image, without the server.

From backend/:
    python -m scripts.try_depth [image]

The first run downloads and caches the model. Writes a grayscale preview of
the depth map (near = dark) to depth_out.png in the current folder.
"""

import sys

import numpy as np
from PIL import Image

from services.DepthEstService import DepthEstService


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "samples/depth/room.jpg"
    depth = DepthEstService()
    print("Model loaded.")

    depth_map = depth.runModel(Image.open(path).convert("RGB"))
    print(depth_map.shape, "min", depth_map.min(), "max", depth_map.max(), "(meters)")

    norm = ((depth_map - depth_map.min()) / (depth_map.max() - depth_map.min()) * 255).astype(np.uint8)
    Image.fromarray(norm).save("depth_out.png")
    print("Wrote depth_out.png")


if __name__ == "__main__":
    main()
