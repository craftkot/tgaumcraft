#!/usr/bin/env python3
"""Positive and negative controls for the original BETA26 jar-brace KEY PCM matcher."""
from pathlib import Path
import numpy as np
from match_key_audio_pcm import check_samples
from match_page_audio_pcm import RATE, decode

source = decode(Path("src/main/resources/assets/thaumcraft/sounds/key.ogg"))
quiet = np.zeros(RATE * 2, dtype=np.float64)
event = np.zeros(RATE * 5, dtype=np.float64)
offset = int(RATE * 0.7)
if offset + len(source) > len(event):
    raise AssertionError("Original key.ogg exceeds the synthetic event window")
event[offset:offset + len(source)] = source * 0.4
correlation, found_offset, baseline = check_samples(quiet, event, source)
assert correlation > 0.99, correlation
assert abs(found_offset - 0.7) < 0.01, found_offset
assert baseline == 0.0, baseline

noise = np.random.default_rng(41113).normal(0, 0.003, size=RATE * 5)
try:
    check_samples(quiet, noise, source)
except AssertionError:
    pass
else:
    raise AssertionError("Noise-only PCM incorrectly matched original key.ogg")
print("Original BETA26 key.ogg synthetic positive and noise-only negative controls: PASS")
