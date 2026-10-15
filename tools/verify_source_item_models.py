#!/usr/bin/env python3
"""Source-faithful BETA26 scanner/Salis visual assets, not stub textures."""
from pathlib import Path
import hashlib
import json

root = Path("src/main/resources/assets/thaumcraft")
original_hashes = {
    "textures/item/salis_mundus.png": "2e193df6de2f3237d1ff54b04a66a9b0332429156912e19e11baadb5022e2514",
    "textures/item/scanner.png": "6267ea1ede77e04f66e506a4ca997d795ee3d0c7be4009bd6ef49abd5ff6442b",
    "textures/item/scanscreen.png": "68de90dafd6a72aed37166ac774017dbcf935215236c862b0c71056bc47c75f5",
    "models/item/scanner.obj": "6adbc3c4a4ec444e2b07c0bdde3566203158a660676187747f1c447a9e130f49"
}
for relative, expected in original_hashes.items():
    file = root / relative
    assert file.is_file(), file
    content = file.read_bytes()
    assert hashlib.sha256(content).hexdigest() == expected, file
    if relative.endswith(".png"):
        assert content.startswith(bytes.fromhex("89504e470d0a1a0a")), file

obj = (root / "models/item/scanner.obj").read_text()
assert obj.count("\ng ") == 3
assert sum(line.startswith("f ") for line in obj.splitlines()) == 56
for group in ("g scanner", "g screen", "g screen_back"):
    assert group in obj, group

salis = json.loads((root / "models/item/salis_mundus.json").read_text())
assert salis == {"parent":"minecraft:item/generated",
                 "textures":{"layer0":"thaumcraft:item/salis_mundus"}}
assert json.loads((root / "textures/item/salis_mundus.png.mcmeta").read_text()) == {
    "animation":{"frametime":2}}
assert json.loads((root / "textures/item/scanscreen.png.mcmeta").read_text()) == {
    "texture":{"blur":True}}

scanner = json.loads((root / "models/item/thaumometer.json").read_text())
assert scanner["loader"] == "neoforge:obj"
assert scanner["model"] == "thaumcraft:models/item/scanner.obj"
assert scanner["mtl_override"] == "thaumcraft:models/item/scanner.mtl"
assert scanner["render_type"] == "minecraft:translucent"
assert scanner["flip_v"] is True
assert scanner["textures"] == {
    "body":"thaumcraft:item/scanner",
    "pane":"thaumcraft:item/scanscreen",
    "particle":"thaumcraft:item/scanner"}
for display in ("gui", "ground", "fixed", "firstperson_righthand",
                "firstperson_lefthand","thirdperson_righthand","thirdperson_lefthand"):
    assert display in scanner["display"], display
mtl = (root / "models/item/scanner.mtl").read_text()
assert "newmtl body" in mtl and "newmtl pane" in mtl
assert "map_Kd #body" in mtl and "map_Kd #pane" in mtl
print("Original BETA26 scanner OBJ/PNG hashes and Salis Mundus animated art: OK")
