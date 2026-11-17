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
    baseline_101 = root / "baseline-101.ppm"
    hit_299 = root / "hit-299.ppm"
    hit_300 = root / "hit-300.ppm"

    unchanged.write_bytes(ppm(bytes(BACKGROUND) * PIXELS))
    noise = bytearray(bytes(BACKGROUND) * PIXELS)
    for index in range(1668):
        offset = index * 3
        noise[offset:offset + 3] = bytes((45, 45, 45))
    low_noise.write_bytes(ppm(bytes(noise)))

    # The ImageMagick AE result is the contract; calibrate exact boundary
    # fixtures instead of assuming decoder pixel counts.
    controls = {}
    for count in range(295, 305):
        candidate = root / f"candidate-{count}.ppm"
        candidate.write_bytes(image_with_changes(count, (255, 255, 255)))
        measured = changed_pixels(unchanged, candidate)
        if measured in (299, 300):
            controls[measured] = candidate.read_bytes()
    assert set(controls) == {299, 300}, "unable to construct 299/300-pixel boundary controls"
    hit_299.write_bytes(controls[299])
    hit_300.write_bytes(controls[300])
    baseline_101.write_bytes(image_with_changes(101, (255, 255, 255)))

    assert changed_pixels(unchanged, low_noise) == 0, "low-amplitude capture noise must be ignored"
    assert changed_pixels(unchanged, baseline_101) == 101
    assert changed_pixels(unchanged, hit_299) == 299
    assert changed_pixels(unchanged, hit_300) == 300

    # A low-contrast crack frame passes only when it is materially above a
    # same-duration no-hit control, not merely because the scene changed.
    assert diagnose(unchanged, low_noise, unchanged, hit_300) == (300, 0)
    assert verify(unchanged, low_noise, unchanged, hit_300) == 300

    try:
        verify(unchanged, low_noise, unchanged, hit_299)
    except SystemExit as exc:
        assert "hit_pixels=299" in str(exc)
    else:
        raise AssertionError("299 changed pixels incorrectly passed the 300-pixel floor")

    try:
        verify(unchanged, baseline_101, unchanged, hit_300)
    except SystemExit as exc:
        assert "excess=199" in str(exc)
    else:
        raise AssertionError("a hit only 199 pixels above baseline incorrectly passed")

print("Jar damage overlay detector: low-noise baseline and 299/300 plus 199/200 excess boundaries: PASS")
