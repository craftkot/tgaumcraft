#!/usr/bin/env python3
"""Attribute a real Salis Mundus dust action to BETA26's exact dust.ogg."""
import argparse
import hashlib
from pathlib import Path
import numpy as np
from match_crystal_audio_pcm import stretch
from match_page_audio_pcm import decode, active_clip, best_match

ORIGINAL_BLOB = "cc1934233f9757e735c55c7f36e4da5d86bbf9f4"
PITCHES = np.linspace(0.85, 1.15, 301)
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08

def check_samples(quiet, event, source):
    clip = active_clip(source)
    if not len(clip) or not len(event):
        raise AssertionError("Missing audio samples")
    candidates = []
    for pitch in PITCHES:
        template = stretch(clip, float(pitch))
        found = best_match(event, template)
        if found is not None:
            candidates.append((found[0], found[1], float(pitch), template))
    if not candidates:
        raise AssertionError("Event too short for original dust.ogg")
    corr, offset, pitch, template = max(candidates, key=lambda x: x[0])
    background = best_match(quiet, template)
    baseline = background[0] if background else 0.0
    print(f"DUST_SOURCE_CANDIDATE correlation={corr:.4f} baseline={baseline:.4f} "
          f"offset={offset:.3f}s pitch={pitch:.4f}")
    if corr < MIN_CORRELATION or corr - baseline < MIN_MARGIN:
        raise AssertionError("Game PCM not attributable to original BETA26 dust.ogg")
    if not np.any(np.abs(event) > 0.001):
        raise AssertionError("Event PCM is silent")
    print(f"DUST_ORIGINAL_PCM=PASS correlation={corr:.4f} pitch={pitch:.4f}")
    return corr, pitch

def check_files(quiet, event, original):
    data = Path(original).read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    if blob != ORIGINAL_BLOB:
        raise AssertionError(f"Wrong original dust.ogg blob {blob}")
    return check_samples(decode(Path(quiet), raw=True), decode(Path(event), raw=True),
                         decode(Path(original)))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for name in ("quiet", "event", "original"):
        parser.add_argument(name)
    args = parser.parse_args()
    check_files(args.quiet, args.event, args.original)
