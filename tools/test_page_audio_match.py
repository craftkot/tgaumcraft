#!/usr/bin/env python3
"""Positive source-clip and negative noise controls for PCM attribution."""
from pathlib import Path
import subprocess
import tempfile
import numpy as np
from match_page_audio_pcm import check


def ogg(path: Path, frequency: int):
    subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i",
                    f"sine=frequency={frequency}:duration=0.6:sample_rate=48000",
                    "-c:a", "libvorbis", str(path)], check=True)


def pcm(path: Path, mono: np.ndarray):
    stereo = np.column_stack((mono, mono))
    path.write_bytes((np.clip(stereo, -1, 1) * 32767).astype("<i2").tobytes())


with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    ogg(root / "page1.ogg", 510)
    ogg(root / "page2.ogg", 770)
    result = subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error",
                             "-i", str(root / "page2.ogg"), "-ac", "1",
                             "-ar", "48000", "-f", "f32le", "pipe:1"],
                            check=True, capture_output=True)
    clip = np.frombuffer(result.stdout, dtype="<f4")
    rng = np.random.default_rng(37)
    baseline = rng.normal(0, 0.001, 48000 * 2)
    event = rng.normal(0, 0.001, 48000 * 3)
    event[48000:48000 + len(clip)] += clip * 0.5
    pcm(root / "baseline.pcm", baseline)
    pcm(root / "event.pcm", event)
    selected, correlation = check(root / "baseline.pcm", root / "event.pcm", root)
    assert selected == "page2" and correlation > 0.9
    pcm(root / "event.pcm", rng.normal(0, 0.001, 48000 * 3))
    try:
        check(root / "baseline.pcm", root / "event.pcm", root)
    except AssertionError:
        pass
    else:
        raise AssertionError("Noise-only negative control falsely accepted")
print("BETA26 page source-waveform matching controls: PASS")
