#!/usr/bin/env python3
"""Source integrity and non-fake gameplay guard for seven BETA26 BlockCrystals.

This tests resources and source code, not a complete Minecraft world. Real
client screenshots and server-side aura/loot behavior require separate checks.
"""
from pathlib import Path
import hashlib, json
asset=Path("src/main/resources/assets/thaumcraft")
data=Path("src/main/resources/data/thaumcraft/loot_table/blocks")
expected={
    "block/crystal.png": "606cfd0ca77a5c80366412c3207b08b55a57ff2d1860d7a252dc18e3c1163093",
    "item/crystal_planter.png": "7beb6d3e520eed694c5523d557044b589e8c2a2d970012e2ca2eb949ceeff0b4",
}
mesh=(asset/"models/block/crystal_source.obj").read_bytes()
assert hashlib.sha256(mesh).hexdigest()=="8221ea1f3001ae7abb41f2f87a007c652b77cd138b2d29d707a06253faec23c6"
assert sum(x.startswith(b"f ") for x in mesh.splitlines())==96
assert sum(x.startswith(b"v ") for x in mesh.splitlines())==72
assert "map_Kd #crystal" in (asset/"models/block/crystal_source.mtl").read_text()
for file,digest in expected.items():
    content=(asset/"textures"/file).read_bytes()
    assert content[:8]==bytes.fromhex("89504e470d0a1a0a"),file
    assert hashlib.sha256(content).hexdigest()==digest,file
tags=("aer","ignis","aqua","terra","ordo","perditio","vitium")
for tag in tags:
    name="crystal_"+tag
    states=json.loads((asset/"blockstates"/f"{name}.json").read_text())["variants"]
    assert len(states)==16
    assert set(states)=={f"size={n},gen={g}" for n in range(4) for g in range(1,5)}
    assert all(states[f"size={n},gen={g}"]["model"]==
        f"thaumcraft:block/{name}_size{n}" for n in range(4) for g in range(1,5))
    for n in range(4):
        model=json.loads((asset/"models/block"/f"{name}_size{n}.json").read_text())
        assert model["loader"]=="neoforge:obj"
        assert model["model"]=="thaumcraft:models/block/crystal_source.obj"
        assert model["mtl_override"]=="thaumcraft:models/block/crystal_source.mtl"
        assert model["render_type"]=="minecraft:translucent"
        assert model["flip_v"] is True
        parts=model["visibility"]
        groups=("s014","s015","s016","s017","s018","s019","s024","s026")
        assert set(parts)==set(groups)
        assert [part for part in groups if parts[part]]==list(groups[:n+1])
    item=json.loads((asset/"models/item"/f"{name}.json").read_text())
    assert item["textures"]["layer0"]=="thaumcraft:item/crystal_planter"
    loot=json.loads((data/f"{name}.json").read_text())
    assert len(loot["pools"])==4
    for size,pool in enumerate(loot["pools"]):
        assert pool["conditions"][0]["block"]=="thaumcraft:"+name
        assert pool["conditions"][0]["properties"]=={"size":str(size)}
        entry=pool["entries"][0]
        assert entry["name"]=="thaumcraft:crystal_essence"
        assert entry["functions"][0]=={"function":"minecraft:set_count","count":size+1}
        assert entry["functions"][1]["function"]=="minecraft:set_custom_data"
        assert f'key:"{tag}"' in entry["functions"][1]["tag"]
    for locale in ("en_us","ru_ru"):
        lang=json.loads((asset/"lang"/f"{locale}.json").read_text())
        assert lang["block.thaumcraft."+name]
        assert lang["item.thaumcraft."+name]
# Eight source OBJ groups must be baked separately and selected at
# runtime using the ORIGINAL java.util.Random(seed) permutation.
groups=("s014","s015","s016","s017","s018","s019","s024","s026")
for target in groups:
    shard=json.loads((asset/"models/block"/f"crystal_shard_{target}.json").read_text())
    assert shard["loader"]=="neoforge:obj"
    assert shard["model"]=="thaumcraft:models/block/crystal_source.obj"
    assert shard["mtl_override"]=="thaumcraft:models/block/crystal_source.mtl"
    assert shard["render_type"]=="minecraft:translucent"
    assert shard["visibility"]=={group:(group==target) for group in groups}
model_events=Path("src/neoforge/java/dev/ehtid/thaumcraft/client/CrystalModelEvents.java").read_text()
wrapper=Path("src/neoforge/java/dev/ehtid/thaumcraft/client/SeededCrystalBakedModel.java").read_text()
assert "ModelEvent.RegisterAdditional" in model_events
assert "ModelEvent.ModifyBakingResult" in model_events
assert "ModelResourceLocation.standalone(" in model_events
assert "BlockModelShaper.stateToModelLocation(state)" in model_events
assert "new SeededCrystalBakedModel(prior, parts)" in model_events
assert "state.getSeed(pos)" in wrapper
assert "LegacyCrystalMeshSelection.selectForFace(size, seed, face)" in wrapper
assert "LegacyCrystalMeshSelection.orient(face, p)" in wrapper
assert "LegacyCrystalMeshSelection.orientNormal(face, n)" in wrapper
assert "SUPPORT_FACES" in wrapper
assert "CrystalClusterBlock.hasCrystalSupport" in wrapper
assert "stride == 8" in wrapper
source=Path("src/main/java/dev/ehtid/thaumcraft/port/core/LegacyCrystalMeshSelection.java").read_text()
assert all(f"{face}({offset})" in source for face,offset in
    (("UP",0),("DOWN",5),("EAST",10),("WEST",15),("NORTH",20),("SOUTH",25)))
assert "public static Point orient(Face face, Point p)" in source
assert "originalModel.getQuads" in wrapper  # fallback on unavailable model data
sources={
"src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java":
    ("LegacyBlockCrystalGrowthRules.canGrow","aura.trySpendCrystalAura"),
"src/neoforge/java/dev/ehtid/thaumcraft/world/ThaumcraftAuraSavedData.java":
    ("public float drainFlux","public float getFlux","public void polluteFlux")}
for src,parts in sources.items():
    code=Path(src).read_text()
    for part in parts: assert part in code,(src,part)
# Regression guard against partial aura debit spawning a FULL crystal.
cluster=Path("src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java").read_text()
aura=Path("src/neoforge/java/dev/ehtid/thaumcraft/world/ThaumcraftAuraSavedData.java").read_text()
assert cluster.count("trySpendCrystalAura(pos,flux,10)") == 2
assert "drained>0" not in cluster and "drained<=0" not in cluster
assert "if (available < required) return false;" in aura
assert "if (before == null) return false;" in aura
assert "setDirty();" in aura
print("BETA26 crystal world assets: 7 variants, 16 states, source PNGs, aspect loot OK")
