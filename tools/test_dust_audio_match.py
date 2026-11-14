#!/usr/bin/env python3
"""Deterministic original dust waveform positive/negative attribution controls."""
import numpy as np
from pathlib import Path
from match_page_audio_pcm import decode, active_clip
from match_crystal_audio_pcm import stretch
from match_dust_audio_pcm import check_samples

source = decode(Path("src/main/resources/assets/thaumcraft/sounds/dust.ogg"))
clip = active_clip(source)
rng = np.random.default_rng(2602)
quiet = rng.normal(0, 0.0003, 48000 * 2)
event = rng.normal(0, 0.0003, 48000 * 4)
sample = stretch(clip, 1.035)
offset = 48000
assert len(sample) < len(event) - offset
event[offset:offset + len(sample)] += sample * 0.33
corr, pitch = check_samples(quiet, event, source)
assert corr > 0.8 and abs(pitch - 1.035) < 0.01, (corr, pitch)
try:
    check_samples(quiet, rng.normal(0, 0.003, len(event)), source)
except AssertionError:
    pass
else:
    raise AssertionError("Noise-only event incorrectly passed")
print("BETA26 dust source-positive and noise-negative controls: PASS")
