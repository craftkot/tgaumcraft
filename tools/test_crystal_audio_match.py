#!/usr/bin/env python3
"""Deterministic positive and negative tests for crystal source PCM matcher."""
from pathlib import Path
import numpy as np
from match_crystal_audio_pcm import RATE, check_samples, stretch
from match_page_audio_pcm import active_clip, decode

original = decode(Path("src/main/resources/assets/thaumcraft/sounds/crystal.ogg"))
clip = active_clip(original)
rng = np.random.default_rng(412)
quiet = np.zeros(RATE * 2, dtype=np.float64)
played = np.zeros(RATE * 5, dtype=np.float64)
template = stretch(clip, 0.83)
offset = int(RATE * 0.8)
played[offset:offset + len(template)] = template * 0.5
corr, pitch = check_samples(quiet, played, original)
assert corr > 0.9 and abs(pitch - 0.83) < 0.015, (corr, pitch)
noise = rng.normal(0, 0.004, size=RATE * 5)
try:
    check_samples(quiet, noise, original)
except AssertionError:
    pass
else:
    raise AssertionError("Noise-only negative control incorrectly passed")
print("Original crystal pitched-source positive/noise negative: PASS")
