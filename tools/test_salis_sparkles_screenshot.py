#!/usr/bin/env python3
"""Positive and adversarial screenshot gates: spatial and temporal controls."""
from PIL import Image, ImageDraw
from verify_salis_sparkles_screenshot import new_warm_pixels, transient_warm_pixels

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
print("Salis transient-particle screenshot gate: 8/8 PASS")
