#!/usr/bin/env python3
"""BETA26 tube sprite, blockshape and six-way resource parity guards.

Original source: TheDarkTower314/Thaumcraft-6-Source-Code at
954022bb777b7546281fb36df8522f0ba6b43f81.
Only original assets renamed from 1.12 'blocks/' and 'items/' to 1.21 paths.
"""
from pathlib import Path
import hashlib
import json

base=Path("src/main/resources/assets/thaumcraft")
expected={
  "textures/block/tube.png":"91d733abc39f0229309307b3065d97f2d5460feb",
  "textures/item/tube_normal.png":"e4133109d5826f867e2fb01d2166a8097e47ab31",
}
for path,sha in expected.items():
    data=(base/path).read_bytes()
    assert data.startswith(bytes.fromhex("89504e470d0a1a0a")),path
    assert hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()==sha,path

core=json.loads((base/"models/block/tube_core.json").read_text())
side=json.loads((base/"models/block/tube_side.json").read_text())
assert core["elements"][0]["from"]==[6.5,6.5,6.5]
assert core["elements"][0]["to"]==[9.5,9.5,9.5]
assert len(side["elements"])==2
assert side["elements"][0]["from"]==[6,0,6]
assert side["elements"][0]["to"]==[10,1,10]
assert side["elements"][1]["from"]==[6.5,1,6.5]
assert side["elements"][1]["to"]==[9.5,6.5,9.5]
assert core["textures"]["side"]==side["textures"]["side"]=="thaumcraft:block/tube"
blockstate=json.loads((base/"blockstates/tube.json").read_text())
assert len(blockstate["multipart"])==7
assert blockstate["multipart"][0]["apply"]["model"]=="thaumcraft:block/tube_core"
assert {list(part["when"])[0] for part in blockstate["multipart"][1:]}=={
    "down","up","north","south","west","east"
}
item=json.loads((base/"models/item/tube.json").read_text())
assert item["textures"]["layer0"]=="thaumcraft:item/tube_normal"
print("SOURCE_BETA26_TUBE_GEOMETRY_TEXTURES_OK")
