#!/usr/bin/env python3
"""Positive and adversarial pixel checks for the real-client Salis screenshot gate."""
from PIL import Image, ImageDraw
from verify_salis_sparkles_screenshot import new_warm_pixels

base = Image.new("RGB", (1280, 800), (85, 62, 45))
assert new_warm_pixels(base, base) == 0

warm = base.copy()
ImageDraw.Draw(warm).rectangle((140, 700, 174, 719), fill=(239, 202, 135))
assert new_warm_pixels(base, warm) == 35 * 20

# Hand-swing movement to the right must not be mistaken for the particle trail.
hand = base.copy()
ImageDraw.Draw(hand).rectangle((600, 600, 750, 750), fill=(239, 202, 135))
assert new_warm_pixels(base, hand) == 0

# Non-warm changed pixels in the same crop must not pass.
cold = base.copy()
ImageDraw.Draw(cold).rectangle((140, 700, 174, 719), fill=(70, 100, 220))
assert new_warm_pixels(base, cold) == 0

# Size mismatch must fail closed rather than comparing unrelated framebuffers.
try:
    new_warm_pixels(base, Image.new("RGB", (640, 400)))
except ValueError:
    pass
else:
    raise AssertionError("mismatched framebuffer must fail")
print("Salis visible-particle screenshot gate: 5/5 PASS")
