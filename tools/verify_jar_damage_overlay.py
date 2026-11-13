#!/usr/bin/env python3
"""Fail-closed verification that a real jar hit rendered a visible crack overlay."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

FUZZ = "8%"
MIN_CHANGED_PIXELS = 40


def changed_pixels(before: Path, after: Path) -> int:
    result = subprocess.run(
        ["compare", "-metric", "AE", "-fuzz", FUZZ, str(before), str(after), "null:"],
        capture_output=True,
        text=True,
        check=False,
    )
    output = (result.stderr or result.stdout).strip()
    first = output.split(maxsplit=1)[0] if output else ""
    if result.returncode not in (0, 1) or not first.isdigit():
        raise RuntimeError(
            f"ImageMagick compare failed ({result.returncode}): {output or result.stdout or result.stderr}"
        )
    return int(first)


def verify(before: Path, after: Path) -> int:
    pixels = changed_pixels(before, after)
    if pixels < MIN_CHANGED_PIXELS:
        raise SystemExit(
            "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES "
            f"high_contrast_pixels={pixels} min={MIN_CHANGED_PIXELS} fuzz={FUZZ}"
        )
    print(
        "JAR_HIT_CLIENT_DAMAGE_OVERLAY=PASS "
        f"high_contrast_pixels={pixels} min={MIN_CHANGED_PIXELS} fuzz={FUZZ}"
    )
    return pixels


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: verify_jar_damage_overlay.py BEFORE.png AFTER.png")
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
