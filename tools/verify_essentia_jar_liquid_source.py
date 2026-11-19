#!/usr/bin/env python3
"""Verify the imported animated liquid texture is byte-identical to pinned BETA26."""
from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PNG = ROOT / "src/main/resources/assets/thaumcraft/textures/block/animatedglow.png"
META = ROOT / "src/main/resources/assets/thaumcraft/textures/block/animatedglow.png.mcmeta"
SOURCE_BLOB_SHA1 = "8ab0fc7d8f4dc6df54527bce4900d6e8cd12ec03"
EXPECTED_FRAMES = list(range(20)) + list(range(18, 0, -1))

data = PNG.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("BETA26 animatedglow is not a PNG")
width, height = struct.unpack(">II", data[16:24])
git_blob_sha1 = hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()
if git_blob_sha1 != SOURCE_BLOB_SHA1:
    raise SystemExit(f"animatedglow source blob mismatch: {git_blob_sha1}")
if (width, height) != (16, 320):
    raise SystemExit(f"animatedglow dimensions changed: {(width, height)}")

metadata = json.loads(META.read_text(encoding="utf-8"))
animation = metadata.get("animation", {})
if animation.get("frametime") != 2 or animation.get("frames") != EXPECTED_FRAMES:
    raise SystemExit("BETA26 animatedglow ping-pong animation metadata changed")

print(f"BETA26 animatedglow source identity: PASS sha1={git_blob_sha1} bytes={len(data)} size={width}x{height}")
print(f"BETA26 animatedglow animation: PASS frametime=2 frames={len(EXPECTED_FRAMES)} ping-pong=0..19..1")
