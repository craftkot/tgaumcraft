#!/usr/bin/env python3
"""Positive and adversarial screenshot gates: spatial and temporal controls."""
from PIL import Image, ImageDraw
from verify_salis_sparkles_screenshot import (new_warm_pixels,
    transient_warm_pixels, scene_change_fraction, sparkle_regions,
    strongest_stable_early)

base = Image.new("RGB", (1280, 800), (85, 62, 45))
assert sparkle_regions(1280, 800) == ((100, 690, 300, 760), (320, 110, 1040, 570))
assert new_warm_pixels(base, base) == 0

warm = base.copy()
ImageDraw.Draw(warm).rectangle((140, 700, 174, 719), fill=(239, 202, 135))
assert new_warm_pixels(base, warm) == 35 * 20

# The real BETA26 hand-origin run shows the burst on the bookshelf face,
# outside the previous lower-left-only ROI.
face = base.copy()
ImageDraw.Draw(face).rectangle((600, 300, 634, 319), fill=(239, 202, 135))
assert new_warm_pixels(base, face) == 35 * 20
assert transient_warm_pixels(base, face, base) == (700, 0)
assert transient_warm_pixels(base, face, face) == (700, 700)

# A moving hand is outside BOTH monitored regions.
hand = base.copy()
ImageDraw.Draw(hand).rectangle((600, 600, 750, 750), fill=(239, 202, 135))
assert new_warm_pixels(base, hand) == 0

cold = base.copy()
ImageDraw.Draw(cold).rectangle((600, 300, 634, 319), fill=(70, 100, 220))
assert new_warm_pixels(base, cold) == 0

try:
    new_warm_pixels(base, Image.new("RGB", (640, 400)))
except ValueError:
    pass
else:
    raise AssertionError("mismatched framebuffer must fail")

# Actual transient particles must disappear in the late frame.
assert transient_warm_pixels(base, warm, base) == (700, 0)
# Persistent warm pixels (scene changes, permanent overlay) are NOT particles.
assert transient_warm_pixels(base, warm, warm) == (700, 700)
# A late-only flash must not satisfy the early-effect gate.
assert transient_warm_pixels(base, base, warm) == (0, 700)
# The actual failed CI #41450839661 compared a bookshelf to open sky after
# Salis consumed the block. That must be rejected as a changed scene.
assert scene_change_fraction(base, base) == 0
region_area = sum((right-left) * (bottom-top) for left, top, right, bottom in sparkle_regions(1280, 800))
assert abs(scene_change_fraction(base, warm) - 700 / region_area) < 1e-9
assert abs(scene_change_fraction(base, face) - 700 / region_area) < 1e-9
sky = Image.new("RGB", (1280, 800), (160, 195, 240))
assert scene_change_fraction(base, sky) == 1
try:
    scene_change_fraction(base, Image.new("RGB", (640, 400)))
except ValueError:
    pass
else:
    raise AssertionError("late scene size mismatch must fail")

# Delayed BETA26 particles may be absent at 0.2s but visible at 0.7-1.4s.
# The second capture must be eligible without weakening the pixel threshold.
selected, scores = strongest_stable_early(base, [base, face], base, 0.15)
assert selected == 700 and scores == [(0, 0.0), (700, 700 / region_area)]
# Reject a visually warm but entirely different early scene (e.g. bookshelf
# destroyed before screenshot); only an unchanged-world frame may qualify.
selected, scores = strongest_stable_early(base, [sky, face], base, 0.15)
assert selected == 700 and scores[0][1] == 1
selected, scores = strongest_stable_early(base, [sky], base, 0.15)
assert selected == 0 and scores[0][1] == 1
try:
    strongest_stable_early(base, [], base, 0.15)
except ValueError:
    pass
else:
    raise AssertionError("empty early frame sequence must fail")

print("Salis transient-particle screenshot gate: 21/21 PASS")
