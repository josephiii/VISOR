"""From backend/:  python -m unittest discover tests"""

import unittest

from services.HazardWatchFilter import HAZARD_WORDS, YOLOE_WATCH_CONFIG, WatchConfig, after_depth, before_depth

FRAME = (640, 480)


def obj(name, coords, conf=0.8, depth=None):
    o = {"object": name, "classID": 0, "coords": coords, "conf": conf}
    if depth is not None:
        o["depth"] = depth
    return o


class BeforeDepthTest(unittest.TestCase):
    def test_drops_low_confidence(self):
        kept = before_depth([obj("person", [0, 0, 100, 200], conf=0.28), obj("chair", [200, 200, 300, 400])], FRAME)
        self.assertEqual([o["object"] for o in kept], ["chair"])

    def test_drops_classes_that_are_not_hazards(self):
        kept = before_depth([obj("tv", [0, 0, 200, 200]), obj("person", [300, 0, 400, 300])], FRAME)
        self.assertEqual([o["object"] for o in kept], ["person"])

    def test_no_class_list_keeps_every_class(self):
        kept = before_depth([obj("tv", [0, 0, 200, 200])], FRAME, WatchConfig(hazard_classes=None))
        self.assertEqual(len(kept), 1)

    def test_drops_tiny_boxes(self):
        # 20 x 20 px is 0.13% of a 640 x 480 frame.
        self.assertEqual(before_depth([obj("person", [0, 0, 20, 20])], FRAME), [])

    def test_merges_nested_boxes_of_one_class(self):
        kept = before_depth(
            [obj("bench", [100, 100, 400, 300], conf=0.9), obj("bench", [120, 120, 250, 280], conf=0.5)], FRAME
        )
        self.assertEqual(len(kept), 1)
        self.assertEqual(kept[0]["coords"], [100, 100, 400, 300])
        self.assertEqual(kept[0]["conf"], 0.9)

    def test_keeps_two_separate_objects_of_one_class(self):
        kept = before_depth([obj("chair", [0, 100, 150, 400]), obj("chair", [400, 100, 550, 400])], FRAME)
        self.assertEqual(len(kept), 2)

    def test_does_not_merge_different_classes(self):
        kept = before_depth([obj("person", [100, 50, 300, 450]), obj("bicycle", [120, 200, 300, 450])], FRAME)
        self.assertEqual(len(kept), 2)

    def test_does_not_change_the_input(self):
        objects = [obj("bench", [100, 100, 400, 300], conf=0.9), obj("bench", [90, 120, 250, 280], conf=0.5)]
        before_depth(objects, FRAME)
        self.assertEqual(objects[0]["coords"], [100, 100, 400, 300])


class YoloeTest(unittest.TestCase):
    def test_yoloe_keeps_its_own_classes(self):
        kept = before_depth([obj("open manhole", [100, 300, 300, 450])], FRAME, YOLOE_WATCH_CONFIG)
        self.assertEqual([o["object"] for o in kept], ["open manhole"])

    def test_hazard_words_are_unique_and_without_vague_ones(self):
        self.assertEqual(len(HAZARD_WORDS), len(set(HAZARD_WORDS)))
        self.assertNotIn("fire", HAZARD_WORDS)


class AfterDepthTest(unittest.TestCase):
    def test_drops_what_is_too_far(self):
        kept = after_depth([obj("bench", [0, 0, 1, 1], depth=12.0), obj("chair", [0, 0, 1, 1], depth=1.5)])
        self.assertEqual([o["object"] for o in kept], ["chair"])

    def test_keeps_vehicles_farther_out(self):
        kept = after_depth([obj("car", [0, 0, 1, 1], depth=12.0), obj("car", [0, 0, 1, 1], depth=20.0)])
        self.assertEqual([o["depth"] for o in kept], [12.0])

    def test_keeps_the_edge_of_the_range(self):
        self.assertEqual(len(after_depth([obj("chair", [0, 0, 1, 1], depth=5.0)])), 1)


if __name__ == "__main__":
    unittest.main()
