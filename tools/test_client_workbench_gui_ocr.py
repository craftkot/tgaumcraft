#!/usr/bin/env python3
"""Regression fixtures captured from real EN/RU Minecraft GUI artifacts."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_gui_ocr.py")
spec = importlib.util.spec_from_file_location("workbench_ocr", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

fixtures = [
    (
        "en_us",
        ("Arcane Workbench", "Arcane Workbench"),
        ("239\naVailab...\n2H wis", "aod  availab...  26 wis"),
    ),
    (
        "en_us",
        ("Arcane Horkbenct lems", "Arcane Workbenctk Ene"),
        ("25 aVaillab...\n2H ways", "aos  SVailak...  2H ws"),
    ),
    (
        "ru_ru",
        ("Магический верстак", "Магический верстак"),
        ("184\nдост...\n2 ЕС", "154  дост...  2 ЕМС"),
    ),
    (
        "ru_ru",
        ("Магический верстфимя", "Магический верстфимя"),
        ("165\nдосты...\n2 ЕС:", "1655  досты...  2 ЕС:"),
    ),
]
checks = 0
for language, title, panel in fixtures:
    result = module.assert_workbench_ocr(language, title, panel)
    assert all(result.values()), (language, title, panel, result)
    checks += len(result)

negative = [
    ("en_us", ("Arcane Furnace",), ("239\navailable\n20 vis",)),
    ("en_us", ("Arcane Workbench",), ("239\nmissing\n20 vis",)),
    ("en_us", ("Arcane Workbench",), ("239\navailable\n30 vis",)),
    ("en_us", ("Arcane Workbench",), ("239\navailable\n20 way",)),
    ("ru_ru", ("Магический верстак",), ("184\nдос\n20 вис",)),
    ("ru_ru", ("Магический верстак",), ("184\nдоступно\n20 ос",)),
    ("ru_ru", ("Магический тигель",), ("184\nдоступно\n20 вис",)),
    # A visible 20 on a separate line cannot substitute for the recipe cost.
    ("en_us", ("Arcane Workbench",), ("20\navailable\n30 vis",)),
]
for language, title, panel in negative:
    result = module.assert_workbench_ocr(language, title, panel)
    assert not all(result.values()), (language, title, panel, result)
    checks += 1

print(f"Workbench GUI OCR matcher: PASS ({checks} positive/negative assertions)")
