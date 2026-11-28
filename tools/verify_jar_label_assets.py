#!/usr/bin/env python3
"""Verify the jar label background and all 37 BETA26 aspect icons byte-for-byte."""
from pathlib import Path
import hashlib
import json
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "analysis/assets/beta26_jar_label_manifest.json"
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

def verify(entry):
    path = ROOT / entry["path"]
    if not path.is_file():
        raise SystemExit(f"JAR_LABEL_ASSET_MISSING={entry['path']}")
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit(f"JAR_LABEL_ASSET_NOT_PNG={entry['path']}")
    actual_sha = hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()
    if actual_sha != entry["git_blob_sha1"]:
        raise SystemExit(f"JAR_LABEL_ASSET_SHA_MISMATCH={entry['path']} expected={entry['git_blob_sha1']} actual={actual_sha}")
    width, height = struct.unpack(">II", data[16:24])
    if [width, height] != entry["dimensions"]:
        raise SystemExit(f"JAR_LABEL_ASSET_DIMENSIONS={entry['path']} expected={entry['dimensions']} actual={[width, height]}")

verify(manifest["label"])
for entry in manifest["aspects"]:
    verify(entry)

registry = (ROOT / "src/main/java/dev/ehtid/thaumcraft/port/core/AspectRegistry.java").read_text(encoding="utf-8")
registry_tags = re.findall(r'(?:primal|compound)\("([^"]+)"', registry)
manifest_tags = [entry["tag"] for entry in manifest["aspects"]]
if registry_tags != manifest_tags:
    raise SystemExit(f"JAR_LABEL_ASPECT_COVERAGE_MISMATCH registry={registry_tags} manifest={manifest_tags}")
if len(manifest_tags) != 37:
    raise SystemExit(f"JAR_LABEL_ASPECT_COUNT={len(manifest_tags)} expected=37")

print("PASS: BETA26 jar label background + 37 aspect icons are byte-identical to source commit "
      + manifest["source_commit"])
