#!/usr/bin/env python3
"""Regression fixtures captured from all four real EN/RU Workbench artifacts."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_gui_ocr.py")
spec = importlib.util.spec_from_file_location("workbench_ocr", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

fixtures = [
    ("en_us", ("; ¥", "Meeane Workbench"), ("196\na\\ail ab...\n28 Wis", "196\naVailab...\n28 wis")),
    ("en_us", ("ra Fi 7 .", "Arcane Workbench ©"), ("148\na ailabk...\n2h Wis", "148\navailabk...\n26 wis")),
    ("ru_ru", ("р х", "Мэгический верстак"), ("139\nдостч...\n2 вис", "139\nдестч...\n28 вс")),
    ("ru_ru", ("р х", "Магическым верстак —"), ("155\nдосты...\n2 ЕМС", "155\nдсеты...\n2 ЕМС")),
]
checks = 0
for language, title, panel in fixtures:
    result = module.assert_workbench_ocr(language, title, panel)
    assert all(result.values()), (language, title, panel, result)
    checks += len(result)

negative = [
    ("en_us", ("Arcane Workbench",), ("196\nmissing\n20 vis",)),
    ("en_us", ("Arcane Workbench",), ("196\navailable\n20 vis",)),
    ("ru_ru", ("Магический верстак",), ("139\nдос\n20 вис",)),
    ("ru_ru", ("Магический верстак",), ("139\nдоступно\n20 вис",)),
]
for language, title, panel in negative:
    result = module.assert_workbench_ocr(language, title, panel)
    assert not all(result.values()), (language, title, panel, result)
    checks += 1

print(f"Workbench available-label OCR: PASS ({checks} positive/negative assertions)")
