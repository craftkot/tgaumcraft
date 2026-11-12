#!/usr/bin/env python3
"""Source and numeric regression for the BETA26 Nitor FXGeneric flame/core port."""
from pathlib import Path
import json
import math
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PARTICLE = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/NitorParticleClientEvents.java").read_text()
REGISTRATION = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/NitorParticleRegistration.java").read_text()
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/NitorBlock.java").read_text()
ENTITY = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/NitorBlockEntity.java").read_text()
VERIFIER = ROOT / "tools/verify_nitor_client_tick.py"

def near(a: float, b: float, label: str) -> None:
    if not math.isclose(a, b, rel_tol=0.0, abs_tol=1e-6):
        raise AssertionError(f"{label}: {a} != {b}")

def legacy_scale(keys: list[float], age: int, lifetime: int) -> float:
    # Exact FXGeneric.calculateFrames arithmetic, with age clamped to rendered frames.
    sample = min(max(age, 0), lifetime) * (len(keys) - 1) / float(lifetime)
    index = min(int(sample), len(keys) - 1)
    fraction = sample - index
    delta = keys[index + 1] - keys[index] if index < len(keys) - 1 else 0.0
    return keys[index] + delta * fraction

def flame_scale(start: float, age: int, lifetime: int) -> float:
    return start + (0.05 - start) * min(max(age, 0), lifetime) / float(lifetime)

def core_scale(middle: float, age: int, lifetime: int) -> float:
    progress = min(max(age, 0), lifetime) / float(lifetime)
    if progress <= 0.5:
        return 1.0 + (middle - 1.0) * (progress * 2.0)
    return middle + (1.0 - middle) * ((progress - 0.5) * 2.0)

# Compare the modern formulas against the exact source keyframe interpolator.
for lifetime in range(10, 15):
    for start in (3.0, 3.375, 4.0):
        for age in range(-1, lifetime + 2):
            near(legacy_scale([start, 0.05], age, lifetime),
                 flame_scale(start, age, lifetime), "flame scale")
    for middle in (0.7, 1.0, 1.15, 1.3):
        for age in range(-1, lifetime + 2):
            near(legacy_scale([1.0, middle, 1.0], age, lifetime),
                 core_scale(middle, age, lifetime), "core scale")

frames = [264 + age % 8 for age in range(24)]
assert frames[:8] == list(range(264, 272))
assert frames[8:16] == list(range(264, 272))
assert frames[16:24] == list(range(264, 272))

# Source contract: original atlas indices, layer blend, lifetime, alpha and motion.
assert "FLAME_FIRST_FRAME = 264" in PARTICLE
assert "FLAME_FRAME_COUNT = 8" in PARTICLE
assert "CORE_FRAME = 457" in PARTICLE
assert "this.lifetime = flame ? 10 + level.random.nextInt(5) : 10;" in PARTICLE
assert "this.age = 1;" in PARTICLE
assert "this.quadSize = 0.1F * scaleAt(this.age);" in PARTICLE
assert "private static final AtomicInteger FLAME_FRAME_MASK" in PARTICLE
assert "FLAME_FRAME_MASK.getAndUpdate(mask -> mask | bit)" in PARTICLE
assert "this.alpha = flame ? 0.66F : 1.0F;" in PARTICLE
assert "this.randomX = flame ? 0.0025D : 0.0002D;" in PARTICLE
assert "this.randomY = flame ? 0.0D : 0.0002D;" in PARTICLE
assert "this.randomZ = flame ? 0.0025D : 0.0002D;" in PARTICLE
assert "this.gravity = 0.0F;" in PARTICLE
assert "this.move(this.xd, this.yd, this.zd);" in PARTICLE
assert "this.level.random.nextGaussian() * randomX" in PARTICLE
assert "this.level.random.nextGaussian() * randomY" in PARTICLE
assert "this.level.random.nextGaussian() * randomZ" in PARTICLE
assert "this.atlas = sprites.get(0, 1);" in PARTICLE
assert "RenderSystem.blendFunc(770, 1);" in PARTICLE
assert "ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin" in PARTICLE
assert "return flame ? BETA26_ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;" in PARTICLE
assert "this.setColor(" in PARTICLE
assert "nitor.particleRgb()" in PARTICLE
assert "nitor.reportParticleColorIfNeeded()" in PARTICLE

