#!/usr/bin/env python3
"""Fail-closed pixel evidence that the seeded BETA26 crystal mesh reached the real client framebuffer.

This proves visibility, not visual identity against a reference screenshot.
Exact OBJ bytes, group names, seeded selection and face transforms are checked separately.
"""
from __future__ import annotations

import argparse
import math
import subprocess
from pathlib import Path

ROI_SIZE = 256
DIFF_THRESHOLD = 12
MIN_CHANGED_PIXELS = 80
MIN_COMPONENT_PIXELS = 40
MIN_COMPONENT_EDGE = 6
MAX_COMPONENT_CENTER_OFFSET = 96.0


def analyze_rgb_pair(before: bytes, after: bytes, width: int, height: int,
                     threshold: int = DIFF_THRESHOLD) -> dict:
    if width <= 0 or height <= 0 or len(before) != width * height * 3 or len(after) != len(before):
        raise ValueError("RGB dimensions do not match the supplied buffers")
    side = min(ROI_SIZE, width, height)
    x0 = (width - side) // 2
    y0 = (height - side) // 2
    mask = bytearray(side * side)
    changed = 0
    for y in range(side):
        source_row = ((y0 + y) * width + x0) * 3
        target_row = y * side
        for x in range(side):
            offset = source_row + x * 3
            if max(
                abs(before[offset] - after[offset]),
                abs(before[offset + 1] - after[offset + 1]),
                abs(before[offset + 2] - after[offset + 2]),
            ) > threshold:
                mask[target_row + x] = 1
                changed += 1

    visited = bytearray(len(mask))
    largest = 0
    largest_box = None
    for start, active in enumerate(mask):
        if not active or visited[start]:
            continue
        stack = [start]
        visited[start] = 1
        count = 0
        min_x = max_x = start % side
        min_y = max_y = start // side
        while stack:
            index = stack.pop()
            x = index % side
            y = index // side
            count += 1
            min_x = min(min_x, x)
            max_x = max(max_x, x)
            min_y = min(min_y, y)
            max_y = max(max_y, y)
            for next_y in range(max(0, y - 1), min(side, y + 2)):
                row = next_y * side
                for next_x in range(max(0, x - 1), min(side, x + 2)):
                    neighbour = row + next_x
                    if mask[neighbour] and not visited[neighbour]:
                        visited[neighbour] = 1
                        stack.append(neighbour)
        if count > largest:
            largest = count
            largest_box = (x0 + min_x, y0 + min_y, x0 + max_x, y0 + max_y)

    return {"changed": changed, "largest": largest, "bbox": largest_box}


def has_visible_mesh(metrics: dict, width: int, height: int) -> bool:
    box = metrics["bbox"]
    if metrics["changed"] < MIN_CHANGED_PIXELS or metrics["largest"] < MIN_COMPONENT_PIXELS or box is None:
        return False
    min_x, min_y, max_x, max_y = box
    if max_x - min_x + 1 < MIN_COMPONENT_EDGE or max_y - min_y + 1 < MIN_COMPONENT_EDGE:
        return False
    center_x = (min_x + max_x) / 2.0
    center_y = (min_y + max_y) / 2.0
    return math.hypot(center_x - width / 2.0, center_y - height / 2.0) <= MAX_COMPONENT_CENTER_OFFSET


def load_rgb(path: Path) -> tuple[int, int, bytes]:
    identify = subprocess.run(
        ["identify", "-format", "%w %h", str(path)],
        check=True, capture_output=True, text=True,
    )
    width, height = map(int, identify.stdout.split())
    convert = subprocess.run(
        ["convert", str(path), "-alpha", "off", "-depth", "8", "RGB:-"],
        check=True, capture_output=True,
    )
    expected = width * height * 3
    if len(convert.stdout) != expected:
        raise RuntimeError(f"{path}: expected {expected} RGB bytes, got {len(convert.stdout)}")
    return width, height, convert.stdout


def verify_pair(label: str, before_path: Path, after_path: Path) -> dict:
    before_width, before_height, before = load_rgb(before_path)
    after_width, after_height, after = load_rgb(after_path)
    if (before_width, before_height) != (after_width, after_height):
        raise SystemExit(f"CRYSTAL_MESH_PIXELS=FAIL label={label} reason=dimension-mismatch")
    metrics = analyze_rgb_pair(before, after, before_width, before_height)
    if not has_visible_mesh(metrics, before_width, before_height):
        raise SystemExit(
            f"CRYSTAL_MESH_PIXELS=FAIL label={label} changed={metrics['changed']} "
            f"largest={metrics['largest']} bbox={metrics['bbox']}"
        )
    box = metrics["bbox"]
    print(
        f"CRYSTAL_MESH_PIXELS=PASS label={label} changed={metrics['changed']} "
        f"largest={metrics['largest']} bbox={box[0]},{box[1]},{box[2]},{box[3]}"
    )
    return metrics


def run_synthetic_tests() -> None:
    width = height = 384
    base = bytes([80]) * (width * height * 3)

    # Low-contrast but contiguous shard: this also covers the grey Ordo tint.
    low_contrast = bytearray(base)
    for y in range(180, 192):
        for x in range(180, 192):
            offset = (y * width + x) * 3
            low_contrast[offset:offset + 3] = bytes((95, 95, 95))
    positive = analyze_rgb_pair(base, bytes(low_contrast), width, height)
    assert has_visible_mesh(positive, width, height), positive

    identical = analyze_rgb_pair(base, base, width, height)
    assert not has_visible_mesh(identical, width, height), identical

    outside = bytearray(base)
    for y in range(8, 20):
        for x in range(8, 20):
            offset = (y * width + x) * 3
            outside[offset:offset + 3] = bytes((255, 255, 255))
    outside_result = analyze_rgb_pair(base, bytes(outside), width, height)
    assert not has_visible_mesh(outside_result, width, height), outside_result

    scattered = bytearray(base)
    for index in range(100):
        x = 80 + (index % 10) * 3
        y = 80 + (index // 10) * 3
        offset = (y * width + x) * 3
        scattered[offset:offset + 3] = bytes((120, 120, 120))
    scattered_result = analyze_rgb_pair(base, bytes(scattered), width, height)
    assert not has_visible_mesh(scattered_result, width, height), scattered_result

    tiny = bytearray(base)
    for y in range(190, 195):
        for x in range(190, 195):
            offset = (y * width + x) * 3
            tiny[offset:offset + 3] = bytes((120, 120, 120))
    tiny_result = analyze_rgb_pair(base, bytes(tiny), width, height)
    assert not has_visible_mesh(tiny_result, width, height), tiny_result
    print("CRYSTAL_MESH_PIXEL_LOGIC_TESTS=PASS cases=5")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pair", nargs=3, action="append", required=True,
                        metavar=("LABEL", "BEFORE_PNG", "AFTER_PNG"))
    args = parser.parse_args()
    for label, before, after in args.pair:
        verify_pair(label, Path(before), Path(after))


if __name__ == "__main__":
    main()
