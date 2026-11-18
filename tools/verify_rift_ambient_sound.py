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
    "smokeProbeEnabled()",
    "TC_SMOKE_RIFT_AMBIENT_DUE=YES",
    "TC_SMOKE_RIFT_AMBIENT_CALL=YES",
    "eligiblePlayers=",
):
    assert fragment in method, fragment
assert "TC_SMOKE_RIFT_TICK=YES" in rift, "entity-age probe must be present in tick()"

client_events = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftClientGameEvents.java").read_text()
for fragment in (
    "PlayLevelSoundEvent.AtEntity",
    "event.getEntity() instanceof FluxRiftEntity",
    "PlayLevelSoundEvent.AtPosition",
    "event.getPosition().x",
    "event.getSound().value() == ThaumcraftSounds.EVILPORTAL.get()",
    "TC_SMOKE_RIFT_SOUND_CLIENT_ENTITY_EVENT=YES",
    "TC_SMOKE_RIFT_SOUND_CLIENT_POSITION_EVENT=YES",
):
    assert fragment in client_events, fragment

driver = Path("tools/client_rift_ambient_sound_smoke.py").read_text()
for fragment in (
    "rift-positive-control.pcm",
    "/playsound thaumcraft:evilportal neutral @p ~ ~ ~ 1 1",
    "rift_x=3.5",
    "rift_y=150",
    "rift_z=12.5",
    'send_chat "/tp @p $rift_x $rift_y $rift_z 0 0"',
    'send_chat "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z {RiftSeed:1234567,RiftSize:5,Stability:0.0f}"',
    "RIFT_LISTENER_AT_SOURCE=PASS",
    'send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"',
    "RIFT_LISTENER_PLATFORM=PASS",
    "nearestPlayerDistanceSqr=",
    "RIFT_LISTENER_AT_EVENT=PASS",
    "RIFT_EVILPORTAL_POSITIVE_CONTROL=PASS",
    "query_game_time() {",
    "/time query gametime",
    "RIFT_GAME_TIME_QUERY=PASS",
    "RIFT_GAME_TICK_WINDOW=PASS",
    "TC_RIFT_AMBIENT_ENTITY_STILL_PRESENT",
    "RIFT_AMBIENT_ENTITY_AFTER_TICK_WINDOW=PASS",
    "TC_SMOKE_RIFT_AMBIENT_DUE=YES",
    "RIFT_SERVER_AMBIENT_DUE=PASS",
    "TC_SMOKE_RIFT_AMBIENT_CALL=YES",
    "RIFT_SERVER_SOUND_CALL=PASS",
    "grep -F 'TC_SMOKE_RIFT_SOUND_CLIENT_'",
    'server_pos="${server_due_line#* pos=}"',
    'server_pitch="${server_due_line#* pitch=}"',
    'grep -F "pos=$server_pos"',
    'grep -F "volume=$server_volume"',
    'grep -F "pitch=$server_pitch"',
    "RIFT_CLIENT_SOUND_EVENT=PASS",
    "probe_log=client-smoke/client.log",
    'server_volume="${server_due_line#* volume=}"',
    'server_volume="${server_volume%% *}"',
    "RIFT_PCM_CAPTURE_STOP=PASS",
    "trap 'if [ -n \"${rift_capture_pid:-}\" ]",
    "rift_capture_pid=''",
):
    assert fragment in driver, fragment
assert driver.index("rift-positive-control.pcm") < driver.index("rift-event.pcm"), (
    "the original-sound positive control must be captured separately before the natural Rift window"
)

assert driver.index('send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"') < driver.index('send_chat "/tp @p $rift_x $rift_y $rift_z 0 0"'), "the support platform must exist before teleport"
assert driver.index('send_chat "/tp @p $rift_x $rift_y $rift_z 0 0"') < driver.index(
    'send_chat "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z'
), "the listener must be moved to the Rift source before the entity is summoned"
assert "send_chat '/tp @p 2.5 150 11.5 0 0'" not in driver, (
    "the old 1.4-block offset can exceed a low-volume variable sound's audible radius"
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
