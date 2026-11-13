#!/usr/bin/env python3
"""Positive and fail-closed fixtures for the real-client Workbench render marker."""
import base64
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_gui_render.py")
spec = importlib.util.spec_from_file_location("workbench_render", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def b64(value: str) -> str:
    return base64.b64encode(value.encode("utf-8")).decode("ascii")


def marker(language: str, **overrides: str) -> str:
    title, available_label, cost_label = module.EXPECTED[language]
    values = {
        "title_b64": b64(title),
        "available_text_b64": b64(f"196 {available_label}"),
        "cost_text_b64": b64(f"20 {cost_label}"),
        "available_vis": "196",
        "cost_vis": "20",
        "crystal_mask": "63",
        "title_width": "96",
        "title_x": "16",
        "title_y": "5",
        "panel_x": "144",
        "panel_y": "42",
        "panel_width": "46",
        "panel_height": "54",
        "text_width": "43",
        "available_source_width": "80",
        "available_rendered_width": "41",
        "available_scale_milli": "500",
        "available_center_x": "168",
        "available_y": "46",
        "max_lines": "2",
        "gui_width": "190",
        "gui_height": "234",
    }
    values.update(overrides)
    return module.MARKER + " " + " ".join(f"{key}={value}" for key, value in values.items())


checks = 0


def expect(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


def rejected(log: str, message: str, title_ink: str = "0.18", panel_ink: str = "0.15") -> None:
    try:
        module.assert_workbench_render("en_us", log, title_ink, panel_ink)
    except AssertionError:
        expect(True, message)
    else:
        expect(False, message)


for language in ("en_us", "ru_ru"):
    result = module.assert_workbench_render(
        language, "client prefix\n" + marker(language) + "\n", "0.18", "0.15"
    )
    expect(all(result.values()), f"valid {language} marker rejected")

rejected("no marker\n", "missing render marker passed")
rejected(marker("en_us") + "\n" + marker("en_us"), "duplicate render markers passed")
rejected(marker("en_us", title_b64=b64("Arcane Furnace")), "wrong localized title passed")
rejected(marker("en_us", available_text_b64=b64("196 missing")), "wrong available label passed")
rejected(marker("en_us", cost_text_b64=b64("20 essentia")), "wrong cost label passed")
rejected(marker("en_us", cost_vis="28", cost_text_b64=b64("28 vis")), "non-20 recipe cost passed")
rejected(marker("en_us", crystal_mask="31"), "incomplete primal requirement mask passed")
rejected(marker("en_us", title_width="180"), "overflowing title passed")
rejected(marker("en_us", panel_y="43"), "shifted Vis panel passed")
rejected(marker("en_us", available_rendered_width="43"), "clipped available label passed")
rejected(marker("en_us", available_source_width="100"), "inconsistent available text width passed")
rejected(marker("en_us", available_scale_milli="1000"), "full-size available label passed")
rejected(marker("en_us", available_center_x="160"), "miscentered available label passed")
rejected(marker("en_us", available_y="44"), "misplaced available label passed")
rejected(marker("en_us", available_vis="10", available_text_b64=b64("10 available")), "insufficient Vis passed")
rejected(marker("en_us"), "empty title pixels passed", title_ink="0.02")
rejected(marker("en_us"), "empty panel pixels passed", panel_ink="0.02")
rejected(marker("en_us", title_b64="%%%"), "malformed Base64 passed")
rejected(marker("en_us"), "invalid pixel fraction passed", title_ink="nan")

print(f"Workbench GUI render marker: PASS ({checks} positive/negative assertions)")
