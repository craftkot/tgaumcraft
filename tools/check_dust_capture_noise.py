#!/usr/bin/env python3
"""Fail fast when unrelated Minecraft audio masks the quiet BETA26 Salis dust event.

The Salis sound uses SoundSource.PLAYERS, so the isolated real-client smoke
mutes only music and ambient categories; it must not mute players/master.
"""
import argparse
from pathlib import Path
import numpy as np

MAX_BASELINE_RMS = 0.01
MIN_SAMPLES = 48000 * 2  # at least one second of stereo 48 kHz s16le


def check_baseline(data: bytes) -> float:
    if len(data) % 4 or len(data) < MIN_SAMPLES * 2:
        raise AssertionError("Invalid/short 48 kHz stereo s16le baseline capture")
    pcm = np.frombuffer(data, dtype="<i2").astype(np.float64) / 32768.0
    rms = float(np.sqrt(np.mean(pcm * pcm)))
    print(f"DUST_BASELINE_RMS={rms:.6f} limit={MAX_BASELINE_RMS:.6f}")
    if not np.isfinite(rms) or rms > MAX_BASELINE_RMS:
        raise AssertionError("Background audio masks quiet dust.ogg; inspect music/ambient mute")
    return rms


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    args = parser.parse_args()
    check_baseline(args.baseline.read_bytes())
