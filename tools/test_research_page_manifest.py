#!/usr/bin/env python3
"""Compare generated M07 Java stage fields with actual BETA26 manifest JSON.

Java is generated source, not a new rule engine. The verifier ensures no field
was silently omitted or populated with guessed replacements.
"""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
manifest = json.loads((root / "analysis/research_manifest.json").read_text(encoding="utf-8"))
src = (root / "src/main/java/dev/ehtid/thaumcraft/port/core/LegacyResearchPages.java"
       ).read_text(encoding="utf-8")

# Check exact source string constants and field presence, plus 271 source
# stage registrations; never count Java constructors inside record definitions.
actual_stages = re.findall(r'new Stage\("([^"]+)"', src)
expected_stages = [s["text"] for e in manifest["entries"] for s in e.get("stages", [])]
actual_addenda = re.findall(r'new Addendum\("([^"]+)"', src)
expected_addenda = [a["text"] for e in manifest["entries"] for a in e.get("addenda", [])]
actual_pages = re.findall(r'put\(dest, new Page\("([^"]+)"', src)
expected_pages = [e["key"] for cat in (
    "ALCHEMY", "ARTIFICE", "AUROMANCY", "BASICS",
    "ELDRITCH", "GOLEMANCY", "INFUSION")
    for e in manifest["entries"] if e["category"] == cat]
if actual_stages != [s["text"] for cat in (
        "ALCHEMY", "ARTIFICE", "AUROMANCY", "BASICS",
        "ELDRITCH", "GOLEMANCY", "INFUSION")
        for e in manifest["entries"] if e["category"] == cat
        for s in e.get("stages", [])]:
    sys.exit("M07 stage localization source order/count mismatch")
if actual_addenda != [a["text"] for cat in (
        "ALCHEMY", "ARTIFICE", "AUROMANCY", "BASICS",
        "ELDRITCH", "GOLEMANCY", "INFUSION")
        for e in manifest["entries"] if e["category"] == cat
        for a in e.get("addenda", [])]:
    sys.exit("M07 original addenda order mismatch")
if actual_pages != expected_pages:
    sys.exit("M07 research page key/order mismatch")
for entry in manifest["entries"]:
    if entry["name"] not in src:
        sys.exit("Missing title from source: " + entry["key"])
    for stage in entry.get("stages", []):
        for field in ("recipes", "required_item", "required_craft",
                      "required_knowledge", "required_knowledgeauram",
                      "required_research"):
            for value in stage.get(field, []):
                if json.dumps(value, ensure_ascii=False) not in src:
                    sys.exit("Missing source requirement: " + entry["key"] + ":" + value)
    for addendum in entry.get("addenda", []):
        for value in addendum.get("required_research", []):
            if json.dumps(value) not in src:
                sys.exit("Missing original addenda gate: " + entry["key"] + ":" + value)
assert len(actual_pages) == 148
assert len(actual_stages) == 271
assert len(actual_addenda) == 16
print("M07 research pages manifest: PASS 148 pages / 271 stages / 16 addenda; fields present")
