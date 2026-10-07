#!/usr/bin/env python3
"""Extract static scan-registration constructor sites from javap output of BETA26 ConfigResearch.initScannables.

This does not pretend loop sites are single runtime instances: ScanEnchantment and ScanPotion are instantiated once
per vanilla registry entry. ScanBlock also creates companion ScanItem handlers inside its constructor. The report keeps
those distinctions explicit.
"""
from __future__ import annotations
from pathlib import Path
import collections, json, re

ROOT = Path(__file__).resolve().parents[1]
JAVAP = ROOT / "analysis" / "javap" / "ConfigResearch.txt"
OUT = ROOT / "analysis" / "scan_registration_manifest.json"
text = JAVAP.read_text(encoding="utf-8")
sec = text.split("private static void initScannables();", 1)[1]
# The method is the last non-static-init method in this class; static {} marks the end.
sec = sec.split("\n  static {};", 1)[0]
lines = sec.splitlines()

ctor = re.compile(r'// Method (thaumcraft/(?:api/research|common/lib/research)/(Scan[^.]+))\."<init>"')
new_re = re.compile(r'// class (thaumcraft/(?:api/research|common/lib/research)/(Scan\S+))')
str_re = re.compile(r'// String (.*)$')

sites = []
for i, line in enumerate(lines):
    m = ctor.search(line)
    if not m:
        continue
    fqcn = m.group(1).replace('/', '.')
    simple = m.group(2)
    # Find nearest matching `new` within this construction sequence.
    start = i
    while start >= 0:
        nm = new_re.search(lines[start])
        if nm and nm.group(1).replace('/', '.') == fqcn:
            break
        start -= 1
    chunk = lines[max(0, start):i+1]
    strings = [sm.group(1) for x in chunk if (sm := str_re.search(x))]
    research_key = None
    # For all static scan constructors whose first arg is a String, the first string in this local construction is key.
    if simple in {"ScanEntity", "ScanBlock", "ScanItem", "ScanOreDictionary", "ScanMaterial"} and strings:
        research_key = strings[0]
    sites.append({
        "ordinal": len(sites),
        "type": simple,
        "class": fqcn,
        "research_key_candidate": research_key,
        "string_constants_in_constructor_window": strings,
    })

counts = collections.Counter(x["type"] for x in sites)
report = {
    "source": "Thaumcraft-1.12.2-6.1.BETA26.jar / ConfigResearch.initScannables javap bytecode",
    "explicit_constructor_sites": len(sites),
    "explicit_addScannableThing_calls": sec.count("ScanningManager.addScannableThing"),
    "constructor_site_counts": dict(sorted(counts.items())),
    "runtime_expansion_notes": [
        "ScanEnchantment constructor site is inside a loop over the entire Enchantment registry.",
        "ScanPotion constructor site is inside a loop over the entire Potion registry.",
        "Each ScanBlock(String, Block...) constructor also registers companion ScanItem handlers for its blocks.",
    ],
    "sites": sites,
}
OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"wrote {OUT}: {len(sites)} constructor sites")
print(dict(sorted(counts.items())))
