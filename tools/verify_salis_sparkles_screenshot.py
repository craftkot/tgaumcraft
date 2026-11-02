#!/usr/bin/env python3
"""Real-client Salis particle evidence: transient warm pixels, not persistent scene changes.

Fixed 1280x800 camera, lower-left hand-to-target crop. Compare before, early
(~0.2s after use), and late (>4s after use, bookshelf fixture restored)
screenshots. The late frame MUST have the same scene; otherwise comparing warm
pixels against the pre-use frame confuses distant terrain with particles.
This is evidence of transient pixels, not original BETA26 sprite parity.
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


def scene_change_fraction(before: Image.Image, late: Image.Image) -> float:
    """Fraction of target ROI with a large RGB change in the late control.

    Salis consumes the bookshelf; comparing a late SKY frame to a pre-use
    BOOKSHELF frame is invalid. The smoke must restore the disposable fixture
    before this check. Reject changed scenes instead of granting a false PASS.
    """
    if before.size != late.size:
        raise ValueError(f"different screenshot sizes: {before.size} != {late.size}")
    width, height = before.size
    if width < 640 or height < 400:
        raise ValueError(f"unexpected game framebuffer {width}x{height}")
    box = (int(width * 100 / 1280), int(height * 690 / 800),
           int(width * 300 / 1280), int(height * 760 / 800))
    a = before.convert("RGB").crop(box).tobytes()
    b = late.convert("RGB").crop(box).tobytes()
    changed = sum(
        max(abs(a[i] - b[i]), abs(a[i + 1] - b[i + 1]),
            abs(a[i + 2] - b[i + 2])) >= 45
        for i in range(0, len(a), 3)
    )
    return changed / (len(a) // 3)


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
    p.add_argument("--max-scene-change-fraction", type=float, default=0.15)
    args = p.parse_args()
    if args.min_pixels <= 0:
        p.error("--min-pixels must be positive")
    if not (0 <= args.max_late_fraction < 1):
        p.error("--max-late-fraction must be >=0 and <1")
    if not (0 <= args.max_scene_change_fraction < 1):
        p.error("--max-scene-change-fraction must be >=0 and <1")
    with Image.open(args.before) as before, Image.open(args.early) as early, Image.open(args.late) as late:
        early_count, late_count = transient_warm_pixels(before, early, late)
        late_scene_change = scene_change_fraction(before, late)
    print(f"SALIS_NEW_WARM_PIXELS={early_count} threshold={args.min_pixels}")
    print(f"SALIS_LATE_WARM_PIXELS={late_count} max_fraction={args.max_late_fraction}")
    print(f"SALIS_LATE_SCENE_CHANGE_FRACTION={late_scene_change:.4f} max={args.max_scene_change_fraction}")
    if late_scene_change > args.max_scene_change_fraction:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: late control has different scene; restore bookshelf before capture")
    if early_count < args.min_pixels:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: no convincing new warm trail pixels")
    if late_count > early_count * args.max_late_fraction:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: warm pixels persist after particle lifetime")
    print("SALIS_VISIBLE_SPARKLES=PASS (transient screen pixels, not BETA26 sprite parity)")


if __name__ == "__main__":
    main()
