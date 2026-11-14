#!/usr/bin/env python3
"""Attribute a real Thaumometer air-click PCM recording to original BETA26 scan.ogg.

The scan sound plays even when no block/entity is successfully scanned in BETA26.
This is a real-client audio acceptance test, not a substitute for scan progression.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import numpy as np

from match_page_audio_pcm import decode, active_clip, best_match

# analysis/thaumometer_resource_audit.txt: byte-identical BETA26 original.
ORIGINAL_SCAN_SHA256 = "6dba5438b6ca6778bb9a8ffde9e5a1966eae3e03e8dc2b2d29e98e6bc12c9bc3"
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08


def check_samples(quiet: np.ndarray, played: np.ndarray,
                  original: np.ndarray) -> tuple[float, float]:
    template = active_clip(original)
    result = best_match(played, template)
    if result is None:
        raise AssertionError("Event capture shorter than original scan.ogg")
    correlation, offset = result
    baseline = best_match(quiet, template)
    background = baseline[0] if baseline else 0.0
    print(f"SCAN_SOURCE_CANDIDATE correlation={correlation:.4f} "
          f"offset={offset:.3f}s baseline_correlation={background:.4f} "
          f"source_duration={len(template) / 8000:.3f}s")
    if correlation < MIN_CORRELATION or correlation - background < MIN_MARGIN:
        raise AssertionError("Actual scan PCM does not match original BETA26 scan.ogg "
                             f"(correlation={correlation:.4f}, baseline={background:.4f})")
    if not np.any(np.abs(played) > 0.001):
        raise AssertionError("Captured PCM is silent")
    print(f"SCAN_SOURCE_CLIP_MATCH=PASS correlation={correlation:.4f}")
    return correlation, offset


def check_files(baseline: Path, event: Path, original: Path) -> tuple[float, float]:
    sha = hashlib.sha256(original.read_bytes()).hexdigest()
    if sha != ORIGINAL_SCAN_SHA256:
        raise AssertionError(f"scan.ogg does not match audited BETA26 original: {sha}")
    print("SCAN_SOURCE_ORIGINAL_SHA256=PASS")
    return check_samples(decode(baseline, raw=True),
                         decode(event, raw=True), decode(original))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("original", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event, args.original)
