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
# Server knowledge is authoritative; client-side attachment snapshots can lag.
# The original effect is actor-only, not a broadcast to nearby players.
assert item.index("DustTarget.NONE") < item.index("new ClientboundSoundPacket(")
assert "if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer)" in item
assert "serverPlayer.connection.send(new ClientboundSoundPacket(" in item
assert "BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ThaumcraftSounds.DUST.get())" in item
assert re.search(r"float pitch = 1\.0F \+ \(float\) level\.getRandom\(\)\.nextGaussian\(\) \* 0\.05F;", item)
assert re.search(r"SoundSource\.PLAYERS, pos\.getX\(\), pos\.getY\(\), pos\.getZ\(\),\s*0\.33F, pitch", item)
assert "playLocalSound(" not in item
assert "playSound(null" not in item
print("BETA26 original dust.ogg, valid server-side gate, actor-only sound packet: PASS")
