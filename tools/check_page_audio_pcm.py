#!/usr/bin/env python3
"""Validate captured OpenAL/PulseAudio PCM before source-clip attribution.

Energy is a capture-health diagnostic, not an event identity test. Minecraft
ambient/menu sounds can make the pre-click recording louder than the desired
sound; the downstream original-OGG cross-correlation is the acceptance gate.
"""
from array import array
import math
from pathlib import Path
import sys

SAMPLE_RATE = 48000
CHANNELS = 2
WINDOW_FRAMES = 4800


def inspect(path: Path):
    data = path.read_bytes()
    if len(data) % 2:
        raise ValueError(f"{path}: incomplete signed-16 sample")
    samples = array("h")
    samples.frombytes(data)
    if sys.byteorder != "little":
        samples.byteswap()
    seconds = len(samples) / (SAMPLE_RATE * CHANNELS)
    window = WINDOW_FRAMES * CHANNELS
    energies = []
    for start in range(0, len(samples) - window + 1, window):
        segment = samples[start:start + window]
        energies.append(math.sqrt(sum(float(v) * v for v in segment) / len(segment)) / 32768.0)
    return seconds, max(energies, default=0.0), max((abs(v) for v in samples), default=0) / 32768.0


def check(baseline: Path, event: Path):
    b_duration, b_rms, b_peak = inspect(baseline)
    e_duration, e_rms, e_peak = inspect(event)
    print(f"PAGE_PCM_BASELINE duration={b_duration:.2f}s max_100ms_rms={b_rms:.5f} peak={b_peak:.5f}")
    print(f"PAGE_PCM_EVENT duration={e_duration:.2f}s max_100ms_rms={e_rms:.5f} peak={e_peak:.5f}")
    if b_duration < 1.2 or e_duration < 3.0:
        raise AssertionError("PCM capture incomplete: PulseAudio monitor or parec failed")
    if e_rms < 0.001 or e_peak < 0.003:
        raise AssertionError("Event PCM capture is effectively silent")
    print("PAGE_PCM_CAPTURE=PASS (event identity requires downstream original-OGG matcher)")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: check_page_audio_pcm.py baseline.pcm event.pcm")
    check(Path(sys.argv[1]), Path(sys.argv[2]))
