#!/usr/bin/env python3
"""Verify original BETA26 jar sound bytes and the NeoForge SoundType wiring."""
from pathlib import Path
import hashlib
import json
import re

ROOT = Path("src")
EXPECTED = {
    "jar1.ogg": ("ebdb6360276702bf5f72de24e7210943a5f059af", 4271),
    "jar2.ogg": ("cb1190b2a1584f7928547f2aad21ef503a1ea010", 3947),
    "jar3.ogg": ("95c7f7116e4f6eaa0e55d4fea4ab648a1854f611", 3842),
    "jar4.ogg": ("3fe072d39a72182883bc61b284ca2d18043d6fba", 4230),
}
for name, (expected_blob, expected_size) in EXPECTED.items():
    data = (ROOT / "main/resources/assets/thaumcraft/sounds" / name).read_bytes()
    blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    assert blob == expected_blob, (name, blob)
    assert len(data) == expected_size, (name, len(data))
    assert data.startswith(b"OggS"), name

manifest = json.loads((ROOT / "main/resources/assets/thaumcraft/sounds.json").read_text())
assert manifest["jar"] == {
    "category": "block",
    "sounds": ["thaumcraft:jar1", "thaumcraft:jar2", "thaumcraft:jar3", "thaumcraft:jar4"],
}
registry = (ROOT / "neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java").read_text()
assert 'JAR = SOUND_EVENTS.register("jar"' in registry
assert 'ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "jar")' in registry
block = (ROOT / "neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java").read_text()
assert "private static final SoundType ORIGINAL_JAR_SOUND" in block
assert "0.5F, 1.0F" in block
assert "super(props.sound(ORIGINAL_JAR_SOUND));" in block
for method in ("Break", "Step", "Place", "Hit", "Fall"):
    pattern = r"@Override\s+public SoundEvent get" + method + r"Sound\(\)\s*\{\s*return ThaumcraftSounds\.JAR\.get\(\);\s*\}"
    assert re.search(pattern, block), method
print("BETA26 jar1..jar4 exact Git blobs, random jar event and five SoundType hooks: PASS")
