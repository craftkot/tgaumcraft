#!/usr/bin/env python3
"""Fail-closed semantic and pixel evidence for the real Arcane Workbench GUI."""
from __future__ import annotations

import base64
import math
import re
import sys
from pathlib import Path

MARKER = "TC_SMOKE_WORKBENCH_GUI_RENDERED=YES"
FIELD = re.compile(r"([a-z][a-z0-9_]*)=([^\s]+)")
EXPECTED = {
    "en_us": ("Arcane Workbench", "available", "vis"),
    "ru_ru": ("Магический верстак", "доступно", "вис"),
}


def _decode(fields: dict[str, str], name: str) -> str:
    try:
        raw = base64.b64decode(fields[name], validate=True)
        return raw.decode("utf-8")
    except (KeyError, ValueError, UnicodeDecodeError) as exc:
        raise AssertionError(f"invalid or missing {name}") from exc


def _integer(fields: dict[str, str], name: str) -> int:
    try:
        return int(fields[name], 10)
    except (KeyError, ValueError) as exc:
        raise AssertionError(f"invalid or missing {name}") from exc


def _fraction(value: str, name: str) -> float:
    try:
        result = float(value)
    except ValueError as exc:
        raise AssertionError(f"invalid {name} pixel fraction") from exc
    if not math.isfinite(result) or not 0.0 <= result <= 1.0:
        raise AssertionError(f"out-of-range {name} pixel fraction")
    return result


def assert_workbench_render(
    language: str, client_log: str, title_ink: str, panel_ink: str
) -> dict[str, bool]:
    if language not in EXPECTED:
        raise AssertionError(f"unsupported Minecraft language: {language}")
    markers = [line for line in client_log.splitlines() if MARKER in line]
    if len(markers) != 1:
        raise AssertionError(f"expected exactly one rendered-screen marker, got {len(markers)}")

    payload = markers[0].split(MARKER, 1)[1]
    fields = dict(FIELD.findall(payload))
    required = {
        "title_b64", "available_text_b64", "cost_text_b64", "available_vis",
        "cost_vis", "crystal_mask", "title_width", "title_x", "title_y",
        "panel_x", "panel_y", "panel_width", "panel_height", "text_width",
        "available_source_width", "available_rendered_width", "available_scale_milli",
        "available_center_x", "available_y", "max_lines", "gui_width", "gui_height",
    }
    missing = sorted(required - fields.keys())
    if missing:
        raise AssertionError("missing render fields: " + ", ".join(missing))

    expected_title, expected_available, expected_cost = EXPECTED[language]
    title = _decode(fields, "title_b64")
    available_text = _decode(fields, "available_text_b64")
    cost_text = _decode(fields, "cost_text_b64")
    available = _integer(fields, "available_vis")
    cost = _integer(fields, "cost_vis")
    title_width = _integer(fields, "title_width")
    title_x = _integer(fields, "title_x")
    title_y = _integer(fields, "title_y")
    gui_width = _integer(fields, "gui_width")
    panel_width = _integer(fields, "panel_width")
    available_source_width = _integer(fields, "available_source_width")
    available_rendered_width = _integer(fields, "available_rendered_width")
    available_scale_milli = _integer(fields, "available_scale_milli")
    available_center_x = _integer(fields, "available_center_x")
    available_y = _integer(fields, "available_y")
    available_scale = available_scale_milli / 1000.0

    checks = {
        "localized title component": title == expected_title,
        "localized available component": available_text == f"{available} {expected_available}",
        "localized cost component": cost_text == f"{cost} {expected_cost}",
        "20-vis recipe preview": cost == 20,
        "sufficient synced Vis": available >= cost,
        "six BETA26 primal requirements": _integer(fields, "crystal_mask") == 0b111111,
        "available label fits panel without clipping": (
            available_source_width > 0
            and available_rendered_width == math.ceil(
                available_source_width * available_scale + available_scale
            )
            and available_rendered_width <= panel_width - 4
        ),
        "available label uses BETA26 half-scale placement": (
            available_scale_milli == 500 and available_center_x == 168
            and available_y == 46
        ),
        "title stays inside GUI": title_width > 0 and title_x == 16 and title_y == 5
        and title_x + title_width <= gui_width - 2,
        "original panel geometry": (
            _integer(fields, "panel_x"), _integer(fields, "panel_y"),
            panel_width, _integer(fields, "panel_height"),
            _integer(fields, "text_width"), _integer(fields, "max_lines"),
            _integer(fields, "gui_width"), _integer(fields, "gui_height"),
        ) == (144, 42, 46, 54, 43, 2, 190, 234),
        "title pixels present": _fraction(title_ink, "title") >= 0.10,
        "Vis panel pixels present": _fraction(panel_ink, "panel") >= 0.10,
    }
    failed = [name for name, passed in checks.items() if not passed]
    if failed:
        raise AssertionError("failed: " + ", ".join(failed))
    return checks


def main(argv: list[str]) -> int:
    if len(argv) != 5:
        print(
            "usage: assert_client_workbench_gui_render.py LANG CLIENT_LOG TITLE_INK PANEL_INK",
            file=sys.stderr,
        )
        return 2
    language, log_path, title_ink, panel_ink = argv[1:]
    try:
        checks = assert_workbench_render(
            language, Path(log_path).read_text(encoding="utf-8", errors="replace"),
            title_ink, panel_ink,
        )
    except (OSError, AssertionError) as exc:
        print(f"WORKBENCH_GUI_RENDER=FAIL {exc}")
        return 1
    for name in checks:
        print(f"WORKBENCH_GUI_{name.upper().replace(' ', '_')}=PASS")
    print("WORKBENCH_GUI_RENDER=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
