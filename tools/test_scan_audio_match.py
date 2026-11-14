#!/usr/bin/env python3
"""Deterministic positive and negative controls for scan-source PCM matching."""
import numpy as np
from match_scan_audio_pcm import check_samples

rng = np.random.default_rng(20261101)
original = rng.normal(0, 0.16, 3200).astype(np.float64)
quiet = rng.normal(0, 0.0005, 16000)
played = rng.normal(0, 0.0005, 32000)
played[9000:12200] += original * 0.45

correlation, offset = check_samples(quiet, played, original)
assert correlation > 0.99, correlation
assert 1.0 < offset < 1.2, offset

unrelated = rng.normal(0, 0.0005, 32000)
try:
    check_samples(quiet, unrelated, original)
except AssertionError:
    print("SCAN_SOURCE_NEGATIVE_CONTROL=PASS")
else:
    raise AssertionError("Unrelated audio was incorrectly accepted")
print("BETA26 scan-source matching controls: PASS")
