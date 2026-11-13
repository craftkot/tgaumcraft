#!/usr/bin/env python3
"""Regression fixtures for isolated full EN/RU Workbench available-label OCR."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_gui_ocr.py")
spec = importlib.util.spec_from_file_location("workbench_ocr", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

fixtures = [
    ("en_us", ("196\navailable", "196\navailabl")),
    ("en_us", ("148\navailabk", "148\navailable")),
    # Actual EN scale-3 client OCR: one bitmap glyph is substituted.
    ("en_us", ("232 avaiable", "232 avaiable")),
    ("ru_ru", ("139\nдоступно", "139\nдоступн")),
    ("ru_ru", ("155\nдоступнo", "155\nдоступно")),
    # Actual RU scale-2 client artifact: PSM11 reads the full word, with one digit substitution.
    ("ru_ru", ("192 доступно", "192 доступно")),
    # Actual RU scale-3 client OCR from the isolated available-line crop.
    ("ru_ru", ("148 доступно.", "148 доступно.")),
    # Inverted-crop PSM8 output from the four exact-head real-client artifacts.
    ("en_us", ("14 avaiable",)),
    ("en_us", ("186 available",)),
    ("ru_ru", ("344 доотулно",)),
    ("ru_ru", ("233 доступна",)),
    # Full-panel PSM11 output from the four actual 2026-11-13 client artifacts.
    ("en_us", ("2@i available 26 wis",)),
    ("en_us", ("149 available 246 Wis",)),
    ("ru_ru", ("149 доступно 2 ЕМС",)),
    ("ru_ru", ("355 доступно 2 ЕС:",)),
]
checks = 0
for language, variants in fixtures:
    result = module.assert_workbench_ocr(language, variants)
    assert all(result.values()), (language, variants, result)
    checks += len(result)

negative = [
    ("en_us", ("196\navailab...",)),
    ("en_us", ("196\navailab",)),
    ("en_us", ("196\navail",)),
    ("ru_ru", ("139\nдост...",)),
    ("ru_ru", ("139\nдоступ",)),
    ("ru_ru", ("139\nдос...",)),
]
for language, variants in negative:
    result = module.assert_workbench_ocr(language, variants)
    assert not all(result.values()), (language, variants, result)
    checks += 1

print(f"Workbench isolated available-label OCR: PASS ({checks} positive/negative assertions)")
