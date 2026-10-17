#!/usr/bin/env python3
"""6.1.BETA26 CraftingEvents.getBurnTime: Alumentum burns for 4800 ticks."""
import json
from pathlib import Path
fuel=json.loads(Path("src/main/resources/data/neoforge/data_maps/item/furnace_fuels.json").read_text())
assert fuel["replace"] is False
assert fuel["values"]["thaumcraft:alumentum"]["burn_time"]==4800
assert len(fuel["values"])==1
print("BETA26_ALUMENTUM_FUEL_4800_TICKS_OK")
