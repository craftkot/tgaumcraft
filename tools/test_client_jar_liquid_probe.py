#!/usr/bin/env python3
"""Source-contract test for the native essentia-jar client-state diagnostic."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
generator = ROOT / "tools" / "client_jar_liquid_smoke.py"
probe_path = ROOT / "src" / "neoforge" / "java" / "dev" / "ehtid" / "thaumcraft" / "client" / "EssentiaJarClientSmokeProbe.java"

generated = subprocess.run(
    [sys.executable, str(generator)],
    cwd=ROOT,
    check=True,
    capture_output=True,
    text=True,
)
script = generated.stdout
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
)
positions = [script.find(marker) for marker in ordered]
if any(position < 0 for position in positions) or positions != sorted(positions):
    raise SystemExit(f"jar-liquid client probe/screenshot order is invalid: {positions}")

for marker in (
    "JAR_LIQUID_CLIENT_BLOCK_ENTITIES_NOT_READY=YES",
    "JAR_LIQUID_STAGE_PRESENT=TC_SMOKE_JAR_LIQUID_RENDERER_REGISTERED",
    "JAR_LIQUID_STAGE_ABSENT=TC_SMOKE_JAR_LIQUID_RENDER_ENTRY",
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

print("Jar-liquid client BE probe contract: PASS (real GUI tick, chunk/BE/type/NBT checks, screenshot before renderer gate)")
