#!/usr/bin/env python3
"""Positive and adversarial screenshot gates: spatial and temporal controls."""
from PIL import Image, ImageDraw
from verify_salis_sparkles_screenshot import (new_warm_pixels,
    transient_warm_pixels, scene_change_fraction)

base = Image.new("RGB", (1280, 800), (85, 62, 45))
assert new_warm_pixels(base, base) == 0

warm = base.copy()
ImageDraw.Draw(warm).rectangle((140, 700, 174, 719), fill=(239, 202, 135))
assert new_warm_pixels(base, warm) == 35 * 20

# A moving hand is outside the restricted trail crop.
hand = base.copy()
ImageDraw.Draw(hand).rectangle((600, 600, 750, 750), fill=(239, 202, 135))
assert new_warm_pixels(base, hand) == 0

cold = base.copy()
ImageDraw.Draw(cold).rectangle((140, 700, 174, 719), fill=(70, 100, 220))
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
assert abs(scene_change_fraction(base, warm) - 0.05) < 1e-9
sky = Image.new("RGB", (1280, 800), (160, 195, 240))
assert scene_change_fraction(base, sky) == 1
try:
    scene_change_fraction(base, Image.new("RGB", (640, 400)))
except ValueError:
    pass
else:
    raise AssertionError("late scene size mismatch must fail")
print("Salis transient-particle screenshot gate: 12/12 PASS")
