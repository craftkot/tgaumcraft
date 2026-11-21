#!/usr/bin/env python3
"""Guard the crystal starvation adjacency path against joining in-flight chunks."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java"
source = SOURCE.read_text(encoding="utf-8")

start = source.index("if(LegacyBlockCrystalGrowthRules.starved(available))")
end = source.index("if(LegacyBlockCrystalGrowthRules.canGrow", start)
starvation = source[start:end]

assert "BlockState neighborState = loadedDestination(level, neighbor);" in starvation, (
    "size-zero starvation must read only fully available neighbor chunks"
)
assert "neighborState != null && neighborState.is(this)" in starvation
assert "level.hasChunkAt(neighbor)" not in starvation, (
    "hasChunkAt may be true before the neighbor LevelChunk is ready"
)
assert "level.getBlockState(neighbor)" not in starvation, (
    "Level#getBlockState may join an in-flight chunk future"
)

lookup_start = source.index("private static BlockState loadedDestination")
lookup_end = source.index("@Override public boolean canSurvive", lookup_start)
lookup = source[lookup_start:lookup_end]
assert "getChunkSource().getChunkNow(" in lookup
assert "return chunk == null ? null : chunk.getBlockState(pos);" in lookup

print("TC_CRYSTAL_STARVATION_LOOKUP_GUARD=PASS lookup=getChunkNow no_join=YES")
