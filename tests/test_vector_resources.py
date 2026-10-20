"""Real resource validation; no Android device or optional parser dependency required."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from scripts.verify_vector_resources import ANDROID, validate_path, verify

ROOT = Path(__file__).resolve().parents[1]


class VectorResourcesTest(unittest.TestCase):
    def test_all_shipped_android_vector_paths_have_valid_command_arguments(self):
        report = verify(ROOT / "app/src/main/res")
        self.assertGreaterEqual(report["vectors"], 44)
        self.assertFalse(report["errors"], "\n".join(report["errors"]))

    def test_old_broken_coordinate_conversion_is_rejected(self):
        for data in ("M4 L17 L10 L11 L4 L5", "M6 L3 L20 L12 L6 L21 L6 L3 Z"):
            with self.subTest(data=data), self.assertRaises(ValueError):
                validate_path(data)

    def test_supported_curves_relative_moves_and_exponent_numbers(self):
        for data in ("M4,17 L10,11 L4,5", "M.1,-2e-3c1 2 3 4 5 6z", "M0 0A1 2 0 0 1 3 4", "m0 0 1 2h3v4s1 2 3 4q1 2 3 4t5 6"):
            with self.subTest(data=data):
                validate_path(data)

    def test_invalid_commands_and_incomplete_curves_are_rejected(self):
        for data in ("L1 2", "M0 0 L1", "M0 0 Z1 2", "M0 0 Q1 2 3", "M0 0 A1 2 0 2 1 3 4", "M0 0 U1 2", "M0 0 @ 1 2"):
            with self.subTest(data=data), self.assertRaises(ValueError):
                validate_path(data)

    def test_polylines_and_polygons_preserve_the_original_lucide_vertices(self):
        checked = 0
        for source in sorted((ROOT / "third-party/lucide/icons").glob("*.svg")):
            shapes = list(ET.parse(source).getroot())
            target = ROOT / "app/src/main/res/drawable" / ("lucide_" + source.stem.replace("-", "_") + ".xml")
            paths = list(ET.parse(target).getroot())
            self.assertEqual(len(shapes), len(paths), source.name)
            for shape, path in zip(shapes, paths):
                kind = shape.tag.rsplit("}", 1)[-1]
                if kind not in ("polyline", "polygon"):
                    continue
                numbers = re.findall(r"[-+]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)", shape.attrib["points"])
                self.assertEqual(len(numbers) % 2, 0)
                expected = "M" + ",".join(numbers[:2]) + "".join(" L" + ",".join(numbers[i:i+2]) for i in range(2, len(numbers), 2))
                if kind == "polygon":
                    expected += " Z"
                self.assertEqual(expected, path.get(ANDROID + "pathData"), source.name)
                checked += 1
        self.assertEqual(8, checked)
