#!/usr/bin/env python3
"""Verify the original BETA26 key sound on a real jar-brace interaction."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ITEM = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/item/JarBraceItem.java"
REGISTRY = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java"
MANIFEST = ROOT / "src/main/resources/assets/thaumcraft/sounds.json"
AUDIO = ROOT / "src/main/resources/assets/thaumcraft/sounds/key.ogg"
SMOKE = ROOT / "tools/client_jar_sound_smoke.py"
WORKFLOW = ROOT / ".github/workflows/closure-jar-sound-client.yml"
EXPECTED_KEY_BLOB = "d31d64c98a5e31416dfc32659c66c2f367b19960"

def git_blob_sha1(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()

item = ITEM.read_text(encoding="utf-8")
registry = REGISTRY.read_text(encoding="utf-8")
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
smoke = SMOKE.read_text(encoding="utf-8")
workflow = WORKFLOW.read_text(encoding="utf-8")

data = AUDIO.read_bytes()
actual_blob = git_blob_sha1(data)
if actual_blob != EXPECTED_KEY_BLOB:
    raise SystemExit(f"Original BETA26 key.ogg Git blob mismatch: {actual_blob}")
if not data.startswith(b"OggS"):
    raise SystemExit("key.ogg is not an Ogg/Vorbis stream")
if manifest.get("key") != {
    "category": "ambient",
    "sounds": [{"name": "thaumcraft:key", "stream": False}],
}:
    raise SystemExit("BETA26 key sound manifest differs from the original one-shot ambient event")
if not re.search(r'KEY\s*=\s*SOUND_EVENTS\.register\("key"', registry):
    raise SystemExit("ThaumcraftSounds.KEY is not registered")
if 'ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "key")' not in registry:
    raise SystemExit("KEY event uses a different resource location")

method_start = item.index("public InteractionResult useOn")
client_start = item.index("if (context.getLevel().isClientSide())", method_start)
client_return = item.index("return InteractionResult.SUCCESS;", client_start)
client_branch = item[client_start:client_return]
expected_call = (
    "context.getLevel().playLocalSound(context.getClickedPos(),\n"
    "                    ThaumcraftSounds.KEY.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);"
)
if expected_call not in client_branch:
    raise SystemExit("Client jar-brace path does not play original KEY at BETA26 volume/pitch")
if item.count("playLocalSound(") != 1:
    raise SystemExit("JarBraceItem must play KEY exactly once and only in the client branch")
if item.index("if (jar.blocked()) return InteractionResult.PASS;") > client_start:
    raise SystemExit("Already-braced jars must not play KEY")
if item.index("if (!player.mayUseItemAt") > client_start:
    raise SystemExit("Interaction permission must be checked before playing KEY")
server_branch = item[client_return:]
if "jar.applyBrace()" not in server_branch or "context.getItemInHand().shrink(1);" not in server_branch:
    raise SystemExit("Server must still apply the brace and consume exactly one item")
if "playLocalSound(" in server_branch:
    raise SystemExit("Server branch must not play a duplicate KEY sound")

for marker in (
    "JAR_BRACE_FIXTURE_READY", "JAR_BRACE_ITEM_READY",
    "JAR_BRACE_SERVER_APPLIED", "JAR_BRACE_ITEM_CONSUMED",
    "JAR_BRACE_KEY_INTERACTION_AND_ORIGINAL_PCM=PASS",
):
    if marker not in smoke:
        raise SystemExit(f"Real-client jar-brace scenario missing marker: {marker}")
if "match_key_audio_pcm.py client-smoke/key-baseline.pcm client-smoke/key-event.pcm" not in smoke:
    raise SystemExit("Real-client PCM capture is not matched against original key.ogg")
if "python tools/verify_jar_brace_key_sound.py" not in workflow:
    raise SystemExit("CI does not run the BETA26 jar-brace sound verifier")
if "python tools/test_key_audio_match.py" not in workflow:
    raise SystemExit("CI does not run the PCM matcher regression")
print("BETA26 jar-brace KEY event, client-only timing, original OGG bytes and real interaction smoke: PASS")
