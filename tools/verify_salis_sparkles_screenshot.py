#!/usr/bin/env python3
"""Real-client Salis particle evidence: transient warm pixels, not persistent scene changes.

Fixed 1280x800 camera, lower-left hand-to-target crop. Compare before, early
(~0.2s after use), and late (>4s after use) screenshots. This is evidence of
visible transient particles, not proof of original BETA26 sprite parity.
"""
import argparse
from pathlib import Path
from PIL import Image


def new_warm_pixels(before: Image.Image, after: Image.Image) -> int:
    if before.size != after.size:
        raise ValueError(f"different screenshot sizes: {before.size} != {after.size}")
    width, height = before.size
    if width < 640 or height < 400:
        raise ValueError(f"unexpected game framebuffer {width}x{height}")
    # Excludes hand swing and the bookshelf transformation in the fixed camera.
    box = (int(width * 100 / 1280), int(height * 690 / 800),
           int(width * 300 / 1280), int(height * 760 / 800))
    a = before.convert("RGB").crop(box).tobytes()
    b = after.convert("RGB").crop(box).tobytes()
    count = 0
    for i in range(0, len(a), 3):
        r0, g0, b0 = a[i:i+3]
        r1, g1, b1 = b[i:i+3]
        warm = r1 >= 155 and g1 >= 125 and r1 >= b1 + 25 and g1 >= b1 - 5
        changed = max(abs(r1-r0), abs(g1-g0), abs(b1-b0)) >= 45
        if warm and changed:
            count += 1
    return count


def transient_warm_pixels(before: Image.Image, early: Image.Image,
                          late: Image.Image) -> tuple[int, int]:
    """Counts new warm pixels at both times against the SAME pre-use frame."""
    return new_warm_pixels(before, early), new_warm_pixels(before, late)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("before", type=Path)
    p.add_argument("early", type=Path)
    p.add_argument("late", type=Path)
    p.add_argument("--min-pixels", type=int, default=100)
    p.add_argument("--max-late-fraction", type=float, default=0.25)
    args = p.parse_args()
    if args.min_pixels <= 0:
        p.error("--min-pixels must be positive")
    if not (0 <= args.max_late_fraction < 1):
        p.error("--max-late-fraction must be >=0 and <1")
    with Image.open(args.before) as before, Image.open(args.early) as early, Image.open(args.late) as late:
        early_count, late_count = transient_warm_pixels(before, early, late)
    print(f"SALIS_NEW_WARM_PIXELS={early_count} threshold={args.min_pixels}")
    print(f"SALIS_LATE_WARM_PIXELS={late_count} max_fraction={args.max_late_fraction}")
    if early_count < args.min_pixels:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: no convincing new warm trail pixels")
    if late_count > early_count * args.max_late_fraction:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: warm pixels persist after particle lifetime")
    print("SALIS_VISIBLE_SPARKLES=PASS (transient screen pixels, not BETA26 sprite parity)")


if __name__ == "__main__":
    main()
