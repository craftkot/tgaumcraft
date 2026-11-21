#!/usr/bin/env python3
"""Fail-closed pixel check for the real-client Crucible Bubble smoke."""
from __future__ import annotations

import sys
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


def center_pixels(image: Image.Image) -> np.ndarray:
    rgb = image.convert("RGB")
    width, height = rgb.size
    if width < 640 or height < 400:
        raise ValueError(f"screen too small for a stable center crop: {width}x{height}")
    return np.asarray(
        rgb.crop((width * 35 // 100, height * 30 // 100,
                  width * 65 // 100, height * 70 // 100)),
        dtype=np.uint8,
    )


def crucible_pixels(image: Image.Image) -> tuple[np.ndarray, tuple[int, int, int, int]]:
    """Crop only the water cavity in the fixed 1024x768 native-client fixture.

    The current Xvfb desktop is 1280x800 while the Minecraft window is 1024x768.
    The glowstone beside the Crucible is deliberately outside this ROI.
    """
    rgb = image.convert("RGB")
    width, height = rgb.size
    viewport_width = min(width, 1024)
    viewport_height = min(height, 768)
    if viewport_width < 1024 or viewport_height < 768:
        raise ValueError(
            f"native Crucible fixture viewport is too small: {viewport_width}x{viewport_height}"
        )
    x0 = round(viewport_width * 0.46)
    x1 = round(viewport_width * 0.55)
    y0 = round(viewport_height * 0.41)
    y1 = round(viewport_height * 0.52)
    return np.asarray(rgb.crop((x0, y0, x1, y1)), dtype=np.uint8), (
        x0, y0, viewport_width, viewport_height
    )


def metrics(pixels: np.ndarray) -> dict[str, float | int]:
    rgb = pixels.astype(np.int16)
    luminance = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
    return {
        "mean_luma": float(luminance.mean()),
        "std_luma": float(luminance.std()),
        "unique_colors": int(np.unique(pixels.reshape(-1, 3), axis=0).shape[0]),
    }


def yellow_mask(pixels: np.ndarray) -> np.ndarray:
    rgb = pixels.astype(np.int16)
    return (
        (rgb[:, :, 0] >= 150)
        & (rgb[:, :, 1] >= 150)
        & (rgb[:, :, 2] <= 180)
        & (rgb[:, :, 0] - rgb[:, :, 2] >= 40)
        & (rgb[:, :, 1] - rgb[:, :, 2] >= 40)
    )


def component_sizes(mask: np.ndarray) -> list[int]:
    """Return 8-connected component sizes without adding SciPy/OpenCV dependencies."""
    height, width = mask.shape
    visited = np.zeros(mask.shape, dtype=bool)
    sizes: list[int] = []
    for y, x in zip(*np.where(mask)):
        if visited[y, x]:
            continue
        visited[y, x] = True
        pending = deque([(int(y), int(x))])
        size = 0
        while pending:
            cy, cx = pending.popleft()
            size += 1
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = cy + dy, cx + dx
                    if (
                        0 <= ny < height
                        and 0 <= nx < width
                        and mask[ny, nx]
                        and not visited[ny, nx]
                    ):
                        visited[ny, nx] = True
                        pending.append((ny, nx))
        sizes.append(size)
    return sorted(sizes, reverse=True)


def verify_images(before: Image.Image, after: Image.Image) -> dict[str, float | int]:
    if before.size != after.size:
        raise ValueError(f"before/after dimensions differ: {before.size} vs {after.size}")
    before_scene = center_pixels(before)
    after_scene = center_pixels(after)
    before_stats = metrics(before_scene)
    after_stats = metrics(after_scene)

    for label, stats in (("before", before_stats), ("after", after_stats)):
        if stats["mean_luma"] < 18.0 or stats["std_luma"] < 6.0 or stats["unique_colors"] < 20:
            raise ValueError(
                f"{label} center view is not a visible lit scene: "
                f"mean_luma={stats['mean_luma']:.2f} std_luma={stats['std_luma']:.2f} "
                f"unique_colors={stats['unique_colors']}"
            )

    before_bowl, bounds = crucible_pixels(before)
    after_bowl, after_bounds = crucible_pixels(after)
    if bounds != after_bounds:
        raise ValueError(f"Crucible ROI geometry differs: {bounds} vs {after_bounds}")
    before_yellow = yellow_mask(before_bowl)
    after_yellow = yellow_mask(after_bowl)

    x0, y0, viewport_width, viewport_height = bounds
    yy, xx = np.mgrid[0:after_yellow.shape[0], 0:after_yellow.shape[1]]
    absolute_x = xx + x0
    absolute_y = yy + y0
    # The vanilla crosshair is fixed at the center of the 1024x768 game window.
    crosshair = (
        (absolute_x - viewport_width / 2) ** 2
        + (absolute_y - viewport_height / 2) ** 2
        <= 8 ** 2
    )
    new_bubble_pixels = after_yellow & ~before_yellow & ~crosshair
    components = component_sizes(new_bubble_pixels)
    visible_components = [size for size in components if size >= 3]
    bubble_pixel_count = int(new_bubble_pixels.sum())

    # Do not count the adjacent animated glowstone or the crosshair. Require
    # one spatially connected new yellow cluster inside the Crucible bowl.
    if bubble_pixel_count < 6 or not visible_components:
        raise ValueError(
            "no visible Aer bubble cluster in the Crucible water ROI: "
            f"new_yellow_pixels={bubble_pixel_count} "
            f"components_ge_3px={len(visible_components)} "
            f"largest_components={visible_components[:5]}"
        )

    return {
        "before_luma": round(float(before_stats["mean_luma"]), 2),
        "after_luma": round(float(after_stats["mean_luma"]), 2),
        "before_unique": int(before_stats["unique_colors"]),
        "after_unique": int(after_stats["unique_colors"]),
        "new_bubble_pixels": bubble_pixel_count,
        "new_bubble_components": len(visible_components),
        "largest_bubble_components": ",".join(map(str, visible_components[:5])),
    }


def self_test() -> None:
    yy, xx = np.mgrid[0:800, 0:1280]
    pixels = np.empty((800, 1280, 3), dtype=np.uint8)
    pixels[:, :, 0] = 28 + (xx % 83)
    pixels[:, :, 1] = 34 + (yy % 79)
    pixels[:, :, 2] = 38 + ((xx + yy) % 91)
    before = Image.fromarray(pixels, mode="RGB")
    draw = ImageDraw.Draw(before)
    draw.rectangle((471, 315, 562, 400), fill=(42, 48, 48))
    draw.rectangle((470, 310, 564, 405), outline=(90, 92, 82), width=4)
    after = before.copy()
    draw_after = ImageDraw.Draw(after)
    draw_after.ellipse((496, 337, 503, 344), fill=(255, 255, 126))
    result = verify_images(before, after)
    if result["new_bubble_pixels"] < 6 or result["new_bubble_components"] < 1:
        raise AssertionError(f"synthetic visible-particle case was not detected: {result}")

    try:
        verify_images(before, before.copy())
    except ValueError:
        pass
    else:
        raise AssertionError("identical before/after images must fail closed")

    unrelated = before.copy()
    ImageDraw.Draw(unrelated).ellipse((800, 300, 820, 320), fill=(255, 255, 126))
    try:
        verify_images(before, unrelated)
    except ValueError:
        pass
    else:
        raise AssertionError("yellow pixels outside the Crucible ROI must not pass")

    single_pixel = before.copy()
    ImageDraw.Draw(single_pixel).point((500, 340), fill=(255, 255, 126))
    try:
        verify_images(before, single_pixel)
    except ValueError:
        pass
    else:
        raise AssertionError("one yellow pixel must not pass as a bubble")

    dark = Image.new("RGB", before.size, (8, 8, 8))
    try:
        verify_images(dark, dark.copy())
    except ValueError:
        pass
    else:
        raise AssertionError("dark/black screenshots must fail the lit-scene gate")

    print(
        "Crucible screenshot verifier self-test: PASS "
        "(one bowl bubble cluster, no-change, outside-ROI, single-pixel and dark-scene rejection)"
    )


def main(argv: list[str]) -> int:
    if argv == ["--self-test"]:
        self_test()
        return 0
    if len(argv) != 2:
        print(
            "usage: verify_crucible_bubble_screenshots.py BEFORE.png AFTER.png | --self-test",
            file=sys.stderr,
        )
        return 2
    before_path, after_path = map(Path, argv)
    with Image.open(before_path) as before, Image.open(after_path) as after:
        result = verify_images(before, after)
    print(
        "CRUCIBLE_BUBBLE_SCREENSHOTS=PASS "
        f"before_luma={result['before_luma']} after_luma={result['after_luma']} "
        f"before_unique={result['before_unique']} after_unique={result['after_unique']} "
        f"new_bubble_pixels={result['new_bubble_pixels']} "
        f"new_bubble_components={result['new_bubble_components']} "
        f"largest_bubble_components={result['largest_bubble_components']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
