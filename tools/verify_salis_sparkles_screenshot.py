#!/usr/bin/env python3
"""Evidence gate for visible Salis sparkles in a real Minecraft screenshot pair.

The crop is intentionally confined to the lower-left hand-to-target trail in
our fixed 1280x800 smoke camera. It excludes the swinging item and the block
that will transform later. This tests visibility, NOT BETA26 sprite parity.
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
    # Relative crop corresponds to x=100..300, y=690..760 at 1280x800.
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


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("before", type=Path)
    p.add_argument("after", type=Path)
    p.add_argument("--min-pixels", type=int, default=100)
    args = p.parse_args()
    if args.min_pixels <= 0:
        p.error("--min-pixels must be positive")
    with Image.open(args.before) as before, Image.open(args.after) as after:
        n = new_warm_pixels(before, after)
    print(f"SALIS_NEW_WARM_PIXELS={n} threshold={args.min_pixels}")
    if n < args.min_pixels:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: no convincing new warm trail pixels")
    print("SALIS_VISIBLE_SPARKLES=PASS (screen pixels, not exact BETA26 sprite parity)")


if __name__ == "__main__":
    main()
