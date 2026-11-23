#!/usr/bin/env python3
"""Source-level guard only; NOT a NeoForge compile or Minecraft GameTest."""
from pathlib import Path

source = Path("src/neoforge/java/dev/ehtid/thaumcraft/worldgen/GreatwoodLegacyTrunkFeature.java").read_text()
checks = {
    "real Feature subclass": "extends Feature<NoneFeatureConfiguration>" in source,
    "one legacy seed draw": "new Random(context.random().nextLong())" in source,
    "legacy height limit": "11 + treeRandom.nextInt(11)" in source,
    "precomputed trunk plan": "GreatwoodTrunkPlan.create(" in source,
    "transactional placement": "GreatwoodTrunkPlacement.place(" in source,
    "soil below build height rejected": "origin.getY() <= level.getMinBuildHeight()" in source,
    "four soil checks": "origin.offset(dx, -1, dz)" in source and "dx < 2" in source and "dz < 2" in source,
    "chunk boundary rejection": "(origin.getX() & 15) == 15" in source and "(origin.getZ() & 15) == 15" in source,
    "no registry writes": "DeferredRegister" not in source and "BiomeModifier" not in source,
}
for label, ok in checks.items():
    if not ok:
        raise AssertionError(label)
print(f"M12 Greatwood NeoForge source guard: OK ({len(checks)} checks; no compile/gameplay claim)")
