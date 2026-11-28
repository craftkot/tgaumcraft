#!/usr/bin/env python3
"""Guard the BETA26 sneak-empty-hand jar sound branch and unchanged server spill."""
from pathlib import Path

BLOCK = Path("src/neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java").read_text()
ENTITY = Path("src/neoforge/java/dev/ehtid/thaumcraft/blockentity/EssentiaJarBlockEntity.java").read_text()
CORE = Path("src/main/java/dev/ehtid/thaumcraft/port/core/LegacyEssentiaJarLogic.java").read_text()
SMOKE = Path("tools/client_jar_sound_smoke.py").read_text()

assert "import net.minecraft.sounds.SoundEvents;" in BLOCK
start = BLOCK.index("protected InteractionResult useWithoutItem")
end = BLOCK.index("/** Vanilla comparator semantics", start)
method = BLOCK[start:end]
client_start = method.index("if (level.isClientSide)")
server_start = method.index("if (!player.mayInteract")
client = method[client_start:server_start]
server = method[server_start:]

label_predicate = (
    "jar.snapshot().filter() != null\n"
    "           && jar.facing() == hit.getDirection().get3DDataValue()"
)
assert label_predicate in client
assert "ThaumcraftSounds.PAGE.get()" in client
assert "ThaumcraftSounds.JAR.get()" in client
assert "SoundEvents.BOTTLE_FILL" in client
assert "SoundSource.BLOCKS" in client
assert "0.4F, 1.0F, false" in client
assert "0.5F, pitch, false" in client
assert "float pitch = 1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.3F;" in client
assert client.count("level.random.nextFloat()") == 2
assert client.index("ThaumcraftSounds.PAGE.get()") < client.index("ThaumcraftSounds.JAR.get()")
assert client.index("ThaumcraftSounds.JAR.get()") < client.index("SoundEvents.BOTTLE_FILL")
assert "return InteractionResult.SUCCESS;" in client
assert "SoundEvents.BOTTLE_FILL" not in server
assert "return jar.emptyByHand() ? InteractionResult.SUCCESS : InteractionResult.PASS;" in server

empty_start = ENTITY.index("public boolean emptyByHand()")
empty_end = ENTITY.index("public boolean hasStoredData()", empty_start)
empty = ENTITY[empty_start:empty_end]
assert "polluteFlux(worldPosition, spilled)" in empty
assert "LegacyEssentiaJarLogic.emptyByHand(stored)" in empty
helper_start = CORE.index("public static State emptyByHand(State jar)")
helper = CORE[helper_start:helper_start + 260]
assert "jar.filter() == null ? null : jar.aspect()" in helper
assert "if (amount == 0) aspect = null;" not in CORE
assert '{Aspect:"aer",AspectFilter:"aer",Amount:0s}' in SMOKE
assert "send_chat '/setblock 250 151 251 minecraft:air'" in SMOKE
assert "JAR_EMPTY_FIXTURE_CLEARED" in SMOKE
assert SMOKE.index("JAR_EMPTY_FIXTURE_CLEARED") < SMOKE.index("JAR_EMPTY_FIXTURE_READY")


print("BETA26 jar-empty fallback: PAGE-face precedence, jar 0.4, bottle-fill 0.5, triangular pitch and server Flux/reset contract: PASS")
