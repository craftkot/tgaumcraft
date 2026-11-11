#!/usr/bin/env python3
"""Guard the BETA26 sneak-empty-hand jar sound branch and unchanged server spill."""
from pathlib import Path

BLOCK = Path("src/neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java").read_text()
ENTITY = Path("src/neoforge/java/dev/ehtid/thaumcraft/blockentity/EssentiaJarBlockEntity.java").read_text()

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
assert "new LegacyEssentiaJarLogic.State(null, stored.filter(), 0)" in empty

print("BETA26 jar-empty fallback: PAGE-face precedence, jar 0.4, bottle-fill 0.5, triangular pitch and server Flux/reset contract: PASS")
