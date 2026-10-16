#!/usr/bin/env python3
"""Check copied BETA26 jar source PNGs and unchanged model geometry."""
from pathlib import Path
import hashlib
import json

root = Path("src/main/resources/assets/thaumcraft")
expected = {
    "jar_bottom": "53ce838d68ec3e566119ced57b08cd0c03574ce416b01ec36d1a866c44297b8b",
    "jar_side": "b2c5e716ad1816a2dc07d28d5d6dc5393b292ffa006e04793ac50ae06e40ba26",
    "jar_top": "7a85f4417ff6d659e6f6fd850d4b641dec2efb0c9db58f1fdeb778f3e6fa8dde"
}
for name, sha in expected.items():
    source = (root / "textures/block" / (name + ".png")).read_bytes()
    assert source.startswith(bytes.fromhex("89504e470d0a1a0a"))
    assert hashlib.sha256(source).hexdigest() == sha, name

model = json.loads((root / "models/block/jar_normal.json").read_text())
assert model["ambientocclusion"] is False
assert model["render_type"] == "minecraft:translucent"
assert model["textures"] == {
    "side": "thaumcraft:block/jar_side",
    "bottom": "thaumcraft:block/jar_bottom",
    "top": "thaumcraft:block/jar_top",
    "particle": "thaumcraft:block/jar_side"
}
assert len(model["elements"]) == 2
assert model["elements"][0]["from"] == [3, 0, 3]
assert model["elements"][0]["to"] == [13, 12, 13]
assert model["elements"][1]["from"] == [5, 12, 5]
assert model["elements"][1]["to"] == [11, 14, 11]
assert set(model["display"]) == {
    "gui", "fixed", "ground", "thirdperson_lefthand", "thirdperson_righthand"
}
assert json.loads((root / "models/item/jar_normal.json").read_text())["parent"] == "thaumcraft:block/jar_normal"
assert json.loads((root / "blockstates/jar_normal.json").read_text())["variants"][""]["model"] == "thaumcraft:block/jar_normal"
print("Source BETA26 jar PNGs, translucency and original geometry: OK")
