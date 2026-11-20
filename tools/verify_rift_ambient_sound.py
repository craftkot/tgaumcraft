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
    'send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"',
    'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"',
    'send_chat "/tp @p $rift_x $rift_y $rift_z 0 0"',
    'send_chat "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z',
    "send_chat_fast() {",
    "sleep 0.1",
    'xdotool type --clearmodifiers --delay 5 "$1"',
    "sleep 0.25",
    "TC_RIFT_PLATFORM_PRESENT",
    "TC_RIFT_LISTENER_AT_SOURCE",
    "RIFT_LISTENER_AT_SOURCE=PASS",
    "spawn_game_time=",
    "RIFT_GAME_TIME_QUERY=PASS",
    "event_log_offset=",
    "parec --device=tc_rift_sink.monitor",
    "rift_capture_pid=$!",
    "RIFT_GAME_TICK_WINDOW=PASS",
    "if [ \"$server_tick_count\" -ne 300 ]; then",
    "RIFT_LISTENER_AT_EVENT=PASS",
    "RIFT_SERVER_AMBIENT_DUE=PASS",
    "RIFT_SERVER_SOUND_CALL=PASS",
    "RIFT_CLIENT_SOUND_EVENT=PASS",
    "send_chat '/tick freeze'",
    "sleep 34",
    "RIFT_TICK_FREEZE=PASS",
    "RIFT_SINGLE_EVENT_CAPTURE_WINDOW=PASS",
    "client_event_signatures",
    "RIFT_PCM_CAPTURE_STOP=PASS",
    'trap \'if [ -n "${rift_capture_pid:-}" ]',
    'kill -TERM "$rift_capture_pid"',
    'wait "$rift_capture_pid"',
    "RIFT_EVILPORTAL_POSITIVE_CONTROL=PASS",
    "nearestPlayerDistanceSqr=",
    "RIFT_OPENAL_DEVICE=PASS",
):
    assert fragment in driver, fragment

assert "timeout --signal=TERM 3s parec" in driver, "quiet baseline capture must remain bounded"
assert "timeout --signal=TERM 30s parec" in driver, "positive-control capture must remain bounded"
assert "timeout --signal=TERM 420s parec" not in driver, (
    "natural PCM capture must own the direct parec PID, not a timeout wrapper with an orphanable child"
)
assert "current_game_time - start_game_time >= 300" not in driver, (
    "do not wait through multiple 300-tick ambient events"
)

high_teleport = 'send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"'
platform_fill = 'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"'
platform_marker = "TC_RIFT_PLATFORM_PRESENT"
source_teleport = 'send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"'
rift_summon = 'send_chat "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z'
runtime_template = driver.split("high_teleport_command =", 1)[0]
assert runtime_template.count(high_teleport) == 1 and runtime_template.count(platform_fill) == 1, (
    "the fast setup commands must be unique in the runtime template"
)
assert runtime_template.count(source_teleport) == 1, (
    "the supported source teleport must be unique in the runtime template"
)
render_capture = "capture rift-ambient-entity-present"
render_gate = "'layer=0 blend=ADDITIVE depthWrite=OFF'"
assert runtime_template.index(render_capture) < runtime_template.index(render_gate), (
    "capture the actual post-spawn client frame before asserting render markers, including on failure"
)
assert (
    runtime_template.index(high_teleport)
    < runtime_template.index(platform_fill)
    < runtime_template.index(platform_marker)
    < runtime_template.index(source_teleport)
    < runtime_template.index("RIFT_LISTENER_AT_SOURCE=PASS")
    < runtime_template.index("spawn_game_time=")
    < runtime_template.index("> client-smoke/rift-event.pcm")
    < runtime_template.index(rift_summon)
), (
    "load the target chunk, place support, validate the listener, then start a fresh PCM capture before summoning"
)
assert (
    runtime_template.index("RIFT_CLIENT_SOUND_EVENT=PASS")
    < runtime_template.index("send_chat '/tick freeze'")
    < runtime_template.index("sleep 34")
    < runtime_template.rindex('kill -TERM "$rift_capture_pid"')
), (
    "freeze after the first received sound and keep the direct recorder alive for the full source clip"
)
assert "TC_RIFT_LISTENER_STILL_AT_SOURCE" not in driver, (
    "the old multi-cadence listener probe must not reintroduce a second ambient event"
)

renderer = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/FluxRiftRenderer.java").read_text()
for fragment in (
    "for (int layer = 0; layer <= 3; layer++)",
    "boolean additive = layer < 3",
    "RiftRenderTypes.additive()",
    "RiftRenderTypes.alphaCore()",
    "TC_SMOKE_RIFT_RENDER_LAYER=PASS",
    "depthWrite=",
):
    assert fragment in renderer, fragment
assert "buffers.getBuffer(RenderType.lightning())" not in renderer, (
    "the old all-additive four-layer pass must not return"
)
render_types = renderer.split("private static final class RiftRenderTypes", 1)[1]
for fragment in (
    "RENDERTYPE_LIGHTNING_SHADER",
    "LIGHTNING_TRANSPARENCY",
    "TRANSLUCENT_TRANSPARENCY",
    "COLOR_WRITE",
    "COLOR_DEPTH_WRITE",
    "LEQUAL_DEPTH_TEST",
    "NO_CULL",
    "DefaultVertexFormat.POSITION_COLOR",
    "VertexFormat.Mode.QUADS",
    "setTextureState(NO_TEXTURE)",
):
    assert fragment in render_types, fragment
for fragment in (
    "layer=0 blend=ADDITIVE depthWrite=OFF",
    "layer=1 blend=ADDITIVE depthWrite=OFF",
    "layer=2 blend=ADDITIVE depthWrite=OFF",
    "layer=3 blend=ALPHA depthWrite=ON",
    "RIFT_RENDER_LAYER_SELECTION=PASS additive=3 alpha=1 depth_write=outer_off_core_on",
):
    assert fragment in driver, fragment

print(f"BETA26 Flux Rift evilportal.ogg blob={blob}, registry, source-aligned render camera and first 300-tick isolated PCM capture: PASS")
