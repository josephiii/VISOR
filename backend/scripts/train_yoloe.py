"""
Fine-tunes YOLOE on VISOR's own hazard photos, so the hazard watch finds what
matters while walking (stairs, curbs, holes, poles...) better than YOLOE's
text prompts alone. Run on the GPU server, from backend/:

  1. Label:  python -m scripts.train_yoloe label --images datasets/visor_hazards/images/train
     YOLOE (told what to look for with HAZARD_WORDS) writes a first guess of the
     boxes, in YOLO format, next to each photo's folder (../labels/...). Check and
     fix every one in a labeling tool (CVAT, Label Studio, Roboflow) before training:
     a wrong box teaches the wrong thing.
  2. Train:  python -m scripts.train_yoloe train --epochs 80
     Starts from the YOLOE file, trains on datasets/visor_hazards.yaml, and copies
     the best result to weights/yoloe_visor.pt, which YoloService then uses.
     --linear trains only the class head (quick, a few hundred photos is enough
     to start); without it the whole model adapts (better, needs more photos).
  3. Check:  python -m scripts.train_yoloe val
     Accuracy (mAP) of weights/yoloe_visor.pt on the validation photos.

Detection boxes are enough: a bounding box per hazard, no outlines needed.
How much data: a few hundred varied photos per class for a pilot, taken with
the glasses from walking height, indoors and out, day and night; Ultralytics
suggests 1,500+ per class for production. Keep some everyday COCO-style photos
of people and vehicles in the set, or the model forgets how to find them.

The fine-tuning recipe follows Ultralytics' YOLOE documentation
(https://docs.ultralytics.com/models/yoloe/): YOLOEPETrainer for detection,
starting from the segmentation weights.
"""

import argparse
import shutil
from pathlib import Path

from services.HazardWatchFilter import HAZARD_WORDS

BACKEND = Path(__file__).resolve().parent.parent
BASE_WEIGHTS = BACKEND / "weights" / "yoloe-26s-seg.pt"
# The same model without its segmentation head: VISOR only needs boxes.
DETECT_CONFIG = "yoloe-26s.yaml"
OUTPUT = BACKEND / "weights" / "yoloe_visor.pt"
DATA = BACKEND / "datasets" / "visor_hazards.yaml"


def label(images: Path, conf: float):
    """First-guess labels for every photo in images/, for a person to check."""
    from ultralytics import YOLOE

    model = YOLOE(str(BASE_WEIGHTS))
    model.set_classes(HAZARD_WORDS)
    # YOLO layout: datasets/x/images/train/a.jpg -> datasets/x/labels/train/a.txt
    labels = Path(str(images).replace("/images", "/labels", 1))
    labels.mkdir(parents=True, exist_ok=True)
    count = 0
    for result in model.predict(source=str(images), conf=conf, stream=True, verbose=False):
        lines = [
            f"{int(cls)} {x:.6f} {y:.6f} {w:.6f} {h:.6f}"
            for cls, (x, y, w, h) in zip(result.boxes.cls.tolist(), result.boxes.xywhn.tolist())
        ]
        (labels / (Path(result.path).stem + ".txt")).write_text("\n".join(lines))
        count += 1
    print(f"Wrote first-guess labels for {count} photos to {labels}. Check every one before training.")


def train(epochs: int, batch: int, linear: bool, device: str):
    from ultralytics import YOLOE
    from ultralytics.models.yolo.yoloe import YOLOEPETrainer

    model = YOLOE(DETECT_CONFIG)
    model.load(str(BASE_WEIGHTS))

    freeze = None
    if linear:
        # Linear probing: everything frozen but the head's class branch (cv3),
        # which learns the new classes from VISOR's photos.
        head = len(model.model.model) - 1
        freeze = [str(i) for i in range(head)]
        freeze += [f"{head}.{name}" for name, _ in model.model.model[-1].named_children() if "cv3" not in name]

    results = model.train(
        data=str(DATA),
        epochs=epochs,
        batch=batch,
        imgsz=640,  # what the hazard watch sends
        close_mosaic=10,
        optimizer="AdamW",
        lr0=1e-3,
        warmup_bias_lr=0.0,
        weight_decay=0.025,
        momentum=0.9,
        device=device,
        freeze=freeze,
        trainer=YOLOEPETrainer,
        project=str(BACKEND / "runs"),
        name="yoloe_visor",
    )
    best = Path(results.save_dir) / "weights" / "best.pt"
    shutil.copy(best, OUTPUT)
    print(f"Copied {best} to {OUTPUT}. Restart the server to use it.")


def val(device: str):
    from ultralytics import YOLOE

    metrics = YOLOE(str(OUTPUT)).val(data=str(DATA), imgsz=640, device=device)
    print(f"mAP50-95 {metrics.box.map:.3f}, mAP50 {metrics.box.map50:.3f}")
    for name, value in zip(metrics.names.values(), metrics.box.maps):
        print(f"  {name:20s} {value:.3f}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    steps = parser.add_subparsers(dest="step", required=True)

    p = steps.add_parser("label", help="first-guess labels for photos, to check by hand")
    p.add_argument("--images", type=Path, required=True)
    p.add_argument("--conf", type=float, default=0.25)

    p = steps.add_parser("train", help="fine-tune YOLOE on datasets/visor_hazards.yaml")
    p.add_argument("--epochs", type=int, default=80)
    p.add_argument("--batch", type=int, default=16)
    p.add_argument("--linear", action="store_true", help="train only the class head")
    p.add_argument("--device", default="0")

    p = steps.add_parser("val", help="accuracy of weights/yoloe_visor.pt")
    p.add_argument("--device", default="0")

    args = parser.parse_args()
    if args.step == "label":
        label(args.images, args.conf)
    elif args.step == "train":
        train(args.epochs, args.batch, args.linear, args.device)
    else:
        val(args.device)


if __name__ == "__main__":
    main()
