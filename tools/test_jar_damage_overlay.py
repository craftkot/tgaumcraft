#!/usr/bin/env python3
"""Keep strict overlay verification and non-gating diagnostic behavior separate."""
from pathlib import Path
from tempfile import TemporaryDirectory

from verify_jar_damage_overlay import changed_pixels, diagnose, verify

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
    before = root / "before.ppm"
    noisy = root / "noise.ppm"
    below = root / "below-threshold.ppm"
    cracked = root / "cracks.ppm"
    before.write_bytes(ppm(bytes(baseline)))
    noisy.write_bytes(ppm(bytes(noise)))

    # ImageMagick AE can count one extra pixel on PPM fixtures, depending
    # on its decoder/version. Calibrate *measured* 39/40-pixel controls,
    # rather than assuming the number of edited source pixels equals AE.
    controls = {}
    for source_count in range(36, 43):
        candidate = bytearray(baseline)
        for index in range(source_count):
            offset = (1000 + index) * 3
            candidate[offset:offset + 3] = bytes((255, 255, 255))
        candidate_path = root / f"candidate-{source_count}.ppm"
        candidate_path.write_bytes(ppm(bytes(candidate)))
        measured = changed_pixels(before, candidate_path)
        if measured in (39, 40):
            controls[measured] = candidate_path.read_bytes()
    assert set(controls) == {39, 40}, "unable to construct ImageMagick AE boundary controls"
    below.write_bytes(controls[39])
    cracked.write_bytes(controls[40])

    assert changed_pixels(before, noisy) == 0, "low-amplitude frame noise must be ignored"
    assert changed_pixels(before, below) == 39, "below-threshold control must contain 39 changed pixels"
    assert changed_pixels(before, cracked) == 40, "positive crack control must contain exactly 40 changed pixels"
    assert diagnose(before, noisy) == 0, "diagnostic mode must report a missing overlay without failing"
    assert diagnose(before, below) == 39, "diagnostic mode must report the below-threshold count"
    assert diagnose(before, cracked) == 40, "diagnostic mode must report the threshold-positive overlay"
    try:
        verify(before, noisy)
    except SystemExit as exc:
        assert "JAR_HIT_CLIENT_DAMAGE_OVERLAY_MISSING=YES" in str(exc)
    else:
        raise AssertionError("noise-only frame incorrectly passed damage-overlay verification")
    try:
        verify(before, below)
    except SystemExit as exc:
        assert "high_contrast_pixels=39" in str(exc)
    else:
        raise AssertionError("39 crack pixels incorrectly passed the 40-pixel threshold")
    assert verify(before, cracked) == 40

print("Jar damage overlay detector: low-amplitude noise and 39-pixel negative, 40-pixel positive threshold controls: PASS")
