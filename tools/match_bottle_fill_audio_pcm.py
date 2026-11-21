#!/usr/bin/env python3
"""Match the vanilla 1.21.1 bottle-fill OGG mixed into the real jar-empty PCM."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import numpy as np

from match_page_audio_pcm import RATE, active_clip, best_match, decode

PITCHES = np.linspace(0.68, 1.32, 129)
MIN_CORRELATION = 0.16
MIN_MARGIN = 0.05
ASSET_NAMES = tuple(
    f"minecraft/sounds/item/bottle/fill{i}.ogg" for i in range(1, 5)
)


def stretch(source: np.ndarray, pitch: float) -> np.ndarray:
    if not 0.0 < pitch:
        raise ValueError("pitch must be positive")
    indices = np.arange(max(1, int(len(source) / pitch)), dtype=np.float64) * pitch
    return np.interp(indices, np.arange(len(source)), source)


def locate_bottle_fill_sources(search_roots=None) -> dict[str, Path]:
    """Resolve vanilla sound objects through the asset index used by runClient."""
    roots = search_roots or (
        Path("run"),
        Path.home() / ".gradle",
        Path.home() / ".minecraft",
    )
    seen = set()
    for root in roots:
        if not root.exists():
            continue
        for index_dir in root.rglob("indexes"):
            if not index_dir.is_dir() or index_dir.parent.name != "assets":
                continue
            for index_path in sorted(index_dir.glob("*.json")):
                if index_path in seen:
                    continue
                seen.add(index_path)
                try:
                    objects = json.loads(index_path.read_text(encoding="utf-8")).get("objects", {})
                except (OSError, json.JSONDecodeError):
                    continue
                entries = {name: objects.get(name) for name in ASSET_NAMES}
                if not all(isinstance(entry, dict) and entry.get("hash") for entry in entries.values()):
                    continue
                asset_root = index_dir.parent
                resolved = {}
                for name, entry in entries.items():
                    digest = entry["hash"]
                    path = asset_root / "objects" / digest[:2] / digest
                    if not path.is_file() or path.stat().st_size != entry.get("size"):
                        break
                    if not path.read_bytes().startswith(b"OggS"):
                        break
                    resolved[name.rsplit("/", 1)[-1]] = path
                if len(resolved) == len(ASSET_NAMES):
                    print(f"VANILLA_BOTTLE_ASSET_INDEX={index_path}")
                    return resolved
    searched = ", ".join(str(root) for root in roots)
    raise FileNotFoundError(
        "Could not resolve 1.21.1 vanilla item/bottle/fill1..4.ogg from a Minecraft asset index; "
        f"searched: {searched}"
    )


def check_samples(quiet: np.ndarray, event: np.ndarray,
                  sources: dict[str, np.ndarray], pitches=PITCHES):
    if not sources:
        raise AssertionError("No vanilla bottle-fill source clips were supplied")
    candidates = []
    for name, source in sources.items():
        clip = active_clip(source)
        for pitch in pitches:
            template = stretch(clip, float(pitch))
            found = best_match(event, template)
            if found is None:
                continue
            background = best_match(quiet, template)
            baseline = background[0] if background else 0.0
            candidates.append((found[0], baseline, found[1], float(pitch), name))
    if not candidates:
        raise AssertionError("PCM capture is shorter than every vanilla bottle-fill clip")
    ranked = sorted(candidates, key=lambda row: row[0], reverse=True)
    correlation, baseline, offset, pitch, name = ranked[0]
    print(
        f"BOTTLE_FILL_SOURCE_CANDIDATE={name} correlation={correlation:.4f} "
        f"baseline={baseline:.4f} offset={offset:.3f}s pitch={pitch:.4f}"
    )
    if correlation < MIN_CORRELATION or correlation - baseline < MIN_MARGIN:
        raise AssertionError(
            "Real game PCM is not attributable to a vanilla bottle-fill clip: "
            f"clip={name} correlation={correlation:.4f} baseline={baseline:.4f}"
        )
    if not 0.68 <= pitch <= 1.32:
        raise AssertionError(f"Measured bottle-fill pitch is outside BETA26 tolerance: {pitch}")
    print(
        f"BOTTLE_FILL_SOURCE_MATCH=PASS clip={name} correlation={correlation:.4f} "
        f"pitch={pitch:.4f}"
    )
    return name, correlation, pitch, offset


def check_files(baseline: Path, event: Path):
    paths = locate_bottle_fill_sources()
    sources = {name: decode(path) for name, path in paths.items()}
    return check_samples(decode(baseline, raw=True), decode(event, raw=True), sources)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("event", type=Path)
    args = parser.parse_args()
    check_files(args.baseline, args.event)
