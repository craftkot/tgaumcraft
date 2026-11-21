#!/usr/bin/env python3
"""BETA26 BlockCrystal sound identity and seven-variant wiring regression.

Source/asset test only. Full NeoForge compile and real game audio are separate.
"""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path("src")
sound = ROOT / "main/resources/assets/thaumcraft/sounds/crystal.ogg"
data = sound.read_bytes()
blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
assert blob == "f0133f05feb6ea9526a5121c4d389bac1cb1b8a8", blob
assert data.startswith(b"OggS") and len(data) == 5524

manifest = json.loads((ROOT / "main/resources/assets/thaumcraft/sounds.json").read_text())
assert manifest["crystal"] == {
    "category": "block",
    "sounds": [{"name": "thaumcraft:crystal", "stream": False}],
}
registry = (ROOT / "neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java").read_text()
assert 'CRYSTAL = SOUND_EVENTS.register("crystal"' in registry
assert 'ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crystal")' in registry

block = (ROOT / "neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java").read_text()
assert "super(props.dynamicShape().sound(ORIGINAL_CRYSTAL_SOUND));" in block
assert block.count("props.dynamicShape()") == 1
assert "new SoundType(" in block and "0.5F, 1.0F" in block
for method in ("Break", "Step", "Place", "Hit", "Fall"):
    assert re.search(
        rf"@Override\s+public SoundEvent get{method}Sound\(\)\s*"
        rf"\{{\s*return ThaumcraftSounds\.CRYSTAL\.get\(\);\s*\}}", block
    ), method
blocks = (ROOT / "neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftBlocks.java").read_text()
for aspect in ("aer", "ignis", "aqua", "terra", "ordo", "perditio", "vitium"):
    assert f'props -> new CrystalClusterBlock(props,"{aspect}")' in blocks, aspect
print("BETA26 crystal.ogg exact Git blob, 5 lazy sound hooks, 7 variants: PASS")