assert '"nitor_flame"' in REGISTRATION and '"nitor_core"' in REGISTRATION
assert "Registries.PARTICLE_TYPE" in REGISTRATION
assert "helper.register(FLAME_ID, FLAME_TYPE)" in REGISTRATION
assert "helper.register(CORE_ID, CORE_TYPE)" in REGISTRATION
assert "event.registerSpriteSet(NitorParticleRegistration.FLAME_TYPE" in PARTICLE
assert "event.registerSpriteSet(NitorParticleRegistration.CORE_TYPE" in PARTICLE
assert "NitorParticleRegistration.FLAME_TYPE" in ENTITY
assert "NitorParticleRegistration.CORE_TYPE" in ENTITY
assert "ParticleTypes.END_ROD" not in ENTITY
assert "DustParticleOptions" not in BLOCK
assert "coloredGlow()" not in BLOCK

for particle_name in ("nitor_flame", "nitor_core"):
    definition = json.loads((ROOT / f"src/main/resources/assets/thaumcraft/particles/{particle_name}.json").read_text())
    assert definition == {"textures": ["thaumcraft:particles"]}

# Exercise the real client-log verifier with positive and fail-closed fixtures.
def verifier_result(name: str, omit: str | None = None, wrong: str | None = None) -> subprocess.CompletedProcess[str]:
    lines = [
        "TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=20 flames=20 cores=2",
        "TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=16701501 mapColor=15066419 particleColor=15066419",
        "TC_SMOKE_NITOR_FLAME_CREATED=YES id=1 frameStart=264 frameCount=8 lifetime=10 startScale=3.5 rgb=15066419",
        "TC_SMOKE_NITOR_CORE_CREATED=YES id=1 frame=457 lifetime=10 middleScale=1.1",
        "TC_SMOKE_NITOR_FLAME_RENDERED=YES id=1 frame=265 age=1",
        "TC_SMOKE_NITOR_CORE_RENDERED=YES id=1 frame=457 age=1",
        "TC_SMOKE_NITOR_FLAME_SCALE=YES id=1 age=1 scale=3.155",
        "TC_SMOKE_NITOR_FLAME_SCALE=YES id=1 age=10 scale=0.05",
        "TC_SMOKE_NITOR_CORE_EXPIRED=YES id=1 age=10 lifetime=10",
        "TC_SMOKE_NITOR_FLAME_EXPIRED=YES id=1 age=10 lifetime=10",
        "TC_SMOKE_NITOR_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1",
    ]
    lines.extend(
        f"TC_SMOKE_NITOR_FLAME_FRAME=YES frame={frame}"
        for frame in range(264, 272)
    )
    if omit == "last-frame":
        lines = [line for line in lines if "frame=271" not in line]
    if omit == "core-render":
        lines = [line for line in lines if "TC_SMOKE_NITOR_CORE_RENDERED" not in line]
    if omit == "blend":
        lines = [line for line in lines if "TC_SMOKE_NITOR_ADDITIVE_BLEND_BEGIN" not in line]
    if wrong == "dye":
        lines = [line.replace("mapColor=15066419 particleColor=15066419",
                              "mapColor=16701501 particleColor=16701501") for line in lines]
    if wrong == "particle":
        lines = [line.replace("particleColor=15066419", "particleColor=16701501") for line in lines]
    with tempfile.TemporaryDirectory() as directory:
        log = Path(directory) / f"{name}.log"
        log.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(VERIFIER), str(log)],
            cwd=ROOT, capture_output=True, text=True,
        )

good = verifier_result("good")
assert good.returncode == 0, good.stdout + good.stderr
assert verifier_result("missing-last-frame", omit="last-frame").returncode != 0
assert verifier_result("missing-core-render", omit="core-render").returncode != 0
assert verifier_result("missing-additive", omit="blend").returncode != 0
assert verifier_result("old-dye-rgb", wrong="dye").returncode != 0
assert verifier_result("wrong-particle-rgb", wrong="particle").returncode != 0

print("BETA26 Nitor FXGeneric flame/core source, frame, blend and scale contracts: PASS")
