#!/usr/bin/env python3
"""BETA26 alembic, basic smelter and furnace GUI original-art regression."""
from pathlib import Path
import hashlib
import json

root=Path("src/main/resources/assets/thaumcraft")
hashes={
    "textures/block/alembic_normal.png":"c068ac47662775da10655d10a61a5d834720fa8d",
    "textures/block/smelter_basic_front_off.png":"f9ed7edb0be5851e782d61b4d4d578084fe87121",
    "textures/block/smelter_basic_front_on.png":"17ea0d3458c32143a33520b16d5bf461250166cc",
    "textures/block/smelter_basic_side.png":"8aab02d6db0b3bdd111c59cef33cb5064bb2631b",
    "textures/block/smelter_top.png":"0435b7b713ce5b5009a99a69cb12f4e3209e0cc8",
    "textures/gui/gui_smelter.png":"7995faddd05718aeb164cf8b11605b36de78421b"
}
for rel,sha in hashes.items():
    data=(root/rel).read_bytes()
    assert data[:8]==bytes.fromhex("89504e470d0a1a0a"),rel
    assert hashlib.sha1(b"blob "+str(len(data)).encode()+bytes([0])+data).hexdigest()==sha,rel

ale=json.loads((root/"models/block/alembic_normal.json").read_text())
assert ale["elements"][0]["from"]==[3,3,3]
assert ale["elements"][0]["to"]==[13,13,13]
assert ale["textures"]["side"]=="thaumcraft:block/alembic_normal"
assert json.loads((root/"models/item/alembic.json").read_text())["parent"]=="thaumcraft:block/alembic_normal"
for name in ("off","on"):
    sm=json.loads((root/("models/block/smelter_basic_"+name+".json")).read_text())
    assert sm["textures"]["north"]=="thaumcraft:block/smelter_basic_front_"+name
    assert sm["textures"]["up"]=="thaumcraft:block/smelter_top"
variants=json.loads((root/"blockstates/smelter_basic.json").read_text())["variants"]
assert len(variants)==8
assert all("facing="+direction+",lit="+lit in variants
           for direction in ("north","south","east","west") for lit in ("true","false"))
print("BETA26_ALEMBIC_SMELTER_GUI_SOURCE_ART_OK")
