#!/usr/bin/env python3
"""Fail closed if generated Java pages differ from original BETA26 JSON.

Checks every one of the 148 pages, 271 stages, 16 addenda, *all* source
requirements and the exact per-page ordering. This is a data parity audit;
it does not claim a NeoForge compile or playable 1.21 recipes.
"""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
manifest = json.loads((root / "analysis/research_manifest.json").read_text(encoding="utf-8"))
source = (root / "src/main/java/dev/ehtid/thaumcraft/port/core/"
          "LegacyResearchPages.java").read_text(encoding="utf-8")
categories = ("ALCHEMY", "ARTIFICE", "AUROMANCY", "BASICS",
              "ELDRITCH", "GOLEMANCY", "INFUSION")


def quoted(value):
    # Matches the JSON-style escaping used by the existing Java generator.
    return json.dumps(value, ensure_ascii=False)


def array(values):
    if not values:
        return "List.of()"
    return "List.of(" + ", ".join(quoted(value) for value in values) + ")"


def stage_repr(stage):
    return ("new Stage(" + quoted(stage["text"]) + ", "
            + ", ".join(array(stage.get(field)) for field in (
                "recipes", "required_item", "required_craft",
                "required_knowledge", "required_knowledgeauram",
                "required_research")) + ", " + str(stage.get("warp", 0)) + ")")


def addendum_repr(addendum):
    return ("new Addendum(" + quoted(addendum["text"]) + ", "
            + array(addendum.get("recipes")) + ", "
            + array(addendum.get("required_research")) + ")")


mappings = list(re.finditer(r'put\(dest, new Page\("([^"]+)"', source))
ordered = [entry for cat in categories for entry in manifest["entries"]
           if entry["category"] == cat]
if len(mappings) != len(ordered) or len(ordered) != 148:
    sys.exit("M07 source page count mismatch")
stages = addenda = 0
for i, (match, entry) in enumerate(zip(mappings, ordered)):
    key = entry["key"]
    if match.group(1) != key:
        sys.exit(f"M07 page ordering mismatch #{i}: {match.group(1)} vs {key}")
    start = match.start()
    end = mappings[i + 1].start() if i + 1 < len(mappings) else len(source)
    chunk = source[start:end]
    heading = 'put(dest, new Page(' + quoted(key) + ', ' + quoted(entry["name"])
    if not chunk.startswith(heading):
        sys.exit(f"M07 source title mismatch: {key}")
    last = -1
    for stage in entry.get("stages", []):
        expected = stage_repr(stage)
        found = chunk.find(expected, last + 1)
        if found < 0:
            sys.exit(f"M07 missing/wrong stage requirement in {key}: {stage['text']}")
        last = found
        stages += 1
    last = -1
    for addon in entry.get("addenda", []):
        expected = addendum_repr(addon)
        found = chunk.find(expected, last + 1)
        if found < 0:
            sys.exit(f"M07 missing/wrong source addendum in {key}: {addon['text']}")
        last = found
        addenda += 1

if stages != 271 or addenda != 16:
    sys.exit(f"M07 source totals wrong: {stages} stages, {addenda} addenda")
print("M07 BETA26 source pages: PASS 148 pages, 271 stages, 16 addenda (exact fields)")
