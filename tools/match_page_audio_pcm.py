#!/usr/bin/env python3
"""Attribute actual Minecraft PulseAudio PCM to either original BETA26 page OGG.

FFT cross-correlation tests waveform identity, not subjective listening quality.
The raw 48-kHz stereo monitor and source OGGs are resampled to 8-kHz mono.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import numpy as np

RATE = 8000
MIN_CORRELATION = 0.20
MIN_MARGIN_OVER_BASELINE = 0.08


def decode(path: Path, raw: bool = False) -> np.ndarray:
    args = ["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error"]
    if raw:
        args += ["-f", "s16le", "-ar", "48000", "-ac", "2"]
    args += ["-i", str(path), "-vn", "-ac", "1", "-ar", str(RATE),
             "-f", "f32le", "pipe:1"]
    result = subprocess.run(args, check=True, capture_output=True)
    samples = np.frombuffer(result.stdout, dtype="<f4").astype(np.float64)
    if not samples.size or not np.all(np.isfinite(samples)):
        raise ValueError(f"Invalid or empty audio: {path}")
    return samples


def active_clip(samples: np.ndarray) -> np.ndarray:
    threshold = max(0.002, float(np.max(np.abs(samples))) * 0.03)
    active = np.flatnonzero(np.abs(samples) >= threshold)
    if not active.size:
        raise ValueError("Original source OGG is silent")
    pad = int(0.03 * RATE)
    clip = samples[max(0, int(active[0]) - pad):
                   min(len(samples), int(active[-1]) + pad + 1)]
    if len(clip) < RATE // 8:
        raise ValueError("Source OGG has insufficient active waveform")
    return clip


def best_match(recording: np.ndarray, template: np.ndarray) -> tuple[float, float] | None:
    """Return max absolute normalized Pearson correlation and time offset."""
    n, m = len(recording), len(template)
    if m > n:
        return None
    centered = template - np.mean(template)
    energy = float(np.dot(centered, centered))
    if energy < 1e-8:
        raise ValueError("Source OGG has no useful waveform variation")
    size = 1 << (n + m - 2).bit_length()
    # Cross-correlation at offset k: sum(recording[k+j] * centered[j]).
    spectrum = np.fft.rfft(recording, size) * np.conj(np.fft.rfft(centered, size))
    numerator = np.fft.irfft(spectrum, size)[:n - m + 1]
    sums = np.concatenate(([0.0], np.cumsum(recording)))
    squares = np.concatenate(([0.0], np.cumsum(recording * recording)))
    window_sum = sums[m:] - sums[:-m]
    variance = np.maximum(0.0, squares[m:] - squares[:-m] - window_sum**2 / m)
    denominator = np.sqrt(variance * energy)
    correlations = np.divide(numerator, denominator,
                             out=np.zeros_like(numerator), where=denominator > 1e-10)
    index = int(np.argmax(np.abs(correlations)))
    return float(abs(correlations[index])), index / RATE


def check(baseline: Path, event: Path, source_dir: Path) -> tuple[str, float]:
    quiet = decode(baseline, raw=True)
    played = decode(event, raw=True)
    candidates = []
    for name in ("page1", "page2"):
        source = active_clip(decode(source_dir / (name + ".ogg")))
        found = best_match(played, source)
        if found is None:
            raise AssertionError(f"PCM event capture shorter than {name} waveform")
        correlation, offset = found
        background = best_match(quiet, source)
        baseline_correlation = background[0] if background else 0.0
        print(f"PAGE_SOURCE_CANDIDATE={name} correlation={correlation:.4f} "
              f"offset={offset:.3f}s baseline_correlation={baseline_correlation:.4f} "
              f"source_duration={len(source) / RATE:.3f}s")
        candidates.append((correlation, name, baseline_correlation))
    correlation, name, background = max(candidates)
    if correlation < MIN_CORRELATION or correlation - background < MIN_MARGIN_OVER_BASELINE:
        raise AssertionError("Actual PCM not attributable to either original BETA26 page OGG: "
                             f"best={name} correlation={correlation:.4f} baseline={background:.4f}")
    print(f"PAGE_SOURCE_CLIP_MATCH=PASS clip={name} correlation={correlation:.4f}")
    return name, correlation


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    parser.add_argument("source_dir", type=Path)
    args = parser.parse_args()
    check(args.baseline, args.event, args.source_dir)
