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
    # BETA26 BlockPillar.breakBlock emits exactly 2 of the corresponding
    # stone material; NEVER self-drops the pillar.
    material = "stone_" + ("eldritch_tile" if variant=="eldritch" else variant)
    loot = json.loads((Path("src/main/resources/data/thaumcraft/loot_table/blocks") /
        f"{block}.json").read_text())
    entry = loot["pools"][0]["entries"][0]
    assert entry["name"] == "thaumcraft:"+material
    assert entry["functions"] == [{"function":"minecraft:set_count","count":2}]
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

# Original BETA26 source PNG SHA256, extracted directly from original_BETA26.jar.
# Refuse files rewritten through image editors or substituted placeholder art.
import hashlib
source_hashes = {
    "arcane_stone_1": "1ffe08de5439f897f8a88276bd9d9514d8225cbe61bc7b6843f8b508d1dfe702",
    "arcane_stone_2": "d80004c95957dc4a13e5e5cc7d400b5068d9d7bf721ba8b529154f691cf0f912",
    "arcane_stone_3": "f71286f9045ebac11a6d33dbbc34fbc8bfd8957378d87d692892e216cd69760a",
    "ancient_stone_0": "fd4260c99fddf9d2be09e12beba863b4be7b97acacb1f2a1a36a7390cfe1f911",
    "ancient_stone_1": "519d0f524bc4868e814a509156b10b06b3b634392e814bfedca044686ddfb3c6",
    "ancient_stone_2": "13e459c9428dcf210892784bdb152712b5e90928a5e8fda469bf91055b080b1e",
    "ancient_stone_3": "3ccf626917b7d73fe5ffe2907a84f9f459bd6b798d5613fdf082e30b61d66a53",
    "ancient_stone_4": "571b7b3e919c7f2b322f0b546627f18197b5878ad94b2eab55d2a176d63c77e4",
    "ancient_stone_5": "f133a10520d618bb4ba5de1340aa579f41ef7cffe2378b7894a9bcc260d9a83f",
    "eldritch_stone_1": "d974511375ce184958755790fda9bf377638d84f219fc22c0363f5b141f4f57c",
    "eldritch_stone_2": "5ddb4ca4e639b2ba895cc363db68f214b4117048b924260928d8b710edcf1a72",
    "eldritch_stone_3": "8db913fc0b25441f2cc17724a11a112b5a07ec61b1c768ceb90f7884b5f2f210",
}
for name,digest in source_hashes.items():
    png = (root/"textures/block"/f"{name}.png").read_bytes()
    assert png.startswith(bytes.fromhex("89504e470d0a1a0a")), name
    assert hashlib.sha256(png).hexdigest()==digest, name
expected = {
    "stone_arcane": [f"arcane_stone_{i}" for i in (1,2,3)],
    "stone_ancient": [f"ancient_stone_{i}" for i in range(6)],
    "stone_eldritch_tile": [f"eldritch_stone_{i}" for i in (1,2,3)]
}
for stone, textures in expected.items():
    model=json.loads((root/"models/block"/f"{stone}.json").read_text())
    assert model["parent"]=="minecraft:block/cube"
    values=set(model["textures"].values())
    assert values=={"thaumcraft:block/"+n for n in textures}
    assert (root/"models/item"/f"{stone}.json").is_file()
    states=json.loads((root/"blockstates"/f"{stone}.json").read_text())["variants"][""]
    assert len(states) == (16 if stone=="stone_ancient" else 4),stone
    assert all(s["model"]==f"thaumcraft:block/{stone}" for s in states)
    loot=json.loads((Path("src/main/resources/data/thaumcraft/loot_table/blocks")/
                     f"{stone}.json").read_text())
    assert loot["pools"][0]["entries"][0]["name"]=="thaumcraft:"+stone

for lang in ("en_us","ru_ru"):
    d=json.loads((root/"lang"/f"{lang}.json").read_text())
    for kind in ("item","block"):
        for name in expected:
            assert d.get(f"{kind}.thaumcraft.{name}"),(lang,kind,name)

print("Original infusion pillar drops, stone models, all 12 PNG SHA256: OK; ritual pending")
