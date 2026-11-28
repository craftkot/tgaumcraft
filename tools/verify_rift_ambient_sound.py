#!/usr/bin/env python3
"""Verify exact BETA26 Flux Rift ambient sound asset and runtime wiring."""
from pathlib import Path
import hashlib
import json
import subprocess
import tempfile

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
    'send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"',
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

assert 'send_chat_fast "/fill 1 149 10 5 149 14 minecraft:barrier"' not in driver, (
    "the fall-sensitive barrier placement must use the reliable chat path"
)
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
platform_fill = 'send_chat "/fill 1 149 10 5 149 14 minecraft:barrier"'
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
high_teleport_probe = "send_chat_fast '/execute if entity @p[x=3,y=150,z=12,dx=1,dy=250,dz=1] run say TC_RIFT_HIGH_TELEPORT_PRESENT'"
return_marker_probe = "send_chat_fast '/execute if entity @p[x=3,y=150,z=12,distance=..1] run say TC_RIFT_LISTENER_RETURNED_AFTER_VISUAL'"
assert runtime_template.count(post_spawn_camera_probe) == 1, "post-spawn camera marker must use the bounded fast path"
assert runtime_template.count(entity_presence_probe) == 1 and runtime_template.count(high_teleport_probe) == 1, "fast entity/high-position commands must be explicit and unique"
assert runtime_template.count(return_marker_probe) == 1, "source-return marker must be explicit and unique"
assert "send_chat '/execute if entity @p[x=5,y=150,z=12,distance=..1] run say TC_RIFT_VISUAL_CAMERA_AFTER'" not in runtime_template, (
    "the post-spawn camera marker must leave time for the tick-300 source return"
)
new_send_block = driver.split('new_send = """', 1)[1].split('"""', 1)[0]
assert 'sleep 0.5' in new_send_block and 'sleep 0.35' in new_send_block and 'wait_for_server_marker() {' in new_send_block, "generated fast helper must wait for chat and poll server markers"
assert not any(line.strip() == 'sleep 0.1' for line in new_send_block.splitlines()), "generated fast helper must not regress to the prefix-loss race"
assert runtime_template.count(high_teleport) == 1 and runtime_template.count(platform_fill) == 1, (
    "the fast setup commands must be unique in the runtime template"
)
assert runtime_template.index(high_teleport) < runtime_template.index(high_teleport_probe) < runtime_template.index("wait_for_server_marker 'TC_RIFT_HIGH_TELEPORT_PRESENT'") < runtime_template.index(platform_fill), (
    "the falling player must be confirmed inside the high X/Z column before the reliable barrier fill"
)
assert "distance=..1" not in high_teleport_probe and "dy=250" in high_teleport_probe, (
    "the high-teleport marker must tolerate falling while still excluding normal spawn"
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


# BETA26 TileCrucible.drawEffects -> FXDispatcher.crucibleBubble visual contract.
crucible_rules = Path("src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyCrucibleBubbleRules.java").read_text()
crucible_test = Path("src/test/java/dev/ehtid/thaumcraft/port/core/LegacyCrucibleBubbleRulesTest.java").read_text()
assert "public static final double DEFAULT_SLOW_DOWN = 0.9800000190734863D;" in crucible_rules
assert "Double.doubleToRawLongBits(LegacyCrucibleBubbleRules.DEFAULT_SLOW_DOWN)" in crucible_test
assert "0x3fef5c2900000000L" in crucible_test
for fragment in (
    "return roll == 0 && aspectCount > 0;",
    "return 15 + randomRoll;",
    "random * 0.3F + 0.3F",
    "age > lifetime - 3",
    "return index == 0 ? 65 : 66;",
    "return 64;",
):
    assert fragment in crucible_rules, fragment

assert "public static void main(String[] args)" in crucible_test
assert "BETA26 Crucible FXDispatcher.crucibleBubble" in crucible_test
# CoreParityTest.java is currently owned by other open PRs; compile and execute
# this pure-Java regression directly in the already provisioned Java 21 client job.
with tempfile.TemporaryDirectory(prefix="thaumcraft-crucible-bubble-") as classes:
    subprocess.run([
        "javac", "-d", classes,
        "src/main/java/dev/ehtid/thaumcraft/port/core/visual/LegacyCrucibleBubbleRules.java",
        "src/test/java/dev/ehtid/thaumcraft/port/core/LegacyCrucibleBubbleRulesTest.java",
    ], check=True, capture_output=True, text=True)
    result = subprocess.run([
        "java", "-cp", classes,
        "dev.ehtid.thaumcraft.port.core.LegacyCrucibleBubbleRulesTest",
    ], check=True, capture_output=True, text=True)
    assert "BETA26 Crucible FXDispatcher.crucibleBubble: OK (" in result.stdout
    print(result.stdout.strip())

crucible_block = Path("src/neoforge/java/dev/ehtid/thaumcraft/block/CrucibleBlock.java").read_text()
assert "if (level.isClientSide)" in crucible_block
assert "CrucibleBubbleClientEffects.tick(world, pos, tile)" in crucible_block

crucible_effects = Path("src/neoforge/java/dev/ehtid/thaumcraft/blockentity/CrucibleBubbleClientEffects.java").read_text()
for fragment in (
    "LegacyCrucibleBubbleRules.shouldSpawn(random.nextInt(6), aspects.size())",
    "LegacyCrucibleSurface.height(tile.waterMb(), aspects.visSize())",
    "level.addParticle(CrucibleBubbleParticleRegistration.TYPE",
    "TC_SMOKE_CRUCIBLE_BUBBLE_SPAWN=YES",
):
    assert fragment in crucible_effects, fragment

crucible_particle = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/CrucibleBubbleParticle.java").read_text()
for fragment in (
    "private static final int GRID_SIZE = 64;",
    "LegacyCrucibleBubbleRules.frame(this.age, this.lifetime)",
    "LegacyCrucibleBubbleRules.lifetime(level.random.nextInt(10))",
    "this.gravity = -0.001F;",
    "private final double slowDown = LegacyCrucibleBubbleRules.DEFAULT_SLOW_DOWN;",
    "TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES",
    "TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES",
):
    assert fragment in crucible_particle, fragment

crucible_registration = Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/CrucibleBubbleParticleRegistration.java").read_text()
assert "new SimpleParticleType(false)" in crucible_registration
assert 'fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crucible_bubble")' in crucible_registration
assert "Registries.PARTICLE_TYPE" in crucible_registration

crucible_client_events = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/CrucibleBubbleParticleClientEvents.java").read_text()
assert "RegisterParticleProvidersEvent" in crucible_client_events
assert "event.registerSpriteSet" in crucible_client_events

crucible_particle_spec = json.loads(Path("src/main/resources/assets/thaumcraft/particles/crucible_bubble.json").read_text())
assert crucible_particle_spec["textures"] == ["thaumcraft:particles"]

# /execute if data consumes NBT paths. A compound SNBT literal is valid for
# /data merge, but invalid as the path argument to /execute if data.
crucible_baseline_probe = """send_chat '/execute if data block 20 64 2 Heat if data block 20 64 2 Water if data block 20 64 2 Aspects unless data block 20 64 2 Aspects[0] run say TC_CRUCIBLE_BASELINE_READY'"""
crucible_aspects_command = "send_chat_fast '/data merge block 20 64 2 {Aspects:[{key:\"aer\",amount:1}]}'"
crucible_aspects_probe = """send_chat_fast '/execute if data block 20 64 2 Aspects[{key:"aer",amount:1}] run say TC_CRUCIBLE_ASPECT_READY'"""
assert runtime_template.count(crucible_baseline_probe) == 1
assert runtime_template.count(crucible_aspects_command) == 1
assert runtime_template.count(crucible_aspects_probe) == 1
assert "send_chat '/data merge block 20 64 2 {Aspects:[{key:\"aer\",amount:1}]}'" not in runtime_template
assert "send_chat '/execute if data block 20 64 2 Aspects[{key:" not in runtime_template
assert "execute if data block 20 64 2 {Heat:" not in runtime_template
assert "execute if data block 20 64 2 {Aspects:" not in runtime_template

for marker in (
    "TC_CRUCIBLE_PRESENT",
    "TC_CRUCIBLE_BASELINE_READY",
    "TC_CRUCIBLE_ASPECT_READY",
    "TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES",
    "TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES",
    "send_chat '/time set noon'",
    "send_chat '/gamerule doDaylightCycle false'",
    "send_chat '/fill 18 63 0 24 63 4 minecraft:stone'",
    "send_chat '/fill 18 64 0 24 72 4 minecraft:air'",
    "send_chat_fast '/tp @p 22.5 68.5 2.5 90 70'",
    "bubble_frame=''",
    "CRUCIBLE_BUBBLE_CAPTURE_BURST=PASS",
    "frames=12 interval_ms=50",
    "python3 tools/verify_crucible_bubble_screenshots.py",
    "capture crucible-bubble-before",
):
    assert marker in runtime_template, marker
assert (
    runtime_template.index("RIFT_AMBIENT_SOUND_REAL_CLIENT=PASS")
    < runtime_template.index("TC_CRUCIBLE_PRESENT")
    < runtime_template.index("TC_CRUCIBLE_BASELINE_READY")
    < runtime_template.index("capture crucible-bubble-before")
    < runtime_template.index("TC_CRUCIBLE_ASPECT_READY")
    < runtime_template.index("TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES", runtime_template.index("TC_CRUCIBLE_ASPECT_READY"))
    < runtime_template.index("TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES", runtime_template.index("TC_CRUCIBLE_ASPECT_READY"))
    < runtime_template.index("bubble_frame=''")
    < runtime_template.index("python3 tools/verify_crucible_bubble_screenshots.py")
    < runtime_template.index("CRUCIBLE_BUBBLE_CAPTURE_BURST=PASS")
), "Crucible bubble smoke must prove the aspect state and capture visible particles within their lifetime"

shot_verifier = Path("tools/verify_crucible_bubble_screenshots.py").read_text()
for fragment in (
    "def center_pixels",
    "def crucible_pixels",
    "def yellow_mask",
    "def component_sizes",
    "def verify_images",
    "bubble_pixel_count < 6",
    "not visible_components",
    "mean_luma",
    "identical before/after images must fail closed",
    "yellow pixels outside the Crucible ROI must not pass",
    "one yellow pixel must not pass as a bubble",
):
    assert fragment in shot_verifier, fragment
shot_test = subprocess.run(
    ["python3", "tools/verify_crucible_bubble_screenshots.py", "--self-test"],
    check=True, capture_output=True, text=True,
)
assert "Crucible screenshot verifier self-test: PASS" in shot_test.stdout
assert "one bowl bubble cluster" in shot_test.stdout
print(shot_test.stdout.strip())
print(f"BETA26 Flux Rift evilportal.ogg blob={blob}, original end-portal shader/audio and BETA26 Crucible bubble real-client contracts: PASS")
