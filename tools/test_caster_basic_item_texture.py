#!/usr/bin/env python3
"""Regression for the BETA26 basic-caster item texture's 1.21 block-atlas path."""
from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/thaumcraft"
ARMOR_TEXTURE = ASSETS / "textures/entity/armor/caster_basic.png"
ITEM_TEXTURE = ASSETS / "textures/item/caster_basic.png"
ITEM_MODEL = ASSETS / "models/item/caster_basic.json"
PARENT_MODEL = ASSETS / "models/item/caster_gauntlet.json"

armor_bytes = ARMOR_TEXTURE.read_bytes()
item_bytes = ITEM_TEXTURE.read_bytes()
assert armor_bytes.startswith(b"\x89PNG\r\n\x1a\n"), "BETA26 armor texture is not PNG"
assert item_bytes == armor_bytes, "item-atlas copy must remain byte-identical to BETA26 source"
width, height = struct.unpack(">II", armor_bytes[16:24])
assert (width, height) == (32, 32), f"unexpected BETA26 caster texture dimensions: {width}x{height}"

item_model = json.loads(ITEM_MODEL.read_text(encoding="utf-8"))
parent_model = json.loads(PARENT_MODEL.read_text(encoding="utf-8"))
assert item_model.get("parent") == "thaumcraft:item/caster_gauntlet"
assert item_model.get("textures", {}).get("skin") == "thaumcraft:item/caster_basic", (
    "inventory model must resolve its skin through the item atlas, not textures/entity/armor"
)
faces = [
    face
    for element in parent_model.get("elements", [])
    for face in element.get("faces", {}).values()
]
assert faces, "caster gauntlet parent model has no textured faces"
assert all(face.get("texture") == "#skin" for face in faces), (
    "parent model no longer consumes the child skin texture override"
)

for model_path in (ASSETS / "models/item").glob("*.json"):
    source = model_path.read_text(encoding="utf-8")
    assert "thaumcraft:entity/armor/caster_basic" not in source, (
        f"{model_path.relative_to(ROOT)} still points an item model at the armor-only atlas path"
    )

print(
    "CASTER_BASIC_ITEM_ATLAS_TEXTURE=PASS "
    f"dimensions={width}x{height} sha1={hashlib.sha1(item_bytes).hexdigest()}"
)
