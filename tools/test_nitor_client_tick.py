#!/usr/bin/env python3
"""Static and generated-client contract for the BETA26 TileNitor tick and map color."""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/NitorBlock.java").read_text()
ENTITY = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/NitorBlockEntity.java").read_text()
REGISTRATION = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/NitorBlockEntityRegistration.java").read_text()
LEGACY_COLORS = (ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/LegacyNitorColors.java").read_text()
VERIFIER = ROOT / "tools/verify_nitor_client_tick.py"
WORKFLOW = (ROOT / ".github/workflows/closure-dust-client.yml").read_text()

assert "extends BaseEntityBlock" in BLOCK
assert "public static final MapCodec<NitorBlock> CODEC = simpleCodec(NitorBlock::new);" in BLOCK
assert "protected MapCodec<? extends BaseEntityBlock> codec()" in BLOCK
assert "animateTick(" not in BLOCK, "Nitor must not depend on random display ticks"
assert "new NitorBlockEntity(pos, state)" in BLOCK
assert "level.isClientSide" in BLOCK
assert "createTickerHelper(type, NitorBlockEntityRegistration.TYPE, NitorBlockEntity::clientTick)" in BLOCK
assert "import net.neoforged.neoforge.registries.RegisterEvent;" in REGISTRATION
assert "net.neoforged.neoforge.event.RegisterEvent" not in REGISTRATION
assert "ThaumcraftBlocks.NITOR.values()" in REGISTRATION
assert "validBlocks.length != 16" in REGISTRATION
assert "BlockEntityType.Builder.of(NitorBlockEntity::new, validBlocks).build(null)" in REGISTRATION
assert "int tick = blockEntity.tickCount++;" in ENTITY
assert "if (tick % 10 == 0)" in ENTITY
assert "level.addParticle(nitor.coloredGlow()" in ENTITY
assert "random.nextFloat() * 0.06D" in ENTITY
assert 'Boolean.getBoolean("thaumcraft.nitorSmoke")' in ENTITY
assert "TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=" in ENTITY

# BETA26 TileNitor uses BlockNitor.getMapColor(...).colorValue, not the dye/firework RGB.
assert "private static int mapColorForDyeRgb(int dyeRgb)" in BLOCK
assert "LegacyNitorColors.variants()" in BLOCK
assert "variant.particleRgb() == dyeRgb" in BLOCK
assert "DyeColor.valueOf(variant.modernDye().toUpperCase(Locale.ROOT))" in BLOCK
assert ".getMapColor().col & 0xFFFFFF" in BLOCK
assert "this.particleRgb = mapColorForDyeRgb(this.flameRgb);" in BLOCK
assert "((this.particleRgb >> 16) & 0xFF)" in BLOCK
assert "((this.particleRgb >> 8) & 0xFF)" in BLOCK
assert "(this.particleRgb & 0xFF)" in BLOCK
assert "((this.flameRgb >> 16) & 0xFF)" not in BLOCK
assert "TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=" in BLOCK

variants = re.findall(
    r'new Variant\("([^"]+)",\s*"([^"]+)",\s*(0x[0-9A-Fa-f]+)\)',
    LEGACY_COLORS,
)
assert len(variants) == 16, f"expected 16 BETA26 dye variants, got {len(variants)}"
assert len({legacy for legacy, _, _ in variants}) == 16
assert len({int(rgb, 16) for _, _, rgb in variants}) == 16

# Exercise the real client-log verifier: a matching map/particle color passes,
# while the old dye RGB, a mismatched particle, and missing color evidence fail.
def verify_log(name: str, color_marker: str | None) -> subprocess.CompletedProcess[str]:
    tick = "TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=20 flames=20 cores=2"
    parts = [tick]
    if color_marker is not None:
        parts.append(color_marker)
    with tempfile.TemporaryDirectory() as temp_dir:
        log_path = Path(temp_dir) / f"{name}.log"
        log_path.write_text("\n".join(parts) + "\n", encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(VERIFIER), str(log_path)],
            cwd=ROOT, capture_output=True, text=True,
        )

# Use a deterministic, distinct synthetic map color; the verifier tests equality,
# not a hard-coded vanilla palette value.
good = verify_log(
    "good",
    "TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=16767037 mapColor=1193046 particleColor=1193046",
)
assert good.returncode == 0, good.stdout + good.stderr

wrong_particle = verify_log(
    "wrong-particle",
    "TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=16767037 mapColor=1193046 particleColor=16767037",
)
assert wrong_particle.returncode != 0

old_dye_rgb = verify_log(
    "old-dye-rgb",
    "TC_SMOKE_NITOR_PARTICLE_COLOR=YES dyeRgb=16767037 mapColor=16767037 particleColor=16767037",
)
assert old_dye_rgb.returncode != 0

missing_color = verify_log("missing-color", None)
assert missing_color.returncode != 0

generated = subprocess.run(
    [sys.executable, "tools/client_dust_sound_smoke.py"],
    cwd=ROOT, capture_output=True, text=True, check=True,
).stdout
subprocess.run(["bash", "-n"], input=generated, text=True, check=True)
nitor_place = generated.index("send_chat '/setblock 4 151 12 thaumcraft:nitor_yellow'")
nitor_verify = generated.index("python3 tools/verify_nitor_client_tick.py client-smoke/client.log")
salis_capture = generated.index("capture 08k-firststeps-real-bookshelf-before-salis")
dust_baseline = generated.index("python3 tools/check_dust_capture_noise.py client-smoke/dust-baseline.pcm")
assert nitor_place < nitor_verify < salis_capture < dust_baseline
assert "NITOR_CLIENT_TICK=PASS" in generated
assert "-Dthaumcraft.nitorSmoke=true" in WORKFLOW
assert "python tools/test_nitor_client_tick.py" in WORKFLOW
assert "python tools/verify_nitor_client_tick.py client-smoke/client.log" in WORKFLOW
assert "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/NitorBlockEntity.java" in WORKFLOW

print("NITOR_CLIENT_TICK_AND_MAP_COLOR_SOURCE_CONTRACT=PASS (20/20/2 cadence, 16 variants, real client required)")
