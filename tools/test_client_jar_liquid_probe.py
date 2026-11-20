#!/usr/bin/env python3
"""Source-contract test for the native essentia-jar client-state and metal-parts smoke."""
from pathlib import Path
import subprocess
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
generator = ROOT / "tools" / "client_jar_liquid_smoke.py"
probe_path = ROOT / "src" / "neoforge" / "java" / "dev" / "ehtid" / "thaumcraft" / "client" / "EssentiaJarClientSmokeProbe.java"
config_file = "run/config/thaumcraft-client.toml"
build_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")

generated = subprocess.run(
    [sys.executable, str(generator)],
    cwd=ROOT,
    check=True,
    capture_output=True,
    text=True,
)
script = generated.stdout
config_setup = """# ModDevGradle's client() run defaults to the project run/ directory.
# run/closure-gametest is configured only for the separate gameTestServer run.
mkdir -p run/config
cat > run/config/thaumcraft-client.toml <<'EOF'
[graphics]
crooked = false
EOF"""
client_run = re.search(r"(?m)^[ \t]*client[ \t]*\{[ \t]*client\(\)([^}]*)\}", build_gradle)
if client_run is None or "gameDirectory" in client_run.group(1):
    raise SystemExit("runClient no longer uses the documented default project/run game directory; update the config fixture path")
game_test_server = re.search(r"(?ms)^[ \t]*gameTestServer[ \t]*\{(.*?)^[ \t]*\}", build_gradle)
if game_test_server is None or "gameDirectory = project.file('run/closure-gametest')" not in game_test_server.group(1):
    raise SystemExit("expected custom run/closure-gametest directory belongs only to gameTestServer")
if config_file not in script or config_setup not in script:
    raise SystemExit("jar smoke must seed the CLIENT config before launching Minecraft")
if script.index(config_setup) > script.index("gradle --init-script tools/gradle-plugin-portal-fallback.init.gradle runClient"):
    raise SystemExit("crooked=false must be written before the real Minecraft client starts")
if "JAR_LABEL_CONFIG_CROOKED=false=WRITTEN" not in script:
    raise SystemExit("jar smoke must report the prelaunch config write")
if "run/closure-gametest/config" in script:
    raise SystemExit("client config must not be written into the separate GameTest server directory")
if "JAR_LABEL_CONFIG_EXPECTED_PATH=YES" not in script:
    raise SystemExit("jar smoke must report the expected runClient config path after exit")
for marker in (
    "aspect=terra facing=2 crooked=0",
    "aspect=terra crooked=0 texture=thaumcraft:textures/aspects/terra.png",
):
    if marker not in script:
        raise SystemExit(f"jar smoke must require the disabled crooked path: {marker}")

floor = "send_chat '/fill 248 149 248 252 149 252 minecraft:stone'"
old_floor = "send_chat '/fill 248 149 250 252 149 252 minecraft:stone'"
camera_teleport = "send_chat '/tp @p 250.5 150 248.5 0 16'"
if floor not in script or old_floor in script:
    raise SystemExit("jar-liquid fixture platform must cover the camera stand at z=248")
if script.index(floor) > script.index(camera_teleport):
    raise SystemExit("jar-liquid camera must be placed only after its support platform exists")

syntax = subprocess.run(
    ["bash", "-n"],
    input=script,
    cwd=ROOT,
    capture_output=True,
    text=True,
)
if syntax.returncode != 0:
    raise SystemExit(f"generated jar-liquid smoke has invalid shell syntax: {syntax.stderr}")

ordered = (
    "send_chat '/tp @p 250.5 150 248.5 0 16'",
    "capture 96-jar-liquid-fixture-camera",
    "TC_SMOKE_JAR_LIQUID_CLIENT_STATE=READY",
    "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=249,150,251",
    "capture 97-jar-liquid-filled",
    "send_chat '/setblock 250 150 251 thaumcraft:jar_normal{blocked:1b,facing:2b}'",
    "capture 98-jar-brace",
    "send_chat '/setblock 250 151 251 thaumcraft:tube'",
    "send_chat '/setblock 250 150 251 thaumcraft:jar_normal{facing:2b}'",
    "capture 99-jar-lid-extension",
    "TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=terra facing=2 crooked=0",
    "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=terra crooked=0 texture=thaumcraft:textures/aspects/terra.png",
    "capture 100-jar-aspect-label",
)
positions = [script.find(marker) for marker in ordered]
if any(position < 0 for position in positions) or positions != sorted(positions):
    raise SystemExit(f"jar liquid/metal-parts fixture order is invalid: {positions}")

for marker in (
    "JAR_LIQUID_REQUIRED_CLIENT_MARKER_MISSING=$marker",
    "TC_SMOKE_JAR_METAL_PARTS_LAYER_REGISTERED=YES",
    "for stage in TC_SMOKE_JAR_LIQUID_RENDERER_REGISTERED TC_SMOKE_JAR_LIQUID_RENDER_ENTRY TC_SMOKE_JAR_LIQUID_RENDERED",
    'echo "JAR_LIQUID_STAGE_PRESENT=$stage"',
    'echo "JAR_LIQUID_STAGE_ABSENT=$stage"',
    "JAR_BRACE_FIXTURE_READY",
    "JAR_EXTENSION_TUBE_FIXTURE_READY",
):
    if marker not in script:
        raise SystemExit(f"generated smoke is missing diagnostic marker: {marker}")

probe = probe_path.read_text(encoding="utf-8")
for marker in (
    "EventBusSubscriber.Bus.GAME",
    "RenderGuiEvent.Post",
    'Boolean.getBoolean("thaumcraft.jarLiquidSmoke")',
    "level.hasChunkAt(pos)",
    "level.getBlockEntity(pos)",
    "ThaumcraftBlockEntities.JAR_NORMAL.get()",
    "contents.amount() == expectedAmount",
    "Objects.equals(contents.aspect(), expectedAspect)",
):
    if marker not in probe:
        raise SystemExit(f"client probe is missing required source contract: {marker}")

print("Jar-liquid client BE probe contract: PASS (real GUI tick, chunk/BE/type/NBT checks, liquid and metal-part screenshots)")
