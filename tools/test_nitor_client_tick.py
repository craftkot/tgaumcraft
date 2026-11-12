#!/usr/bin/env python3
"""Static and generated-client contract for the BETA26 Nitor block and FXGeneric particles."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCK = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/NitorBlock.java").read_text()
ENTITY = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/NitorBlockEntity.java").read_text()
REGISTRATION = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/NitorParticleRegistration.java").read_text()
PARTICLE = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/NitorParticleClientEvents.java").read_text()
WORKFLOW = (ROOT / ".github/workflows/closure-dust-client.yml").read_text()

assert "extends BaseEntityBlock" in BLOCK
assert "simpleCodec(NitorBlock::new)" in BLOCK
assert "box(5.28, 5.28, 5.28, 10.56, 10.56, 10.56)" in BLOCK
assert "return Shapes.empty();" in BLOCK
assert "getMapColor().col" in BLOCK
assert "reportParticleColorIfNeeded()" in BLOCK
assert "DustParticleOptions" not in BLOCK and "coloredGlow()" not in BLOCK

assert "if (!level.isClientSide" in ENTITY
assert "random.nextGaussian() * 0.025D" in ENTITY
assert "random.nextFloat() * 0.06D" in ENTITY
assert "NitorParticleRegistration.FLAME_TYPE" in ENTITY
assert "NitorParticleRegistration.CORE_TYPE" in ENTITY
assert "int tick = blockEntity.tickCount++;" in ENTITY
assert "if (tick % 10 == 0)" in ENTITY
assert "tickCount >= 20" in ENTITY
assert "ParticleTypes.END_ROD" not in ENTITY and "coloredGlow()" not in ENTITY

assert '"nitor_flame"' in REGISTRATION and '"nitor_core"' in REGISTRATION
assert "Registries.PARTICLE_TYPE" in REGISTRATION
assert "helper.register(FLAME_ID, FLAME_TYPE)" in REGISTRATION
assert "helper.register(CORE_ID, CORE_TYPE)" in REGISTRATION

assert "event.registerSpriteSet(NitorParticleRegistration.FLAME_TYPE" in PARTICLE
assert "event.registerSpriteSet(NitorParticleRegistration.CORE_TYPE" in PARTICLE
assert "FLAME_FIRST_FRAME = 264" in PARTICLE
assert "FLAME_FRAME_COUNT = 8" in PARTICLE
assert "CORE_FRAME = 457" in PARTICLE
assert "RenderSystem.blendFunc(770, 1);" in PARTICLE
assert "ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin" in PARTICLE

subprocess.run([sys.executable, "tools/test_nitor_particle.py"], cwd=ROOT, check=True)
generated = subprocess.run(
    [sys.executable, "tools/client_dust_sound_smoke.py"],
    cwd=ROOT, capture_output=True, text=True, check=True,
).stdout
subprocess.run(["bash", "-n"], input=generated, text=True, cwd=ROOT, check=True)

place = generated.index("send_chat '/setblock 3 151 12 thaumcraft:nitor_yellow'")
baseline = generated.index("capture 08m-nitor-particles-before")
active = generated.index("capture 08n-nitor-flame-core-active")
verify = generated.index("python3 tools/verify_nitor_client_tick.py client-smoke/client.log")
remove = generated.index("send_chat '/setblock 3 151 12 minecraft:air'")
salis = generated.index("xdotool click 3", remove)
assert baseline < place < active < verify < remove < salis
assert "send_chat '/tp @p 2.5 150 8.5 -14 0'" in generated
assert "send_chat '/tp @p 2.5 150 11.5 0 0'" in generated
assert generated.index("send_chat '/tp @p 2.5 150 8.5 -14 0'") < baseline
assert "FIRSTSTEPS_REAL_DREAM_SALIS_BOOKSHELF_OK" in generated
assert "assert_smoke_event BOOKSHELF_SALIS_USED" in generated
assert "assert_smoke_event BOOKSHELF_TRANSFORMED" in generated
assert generated.count("capture 08k-firststeps-real-bookshelf-before-salis") == 1
assert generated.count("capture 08l-firststeps-real-bookshelf-after-salis") == 1
assert generated.count("FIRSTSTEPS_REAL_DREAM_SALIS_BOOKSHELF_OK") == 2
assert generated.count("assert_smoke_event BOOKSHELF_SALIS_USED") == 1
assert generated.count("assert_smoke_event BOOKSHELF_TRANSFORMED") == 1
assert generated.find("xdotool click 3", salis + 1) == -1
assert "client-smoke/dust-baseline.pcm" in generated
assert "client-smoke/dust-event.pcm" in generated
assert "python3 tools/match_dust_audio_pcm.py" in generated
assert "soundCategory_music:0.0" in generated
assert "soundCategory_ambient:0.0" in generated
assert "TC_SMOKE_NITOR_CLIENT_TICK=YES ticks=20 flames=20 cores=2" in generated

for path in (
    "src/neoforge/java/dev/ehtid/thaumcraft/client/NitorParticleClientEvents.java",
    "src/neoforge/java/dev/ehtid/thaumcraft/registry/NitorParticleRegistration.java",
    "tools/test_nitor_particle.py",
):
    assert path in WORKFLOW, f"real-client workflow does not trigger for {path}"
assert "src/main/resources/assets/thaumcraft/particles/nitor_*.json" in WORKFLOW
assert "python tools/test_nitor_particle.py" in WORKFLOW

print("BETA26 Nitor block tick, particle registry, generated real-client smoke: PASS")
