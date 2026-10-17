#!/usr/bin/env python3
"""Guard legacy Thaumcraft survival block drops and original Nitor colors."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
base = ROOT / "src/main/resources/data/thaumcraft/loot_table/blocks"
colors = (
    "black", "blue", "brown", "cyan", "gray", "green", "lightblue", "lime",
    "magenta", "orange", "pink", "purple", "red", "silver", "white", "yellow"
)
names = ("arcane_workbench", "crucible", *(f"nitor_{color}" for color in colors))
for block in names:
    path = base / f"{block}.json"
    assert path.is_file(), f"missing break drop for {block}"
    root = json.loads(path.read_text(encoding="utf-8"))
    assert root.get("type") == "minecraft:block", block
    assert len(root.get("pools", ())) == 1, block
    pool = root["pools"][0]
    assert pool.get("rolls") == 1, block
    entries = pool.get("entries", ())
    assert len(entries) == 1, block
    assert entries[0].get("type") == "minecraft:item", block
    assert entries[0].get("name") == "thaumcraft:" + block, block
    assert any(x.get("condition") == "minecraft:survives_explosion"
               for x in pool.get("conditions", ())), block
print(f"THAUMCRAFT_SURVIVAL_DROP_TABLES_OK={len(names)}")
