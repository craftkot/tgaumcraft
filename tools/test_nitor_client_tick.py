#!/usr/bin/env python3
"""Static and generated-client contract for the BETA26 TileNitor tick cadence."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/NitorBlock.java").read_text()
ENTITY = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/NitorBlockEntity.java").read_text()
REGISTRATION = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/NitorBlockEntityRegistration.java").read_text()
WORKFLOW = (ROOT / ".github/workflows/closure-dust-client.yml").read_text()

assert "extends BaseEntityBlock" in BLOCK
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

print("NITOR_CLIENT_TICK_SOURCE_CONTRACT=PASS (20 flame ticks / 2 core ticks; real client required)")
