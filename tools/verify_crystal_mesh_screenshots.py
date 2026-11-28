#!/usr/bin/env python3
"""Fail-closed pixel evidence that the seeded BETA26 crystal shards reached the real client framebuffer.

This proves visible aqua-tinted shard regions, not visual identity against a reference screenshot.
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
MIN_AQUA_PIXELS = 40
MIN_AQUA_RATIO = 0.60
MIN_VISIBLE_SHARD_COMPONENTS = 3
MAX_COMPONENT_CENTER_OFFSET = 96.0


def analyze_rgb_pair(before: bytes, after: bytes, width: int, height: int,
                     threshold: int = DIFF_THRESHOLD,
                     viewport: tuple[int, int, int, int] | None = None) -> dict:
    if width <= 0 or height <= 0 or len(before) != width * height * 3 or len(after) != len(before):
        raise ValueError("RGB dimensions do not match the supplied buffers")
    if viewport is None:
        viewport = (0, 0, width, height)
    vx, vy, viewport_width, viewport_height = viewport
    if (vx < 0 or vy < 0 or viewport_width <= 0 or viewport_height <= 0
            or vx + viewport_width > width or vy + viewport_height > height):
        raise ValueError("viewport must be positive and fully contained within the screenshot")
    side = min(ROI_SIZE, viewport_width, viewport_height)
    x0 = vx + (viewport_width - side) // 2
    y0 = vy + (viewport_height - side) // 2
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
    components = []
    for start, active in enumerate(mask):
        if not active or visited[start]:
            continue
        stack = [start]
        visited[start] = 1
        count = 0
        aqua_count = 0
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

            # This smoke deliberately places crystal_aqua. Require the actual
            # aqua tint in changed pixels, not a larger unrelated lighting or
            # skybox delta elsewhere in the ROI.
            pixel_offset = ((y0 + y) * width + (x0 + x)) * 3
            red, green, blue = after[pixel_offset:pixel_offset + 3]
            if green >= red * 1.35 + 8 and blue >= green * 1.05 + 4:
                aqua_count += 1

            for next_y in range(max(0, y - 1), min(side, y + 2)):
                row = next_y * side
                for next_x in range(max(0, x - 1), min(side, x + 2)):
                    neighbour = row + next_x
                    if mask[neighbour] and not visited[neighbour]:
                        visited[neighbour] = 1
                        stack.append(neighbour)

        box = (x0 + min_x, y0 + min_y, x0 + max_x, y0 + max_y)
        if count > largest:
            largest = count
            largest_box = box
        center_x = (box[0] + box[2]) / 2.0
        center_y = (box[1] + box[3]) / 2.0
        viewport_center_x = vx + viewport_width / 2.0
        viewport_center_y = vy + viewport_height / 2.0
        component_width = box[2] - box[0] + 1
        component_height = box[3] - box[1] + 1
        components.append({
            "pixels": count,
            "aqua_pixels": aqua_count,
            "aqua_ratio": aqua_count / count,
            "bbox": box,
            "width": component_width,
            "height": component_height,
            "center_offset": math.hypot(
                center_x - viewport_center_x,
                center_y - viewport_center_y,
            ),
        })

    # The source OBJ is eight separate shard groups; after seeded selection,
    # its visible shards normally remain several disconnected screen regions.
    # Do not let a larger non-aqua lighting delta mask those real shard regions.
    visible_shards = [
        component for component in components
        if component["pixels"] >= MIN_COMPONENT_PIXELS
        and component["width"] >= MIN_COMPONENT_EDGE
        and component["height"] >= MIN_COMPONENT_EDGE
        and component["center_offset"] <= MAX_COMPONENT_CENTER_OFFSET
        and component["aqua_pixels"] >= MIN_AQUA_PIXELS
        and component["aqua_ratio"] >= MIN_AQUA_RATIO
    ]
    return {
        "changed": changed,
        "largest": largest,
        "bbox": largest_box,
        "visible_shards": visible_shards,
        "viewport": (vx, vy, viewport_width, viewport_height),
    }


def has_visible_mesh(metrics: dict, width: int, height: int) -> bool:
    return (
        metrics["changed"] >= MIN_CHANGED_PIXELS
        and len(metrics.get("visible_shards", ())) >= MIN_VISIBLE_SHARD_COMPONENTS
    )


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


def verify_pair(label: str, before_path: Path, after_path: Path,
                viewport: tuple[int, int, int, int] | None = None) -> dict:
    before_width, before_height, before = load_rgb(before_path)
    after_width, after_height, after = load_rgb(after_path)
    if (before_width, before_height) != (after_width, after_height):
        raise SystemExit(f"CRYSTAL_MESH_PIXELS=FAIL label={label} reason=dimension-mismatch")
    metrics = analyze_rgb_pair(before, after, before_width, before_height, viewport=viewport)
    if not has_visible_mesh(metrics, before_width, before_height):
        raise SystemExit(
            f"CRYSTAL_MESH_PIXELS=FAIL label={label} changed={metrics['changed']} "
            f"visible_shards={len(metrics.get('visible_shards', ())) } "
            f"largest={metrics['largest']} bbox={metrics['bbox']}"
        )
    box = metrics["bbox"]
    aqua_pixels = sum(component["aqua_pixels"] for component in metrics["visible_shards"])
    print(
        f"CRYSTAL_MESH_PIXELS=PASS label={label} changed={metrics['changed']} "
        f"visible_shards={len(metrics['visible_shards'])} aqua_pixels={aqua_pixels} "
        f"largest={metrics['largest']} bbox={box[0]},{box[1]},{box[2]},{box[3]}"
    )
    return metrics


def run_synthetic_tests() -> None:
    width = height = 384
    base = bytes([80]) * (width * height * 3)

    def paint(image: bytearray, image_width: int, x: int, y: int,
              rect_width: int, rect_height: int,
              color: tuple[int, int, int]) -> None:
        row = bytes(color) * rect_width
        for row_y in range(y, y + rect_height):
            offset = (row_y * image_width + x) * 3
            image[offset:offset + len(row)] = row

    def add_three_shards(image: bytearray, image_width: int,
                         center_x: int, center_y: int,
                         color: tuple[int, int, int] = (50, 80, 95)) -> None:
        for dx, dy in ((-22, -14), (0, 16), (22, -14)):
            paint(image, image_width, center_x + dx - 6, center_y + dy - 6,
                  12, 12, color)

    # Three low-contrast, disconnected aqua shards are a valid source mesh.
    low_contrast = bytearray(base)
    add_three_shards(low_contrast, width, width // 2, height // 2)
    positive = analyze_rgb_pair(base, bytes(low_contrast), width, height)
    assert has_visible_mesh(positive, width, height), positive
    assert len(positive["visible_shards"]) == 3, positive

    identical = analyze_rgb_pair(base, base, width, height)
    assert not has_visible_mesh(identical, width, height), identical

    outside = bytearray(base)
    paint(outside, width, 8, 8, 12, 12, (255, 255, 255))
    outside_result = analyze_rgb_pair(base, bytes(outside), width, height)
    assert not has_visible_mesh(outside_result, width, height), outside_result

    scattered = bytearray(base)
    for index in range(100):
        x = 80 + (index % 10) * 3
        y = 80 + (index // 10) * 3
        paint(scattered, width, x, y, 1, 1, (50, 80, 95))
    scattered_result = analyze_rgb_pair(base, bytes(scattered), width, height)
    assert not has_visible_mesh(scattered_result, width, height), scattered_result

    tiny = bytearray(base)
    paint(tiny, width, 190, 190, 5, 5, (50, 80, 95))
    tiny_result = analyze_rgb_pair(base, bytes(tiny), width, height)
    assert not has_visible_mesh(tiny_result, width, height), tiny_result

    single_shard = bytearray(base)
    paint(single_shard, width, 186, 186, 12, 12, (50, 80, 95))
    single_result = analyze_rgb_pair(base, bytes(single_shard), width, height)
    assert not has_visible_mesh(single_result, width, height), single_result

    # Blue-only UI/background changes must not impersonate crystal_aqua tint.
    blue_only = bytearray(base)
    add_three_shards(blue_only, width, width // 2, height // 2, (50, 70, 120))
    blue_result = analyze_rgb_pair(base, bytes(blue_only), width, height)
    assert not has_visible_mesh(blue_result, width, height), blue_result

    # A larger unrelated dark/light component must not mask three real shards.
    background_noise = bytearray(base)
    add_three_shards(background_noise, width, width // 2, height // 2)
    paint(background_noise, width, 64, 64, 80, 20, (10, 10, 20))
    background_result = analyze_rgb_pair(base, bytes(background_noise), width, height)
    assert background_result["largest"] > max(
        component["pixels"] for component in background_result["visible_shards"]
    ), background_result
    assert has_visible_mesh(background_result, width, height), background_result

    # Xvfb's root screenshot can be larger than the Minecraft client window.
    desktop_width, desktop_height = 1280, 800
    desktop_base = bytes([80]) * (desktop_width * desktop_height * 3)
    viewport = (1, 20, 1024, 768)
    viewport_center_x = viewport[0] + viewport[2] // 2
    viewport_center_y = viewport[1] + viewport[3] // 2
    desktop_mesh = bytearray(desktop_base)
    add_three_shards(desktop_mesh, desktop_width, viewport_center_x, viewport_center_y)
    root_center_result = analyze_rgb_pair(
        desktop_base, bytes(desktop_mesh), desktop_width, desktop_height
    )
    assert not has_visible_mesh(root_center_result, desktop_width, desktop_height), root_center_result
    viewport_result = analyze_rgb_pair(
        desktop_base, bytes(desktop_mesh), desktop_width, desktop_height, viewport=viewport
    )
    assert has_visible_mesh(viewport_result, desktop_width, desktop_height), viewport_result

    desktop_overlay = bytearray(desktop_base)
    add_three_shards(desktop_overlay, desktop_width, 1100, viewport_center_y)
    overlay_result = analyze_rgb_pair(
        desktop_base, bytes(desktop_overlay), desktop_width, desktop_height, viewport=viewport
    )
    assert not has_visible_mesh(overlay_result, desktop_width, desktop_height), overlay_result

    try:
        analyze_rgb_pair(
            desktop_base, desktop_base, desktop_width, desktop_height,
            viewport=(1200, 0, 200, 100),
        )
    except ValueError:
        pass
    else:
        raise AssertionError("out-of-bounds screenshot viewport was accepted")

    print("CRYSTAL_MESH_PIXEL_LOGIC_TESTS=PASS cases=12")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--viewport", nargs=4, type=int,
                        metavar=("X", "Y", "WIDTH", "HEIGHT"))
    parser.add_argument("--pair", nargs=3, action="append", required=True,
                        metavar=("LABEL", "BEFORE_PNG", "AFTER_PNG"))
    args = parser.parse_args()
    viewport = tuple(args.viewport) if args.viewport is not None else None
    for label, before, after in args.pair:
        verify_pair(label, Path(before), Path(after), viewport=viewport)


if __name__ == "__main__":
    main()
