"""
Per-frame filtering for the hazard watch (POST /yolo/runHazardDetection?watch=1).

The hazard watch sends a frame about twice a second and the phone decides what
to say (frontend hazards/AlertPolicy). Everything here needs only the one frame,
so it belongs on the server: it is tied to the model (confidence), it cuts what
is sent back, and it lets the depth model be skipped when nothing is left.
What needs earlier frames (matching objects across frames, cooldowns, what was
already said) stays on the phone, so the server keeps no per-user state.

Pure Python on purpose, so tests/test_hazard_watch_filter.py runs without torch.
"""

from __future__ import annotations

from dataclasses import dataclass, field

# What YOLOE is told to look for (YoloService), and the classes of the dataset it
# is fine-tuned on (datasets/visor_hazards.yaml): things in the way while walking,
# things that move toward you, and the drops YOLO11 has no word for. Curated:
# a vague word causes false alarms (the bench's "fire" matched a red hydrant), and
# a missing one is never found (it missed a bed and a couch that weren't listed).
HAZARD_WORDS = [
    "person", "bicycle", "car", "motorcycle", "bus", "truck", "scooter", "stroller", "dog",
    "stairs", "curb", "open manhole", "hole in the ground", "pole", "bollard",
    "traffic cone", "barrier", "fence", "wet floor sign", "fire hydrant", "trash can",
    "bench", "chair", "table", "couch", "bed", "door", "glass door", "tree",
]

# YOLO11's COCO classes worth warning about while walking. Leaves out what can't
# be walked into (tv, cell phone, cup, book...). With YOLOE every class is a
# hazard word, so none are filtered (YOLOE_WATCH_CONFIG).
DEFAULT_HAZARD_CLASSES = frozenset({
    "person", "bicycle", "car", "motorcycle", "bus", "truck", "train",
    "fire hydrant", "stop sign", "parking meter", "bench",
    "dog", "cat", "horse", "cow",
    "suitcase", "chair", "couch", "potted plant", "bed", "dining table",
    "toilet", "refrigerator",
})


@dataclass(frozen=True)
class WatchConfig:
    # Below this YOLO is guessing. The bench's false "dog" (a shoe) was 0.28.
    min_confidence: float = 0.35
    # None keeps every class the model knows.
    hazard_classes: frozenset | None = field(default=DEFAULT_HAZARD_CLASSES)
    # Boxes smaller than this share of the frame are far away or noise.
    min_box_fraction: float = 0.01
    # The phone's farthest alert band ends at 4 m. A little margin for depth noise.
    max_range_m: float = 5.0
    # Vehicles are kept farther out: one closing in fast is critical before it is
    # near (the phone judges that from its box growing, frame to frame).
    vehicle_classes: frozenset = frozenset({"car", "bus", "truck", "motorcycle", "bicycle", "train", "scooter"})
    vehicle_range_m: float = 15.0
    # Two boxes of one class are one object when this much of the smaller one
    # lies inside the larger (YOLO's own NMS leaves nested and split boxes).
    merge_overlap: float = 0.6


WATCH_CONFIG = WatchConfig()
# YOLOE only finds what it was told to (or trained on), all of it hazards.
YOLOE_WATCH_CONFIG = WatchConfig(hazard_classes=None)


def _area(box):
    x1, y1, x2, y2 = box
    return max(0.0, x2 - x1) * max(0.0, y2 - y1)


def _overlap_of_smaller(a, b):
    """How much of the smaller box lies inside the other, from 0 to 1."""
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    inter = _area((ix1, iy1, ix2, iy2))
    smaller = min(_area(a), _area(b))
    return inter / smaller if smaller > 0 else 0.0


def _merge_same_object(objects, overlap):
    """One box per object: same-class boxes that overlap enough become their union."""
    kept = []
    for obj in sorted(objects, key=lambda o: o["conf"], reverse=True):
        for other in kept:
            if other["object"] == obj["object"] and _overlap_of_smaller(other["coords"], obj["coords"]) >= overlap:
                a, b = other["coords"], obj["coords"]
                other["coords"] = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])]
                break
        else:
            kept.append(dict(obj, coords=list(obj["coords"])))
    return kept


def before_depth(objects, image_size, config=WATCH_CONFIG):
    """
    YoloService's objects worth measuring: confident, a hazard class, big
    enough, one box per object. Runs before the depth model, which is skipped
    when nothing is left. image_size is (width, height) in pixels.
    """
    width, height = image_size
    frame_area = width * height
    kept = [
        obj for obj in objects
        if obj["conf"] >= config.min_confidence
        and (config.hazard_classes is None or obj["object"] in config.hazard_classes)
        and frame_area > 0 and _area(obj["coords"]) / frame_area >= config.min_box_fraction
    ]
    return _merge_same_object(kept, config.merge_overlap)


def after_depth(objects, config=WATCH_CONFIG):
    """Drops what is too far away to warn about. Vehicles have a longer range."""
    return [
        obj for obj in objects
        if obj["depth"] <= (config.vehicle_range_m if obj["object"] in config.vehicle_classes else config.max_range_m)
    ]
