#!/usr/bin/env python3
"""BETA26 dust asset and narrow client-only sound dispatch regression."""
from pathlib import Path
import hashlib
import json
import re

root = Path("src/main/resources/assets/thaumcraft")
data = (root / "sounds/dust.ogg").read_bytes()
sha = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
assert sha == "cc1934233f9757e735c55c7f36e4da5d86bbf9f4", sha
sounds = json.loads((root / "sounds.json").read_text())
assert sounds["dust"] == {"category": "block", "sounds": [{"name": "thaumcraft:dust", "stream": False}]}
registry = Path("src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java").read_text()
assert 'DUST = SOUND_EVENTS.register("dust"' in registry
item = Path("src/neoforge/java/dev/ehtid/thaumcraft/item/SalisMundusItem.java").read_text()
assert item.index("DustTarget.NONE") < item.index("playLocalSound(")
assert "if (context.getLevel().isClientSide)" in item
assert re.search(r"playLocalSound\([\s\S]*?ThaumcraftSounds.DUST.get\(\), SoundSource.PLAYERS, 0\.33F,[\s\S]*?nextGaussian\(\) \* 0\.05F, false\)", item)
assert "playSound(null" not in item
print("BETA26 original dust.ogg, registration, successful-client-only Salis trigger: PASS")
