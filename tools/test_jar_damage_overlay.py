#!/usr/bin/env python3
"""Calibrate low-contrast jar cracks against a same-duration no-hit baseline."""
from pathlib import Path
from tempfile import TemporaryDirectory

from verify_jar_damage_overlay import changed_pixels, diagnose, verify

WIDTH = HEIGHT = 220
PIXELS = WIDTH * HEIGHT
BACKGROUND = (32, 32, 32)


def ppm(rgb: bytes) -> bytes:
    assert len(rgb) == PIXELS * 3
    return f"P6\n{WIDTH} {HEIGHT}\n255\n".encode("ascii") + rgb


def image_with_changes(count: int, color: tuple[int, int, int]) -> bytes:
    rgb = bytearray(bytes(BACKGROUND) * PIXELS)
    for index in range(count):
        offset = (1000 + index) * 3
        rgb[offset:offset + 3] = bytes(color)
    return ppm(bytes(rgb))


with TemporaryDirectory(prefix="tc-jar-overlay-") as temp:
    root = Path(temp)
    unchanged = root / "unchanged.ppm"
    low_noise = root / "low-noise.ppm"
    baseline_1 = root / "baseline-1.ppm"
    hit_119 = root / "hit-119.ppm"
    hit_120 = root / "hit-120.ppm"

    unchanged.write_bytes(ppm(bytes(BACKGROUND) * PIXELS))
    noise = bytearray(bytes(BACKGROUND) * PIXELS)
    for index in range(1668):
        offset = index * 3
        noise[offset:offset + 3] = bytes((45, 45, 45))
    low_noise.write_bytes(ppm(bytes(noise)))

    # The ImageMagick AE result is the contract; calibrate exact boundary
    # fixtures instead of assuming decoder pixel counts.
    controls = {}
    for count in range(115, 125):
        candidate = root / f"candidate-{count}.ppm"
        candidate.write_bytes(image_with_changes(count, (255, 255, 255)))
        measured = changed_pixels(unchanged, candidate)
        if measured in (119, 120):
            controls[measured] = candidate.read_bytes()
    assert set(controls) == {119, 120}, "unable to construct 119/120-pixel boundary controls"
    hit_119.write_bytes(controls[119])
    hit_120.write_bytes(controls[120])
    baseline_1.write_bytes(image_with_changes(1, (255, 255, 255)))

    assert changed_pixels(unchanged, low_noise) == 0, "low-amplitude capture noise must be ignored"
    assert changed_pixels(unchanged, baseline_1) == 1
    assert changed_pixels(unchanged, hit_119) == 119
    assert changed_pixels(unchanged, hit_120) == 120

    # The measured real-client hit (162 pixels) is above this calibrated floor;
    # a same-duration no-hit baseline remains mandatory.
    assert diagnose(unchanged, low_noise, unchanged, hit_120) == (120, 0)
    assert verify(unchanged, low_noise, unchanged, hit_120) == 120

    try:
        verify(unchanged, low_noise, unchanged, hit_119)
    except SystemExit as exc:
        assert "hit_pixels=119" in str(exc)
    else:
        raise AssertionError("119 changed pixels incorrectly passed the 120-pixel floor")

    try:
        verify(unchanged, baseline_1, unchanged, hit_120)
    except SystemExit as exc:
        assert "excess=119" in str(exc)
    else:
        raise AssertionError("a hit only 119 pixels above baseline incorrectly passed")

print("Jar damage overlay detector: low-noise baseline and 119/120 hit/excess boundaries: PASS")
