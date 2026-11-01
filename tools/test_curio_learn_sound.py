#!/usr/bin/env python3
"""M16 BETA26 Curio sound source contract and deterministic pitched-PCM controls."""
import hashlib
import json
from pathlib import Path
import numpy as np
from match_curio_audio_pcm import check_samples, stretched

root = Path(__file__).resolve().parent.parent
asset = root / "src/main/resources/assets/thaumcraft"
data = (asset / "sounds/learn.ogg").read_bytes()
assert data.startswith(b"OggS")
assert hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() == (
    "abe84e11186ff87d133b9f8aeeede5b85a9e2be6"
)
sounds = json.loads((asset / "sounds.json").read_text())
assert sounds["learn"] == {
    "category": "master", "sounds": [{"name": "thaumcraft:learn", "stream": False}]
}
registry = (root / "src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java").read_text()
item = (root / "src/neoforge/java/dev/ehtid/thaumcraft/item/CurioItem.java").read_text()
assert 'SOUND_EVENTS.register("learn"' in registry
assert 'ThaumcraftSounds.LEARN.get(), SoundSource.NEUTRAL, 0.5F' in item
assert '0.4F / (LEARN_SOUND_RANDOM.nextFloat() * 0.4F + 0.8F)' in item
assert item.index("level.playSound(null,") < item.index("if (index == 6 && actualWarp <= 20)")
assert item.index("if (level.isClientSide)") < item.index("level.playSound(null,")
assert 'CURIO_LEARN_SOUND_DISPATCHED' in item
print("CURIO_LEARN_SOURCE_AND_TRIGGER_CONTRACT=PASS")

rng = np.random.default_rng(20261101)
original = rng.normal(0, 0.15, 2800).astype(np.float64)
quiet = rng.normal(0, 0.0005, 18000)
event = rng.normal(0, 0.0005, 36000)
pitch = 0.42
clip = stretched(original, pitch)
event[8000:8000 + len(clip)] += 0.5 * clip
correlation, offset, matched_pitch = check_samples(quiet, event, original)
assert correlation > 0.75, correlation
assert 0.9 < offset < 1.1, offset
assert abs(matched_pitch - pitch) < 0.012, matched_pitch
try:
    check_samples(quiet, rng.normal(0, 0.0005, 36000), original)
except AssertionError:
    print("CURIO_LEARN_NEGATIVE_CONTROL=PASS")
else:
    raise AssertionError("Noise-only PCM incorrectly matched learn.ogg")
print("CURIO_LEARN_PITCHED_PCM_CONTROLS=PASS")
