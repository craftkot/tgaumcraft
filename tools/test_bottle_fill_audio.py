#!/usr/bin/env python3
"""Positive/negative mixed-PCM and asset-index tests for vanilla bottle-fill matching."""
from pathlib import Path
import hashlib
import json
import tempfile
import numpy as np

from match_bottle_fill_audio_pcm import (
    check_samples, locate_bottle_fill_sources, stretch,
)


def wave(seed: int) -> np.ndarray:
    rng = np.random.default_rng(seed)
    t = np.arange(8000, dtype=np.float64) / 8000.0
    envelope = np.sin(np.linspace(0.0, np.pi, len(t))) ** 2
    base = 220.0 + seed * 37.0
    sweep = 300.0 + seed * 41.0
    chirp = np.sin(2.0 * np.pi * (base * t + sweep * t * t))
    overtone = 0.31 * np.sin(2.0 * np.pi * (950.0 + seed * 67.0) * t + 0.2)
    return envelope * (chirp + overtone) + rng.normal(0.0, 0.0002, len(t))


rng = np.random.default_rng(42)
source = wave(3)
other = wave(8)
pitch = 1.12
played = stretch(source, pitch)
quiet = rng.normal(0.0, 0.001, 16000)
event = rng.normal(0.0, 0.001, 32000)
event[7000:7000 + len(played)] += played * 0.45
event[4000:4000 + len(other)] += other * 0.18
name, correlation, measured_pitch, _ = check_samples(
    quiet, event, {"fill3.ogg": source}, pitches=np.linspace(0.90, 1.25, 36)
)
assert name == "fill3.ogg"
assert correlation > 0.80
assert abs(measured_pitch - pitch) <= 0.02

noise = rng.normal(0.0, 0.001, 32000)
try:
    check_samples(quiet, noise, {"fill3.ogg": source}, pitches=np.linspace(0.90, 1.25, 36))
except AssertionError:
    pass
else:
    raise AssertionError("Noise-only PCM falsely matched vanilla bottle-fill")

with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    assets = root / "assets"
    index_dir = assets / "indexes"
    index_dir.mkdir(parents=True)
    objects = {}
    for i in range(1, 5):
        name = f"minecraft/sounds/item/bottle/fill{i}.ogg"
        payload = b"OggS" + bytes([i]) * 20
        digest = hashlib.sha1(payload).hexdigest()
        target = assets / "objects" / digest[:2] / digest
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        objects[name] = {"hash": digest, "size": len(payload)}
    (index_dir / "17.json").write_text(json.dumps({"objects": objects}), encoding="utf-8")
    resolved = locate_bottle_fill_sources([root])
    assert set(resolved) == {f"fill{i}.ogg" for i in range(1, 5)}
    assert all(path.read_bytes().startswith(b"OggS") for path in resolved.values())

print("Vanilla bottle-fill matcher: mixed positive, noise negative, pitch range and asset-index lookup PASS")
