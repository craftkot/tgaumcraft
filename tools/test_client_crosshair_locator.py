#!/usr/bin/env python3
"""Regression tests for screenshot-derived Minecraft crosshair coordinates."""
from client_crosshair_locator import locate_crosshair_rgb

WIDTH = HEIGHT = 100
BLACK = (12, 12, 12)
BRIGHT = (255, 255, 190)


def blank() -> bytearray:
    return bytearray(BLACK * (WIDTH * HEIGHT))


def paint(rgb: bytearray, x: int, y: int, color: tuple[int, int, int]) -> None:
    offset = (y * WIDTH + x) * 3
    rgb[offset : offset + 3] = bytes(color)


def crosshair(cx: int, cy: int) -> bytes:
    rgb = blank()
    for x in range(cx - 9, cx + 10):
        for dy in range(-1, 2):
            paint(rgb, x, cy + dy, BRIGHT)
    for y in range(cy - 9, cy + 10):
        for dx in range(-1, 2):
            paint(rgb, cx + dx, y, BRIGHT)
    return bytes(rgb)


# The real CI capture has a 22px viewport/window-center offset. The locator
# must return the rendered crosshair, not the geometry-derived window center.
assert locate_crosshair_rgb(crosshair(47, 28), WIDTH, HEIGHT, 50, 50) == (47, 28)

# Low-amplitude pixels are not a crosshair and must not create a false center.
noise = blank()
for y in range(24, 34):
    for x in range(42, 52):
        paint(noise, x, y, (160, 160, 80))
try:
    locate_crosshair_rgb(bytes(noise), WIDTH, HEIGHT, 50, 50)
except ValueError as exc:
    assert "too few bright crosshair pixels" in str(exc)
else:
    raise AssertionError("low-amplitude framebuffer noise was accepted as a crosshair")

# A single bright pixel cannot satisfy the two-stroke geometry.
point = blank()
paint(point, 50, 50, BRIGHT)
try:
    locate_crosshair_rgb(bytes(point), WIDTH, HEIGHT, 50, 50)
except ValueError:
    pass
else:
    raise AssertionError("a single bright pixel was accepted as a crosshair")

print("Minecraft crosshair locator: viewport offset, dim-noise rejection and geometry guard: PASS")
