#!/usr/bin/env python3
"""M16 BETA26 Curio sound source contract and deterministic pitched-PCM controls."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
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
assert 'ThaumcraftSounds.LEARN.get(), SoundSource.NEUTRAL, 0.5F, requestedPitch' in item
assert 'TC_SMOKE_CURIO_LEARN_REQUESTED_PITCH=' in item
assert '0.4F / (LEARN_SOUND_RANDOM.nextFloat() * 0.4F + 0.8F)' in item
assert item.index("level.playSound(null,") < item.index("if (index == 6 && actualWarp <= 20)")
assert item.index("if (level.isClientSide)") < item.index("level.playSound(null,")
assert 'CURIO_LEARN_SOUND_DISPATCHED' in item
smoke_generator = (root / "tools/client_curio_learn_sound_smoke.py").read_text()
workflow = (root / ".github/workflows/closure-curio-learn-client.yml").read_text()
assert "export ALSOFT_DRIVERS=pulse" in smoke_generator
assert "ALSOFT_LOGLEVEL=3" in smoke_generator
assert "tools/verify_curio_pulse_sink.py" in smoke_generator
assert "tools/verify_curio_openal_backend.py" in smoke_generator
assert "CURIO_LEARN_OPENAL_PULSE_ROUTE=PASS" not in smoke_generator
assert "OpenAL initialized on device Null Output" not in smoke_generator
assert 'export PULSE_SERVER="unix:${XDG_RUNTIME_DIR}/pulse/native"' in workflow
assert "tools/test_curio_pulse_sink.py" in workflow

# Exercise the generator itself: mute only background audio before runClient.
# The Curio event must remain audible; its playback uses SoundSource.NEUTRAL.
generated_result = subprocess.run(
    [sys.executable, str(root / "tools/client_curio_learn_sound_smoke.py")],
    cwd=root, capture_output=True, text=True,
)
assert generated_result.returncode == 0, generated_result.stderr
generated = generated_result.stdout
isolated_options = "printf 'guiScale:2\\nsoundCategory_music:0.0\\nsoundCategory_ambient:0.0\\n' > run/options.txt"
assert isolated_options in generated
assert generated.index(isolated_options) < generated.index("gradle runClient")
assert "CURIO_LEARN_BACKGROUND_AUDIO_ISOLATION=PASS" in generated
assert "soundCategory_neutral:0.0" not in generated
assert "soundCategory_master:0.0" not in generated
print("CURIO_LEARN_BACKGROUND_AUDIO_ISOLATION=PASS")
print("CURIO_LEARN_SOURCE_AND_TRIGGER_CONTRACT=PASS")
print("CURIO_LEARN_REAL_PULSE_CAPTURE_GUARD=PASS")

rng = np.random.default_rng(20261101)
original = rng.normal(0, 0.15, 2800).astype(np.float64)
quiet = rng.normal(0, 0.0005, 18000)
event = rng.normal(0, 0.0005, 36000)
pitch = 0.42013  # deliberately not a grid point; randomized BETA26 pitch
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
