#!/usr/bin/env python3
"""Check player-visible starter item names across both bundled languages."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "src/main/resources/assets/thaumcraft/lang"
en = json.loads((root / "en_us.json").read_text(encoding="utf-8"))
ru = json.loads((root / "ru_ru.json").read_text(encoding="utf-8"))
required = (
    "item.thaumcraft.crystal_essence",
    "item.thaumcraft.crystal_essence.named",
    "item.thaumcraft.crystal_essence.content",
    "item.thaumcraft.salis_mundus",
    "item.thaumcraft.thaumonomicon",
    "item.thaumcraft.thaumometer",
    "item.thaumcraft.jar.contents",
    "item.thaumcraft.jar.filter",
)
for name in required:
    assert en.get(name), f"missing EN translation: {name}"
    assert ru.get(name), f"missing RU translation: {name}"
    assert en[name].count("%s") == ru[name].count("%s"), (
        f"localized placeholder mismatch: {name}"
    )
print("THAUMCRAFT_STARTER_ITEM_LANG_OK")
