#!/usr/bin/env python3
"""Attribute real Minecraft brace-interaction PCM to the original BETA26 key.ogg."""
from __future__ import annotations
import argparse
from pathlib import Path
import numpy as np
from match_page_audio_pcm import active_clip, best_match, decode

MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08

def check_samples(quiet: np.ndarray, played: np.ndarray, source: np.ndarray):
    clip = active_clip(source)
    found = best_match(played, clip)
    if found is None:
        raise AssertionError("PCM event capture is shorter than original key.ogg")
    background = best_match(quiet, clip)
    correlation, offset = found
    baseline = background[0] if background else 0.0
    print(f"KEY_SOURCE correlation={correlation:.4f} baseline={baseline:.4f} offset={offset:.3f}s")
    if correlation < MIN_CORRELATION or correlation - baseline < MIN_MARGIN:
        raise AssertionError("Real Minecraft PCM is not attributable to original BETA26 key.ogg")
    if not np.any(np.abs(played) > 0.001):
        raise AssertionError("Real Minecraft key interaction PCM is silent")
    print(f"KEY_SOURCE_MATCH=PASS correlation={correlation:.4f}")
    return correlation, offset, baseline

def check_files(baseline: Path, event: Path, source_dir: Path):
    quiet = decode(baseline, raw=True)
    played = decode(event, raw=True)
    source = decode(source_dir / "key.ogg")
    return check_samples(quiet, played, source)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("source_dir", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event, args.source_dir)
