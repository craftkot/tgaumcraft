#!/usr/bin/env python3
"""Locate the visible Minecraft crosshair in an X11 root screenshot."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def locate_crosshair_rgb(
    rgb: bytes, width: int, height: int, near_x: int, near_y: int, radius: int = 48
) -> tuple[int, int]:
    if width <= 0 or height <= 0 or radius < 8:
        raise ValueError("invalid screenshot dimensions or search radius")
    if len(rgb) != width * height * 3:
        raise ValueError("RGB buffer length does not match screenshot dimensions")
    if not (0 <= near_x < width and 0 <= near_y < height):
        raise ValueError("expected window center is outside the screenshot")

    x0 = max(0, near_x - radius)
    x1 = min(width, near_x + radius + 1)
    y0 = max(0, near_y - radius)
    y1 = min(height, near_y + radius + 1)
    xs: list[int] = []
    ys: list[int] = []
    row_counts: dict[int, int] = {}
    column_counts: dict[int, int] = {}

    for y in range(y0, y1):
        row_offset = y * width * 3
        for x in range(x0, x1):
            offset = row_offset + x * 3
            red, green, blue = rgb[offset : offset + 3]
            # The vanilla crosshair is the only high-luminance object in the
            # small viewport-center search area. Ignore dark selection edges,
            # glass pixels and low-amplitude framebuffer noise.
            if red >= 180 and green >= 180 and blue >= 100:
                xs.append(x)
                ys.append(y)
                row_counts[y] = row_counts.get(y, 0) + 1
                column_counts[x] = column_counts.get(x, 0) + 1

    if len(xs) < 20:
        raise ValueError(f"too few bright crosshair pixels: {len(xs)}")
    bounds_width = max(xs) - min(xs) + 1
    bounds_height = max(ys) - min(ys) + 1
    if not (12 <= bounds_width <= 32 and 8 <= bounds_height <= 32):
        raise ValueError(
            f"bright-pixel bounds are not a crosshair: {bounds_width}x{bounds_height}"
        )
    if max(row_counts.values()) < 12 or max(column_counts.values()) < 12:
        raise ValueError("crosshair lacks both horizontal and vertical strokes")

    center_x = round(sum(xs) / len(xs))
    center_y = round(sum(ys) / len(ys))
    if abs(center_x - near_x) > radius or abs(center_y - near_y) > radius:
        raise ValueError("detected crosshair is outside the expected viewport center")
    return center_x, center_y


def locate_crosshair_in_image(
    image_path: Path, near_x: int, near_y: int, radius: int = 48
) -> tuple[int, int]:
    dimensions = subprocess.run(
        ["identify", "-format", "%w %h", str(image_path)],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.split()
    if len(dimensions) != 2:
        raise ValueError("ImageMagick did not return image dimensions")
    width, height = (int(value) for value in dimensions)
    rgb = subprocess.run(
        ["convert", str(image_path), "-alpha", "off", "-depth", "8", "RGB:-"],
        capture_output=True,
        check=True,
    ).stdout
    return locate_crosshair_rgb(rgb, width, height, near_x, near_y, radius)


def main(argv: list[str]) -> int:
    if len(argv) not in (4, 5):
        print(
            "usage: client_crosshair_locator.py SCREENSHOT NEAR_X NEAR_Y [RADIUS]",
            file=sys.stderr,
        )
        return 2
    try:
        image_path = Path(argv[1])
        near_x = int(argv[2])
        near_y = int(argv[3])
        radius = int(argv[4]) if len(argv) == 5 else 48
        x, y = locate_crosshair_in_image(image_path, near_x, near_y, radius)
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"MINECRAFT_CROSSHAIR_DETECTION_FAILED={exc}", file=sys.stderr)
        return 1
    print(f"{x} {y}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
