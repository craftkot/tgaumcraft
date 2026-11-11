#!/usr/bin/env python3
"""Positive controls for all four BETA26 jar clips and a noise-only negative control."""
from pathlib import Path
import numpy as np
from match_jar_audio_pcm import RATE, check_samples, stretch
from match_page_audio_pcm import active_clip, decode

sources = {
    f"jar{i}.ogg": decode(Path(f"src/main/resources/assets/thaumcraft/sounds/jar{i}.ogg"))
    for i in range(1, 5)
}
quiet = np.zeros(RATE * 2, dtype=np.float64)
for name, source in sources.items():
    template = stretch(active_clip(source), 0.83)
    event = np.zeros(RATE * 5, dtype=np.float64)
    offset = int(RATE * 0.8)
    event[offset:offset + len(template)] = template * 0.5
    matched, correlation, pitch = check_samples(quiet, event, sources)
    assert matched in sources, matched
    assert correlation > 0.99, (name, matched, correlation)
    assert abs(pitch - 0.83) < 0.015, (name, matched, pitch)

rng = np.random.default_rng(41111)
noise = rng.normal(0, 0.004, size=RATE * 5)
try:
    check_samples(quiet, noise, sources)
except AssertionError:
    pass
else:
    raise AssertionError("Noise-only negative control incorrectly passed")
print("Four original jar clips: synthetic pitch positives and noise negative: PASS")
