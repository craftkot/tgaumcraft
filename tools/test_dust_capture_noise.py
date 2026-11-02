#!/usr/bin/env python3
"""Adversarial baseline tests; no waveform attribution threshold is changed."""
import sys
from pathlib import Path
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_dust_capture_noise import check_baseline

samples = 48000 * 2
silence = np.zeros((samples, 2), dtype="<i2").tobytes()
assert check_baseline(silence) == 0.0
quiet = np.full((samples, 2), 100, dtype="<i2").tobytes()
assert check_baseline(quiet) < 0.01
for data in (np.full((samples, 2), 3000, dtype="<i2").tobytes(), b"", b"x"):
    try:
        check_baseline(data)
    except AssertionError:
        pass
    else:
        raise AssertionError("Noisy/malformed baseline unexpectedly accepted")
print("Dust quiet/noisy/malformed baseline: 5/5 PASS")
