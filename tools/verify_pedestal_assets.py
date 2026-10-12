#!/usr/bin/env python3
"""Check 6 source-original BETA26 textures and all 3 NeoForge pedestal models.

Hashes are SHA-256 of original_BETA26.jar zip members from the user's
Thaumcraft 6.1.BETA26 reference, NOT newly painted approximations.
"""
import hashlib
import json
from pathlib import Path

root = Path("src/main/resources/assets/thaumcraft")
expected = {
    "pedestal_side": "4db862fa9de2d72a49132a6cdf9cc370e5010724c13f06c4fba4241d25fc4edf",
    "pedestal_top": "669938183851ccc34b921e86ea65ac9ac7ef9d65307de2cd9bb3bce372690edb",
    "pedestal_ancient_side": "04617f1214c33137c97b8f8601a81429227141b943f3cb7c723e48a4e9aebdfa",
    "pedestal_eldritch_side": "021aa7a7234a23d245f8ae03aaa9db3ae5e2a6c53d3edf7702536c27548c1fbb",
    "ancient_tile": "f5378c227dc926685a37bbb02c3a7bf84d8c551b5dddb4f3fa09af28eb7645b5",
    "eldritch_stone_1": "d974511375ce184958755790fda9bf377638d84f219fc22c0363f5b141f4f57c"
}
for name, digest in expected.items():
    file = root / "textures/block" / (name + ".png")
    data = file.read_bytes()
    assert data.startswith(bytes.fromhex("89504e470d0a1a0a")), file
    assert hashlib.sha256(data).hexdigest() == digest, file

names = ["arcane", "ancient", "eldritch"]
for language in ("en_us", "ru_ru"):
    lang = json.loads((root / "lang" / (language + ".json")).read_text())
    for n in names:
        for kind in ("block", "item"):
            assert lang.get(f"{kind}.thaumcraft.pedestal_{n}"), (language, kind, n)

for n in names:
    name = "pedestal_" + n
    block = json.loads((root / "models/block" / (name + ".json")).read_text())
    item = json.loads((root / "models/item" / (name + ".json")).read_text())
    state = json.loads((root / "blockstates" / (name + ".json")).read_text())
    assert len(state["variants"]) == 16, name
    for i in range(16):
        assert state["variants"][f"charge={i}"]["model"] == f"thaumcraft:block/{name}"
    assert item["parent"] == f"thaumcraft:block/{name}"
    assert (len(block["elements"]) == 3 if n != "ancient"
            else block["parent"] == "thaumcraft:block/pedestal_eldritch")
    if n == "arcane":
        assert [e["from"] for e in block["elements"]] == [
            [0,0,0], [2,12,2], [4,4,4]]
    for texture in block.get("textures", {}).values():
        assert texture.startswith("thaumcraft:block/")
        name_from_ref = texture.removeprefix("thaumcraft:block/")
        assert (root / "textures/block" / (name_from_ref + ".png")).is_file(), texture
    loot = Path("src/main/resources/data/thaumcraft/loot_table/blocks") / (name + ".json")
    pools = json.loads(loot.read_text())["pools"]
    assert pools[0]["entries"][0]["name"] == "thaumcraft:" + name
assert Path("src/neoforge/java/dev/ehtid/thaumcraft/block/PedestalBlock.java").is_file()
assert Path("src/neoforge/java/dev/ehtid/thaumcraft/blockentity/PedestalBlockEntity.java").is_file()
assert Path("src/neoforge/java/dev/ehtid/thaumcraft/client/PedestalItemRenderer.java").is_file()
print("BETA26 3 pedestals, 6 original textures, models, loot and 16 charge states: OK")
