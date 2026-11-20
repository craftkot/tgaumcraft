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
    "boolean smoke = smokeProbeEnabled();",
    "RandomSource eventRandom = sourceRandom;",
    "smokeSeed = smokeAmbientSoundSeed();",
    "eventRandom = RandomSource.create(smokeSeed);",
    "0.15F + eventRandom.nextGaussian() * 0.066D",
    "eventRandom.nextGaussian() * 0.1D",
    "playSound(ThaumcraftSounds.EVILPORTAL.get(), volume, pitch)",
    "TC_SMOKE_RIFT_AMBIENT_RANDOM=DETERMINISTIC seed=",
    "TC_SMOKE_RIFT_AMBIENT_DUE=YES",
    "TC_SMOKE_RIFT_AMBIENT_CALL=YES",
    "eligiblePlayers=",
):
    assert fragment in method, fragment
seed_method = rift.split("static long smokeAmbientSoundSeed()", 1)[1].split("void playAmbientSoundIfDue", 1)[0]
for fragment in (
    "RandomSource.create(seed)",
    "volume > 0.05F && volume < 0.35F",
    "pitch > 0.2F && pitch < 1.3F",
):
    assert fragment in seed_method, fragment

sound_tests = Path("src/neoforge/java/dev/ehtid/thaumcraft/entity/FluxRiftSoundGameTests.java").read_text()
assert "FluxRiftEntity.smokeAmbientSoundSeed()" in sound_tests
assert "findPositiveVolumeSeed()" not in sound_tests
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
    "rift_visual_x=5.5",
    "rift_visual_yaw=90",
    "rift_visual_pitch=39",
    "rift_size=30",
    'send_chat_fast "/tp @p $rift_x 300 $rift_z 0 0"',
    'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"',
    'send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"',
    'send_chat_fast "/tp @p $rift_visual_x $rift_y $rift_z $rift_visual_yaw $rift_visual_pitch"',
    'send_chat_fast "/tp @p $rift_x $rift_y $rift_z 0 89"',
    'send_chat_fast "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z',
    "RiftSize:$rift_size",
    "send_chat_fast() {",
    "sleep 0.1",
    'xdotool type --clearmodifiers --delay 5 "$1"',
    "sleep 0.25",
    "TC_RIFT_PLATFORM_PRESENT",
    "TC_RIFT_HIGH_TELEPORT_PRESENT",
    "TC_RIFT_LISTENER_AT_SOURCE",
    "TC_RIFT_AMBIENT_ENTITY_PRESENT",
    "TC_RIFT_VISUAL_CAMERA_BEFORE",
    "TC_RIFT_VISUAL_CAMERA_AFTER",
    "TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL",
    "RIFT_VISUAL_CAMERA_BEFORE=PASS",
    "RIFT_VISUAL_CAMERA_AFTER=PASS",
    "RIFT_LISTENER_RETURN_TO_SOURCE=PASS",
    "RIFT_LISTENER_AT_SOURCE=PASS",
    "spawn_game_time=",
    "RIFT_GAME_TIME_QUERY=PASS",
    "event_log_offset=",
    "parec --device=tc_rift_sink.monitor",
    "rift_capture_pid=$!",
    "RIFT_GAME_TICK_WINDOW=PASS",
    "RIFT_SERVER_AMBIENT_RANDOM=PASS",
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
high_teleport_marker = "TC_RIFT_HIGH_TELEPORT_PRESENT"
platform_fill = 'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"'
platform_marker = "TC_RIFT_PLATFORM_PRESENT"
source_teleport = 'send_chat "/tp @p $rift_x $rift_y $rift_z 0 90"'
visual_teleport = 'send_chat_fast "/tp @p $rift_visual_x $rift_y $rift_z $rift_visual_yaw $rift_visual_pitch"'
source_return = 'send_chat_fast "/tp @p $rift_x $rift_y $rift_z 0 89"'
generator_source_teleport = "source_teleport_command = 'send_chat \"/tp @p $rift_x $rift_y $rift_z 0 90\"'"
assert driver.count(generator_source_teleport) == 1, "generator self-check must use the same source-listener command"
rift_summon = 'send_chat_fast "/summon thaumcraft:flux_rift $rift_x $rift_y $rift_z'
runtime_template = driver.split("high_teleport_command =", 1)[0]
post_spawn_camera_probe = "send_chat_fast '/execute if entity @p[x=5,y=150,z=12,distance=..1] run say TC_RIFT_VISUAL_CAMERA_AFTER'"
entity_presence_probe = "send_chat_fast '/execute if entity @e[type=thaumcraft:flux_rift,limit=1] run say TC_RIFT_AMBIENT_ENTITY_PRESENT'"
high_teleport_probe = "send_chat_fast '/execute if entity @p[x=3,y=300,z=12,distance=..1] run say TC_RIFT_HIGH_TELEPORT_PRESENT'"
return_marker_probe = "send_chat_fast '/execute if entity @p[x=3,y=150,z=12,distance=..1] run say TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL'"
assert runtime_template.count(post_spawn_camera_probe) == 1, "post-spawn camera marker must use the bounded fast path"
assert runtime_template.count(entity_presence_probe) == 1 and runtime_template.count(high_teleport_probe) == 1, "fast entity/high-position commands must be explicit and unique"
assert runtime_template.count(return_marker_probe) == 1, "source-return marker must be explicit and unique"
assert "send_chat '/execute if entity @p[x=5,y=150,z=12,distance=..1] run say TC_RIFT_VISUAL_CAMERA_AFTER'" not in runtime_template, (
    "the post-spawn camera marker must leave time for the tick-300 source return"
)
new_send_block = driver.split('new_send = """', 1)[1].split('"""', 1)[0]
assert 'sleep 0.5' in new_send_block and 'sleep 0.35' in new_send_block and 'wait_for_server_marker() {' in new_send_block, "generated fast helper must wait for chat and poll server markers"
assert 'sleep 0.1' not in new_send_block, "generated fast helper must not regress to the prefix-loss race"
assert runtime_template.count(high_teleport) == 1 and runtime_template.count(platform_fill) == 1, (
    "the fast setup commands must be unique in the runtime template"
)
assert runtime_template.index(high_teleport) < runtime_template.index(high_teleport_probe) < runtime_template.index("wait_for_server_marker 'TC_RIFT_HIGH_TELEPORT_PRESENT'") < runtime_template.index(platform_fill), (
    "the high teleport must be server-confirmed before the barrier fill"
)
assert runtime_template.index(entity_presence_probe) < runtime_template.index("wait_for_server_marker 'TC_RIFT_AMBIENT_ENTITY_PRESENT'"), (
    "the spawned Rift must be confirmed before the post-spawn camera capture"
)
assert runtime_template.index(post_spawn_camera_probe) < runtime_template.index("wait_for_server_marker 'TC_RIFT_VISUAL_CAMERA_AFTER'"), (
    "the post-spawn camera must be confirmed before the render screenshot"
)
assert runtime_template.index(source_return) < runtime_template.index(return_marker_probe) < runtime_template.index("wait_for_server_marker 'TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL'"), (
    "the listener must be confirmed at the Rift source before tick-300 audio acceptance"
)
assert runtime_template.count(source_teleport) == 1, (
    "the initial source-listener teleport must be unique in the runtime template"
)
assert runtime_template.count(visual_teleport) == 1 and runtime_template.count(source_return) == 1, (
    "set the side-on camera once before spawn and return to the exact source once after rendering"
)
assert "RiftSize:$rift_size" in runtime_template and "rift_size=30" in driver
render_capture = "capture rift-ambient-entity-present"
render_gate = "'layer=0 blend=ADDITIVE depthWrite=OFF'"
assert runtime_template.index(render_capture) < runtime_template.index(render_gate), (
    "capture the actual post-spawn side-on client frame before asserting render markers"
)
assert (
    runtime_template.index(high_teleport)
    < runtime_template.index(high_teleport_marker)
    < runtime_template.index(platform_fill)
    < runtime_template.index(platform_marker)
    < runtime_template.index(source_teleport)
    < runtime_template.index("RIFT_LISTENER_AT_SOURCE=PASS")
    < runtime_template.index(visual_teleport)
    < runtime_template.index("TC_RIFT_VISUAL_CAMERA_BEFORE")
    < runtime_template.index("capture rift-ambient-before")
    < runtime_template.index("spawn_game_time=")
    < runtime_template.index("> client-smoke/rift-event.pcm")
    < runtime_template.index(rift_summon)
    < runtime_template.index(entity_presence_probe)
    < runtime_template.index("TC_RIFT_VISUAL_CAMERA_AFTER")
    < runtime_template.index(render_capture)
    < runtime_template.index(render_gate)
    < runtime_template.rindex(source_return)
    < runtime_template.index("TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL")
    < runtime_template.index("RIFT_SERVER_AMBIENT_DUE=PASS")
), (
    "capture before/after from the same side view, then return to the exact source before accepting tick-300 audio"
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
    "new ShaderStateShard(FluxRiftRenderer::getRiftShader)",
    "new TextureStateShard(END_PORTAL_TEXTURE, false, false)",
    "LIGHTNING_TRANSPARENCY",
    "TRANSLUCENT_TRANSPARENCY",
    "COLOR_WRITE",
    "COLOR_DEPTH_WRITE",
    "LEQUAL_DEPTH_TEST",
    "NO_CULL",
    "DefaultVertexFormat.POSITION_COLOR",
    "VertexFormat.Mode.QUADS",
    "setTextureState(END_PORTAL)",
):
    assert fragment in render_types, fragment
