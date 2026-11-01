#!/usr/bin/env python3
"""Attribute a real Curio right-click PCM to BETA26 learn.ogg at randomized pitch.

Original ItemCurio uses pitch 0.4/(rand*0.4+0.8): [1/3, 1/2].
Resample the source waveform across that range before normalized correlation.
This is real PCM attribution, not a subjective listening test.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import numpy as np
from match_page_audio_pcm import RATE, decode, active_clip, best_match

ORIGINAL_GIT_BLOB = "abe84e11186ff87d133b9f8aeeede5b85a9e2be6"
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08
PITCHES = np.linspace(1 / 3, 1 / 2, 1001)


def stretched(source: np.ndarray, pitch: float) -> np.ndarray:
    # OpenAL pitch scales the playback sample rate (and therefore duration).
    indices = np.arange(int(len(source) / pitch), dtype=np.float64) * pitch
    return np.interp(indices, np.arange(len(source)), source)


def check_samples(quiet: np.ndarray, played: np.ndarray,
                  original: np.ndarray) -> tuple[float, float, float]:
    clip = active_clip(original)
    candidates = []
    for pitch in PITCHES:
        template = stretched(clip, float(pitch))
        match = best_match(played, template)
        if match is None:
            continue
        correlation, offset = match
        candidates.append((correlation, offset, float(pitch), template))
    if not candidates:
        raise AssertionError("PCM event shorter than pitched original learn.ogg")
    correlation, offset, pitch, template = max(candidates, key=lambda x: x[0])
    baseline = best_match(quiet, template)
    background = baseline[0] if baseline else 0.0
    print(f"CURIO_LEARN_SOURCE_CANDIDATE correlation={correlation:.4f} "
          f"offset={offset:.3f}s pitch={pitch:.4f} "
          f"baseline_correlation={background:.4f}")
    if correlation < MIN_CORRELATION or correlation - background < MIN_MARGIN:
        raise AssertionError("Curio PCM not attributable to original pitched learn.ogg")
    if not np.any(np.abs(played) > 0.001):
        raise AssertionError("Curio PCM is silent")
    print(f"CURIO_LEARN_SOURCE_MATCH=PASS correlation={correlation:.4f} pitch={pitch:.4f}")
    return correlation, offset, pitch


def check_files(baseline: Path, event: Path, original: Path):
    data = original.read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob != ORIGINAL_GIT_BLOB:
        raise AssertionError(f"learn.ogg differs from BETA26 source: {blob}")
    print("CURIO_LEARN_ORIGINAL_GIT_BLOB=PASS")
    return check_samples(decode(baseline, raw=True), decode(event, raw=True),
                         decode(original))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("original", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event, args.original)
