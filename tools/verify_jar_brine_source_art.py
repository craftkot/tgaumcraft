#!/usr/bin/env python3
"""Verify the imported jarbrine model texture is byte-identical to BETA26."""
from __future__ import annotations

import hashlib
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PNG = ROOT / "src/main/resources/assets/thaumcraft/textures/models/jarbrine.png"
SOURCE_BLOB_SHA1 = "bafd0aaa30ce2f3aa0b2c7c1e0152777b145e5b4"

data = PNG.read_bytes()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("BETA26 jarbrine model texture is not a PNG")
width, height = struct.unpack(">II", data[16:24])
git_blob_sha1 = hashlib.sha1(
    b"blob " + str(len(data)).encode("ascii") + b"\0" + data
).hexdigest()
if git_blob_sha1 != SOURCE_BLOB_SHA1:
    raise SystemExit(f"jarbrine source blob mismatch: {git_blob_sha1}")
if (width, height) != (64, 32):
    raise SystemExit(f"jarbrine dimensions changed: {(width, height)}")

print(f"BETA26 jarbrine source identity: PASS sha1={git_blob_sha1} bytes={len(data)} size={width}x{height}")
