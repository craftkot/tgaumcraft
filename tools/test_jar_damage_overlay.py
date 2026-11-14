#!/usr/bin/env python3
"""Reject low-amplitude frame noise and accept a high-contrast crack-like overlay."""
from pathlib import Path
from tempfile import TemporaryDirectory

from verify_jar_damage_overlay import changed_pixels, verify

WIDTH = HEIGHT = 220
PIXELS = WIDTH * HEIGHT
BACKGROUND = (32, 32, 32)


def ppm(rgb: bytes) -> bytes:
    assert len(rgb) == PIXELS * 3
    return f"P6\n{WIDTH} {HEIGHT}\n255\n".encode("ascii") + rgb


with TemporaryDirectory(prefix="tc-jar-overlay-") as temp:
    root = Path(temp)
    baseline = bytearray(BACKGROUND * PIXELS)
    noise = bytearray(baseline)
    for index in range(1668):
        offset = index * 3
        noise[offset:offset + 3] = bytes((45, 45, 45))  # 13/255: capture noise, not a crack
    overlay = bytearray(baseline)
    for index in range(60):
        offset = (1000 + index) * 3
        overlay[offset:offset + 3] = bytes((255, 255, 255))  # clear high-contrast overlay pixels

    before = root / "before.ppm"
    noisy = root / "noise.ppm"
    cracked = root / "cracks.ppm"
    before.write_bytes(ppm(bytes(baseline)))
    noisy.write_bytes(ppm(bytes(noise)))
    cracked.write_bytes(ppm(bytes(overlay)))

    assert changed_pixels(before, noisy) == 0, "low-amplitude frame noise must be ignored"
    assert changed_pixels(before, cracked) >= 60, "high-contrast crack pixels must remain visible"
    try:
        verify(before, noisy)
    except SystemExit as exc:
        assert "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES" in str(exc)
    else:
        raise AssertionError("noise-only frame incorrectly passed damage-overlay verification")
    assert verify(before, cracked) >= 60

print("Jar damage overlay detector: low-amplitude noise negative, high-contrast overlay positive: PASS")
