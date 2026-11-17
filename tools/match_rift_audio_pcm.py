#!/usr/bin/env python3
"""Attribute actual Minecraft PCM to the exact BETA26 evilportal.ogg waveform."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import numpy as np
from match_crystal_audio_pcm import stretch
from match_page_audio_pcm import decode, active_clip, best_match

ORIGINAL_BLOB = "b44b6767fe855520eae55383992510da787d42f4"
PITCHES = np.linspace(0.45, 1.45, 401)
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08


def check_samples(quiet: np.ndarray, event: np.ndarray, source: np.ndarray) -> tuple[float, float]:
    clip = active_clip(source)
    if not len(event):
        raise AssertionError("Missing Flux Rift event PCM")
    candidates = []
    for pitch in PITCHES:
        template = stretch(clip, float(pitch))
        found = best_match(event, template)
        if found is not None:
            candidates.append((found[0], found[1], float(pitch), template))
    if not candidates:
        raise AssertionError("Event capture is shorter than original evilportal.ogg")
    correlation, offset, pitch, template = max(candidates, key=lambda row: row[0])
    background = best_match(quiet, template)
    baseline = background[0] if background else 0.0
    print(f"RIFT_SOUND_CANDIDATE correlation={correlation:.4f} baseline={baseline:.4f} "
          f"offset={offset:.3f}s pitch={pitch:.4f}")
    if correlation < MIN_CORRELATION or correlation - baseline < MIN_MARGIN:
        raise AssertionError("Actual game PCM is not attributable to original BETA26 evilportal.ogg")
    if not np.any(np.abs(event) > 0.001):
        raise AssertionError("Flux Rift event PCM is silent")
    print(f"RIFT_EVILPORTAL_ORIGINAL_PCM=PASS correlation={correlation:.4f} pitch={pitch:.4f}")
    return correlation, pitch


def check_files(quiet_path: Path, event_path: Path, original_path: Path) -> tuple[float, float]:
    data = original_path.read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob != ORIGINAL_BLOB:
        raise AssertionError(f"Wrong original BETA26 evilportal.ogg Git blob: {blob}")
    return check_samples(
        decode(quiet_path, raw=True),
        decode(event_path, raw=True),
        decode(original_path),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("quiet", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("original", type=Path)
    args = parser.parse_args()
    check_files(args.quiet, args.event, args.original)
