#!/usr/bin/env python3
"""Verify exact BETA26 Flux Rift ambient sound asset and runtime wiring."""
from pathlib import Path
import hashlib
import json

ROOT = Path("src/main/resources/assets/thaumcraft")
EXPECTED_BLOB = "b44b6767fe855520eae55383992510da787d42f4"
asset = ROOT / "sounds/evilportal.ogg"
data = asset.read_bytes()
blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
assert blob == EXPECTED_BLOB, blob
assert data.startswith(b"OggS"), "evilportal.ogg is not an Ogg stream"

manifest = json.loads((ROOT / "sounds.json").read_text())
assert manifest["evilportal"] == {
    "category": "block",
    "sounds": [{"name": "thaumcraft:evilportal", "stream": False}],
}

registry = Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java").read_text()
assert 'EVILPORTAL = SOUND_EVENTS.register("evilportal"' in registry
assert 'ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "evilportal")' in registry

rift = Path("src/neoforge/java/dev/ehtid/thaumcraft/entity/FluxRiftEntity.java").read_text()
assert "playAmbientSoundIfDue(random);" in rift
method = rift.split("void playAmbientSoundIfDue(RandomSource sourceRandom)", 1)[1].split("@Override", 1)[0]
for fragment in (
    "tickCount % 300 != 0",
    "0.15F + sourceRandom.nextGaussian() * 0.066D",
    "sourceRandom.nextGaussian() * 0.066D",
    "sourceRandom.nextGaussian() * 0.1D",
    "playSound(ThaumcraftSounds.EVILPORTAL.get(), volume, pitch)",
):
    assert fragment in method, fragment

driver = Path("tools/client_rift_ambient_sound_smoke.py").read_text()
for fragment in (
    "rift-positive-control.pcm",
    "/playsound thaumcraft:evilportal neutral @p ~ ~ ~ 1 1",
    "RIFT_EVILPORTAL_POSITIVE_CONTROL=PASS",
    "query_game_time() {",
    "/time query gametime",
    "RIFT_GAME_TIME_QUERY=PASS",
    "RIFT_GAME_TICK_WINDOW=PASS",
    "TC_RIFT_AMBIENT_ENTITY_STILL_PRESENT",
    "RIFT_AMBIENT_ENTITY_AFTER_TICK_WINDOW=PASS",
    "RIFT_PCM_CAPTURE_STOP=PASS",
    "trap 'if [ -n \"${rift_capture_pid:-}\" ]",
    "rift_capture_pid=''",
):
    assert fragment in driver, fragment
assert driver.index("rift-positive-control.pcm") < driver.index("rift-event.pcm"), (
    "the original-sound positive control must be captured separately before the natural Rift window"
)
assert "sleep 46" not in driver, "ambient acceptance must not rely on wall-clock sleep"
assert "current_game_time - start_game_time >= 300" in driver, (
    "the natural sound window must wait for at least 300 server game ticks"
)
assert "timeout --signal=TERM 420s" in driver, (
    "the PCM capture must cover slow integrated-server tick windows"
)
assert driver.index("RIFT_GAME_TICK_WINDOW=PASS") < driver.index("TC_RIFT_AMBIENT_ENTITY_STILL_PRESENT"), (
    "the post-window entity-alive check must follow the measured game-tick cadence"
)

print(f"BETA26 Flux Rift evilportal.ogg blob={blob}, registry, 300-tick Gaussian sound wiring: PASS")