client_events = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/FluxRiftClientEvents.java").read_text()
for fragment in (
    "RegisterShadersEvent",
    "new ShaderInstance(event.getResourceProvider()",
    "FluxRiftRenderer::setRiftShader",
):
    assert fragment in client_events, fragment

shader_root = ROOT / "shaders/core"
shader_spec = json.loads((shader_root / "flux_rift.json").read_text())
assert shader_spec["vertex"] == "thaumcraft:flux_rift"
assert shader_spec["fragment"] == "thaumcraft:flux_rift"
assert shader_spec["attributes"] == ["Position", "Color"]
assert shader_spec["samplers"] == [{"name": "Sampler0"}]
uniforms = {uniform["name"]: uniform for uniform in shader_spec["uniforms"]}
assert {"ModelViewMat", "ProjMat", "time", "yaw", "pitch"}.issubset(uniforms)
assert uniforms["time"]["type"] == "int"
assert uniforms["yaw"]["type"] == uniforms["pitch"]["type"] == "float"
vertex_shader = (shader_root / "flux_rift.vsh").read_text()
fragment_shader = (shader_root / "flux_rift.fsh").read_text()
for fragment in ("#version 150", "ModelViewMat", "ProjMat", "position = viewPosition.xyz"):
    assert fragment in vertex_shader, fragment
