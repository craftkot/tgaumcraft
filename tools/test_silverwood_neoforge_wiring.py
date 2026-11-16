#!/usr/bin/env python3
"""Source-level M12 wiring guard, NOT a NeoForge compilation or gameplay test."""
from pathlib import Path

repo_root = Path(__file__).resolve().parent.parent
source = (repo_root / "src/neoforge/java/dev/ehtid/thaumcraft/worldgen/SilverwoodLegacyFeature.java").read_text(encoding="utf-8")
checks = {
    "feature subclass": "extends Feature<NoneFeatureConfiguration>" in source,
    "worldgen context": "FeaturePlaceContext<NoneFeatureConfiguration>" in source,
    "preflight before writes": source.index("SilverwoodPlacementPreflight.mayPlace(") < source.index("SilverwoodLegacyTree.place("),
    "shared core tree phases": "SilverwoodLegacyTree.place(" in source,
    "blockstate injection": "SilverwoodLegacyFeature(BlockState log, BlockState leaves)" in source,
    "axis mapping": all(s in source for s in ("Direction.Axis.X", "Direction.Axis.Y", "Direction.Axis.Z")),
    "conditional writes": "if (canReplace(before)) {" in source and "if (level.setBlock(pos, leaves, 2)) {" in source
                          and "if (setLogOverOwnLeaves(level, pos, state, leaves, ownLeaves))" in source,
    "canopy-first log replacement limited to current attempt": "ownLeaves.contains(pos)" in source
                          and "existing.equals(leavesState)" in source
                          and "if (!level.setBlock(pos, logState, 2)) return false;" in source,
    "log overwrite passes exact canopy state": "state, leaves, ownLeaves)" in source
                          and "BlockState logState, BlockState leavesState," in source,
    "overwrite permission consumed after successful log write": "ownLeaves.remove(pos);" in source
                          and source.index("if (!level.setBlock(pos, logState, 2)) return false;") < source.index("ownLeaves.remove(pos);"),
    "successful leaf writes tracked": "if (canReplace(before) && level.setBlock(pos, leaves, 2))" in source
                          and "ownLeaves.add(pos);" in source,
    "no pre-existing leaf-type bypass": "existing.is(leafState.getBlock())" not in source,
    "partial trunk rejected": "if (rejectedLeafWrite[0] || writtenLogs.size() != attemptedLogs[0])" in source
                          and "attemptedLogs[0]++;" in source
                          and "writtenLogs.put(pos, state);" in source,
    "trunk rollback precedes canopy rollback": "SilverwoodTrunkRollback.restore(originalLogs, writtenLogs," in source
                          and source.index("SilverwoodTrunkRollback.restore(") < source.index("SilverwoodCanopyRollback.restore("),
    "original block before own canopy preserved": "originalLeaves.getOrDefault(pos, before)" in source,
    "canopy rollback on partial trunk": "if (rejectedLeafWrite[0] || writtenLogs.size() != attemptedLogs[0])" in source
                          and "originalLeaves.putIfAbsent(pos, before);" in source
                          and "SilverwoodCanopyRollback.restore(originalLeaves, leaves," in source,
    "rollback delegates conditional restore": "level::getBlockState, (pos, before) -> level.setBlock(pos, before, 2)" in source,
    "setBlock success propagated": "if (canReplace(before)) {" in source and "if (level.setBlock(pos, leaves, 2)) {" in source,
    "rejected leaf writes fail placement and trigger rollback": "rejectedLeafWrite[0] = true;" in source
                          and "if (rejectedLeafWrite[0] || writtenLogs.size() != attemptedLogs[0])" in source,
    "failed preflight returns false": "return false;" in source,
    "explicit RNG caveat": "NOT exact legacy RNG parity" in source,
}
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if not all(checks.values()):
    raise SystemExit(1)
print(f"M12 source-level wiring: {len(checks)} checks passed (not a compile test)")
