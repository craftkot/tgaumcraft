#!/usr/bin/env python3
"""Fail-closed verification that a real jar hit rendered a visible crack overlay."""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

# BETA26 jars are 10x12x10 px translucent models, not full-cube opaque blocks.
# The exact-head 1280x800 client capture produced 162 changed AE pixels at 6%
# fuzz with a zero-pixel same-duration no-hit baseline. Keep a 120-pixel floor
# (26% below that observed signal) while retaining independent baseline
# subtraction so camera/scene drift cannot satisfy the gate by itself.
FUZZ = "6%"
MIN_HIT_PIXELS = 120
MIN_OVER_BASELINE_PIXELS = 120


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


def verify(
    no_hit_before: Path,
    no_hit_after: Path,
    hit_before: Path,
    hit_after: Path,
) -> int:
    baseline = changed_pixels(no_hit_before, no_hit_after)
    hit = changed_pixels(hit_before, hit_after)
    excess = hit - baseline
    if hit < MIN_HIT_PIXELS or excess < MIN_OVER_BASELINE_PIXELS:
        raise SystemExit(
            "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES "
            f"hit_pixels={hit} baseline_pixels={baseline} excess={excess} "
            f"min_hit={MIN_HIT_PIXELS} min_excess={MIN_OVER_BASELINE_PIXELS} fuzz={FUZZ}"
        )
    print(
        "JAR_HIT_CLIENT_DAMAGE_OVERLAY=PASS "
        f"hit_pixels={hit} baseline_pixels={baseline} excess={excess} "
        f"min_hit={MIN_HIT_PIXELS} min_excess={MIN_OVER_BASELINE_PIXELS} fuzz={FUZZ}"
    )
    return hit


def diagnose(
    no_hit_before: Path,
    no_hit_after: Path,
    hit_before: Path,
    hit_after: Path,
) -> tuple[int, int]:
    baseline = changed_pixels(no_hit_before, no_hit_after)
    hit = changed_pixels(hit_before, hit_after)
    print(
        "JAR_HIT_CLIENT_DAMAGE_OVERLAY_DIAGNOSTIC "
        f"hit_pixels={hit} baseline_pixels={baseline} excess={hit - baseline} "
        f"min_hit={MIN_HIT_PIXELS} min_excess={MIN_OVER_BASELINE_PIXELS} fuzz={FUZZ}"
    )
    return hit, baseline


if __name__ == "__main__":
    if len(sys.argv) == 6 and sys.argv[1] == "--diagnostic":
        diagnose(*(Path(value) for value in sys.argv[2:]))
    elif len(sys.argv) == 5:
        verify(*(Path(value) for value in sys.argv[1:]))
    else:
        raise SystemExit(
            "usage: verify_jar_damage_overlay.py "
            "[--diagnostic] NO_HIT_BEFORE NO_HIT_AFTER HIT_BEFORE HIT_AFTER"
        )
