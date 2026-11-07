"""Feasibility bench for the hazard watch (documentation/research/ambient-hazard-detection.md).

Times the hazard detection pipeline VISOR has today and the candidate tools
the research document weighs, on the sample images, each resized to the
glasses' default stream size (long side 896 px), as the watch would send them:

  - YOLO11n (services/YoloService.py), at 896 px and at 640 px
  - Depth-Anything-V2 Metric Indoor Small (services/DepthEstService.py)
  - Depth-Anything-V2 Metric Outdoor Small (candidate)
  - YOLOE-26s, text-prompted with VISOR's hazard words (candidate)
  - with --vlm, Qwen3-VL-2B with a terse hazard prompt (to show why it is
    not fast enough for alerts)

From backend/:
    python -m scripts.bench_hazard_watch [--vlm]

The first run downloads the outdoor depth model and YOLOE (its text encoder
too). Writes report.json and each detector's boxes drawn on each image to
bench_out/. Run it on the machine the server will run on (the GPU instance)
for the numbers that matter; a laptop CPU gives an upper bound.
"""

import io
import json
import statistics
import sys
import time
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from transformers import AutoImageProcessor, AutoModelForDepthEstimation

from services.DepthEstService import DepthEstService
from services.YoloService import YoloService

OUT = Path("bench_out")
SAMPLES = [Path("samples/vlm"), Path("samples/depth")]
RUNS = 5

# What the watch should be able to name. YOLO11n's 80 COCO classes cover the
# first seven and a few furniture words; the rest are why YOLOE is a candidate.
HAZARD_WORDS = [
    "person", "bicycle", "car", "motorcycle", "bus", "truck", "dog",
    "stairs", "curb", "open manhole", "hole in the ground", "pole", "bollard",
    "traffic cone", "fence", "bench", "chair", "table", "door", "glass door",
    "tree", "barrier", "wet floor sign", "stroller", "scooter", "fire",
]

VLM_PROMPT = (
    "You help a person with low vision walk safely. List only hazards in or near their "
    "walking path: drops, holes, stairs, curbs, poles, vehicles, people, obstacles at head "
    "height. One per line as: hazard | left/ahead/right | near/far. If none, say NONE."
)
VLM_IMAGES = {"manhole.jpg", "edge.jpg", "dangerous.jpg", "street.jpg"}


def glasses_size(image, long_side=896):
    scale = long_side / max(image.size)
    return image.resize((round(image.width * scale), round(image.height * scale)), Image.BILINEAR)


def jpeg_bytes(image, quality):
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=quality)
    return buffer.tell()


def timed(run, runs=RUNS):
    """The answer, and the median time of [runs] calls after one warm-up, in ms."""
    run()
    times, answer = [], None
    for _ in range(runs):
        start = time.perf_counter()
        answer = run()
        if torch.cuda.is_available():
            torch.cuda.synchronize()
        times.append((time.perf_counter() - start) * 1000)
    return answer, round(statistics.median(times), 1)


class OutdoorDepth(DepthEstService):
    """DepthEstService with the outdoor (Virtual KITTI 2, up to 80 m) weights."""

    def __init__(self):
        self.device = self.chooseDevice()
        self.dtype = torch.float16 if self.device == "cuda" else torch.float32
        repo = "depth-anything/Depth-Anything-V2-Metric-Outdoor-Small-hf"
        self.model = AutoModelForDepthEstimation.from_pretrained(repo, dtype=self.dtype).to(self.device).eval()
        self.image_processor = AutoImageProcessor.from_pretrained(repo)


def boxes(result):
    return [
        {"object": result.names[int(box.cls)], "conf": round(float(box.conf), 2),
         "coords": [round(v, 1) for v in box.xyxy.tolist()[0]]}
        for box in result.boxes
    ]


