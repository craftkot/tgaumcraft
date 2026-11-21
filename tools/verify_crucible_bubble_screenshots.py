#!/usr/bin/env python3
"""Fail-closed pixel check for the real-client Crucible Bubble smoke."""
from __future__ import annotations

import sys
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


def metrics(pixels: np.ndarray) -> dict[str, float | int]:
    rgb = pixels.astype(np.int16)
    luminance = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
    yellow = (
        (rgb[:, :, 0] >= 150)
        & (rgb[:, :, 1] >= 150)
        & (rgb[:, :, 2] <= 180)
        & (rgb[:, :, 0] - rgb[:, :, 2] >= 40)
        & (rgb[:, :, 1] - rgb[:, :, 2] >= 40)
    )
    return {
        "mean_luma": float(luminance.mean()),
        "std_luma": float(luminance.std()),
        "unique_colors": int(np.unique(pixels.reshape(-1, 3), axis=0).shape[0]),
        "yellow_pixels": int(yellow.sum()),
    }


def verify_images(before: Image.Image, after: Image.Image) -> dict[str, float | int]:
    if before.size != after.size:
        raise ValueError(f"before/after dimensions differ: {before.size} vs {after.size}")
    before_pixels = center_pixels(before)
    after_pixels = center_pixels(after)
    before_stats = metrics(before_pixels)
    after_stats = metrics(after_pixels)

    for label, stats in (("before", before_stats), ("after", after_stats)):
        if stats["mean_luma"] < 18.0 or stats["std_luma"] < 6.0 or stats["unique_colors"] < 20:
            raise ValueError(
                f"{label} center view is not a visible lit scene: "
                f"mean_luma={stats['mean_luma']:.2f} std_luma={stats['std_luma']:.2f} "
                f"unique_colors={stats['unique_colors']}"
            )

    delta = np.max(
        np.abs(after_pixels.astype(np.int16) - before_pixels.astype(np.int16)),
        axis=2,
    )
    changed_pixels = int((delta >= 32).sum())
    yellow_delta = int(after_stats["yellow_pixels"] - before_stats["yellow_pixels"])
    if changed_pixels < 8 or yellow_delta < 2:
        raise ValueError(
            "no visible Aer bubble pixels in the centered Crucible view: "
            f"changed_pixels={changed_pixels} yellow_delta={yellow_delta}"
        )

    return {
        "before_luma": round(float(before_stats["mean_luma"]), 2),
        "after_luma": round(float(after_stats["mean_luma"]), 2),
        "before_unique": int(before_stats["unique_colors"]),
        "after_unique": int(after_stats["unique_colors"]),
        "changed_pixels": changed_pixels,
        "yellow_delta": yellow_delta,
    }


def self_test() -> None:
    before = Image.new("RGB", (1280, 800), (12, 14, 16))
    draw = ImageDraw.Draw(before)
    draw.rectangle((500, 300, 780, 520), fill=(52, 48, 40))
    draw.rectangle((590, 365, 690, 465), fill=(90, 92, 82))
    after = before.copy()
    draw_after = ImageDraw.Draw(after)
    draw_after.ellipse((628, 382, 636, 390), fill=(255, 255, 126))
    draw_after.ellipse((648, 396, 655, 403), fill=(255, 245, 120))
    result = verify_images(before, after)
    if result["changed_pixels"] < 8 or result["yellow_delta"] < 2:
        raise AssertionError(f"synthetic visible-particle case was not detected: {result}")
    try:
        verify_images(before, before.copy())
    except ValueError:
        pass
    else:
        raise AssertionError("identical before/after images must fail closed")
    print("Crucible screenshot verifier self-test: PASS (visible scene, yellow particle, no-change rejection)")


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
        f"changed_pixels={result['changed_pixels']} yellow_delta={result['yellow_delta']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
