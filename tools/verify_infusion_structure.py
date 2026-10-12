#!/usr/bin/env python3
"""Lightweight source consistency for newly registered BETA26 infusion blocks.

This is a resource/regression guard; actual client/server behavior is tested
separately in client_smoke.sh and CoreParity.
"""
import json
from pathlib import Path
root=Path("src/main/resources/assets/thaumcraft")
for variant in ("arcane", "ancient", "eldritch"):
    block=f"pillar_{variant}"
    state=json.loads((root/"blockstates"/f"{block}.json").read_text())
    assert set(state["variants"]) == {f"facing={f}" for f in ("north","south","east","west")}
    assert all(v["model"]==f"thaumcraft:block/{block}" for v in state["variants"].values())
    model=json.loads((root/"models/block"/f"{block}.json").read_text())
    assert len(model["elements"])==4
    assert model["elements"][0]["to"][1] == 8
    assert model["elements"][-1]["to"][1] == 32
    assert (root/"textures/block"/(model["textures"]["all"].split("/")[-1]+".png")).exists()
    item=json.loads((root/"models/item"/f"{block}.json").read_text())
    assert item["parent"]==f"thaumcraft:block/{block}"
    # NO fabricated vanilla stone drops: original stoneArcane etc still absent.
    assert not (Path("src/main/resources/data/thaumcraft/loot_table/blocks") / f"{block}.json").exists()
model=json.loads((root/"models/block/infusion_matrix.json").read_text())
assert len(model["elements"])==5
state=json.loads((root/"blockstates/infusion_matrix.json").read_text())
assert state["variants"][""]["model"]=="thaumcraft:block/infusion_matrix"
assert (root/"models/item/infusion_matrix.json").is_file()
for lang in ("en_us","ru_ru"):
    d=json.loads((root/"lang"/f"{lang}.json").read_text())
    for kind in ("block","item"):
        for name in ("pillar_arcane","pillar_ancient","pillar_eldritch","infusion_matrix"):
            assert d.get(f"{kind}.thaumcraft.{name}"), (kind,lang,name)
for path in (
  "src/neoforge/java/dev/ehtid/thaumcraft/block/InfusionMatrixBlock.java",
  "src/neoforge/java/dev/ehtid/thaumcraft/block/InfusionPillarBlock.java",
  "src/neoforge/java/dev/ehtid/thaumcraft/blockentity/InfusionMatrixBlockEntity.java"
):
    assert Path(path).is_file(),path
print("Original infusion pillar & matrix registrations/resources: OK (ritual not yet implemented)")
