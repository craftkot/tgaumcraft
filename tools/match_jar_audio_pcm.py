#!/usr/bin/env python3
"""Attribute real Minecraft jar-break PCM to one of the four original BETA26 jar OGGs."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import numpy as np
from match_page_audio_pcm import RATE, decode, active_clip, best_match

ORIGINAL_GIT_BLOBS = {
    "jar1.ogg": "ebdb6360276702bf5f72de24e7210943a5f059af",
    "jar2.ogg": "cb1190b2a1584f7928547f2aad21ef503a1ea010",
    "jar3.ogg": "95c7f7116e4f6eaa0e55d4fea4ab648a1854f611",
    "jar4.ogg": "3fe072d39a72182883bc61b284ca2d18043d6fba",
}
PITCHES = np.linspace(0.60, 1.20, 301)
MIN_CORRELATION = 0.20
MIN_MARGIN = 0.08


def stretch(source: np.ndarray, pitch: float) -> np.ndarray:
    indices = np.arange(int(len(source) / pitch), dtype=np.float64) * pitch
    return np.interp(indices, np.arange(len(source)), source)


def check_samples(quiet: np.ndarray, event: np.ndarray,
                  sources: dict[str, np.ndarray]) -> tuple[str, float, float]:
    candidates = []
    for name, source in sources.items():
        clip = active_clip(source)
        for pitch in PITCHES:
            template = stretch(clip, float(pitch))
            found = best_match(event, template)
            if found is None:
                continue
            background = best_match(quiet, template)
            baseline = background[0] if background else 0.0
            candidates.append((found[0], baseline, found[1], float(pitch), name))
    if not candidates:
        raise AssertionError("PCM capture is shorter than every original jar clip")
    ranked = sorted(candidates, key=lambda row: row[0], reverse=True)
    correlation, baseline, offset, pitch, name = ranked[0]
    print(f"JAR_SOURCE_CANDIDATE={name} correlation={correlation:.4f} "
          f"baseline={baseline:.4f} offset={offset:.3f}s pitch={pitch:.4f}")
    if correlation < MIN_CORRELATION or correlation - baseline < MIN_MARGIN:
        print(f"JAR_PCM_DIAGNOSTICS event_rms={np.sqrt(np.mean(event * event)):.6f} "
              f"quiet_rms={np.sqrt(np.mean(quiet * quiet)):.6f}")
        for rank, row in enumerate(ranked[:5], 1):
            print(f"JAR_SOURCE_TOP_{rank} clip={row[4]} correlation={row[0]:.4f} "
                  f"baseline={row[1]:.4f} offset={row[2]:.3f}s pitch={row[3]:.4f}")
        raise AssertionError("Real game PCM is not attributable to an original BETA26 jar clip")
    if not np.any(np.abs(event) > 0.001):
        raise AssertionError("Game PCM is silent")
    print(f"JAR_SOURCE_MATCH=PASS clip={name} correlation={correlation:.4f} pitch={pitch:.4f}")
    return name, correlation, pitch


def check_files(baseline: Path, event: Path, source_dir: Path):
    sources = {}
    for name, expected_blob in ORIGINAL_GIT_BLOBS.items():
        path = source_dir / name
        data = path.read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        if blob != expected_blob:
            raise AssertionError(f"Wrong original BETA26 {name} Git blob: {blob}")
        sources[name] = decode(path)
    return check_samples(decode(baseline, raw=True), decode(event, raw=True), sources)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("source_dir", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event, args.source_dir)