def nearest_in_path(depth_map):
    """The near edge (5th percentile) of the bottom-middle of the picture: the ground just ahead."""
    h, w = depth_map.shape
    return round(float(np.percentile(depth_map[int(h * 0.6):, w // 3: 2 * w // 3], 5)), 2)


def load_yoloe():
    try:
        from ultralytics import YOLOE
        model = YOLOE("yoloe-26s-seg.pt")
        model.set_classes(HAZARD_WORDS)
        return model
    except Exception as error:  # noqa: BLE001 - a candidate that won't load is a finding, not a crash
        print(f"YOLOE unavailable: {error}")
        return None


def run_vlm(report):
    from transformers import AutoModelForImageTextToText, AutoProcessor
    model_id = "Qwen/Qwen3-VL-2B-Instruct"
    processor = AutoProcessor.from_pretrained(model_id)
    model = AutoModelForImageTextToText.from_pretrained(model_id, device_map="auto", dtype=torch.bfloat16).eval()
    for folder in SAMPLES:
        for path in sorted(folder.iterdir()):
            if path.name not in VLM_IMAGES:
                continue
            image = glasses_size(Image.open(path).convert("RGB"))
            messages = [{"role": "user", "content": [{"type": "image", "image": image},
                                                     {"type": "text", "text": VLM_PROMPT}]}]
            inputs = processor.apply_chat_template(
                messages, tokenize=True, add_generation_prompt=True, return_tensors="pt", return_dict=True
            ).to(model.device)
            start = time.perf_counter()
            with torch.inference_mode():
                ids = model.generate(**inputs, max_new_tokens=60, do_sample=False)
            ms = round((time.perf_counter() - start) * 1000)
            answer = processor.batch_decode([ids[0][len(inputs.input_ids[0]):]], skip_special_tokens=True)[0]
            report["images"][path.name]["vlm"] = {"ms": ms, "answer": answer.strip()}
            print(f"{path.name}: VLM {ms} ms\n{answer.strip()}\n")


def main():
    OUT.mkdir(exist_ok=True)
    yolo = YoloService()
    indoor = DepthEstService()
    outdoor = OutdoorDepth()
    yoloe = load_yoloe()
    print("Models loaded on", indoor.device)

    report = {"device": indoor.device, "hazard_words": HAZARD_WORDS, "images": {}}
    for folder in SAMPLES:
        for path in sorted(folder.iterdir()):
            if path.suffix.lower() not in (".jpg", ".png"):
                continue
            image = glasses_size(Image.open(path).convert("RGB"))
            small = glasses_size(image, 640)
            entry = {"size": list(image.size),
                     "jpeg_896_q85_bytes": jpeg_bytes(image, 85),
                     "jpeg_640_q70_bytes": jpeg_bytes(small, 70)}

            result, entry["yolo11n_ms"] = timed(lambda: yolo.model(image, verbose=False)[0])
            _, entry["yolo11n_640_ms"] = timed(lambda: yolo.model(small, verbose=False)[0])
            result.save(filename=str(OUT / f"{path.stem}_yolo11n.jpg"))

            indoor_map, entry["depth_indoor_ms"] = timed(lambda: indoor.runModel(image), runs=3)
            outdoor_map, entry["depth_outdoor_ms"] = timed(lambda: outdoor.runModel(image), runs=3)
            found = boxes(result)
            for obj, near_in, near_out in zip(
                found,
                indoor.getObjectdepths([dict(o) for o in found], indoor_map),
                outdoor.getObjectdepths([dict(o) for o in found], outdoor_map),
            ):
                obj["depth_indoor_m"] = round(near_in["depth"], 2)
                obj["depth_outdoor_m"] = round(near_out["depth"], 2)
            entry["yolo11n"] = found
            entry["ground_ahead_indoor_m"] = nearest_in_path(indoor_map)
            entry["ground_ahead_outdoor_m"] = nearest_in_path(outdoor_map)
            entry["depth_max_indoor_m"] = round(float(indoor_map.max()), 1)
            entry["depth_max_outdoor_m"] = round(float(outdoor_map.max()), 1)

            if yoloe is not None:
                result_e, entry["yoloe_ms"] = timed(lambda: yoloe.predict(image, verbose=False, conf=0.25)[0])
                entry["yoloe"] = boxes(result_e)
                result_e.save(filename=str(OUT / f"{path.stem}_yoloe.jpg"))

            report["images"][path.name] = entry
            print(path.name, {k: v for k, v in entry.items() if k.endswith("_ms")})
            print("   YOLO11n:", [o["object"] for o in entry["yolo11n"]])
            if "yoloe" in entry:
                print("   YOLOE:  ", [o["object"] for o in entry["yoloe"]])

    if "--vlm" in sys.argv:
        run_vlm(report)

    def median(key):
        values = [e[key] for e in report["images"].values() if key in e]
        return round(statistics.median(values), 1) if values else None

    report["median_ms"] = {key: median(key) for key in
                           ("yolo11n_ms", "yolo11n_640_ms", "depth_indoor_ms", "depth_outdoor_ms", "yoloe_ms")}
    report["median_jpeg_bytes"] = {key: median(key) for key in ("jpeg_896_q85_bytes", "jpeg_640_q70_bytes")}
    (OUT / "report.json").write_text(json.dumps(report, indent=2))
    print("\nMedian ms:", report["median_ms"])
    print("Wrote", OUT / "report.json")


if __name__ == "__main__":
    main()
