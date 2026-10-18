#!/usr/bin/env python3
"""Verify all 148 original BETA26 coordinates, categories and title/icon IDs.

Compare source manifest JSON against the dedicated Java mapping WITHOUT
relying on a Java JSON parser or a manually duplicated expected table.
"""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
manifest = json.loads((root / "analysis/research_manifest.json").read_text(encoding="utf-8"))
source = (root / "src/main/java/dev/ehtid/thaumcraft/port/core/"
          "LegacyResearchLayout.java").read_text(encoding="utf-8")

pat = re.compile(
    r'^\s*register\(ordered,\s*("(?:[^"\\]|\\.)*"),\s*'
    r'("(?:[^"\\]|\\.)*"),\s*(-?\d+),\s*(-?\d+),\s*'
    r'("(?:[^"\\]|\\.)*"),\s*("(?:[^"\\]|\\.)*")\);',
    re.MULTILINE)
actual = [(json.loads(k), json.loads(cat), int(x), int(y),
           json.loads(title), json.loads(icon))
          for k, cat, x, y, title, icon in pat.findall(source)]
expected = [(e["key"], e["category"], *e["location"],
             e["name"], e["icons"][0]) for e in manifest["entries"]]
if len(actual) != 148 or actual != expected:
    for i, (got, want) in enumerate(zip(actual, expected)):
        if got != want:
            print(f"Mismatch at record {i}: mapping={got!r}, original={want!r}", file=sys.stderr)
            break
    print(f"M07 research geometry MISMATCH: mapping={len(actual)}, original={len(expected)}",
          file=sys.stderr)
    sys.exit(1)
if any(len(e["location"]) != 2 for e in manifest["entries"]):
    sys.exit("M07 invalid source coordinate pair")
if len({(e["category"], *e["location"]) for e in manifest["entries"]}) != 148:
    sys.exit("M07 source research node coordinates overlap")
print("M07 original BETA26 geometry manifest: PASS 148/148 nodes, source order, titles/icons")
