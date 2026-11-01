#!/usr/bin/env python3
"""Verify BETA26 jar-brace and blank/filled label source art and item model mappings."""
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/thaumcraft"
SOURCE_HASHES = {
    "jar_brace": "ee447bf4d1d64ff2536ed8876409a7a156d83b7c9228ef2db0ef8f83e4961260",
    "label": "33254f4dc65ba1ecae796e39dcab51db170a054167de4f3ca4111b6f10af1dd0",
    "label_overlay": "a7531f2eef4b42bb6a05e25c19f277ab292f384e61b821310de54e8162c3dffe",
}
for name, expected in SOURCE_HASHES.items():
    path = ASSETS / "textures/item" / (name + ".png")
    data = path.read_bytes()
    assert hashlib.sha256(data).hexdigest() == expected, f"BETA26 PNG mismatch: {path}"
    assert data[:8] == bytes.fromhex("89504e470d0a1a0a"), path
    assert struct.unpack(">II", data[16:24]) == (16, 16), path

def model(name):
    return json.loads((ASSETS / "models/item" / (name + ".json")).read_text(encoding="utf-8"))

brace = model("jar_brace")
assert brace["parent"] == "minecraft:item/generated"
assert brace["textures"] == {"layer0": "thaumcraft:item/jar_brace"}
blank = model("label")
assert blank["parent"] == "minecraft:item/generated"
assert blank["textures"] == {"layer0": "thaumcraft:item/label"}
assert blank["overrides"] == [
    {"predicate": {"custom_model_data": 1}, "model": "thaumcraft:item/label_filled"}
]
filled = model("label_filled")
assert filled["parent"] == "minecraft:item/generated"
assert filled["textures"] == {
    "layer0": "thaumcraft:item/label",
    "layer1": "thaumcraft:item/label_overlay",
}
item = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/JarLabelItem.java").read_text(encoding="utf-8")
assert "DataComponents.CUSTOM_MODEL_DATA" in item
assert "new CustomModelData(1)" in item
# The original filled-label overlay is a separate layer; it must be tinted
# from the NBT-selected aspect, while the blank label stays unchanged.
client = (ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/client/JarLabelColorEvents.java").read_text(encoding="utf-8")
assert "@EventBusSubscriber" in client and "RegisterColorHandlersEvent.Item" in client
assert "JarLabelItem.selectedAspect(stack)" in client
assert "LegacyLabelTint.colorFor" in client
assert "ThaumcraftItems.JAR_LABEL.get()" in client
print("BETA26 jar-brace/label source art, item models and aspect-tint event wiring: OK")
