#!/usr/bin/env python3
"""Real-client Salis particle evidence: transient warm pixels, not persistent scene changes.

Fixed 1280x800 camera: monitor the lower-left trail AND the visible bookshelf
face. BETA26 chest-anchored particles can fall outside the old trail-only crop.
Compare before, early (~0.2s after use), and late (>4s after use, bookshelf
fixture restored) screenshots. The late frame MUST have the same scene.
This is evidence of transient pixels, not original BETA26 sprite parity.
"""
import argparse
from contextlib import ExitStack
from pathlib import Path
from PIL import Image


def sparkle_regions(width: int, height: int) -> tuple[tuple[int, int, int, int], ...]:
    if width < 640 or height < 400:
        raise ValueError(f"unexpected game framebuffer {width}x{height}")
    # Non-overlapping ROIs for the fixed smoke camera. The central face ROI
    # excludes the bottom-right moving hand, HUD and bookshelf frame edges.
    # Keep the original trail ROI to avoid regressing previously valid runs.
    return tuple(
        (int(width * left / 1280), int(height * top / 800),
         int(width * right / 1280), int(height * bottom / 800))
        for left, top, right, bottom in (
            (100, 690, 300, 760),    # hand-to-block trail
            (320, 110, 1040, 570),  # visible bookshelf face burst
        )
    )


def new_warm_pixels(before: Image.Image, after: Image.Image) -> int:
    if before.size != after.size:
        raise ValueError(f"different screenshot sizes: {before.size} != {after.size}")
    a = before.convert("RGB")
    b = after.convert("RGB")
    count = 0
    for box in sparkle_regions(*before.size):
        old = a.crop(box).tobytes()
        new = b.crop(box).tobytes()
        for i in range(0, len(old), 3):
            r0, g0, b0 = old[i:i + 3]
            r1, g1, b1 = new[i:i + 3]
            warm = r1 >= 155 and g1 >= 125 and r1 >= b1 + 25 and g1 >= b1 - 5
            changed = max(abs(r1 - r0), abs(g1 - g0), abs(b1 - b0)) >= 45
            if warm and changed:
                count += 1
    return count


def scene_change_fraction(before: Image.Image, late: Image.Image) -> float:
    """Fraction of BOTH monitored ROIs changed in the restored late control."""
    if before.size != late.size:
        raise ValueError(f"different screenshot sizes: {before.size} != {late.size}")
    a = before.convert("RGB")
    b = late.convert("RGB")
    changed = total = 0
    for box in sparkle_regions(*before.size):
        old = a.crop(box).tobytes()
        new = b.crop(box).tobytes()
        total += len(old) // 3
        changed += sum(
            max(abs(old[i] - new[i]), abs(old[i + 1] - new[i + 1]),
                abs(old[i + 2] - new[i + 2])) >= 45
            for i in range(0, len(old), 3)
        )
    return changed / total


def transient_warm_pixels(before: Image.Image, early: Image.Image,
                          late: Image.Image) -> tuple[int, int]:
    """Counts new warm pixels at both times against the SAME pre-use frame."""
    return new_warm_pixels(before, early), new_warm_pixels(before, late)


def strongest_stable_early(before: Image.Image, early_frames: list[Image.Image],
                           late: Image.Image, max_scene_change: float) -> tuple[int, list[tuple[int, float]]]:
    """Pick only a transient candidate with the same world scene as the restored control.

    Delayed face sparkles may not yet exist at the first frame. Never turn a
    missing bookshelf, camera movement or persistent scenery into a false PASS.
    """
    if not early_frames:
        raise ValueError("at least one early frame is required")
    scores = [(new_warm_pixels(before, frame), scene_change_fraction(late, frame))
              for frame in early_frames]
    stable = [pixels for pixels, drift in scores if drift <= max_scene_change]
    return max(stable, default=0), scores


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("before", type=Path)
    p.add_argument("early", type=Path)
    p.add_argument("late", type=Path)
    p.add_argument("--extra-early", type=Path, action="append", default=[],
                   help="additional early capture after delayed particle dispatch")
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
    with ExitStack() as stack:
        before = stack.enter_context(Image.open(args.before))
        late = stack.enter_context(Image.open(args.late))
        frames = [stack.enter_context(Image.open(path))
                  for path in [args.early, *args.extra_early]]
        early_count, early_scores = strongest_stable_early(
            before, frames, late, args.max_scene_change_fraction)
        late_count = new_warm_pixels(before, late)
        late_scene_change = scene_change_fraction(before, late)
    for index, (pixels, drift) in enumerate(early_scores):
        print(f"SALIS_EARLY_FRAME_{index}_WARM_PIXELS={pixels} scene_change={drift:.4f}")
    print(f"SALIS_NEW_WARM_PIXELS={early_count} threshold={args.min_pixels}")
    print(f"SALIS_LATE_WARM_PIXELS={late_count} max_fraction={args.max_late_fraction}")
    print(f"SALIS_LATE_SCENE_CHANGE_FRACTION={late_scene_change:.4f} max={args.max_scene_change_fraction}")
    if late_scene_change > args.max_scene_change_fraction:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: late control has different scene; restore bookshelf before capture")
    if not any(drift <= args.max_scene_change_fraction for _, drift in early_scores):
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: no early frame preserves the bookshelf scene")
    if early_count < args.min_pixels:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: no convincing new warm trail or bookshelf-face pixels")
    if late_count > early_count * args.max_late_fraction:
        raise SystemExit("SALIS_VISIBLE_SPARKLES=FAIL: warm pixels persist after particle lifetime")
    print("SALIS_VISIBLE_SPARKLES=PASS (transient screen pixels, not BETA26 sprite parity)")


if __name__ == "__main__":
    main()
