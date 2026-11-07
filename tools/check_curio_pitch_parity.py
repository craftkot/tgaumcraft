#!/usr/bin/env python3
"""BETA26 Curio audible pitch parity from real Minecraft PCM.

Minecraft 1.12.2 SoundManager#getClampedPitch and 1.21.1
SoundEngine#calculatePitch both clamp requested pitch to [0.5, 2.0].
The original randomized [1/3, 1/2] is thus heard at 0.5 in both.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re

REQUEST = re.compile(r"TC_SMOKE_CURIO_LEARN_REQUESTED_PITCH=([0-9]+(?:\.[0-9]+)?)")
PLAYBACK = re.compile(r"CURIO_LEARN_SOURCE_MATCH=PASS correlation=[0-9.]+ pitch=([0-9]+(?:\.[0-9]+)?)")
PITCH_TOLERANCE = 0.015

def check_text(client_log: str, source_match: str) -> tuple[float, float, float]:
    requested = REQUEST.findall(client_log)
    observed = PLAYBACK.findall(source_match)
    if len(requested) != 1 or len(observed) != 1:
        raise AssertionError(f"Expected exactly one server pitch and one real PCM match; got {len(requested)}, {len(observed)}")
    requested_pitch = float(requested[0])
    observed_pitch = float(observed[0])
    if not (1 / 3 - 1e-5 <= requested_pitch <= 0.5 + 1e-5):
        raise AssertionError(f"Curio requested pitch outside BETA26 range: {requested_pitch}")
    expected = max(0.5, min(2.0, requested_pitch))
    if abs(observed_pitch - expected) > PITCH_TOLERANCE:
        raise AssertionError(f"BETA26 audible pitch mismatch: requested={requested_pitch:.5f}, expected={expected:.5f}, real_PCM={observed_pitch:.5f}")
    print(f"CURIO_LEARN_BETA26_AUDIBLE_PITCH_PARITY=PASS requested={requested_pitch:.5f} original_and_modern={expected:.5f} real_PCM={observed_pitch:.5f}")
    return requested_pitch, expected, observed_pitch

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("client_log", type=Path)
    parser.add_argument("source_match", type=Path)
    args = parser.parse_args()
    check_text(args.client_log.read_text(encoding="utf-8", errors="replace"),
               args.source_match.read_text(encoding="utf-8"))
