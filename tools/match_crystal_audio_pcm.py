#!/usr/bin/env python3
"""Match real crystal-break PCM against original BETA26 crystal.ogg.

Vanilla block-break playback applies a random pitch, so fit a bounded range.
This test requires waveform attribution, not merely a non-silent sink.
"""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import numpy as np
from match_page_audio_pcm import RATE, decode, active_clip, best_match

ORIGINAL_GIT_BLOB = "f0133f05feb6ea9526a5121c4d389bac1cb1b8a8"
PITCHES = np.linspace(0.60, 1.20, 601)
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08


def stretch(source: np.ndarray, pitch: float) -> np.ndarray:
    indices = np.arange(int(len(source) / pitch), dtype=np.float64) * pitch
    return np.interp(indices, np.arange(len(source)), source)


def check_samples(quiet: np.ndarray, event: np.ndarray,
                  source: np.ndarray) -> tuple[float, float]:
    clip = active_clip(source)
    candidates = []
    for pitch in PITCHES:
        template = stretch(clip, float(pitch))
        found = best_match(event, template)
        if found is not None:
            candidates.append((found[0], found[1], float(pitch), template))
    if not candidates:
        raise AssertionError("Event shorter than original crystal clip")
    ranked = sorted(candidates, key=lambda x: x[0], reverse=True)
    corr, offset, pitch, template = ranked[0]
    background = best_match(quiet, template)
    baseline = background[0] if background else 0.0
    print(f"CRYSTAL_SOURCE_CANDIDATE correlation={corr:.4f} "
          f"baseline={baseline:.4f} offset={offset:.3f}s pitch={pitch:.4f}")
    if corr < MIN_CORRELATION or corr - baseline < MIN_MARGIN:
        print("CRYSTAL_SOURCE_DIAGNOSTICS "
              f"event_rms={np.sqrt(np.mean(event * event)):.6f} "
              f"quiet_rms={np.sqrt(np.mean(quiet * quiet)):.6f} "
              f"source_rms={np.sqrt(np.mean(source * source)):.6f} "
              f"event_duration={len(event) / RATE:.3f}s "
              f"source_duration={len(source) / RATE:.3f}s")
        for rank, (score, candidate_offset, candidate_pitch, _) in enumerate(ranked[:5], 1):
            print(f"CRYSTAL_SOURCE_TOP_{rank} correlation={score:.4f} "
                  f"offset={candidate_offset:.3f}s pitch={candidate_pitch:.4f}")
        if pitch in (float(PITCHES[0]), float(PITCHES[-1])):
            print("CRYSTAL_SOURCE_PITCH_BOUNDARY=YES")
        raise AssertionError("Game PCM not attributable to original crystal.ogg")
    if not np.any(np.abs(event) > 0.001):
        raise AssertionError("Game PCM is silent")
    print(f"CRYSTAL_SOURCE_MATCH=PASS correlation={corr:.4f} pitch={pitch:.4f}")
    return corr, pitch


def check_files(baseline: Path, event: Path, original: Path):
    data = original.read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob != ORIGINAL_GIT_BLOB:
        raise AssertionError(f"Wrong crystal.ogg Git blob {blob}")
    return check_samples(decode(baseline, raw=True), decode(event, raw=True),
                         decode(original))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("original", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event, args.original)
