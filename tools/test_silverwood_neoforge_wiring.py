#!/usr/bin/env python3
"""Source-level M12 wiring guard, NOT a NeoForge compilation or gameplay test."""
from pathlib import Path

source = Path("src/neoforge/java/dev/ehtid/thaumcraft/worldgen/SilverwoodLegacyFeature.java").read_text()
checks = {
    "feature subclass": "extends Feature<NoneFeatureConfiguration>" in source,
    "worldgen context": "FeaturePlaceContext<NoneFeatureConfiguration>" in source,
    "preflight before writes": source.index("SilverwoodPlacementPreflight.mayPlace(") < source.index("SilverwoodLegacyTree.place("),
    "shared core tree phases": "SilverwoodLegacyTree.place(" in source,
    "blockstate injection": "SilverwoodLegacyFeature(BlockState log, BlockState leaves)" in source,
    "axis mapping": all(s in source for s in ("Direction.Axis.X", "Direction.Axis.Y", "Direction.Axis.Z")),
    "conditional writes": "setIfReplaceable(level, pos, leaves)" in source
                          and "if (setLogOverOwnLeaves(level, new BlockPos(px, py, pz), state, leaves, ownLeaves))" in source,
    "canopy-first log replacement limited to current attempt": "ownLeaves.contains(pos)" in source
                          and "existing.equals(leavesState)" in source
                          and "&& level.setBlock(pos, logState, 2)" in source,
    "log overwrite passes exact canopy state": "state, leaves, ownLeaves)" in source
                          and "BlockState logState, BlockState leavesState," in source,
    "successful leaf writes tracked": "if (setIfReplaceable(level, pos, leaves))" in source
                          and "ownLeaves.add(pos);" in source,
    "no pre-existing leaf-type bypass": "existing.is(leafState.getBlock())" not in source,
    "successful block-write return": "return placed[0] > 0;" in source,
    "setBlock success propagated": "return canReplace(level.getBlockState(pos)) && level.setBlock(pos, state, 2);" in source,
    "failed preflight returns false": "return false;" in source,
    "explicit RNG caveat": "NOT exact legacy RNG parity" in source,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"M12 source-level wiring: {len(checks)} checks passed (not a compile test)")
