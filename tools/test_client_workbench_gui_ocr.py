#!/usr/bin/env python3
"""Regression fixtures for full EN/RU Workbench Vis labels and clipped-prefix rejection."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_gui_ocr.py")
spec = importlib.util.spec_from_file_location("workbench_ocr", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

fixtures = [
    ("en_us", ("; ¥", "Meeane Workbench"), ("196\navailable\n20 vis", "196\navailabl\n20 wis")),
    ("en_us", ("Arcane Workbench",), ("148\navailabk\n20 vis", "148\navailable\n26 wis")),
    ("ru_ru", ("Магический верстак",), ("139\nдоступно\n20 вис", "139\nдоступн\n28 вис")),
    ("ru_ru", ("Магический верстак",), ("155\nдоступнo\n2 вис", "155\nдоступно\n2 вис")),
]
checks = 0
for language, title, panel in fixtures:
    result = module.assert_workbench_ocr(language, title, panel)
    assert all(result.values()), (language, title, panel, result)
    checks += len(result)

negative = [
    ("en_us", ("Arcane Workbench",), ("196\navailab...\n20 vis",)),
    ("en_us", ("Arcane Workbench",), ("196\navailab\n20 vis",)),
    ("en_us", ("Arcane Workbench",), ("196\navail\n20 vis",)),
    ("ru_ru", ("Магический верстак",), ("139\nдост...\n20 вис",)),
    ("ru_ru", ("Магический верстак",), ("139\nдоступ\n20 вис",)),
    ("ru_ru", ("Магический верстак",), ("139\nдос...\n20 вис",)),
]
for language, title, panel in negative:
    result = module.assert_workbench_ocr(language, title, panel)
    assert not all(result.values()), (language, title, panel, result)
    checks += 1

print(f"Workbench full available-label OCR: PASS ({checks} positive/negative assertions)")