for fragment in (
    "uniform sampler2D Sampler0",
    "uniform int time",
    "uniform float yaw",
    "uniform float pitch",
    "for (int layer = 0; layer < 16; layer++)",
    "texture(Sampler0, uv)",
    "0.044, 0.036, 0.063",
    "fragColor = color * vertexColor;",
):
    assert fragment in fragment_shader, fragment
for fragment in (
    "TC_SMOKE_RIFT_SHADER_ACTIVE=YES",
    "shader.safeGetUniform(\"time\").set(time)",
    "shader.safeGetUniform(\"yaw\").set(yaw)",
    "shader.safeGetUniform(\"pitch\").set(pitch)",
):
    assert fragment in renderer, fragment
workflow = Path(".github/workflows/closure-rift-ambient-sound-client.yml").read_text()
for fragment in (
    "src/neoforge/java/dev/ehtid/thaumcraft/client/FluxRiftRenderer.java",
    "src/neoforge/java/dev/ehtid/thaumcraft/client/FluxRiftClientEvents.java",
    "src/main/resources/assets/thaumcraft/shaders/core/flux_rift.json",
    "src/main/resources/assets/thaumcraft/shaders/core/flux_rift.vsh",
    "src/main/resources/assets/thaumcraft/shaders/core/flux_rift.fsh",
):
    assert fragment in workflow, fragment
for fragment in (
    "RIFT_SHADER=PASS registered=YES sampler=Sampler0",
    "TC_SMOKE_RIFT_SHADER_ACTIVE=YES",
    "layer=0 blend=ADDITIVE depthWrite=OFF",
    "layer=1 blend=ADDITIVE depthWrite=OFF",
    "layer=2 blend=ADDITIVE depthWrite=OFF",
    "layer=3 blend=ALPHA depthWrite=ON",
    "RIFT_RENDER_LAYER_SELECTION=PASS additive=3 alpha=1 depth_write=outer_off_core_on",
):
    assert fragment in driver, fragment

print(f"BETA26 Flux Rift evilportal.ogg blob={blob}, original end-portal star shader, render-state and real-client audio contracts: PASS")
