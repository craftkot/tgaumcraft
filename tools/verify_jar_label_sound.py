#!/usr/bin/env python3
"""Source/asset contract for the BETA26 labelled-jar removal sound."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/block/EssentiaJarBlock.java"
SOUNDS = ROOT / "src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java"
MANIFEST = ROOT / "src/main/resources/assets/thaumcraft/sounds.json"
AUDIO = ROOT / "src/main/resources/assets/thaumcraft/sounds"
SMOKE = ROOT / "tools/client_jar_sound_smoke.py"
MATCHER_TEST = ROOT / "tools/test_page_audio_match.py"

# Git blob IDs from the pinned Thaumcraft 6.1.BETA26 source commit
# 954022bb777b7546281fb36df8522f0ba6b43f81.
ORIGINAL_PAGE_BLOBS = {
    "page1.ogg": "a0c4f5b01b58e5720e79978637c20f425b79f2c5",
    "page2.ogg": "2db59662cb448028f88c54d8df245be03d00b09e",
}


def git_blob_sha1(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


block = BLOCK.read_text(encoding="utf-8")
sounds = SOUNDS.read_text(encoding="utf-8")
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
smoke = SMOKE.read_text(encoding="utf-8")

method_start = block.index("protected InteractionResult useWithoutItem")
client_start = block.index("if (level.isClientSide) {", method_start)
client_end = block.index("return InteractionResult.SUCCESS;", client_start)
client_branch = block[client_start:client_end]

required_conditions = (
    "jar.snapshot().filter() != null",
    "jar.facing() == hit.getDirection().get3DDataValue()",
)
required_call = (
    "level.playLocalSound(pos, ThaumcraftSounds.PAGE.get(), SoundSource.BLOCKS,"
)
if not all(condition in client_branch for condition in required_conditions):
    raise SystemExit("Client page sound is not guarded by the original label-face predicate")
if required_call not in client_branch:
    raise SystemExit("Client label-removal branch does not play the original PAGE event")
if "1.0F, 1.0F, false);" not in client_branch:
    raise SystemExit("BETA26 label-removal sound volume/pitch/local flag changed")
if block.count("playLocalSound(") != 3:
    raise SystemExit("EssentiaJarBlock must contain only PAGE, jar-empty and bottle-fill local sound calls")
if "import net.minecraft.sounds.SoundEvents;" not in block:
    raise SystemExit("Vanilla bottle-fill SoundEvents import is missing")
fallback = client_branch.split("} else {", 1)
if len(fallback) != 2:
    raise SystemExit("Jar-empty fallback must be mutually exclusive with the labelled-face PAGE branch")
fallback = fallback[1]
for call in (
    "ThaumcraftSounds.JAR.get()",
    "SoundEvents.BOTTLE_FILL",
    "0.4F, 1.0F, false);",
    "0.5F, pitch, false);",
    "1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.3F",
):
    if call not in fallback:
        raise SystemExit(f"BETA26 jar-empty fallback sound contract is missing: {call}")
if "import net.minecraft.sounds.SoundSource;" not in block:
    raise SystemExit("SoundSource import missing")

server_branch = block[client_end:]
if "jar.removeLabel(hit.getDirection())" not in server_branch:
    raise SystemExit("Server-side label removal is missing")
if "new ItemStack(ThaumcraftItems.JAR_LABEL.get())" not in server_branch:
    raise SystemExit("Server no longer drops a blank label")

page = manifest.get("page")
if not isinstance(page, dict) or page.get("category") != "master" or page.get("sounds") != [
    "thaumcraft:page1", "thaumcraft:page2"
]:
    raise SystemExit("PAGE event manifest differs from the original two-clip event")
if not re.search(r'PAGE\s*=\s*SOUND_EVENTS\.register\("page"', sounds):
    raise SystemExit("ThaumcraftSounds.PAGE is not registered")

for filename, expected in ORIGINAL_PAGE_BLOBS.items():
    path = AUDIO / filename
    if not path.is_file():
        raise SystemExit(f"Missing original BETA26 audio: {path}")
    actual = git_blob_sha1(path.read_bytes())
    if actual != expected:
        raise SystemExit(f"{filename} differs from pinned BETA26 blob: {actual}")

if not MATCHER_TEST.is_file():
    raise SystemExit("Synthetic page PCM matcher regression test is missing")
if smoke.count("xdotool keydown Shift_L") != 3 or smoke.count("xdotool click 3") != 3:
    raise SystemExit("Real-client smoke must exercise wrong-side, labelled-side and jar-empty shift-right-click")
# The jar fixture uses facing=3 (SOUTH). Minecraft yaw 0 looks south and
# yaw 180 looks north, so the north-side attempt must be the negative case.
wrong_face = "send_chat '/tp @p 250.5 150 249.5 0 0'"
configured_face = "send_chat '/tp @p 250.5 150 253.5 180 0'"
if wrong_face not in smoke or configured_face not in smoke:
    raise SystemExit("Label smoke must hit NORTH as wrong face and SOUTH as configured face")
if "JAR_LABEL_WRONG_FACE_EXPECTED=NORTH" not in smoke:
    raise SystemExit("Wrong-face geometry marker is missing")
if "JAR_LABEL_CONFIGURED_FACE=SOUTH" not in smoke:
    raise SystemExit("Configured-face geometry marker is missing")
if "JAR_EMPTY_WRONG_FACE_EXPECTED=NORTH" not in smoke:
    raise SystemExit("Jar-empty fallback geometry marker is missing")
for marker in (
    "JAR_EMPTY_SERVER_RESET_FILTER_RETAINED",
    "match_jar_audio_pcm.py",
    "match_bottle_fill_audio_pcm.py",
    "JAR_EMPTY_BETA26_SOUND_PAIR_AND_SERVER_RESET=PASS",
):
    if marker not in smoke:
        raise SystemExit(f"Jar-empty real-client assertion is missing: {marker}")
for marker in (
    "JAR_LABEL_WRONG_SIDE_PRESERVED",
    "JAR_LABEL_WRONG_SIDE_NO_PAGE_SOUND=PASS",
    "JAR_LABEL_SERVER_FILTER_REMOVED",
    "JAR_LABEL_SERVER_BLANK_DROP_PRESENT",
    "match_page_audio_pcm.py",
    "JAR_LABEL_REMOVE_AND_ORIGINAL_PAGE_PCM=PASS",
):
    if marker not in smoke:
        raise SystemExit(f"Real-client smoke is missing required assertion: {marker}")

print("BETA26_PAGE_EVENT_MANIFEST=PASS")
print("BETA26_PAGE_AUDIO_BLOBS=PASS")
print("JAR_LABEL_CLIENT_SIDE_PREDICATE_AND_SOUND=PASS")
print("JAR_LABEL_SERVER_REMOVAL_AND_BLANK_DROP=PASS")
print("JAR_LABEL_REAL_CLIENT_WRONG_AND_CORRECT_SIDE_ASSERTIONS=PASS")
