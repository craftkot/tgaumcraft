#!/usr/bin/env python3
"""Verify the BETA26 basic-caster skin is addressable by the 1.21.1 item atlas."""
from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORIGINAL_TEXTURE = ROOT / "src/main/resources/assets/thaumcraft/textures/entity/armor/caster_basic.png"
ITEM_TEXTURE = ROOT / "src/main/resources/assets/thaumcraft/textures/item/caster_basic.png"
ITEM_MODEL = ROOT / "src/main/resources/assets/thaumcraft/models/item/caster_basic.json"
PARENT_MODEL = ROOT / "src/main/resources/assets/thaumcraft/models/item/caster_gauntlet.json"
SMOKE_SCRIPT = ROOT / "tools/client_jar_sound_smoke.py"
EXPECTED_SHA256 = "e686075c5b3c5b41946086021860c0310cdfddc3daefd07609a495474b7279c1"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> None:
    original = ORIGINAL_TEXTURE.read_bytes()
    item = ITEM_TEXTURE.read_bytes()
    require(original == item, "item-atlas caster texture must be byte-identical to the BETA26 armor skin")
    require(item.startswith(PNG_SIGNATURE), "caster_basic item texture is not a PNG")
    require(item[12:16] == b"IHDR", "caster_basic PNG has no IHDR header")
    width, height = struct.unpack(">II", item[16:24])
    require((width, height) == (32, 32), f"unexpected BETA26 caster_basic dimensions: {width}x{height}")
    digest = hashlib.sha256(item).hexdigest()
    require(digest == EXPECTED_SHA256, f"BETA26 caster_basic texture SHA-256 mismatch: {digest}")

    model = json.loads(ITEM_MODEL.read_text(encoding="utf-8"))
    require(model.get("parent") == "thaumcraft:item/caster_gauntlet", "basic caster must retain its original 3D gauntlet parent")
    require(model.get("textures", {}).get("skin") == "thaumcraft:item/caster_basic", "basic caster must resolve its skin through the default item atlas")
    parent = json.loads(PARENT_MODEL.read_text(encoding="utf-8"))
    faces = [face for element in parent.get("elements", []) for face in element.get("faces", {}).values()]
    require(faces and all(face.get("texture") == "#skin" for face in faces), "gauntlet parent no longer consumes the overridden #skin texture")

    smoke = SMOKE_SCRIPT.read_text(encoding="utf-8")
    required_smoke_contract = (
        "thaumcraft:caster_basic",
        "CASTER_BASIC_ITEM_READY",
        "CASTER_BASIC_ITEM_TEXTURE_REAL_CLIENT=PASS",
        "Missing textures in model thaumcraft:caster_basic#inventory",
        "caster-basic-empty-hand.png",
        "caster-basic-in-hand.png",
        "200x200+824+550",
    )
    missing = [marker for marker in required_smoke_contract if marker not in smoke]
    require(not missing, "real-client caster smoke is missing contract markers: " + ", ".join(missing))

    print(
        "BETA26 caster_basic texture atlas contract: OK "
        f"sha256={digest} dimensions={width}x{height} bytes={len(item)}"
    )


if __name__ == "__main__":
    main()
