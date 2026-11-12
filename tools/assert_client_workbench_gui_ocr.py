#!/usr/bin/env python3
"""Supplementary OCR check for the clipped localized available-Vis label.

The exact title and 20-Vis cost are verified by the actual screen render-path
marker plus screenshot pixels. Tesseract is deliberately not authoritative for
Minecraft's bitmap-font title or the glyph '0'.
"""
from __future__ import annotations

import re
import sys
import unicodedata


def tokens(value: str) -> list[str]:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return re.findall(r"[0-9a-zа-яё]+", normalized)


def edit_distance(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    for index, char in enumerate(left, 1):
        current = [index]
        for other_index, other in enumerate(right, 1):
            current.append(min(
                current[-1] + 1,
                previous[other_index] + 1,
                previous[other_index - 1] + (char != other),
            ))
        previous = current
    return previous[-1]


def contains_label_prefix(value: str, expected: str) -> bool:
    return any(
        len(word) >= len(expected) and edit_distance(word, expected) <= 1
        for word in tokens(value)
    )


def assert_workbench_ocr(
    language: str,
    _title_variants: tuple[str, ...],
    panel_variants: tuple[str, ...],
) -> dict[str, bool]:
    if language == "en_us":
        available_prefix = "availab"
    elif language == "ru_ru":
        available_prefix = "дост"
    else:
        raise ValueError(f"unsupported Minecraft language: {language}")
    return {
        "localized available label": any(
            contains_label_prefix(text, available_prefix)
            for text in panel_variants
        )
    }


def main(argv: list[str]) -> int:
    if len(argv) != 6:
        print(
            "usage: assert_client_workbench_gui_ocr.py "
            "LANG TITLE_PSM6 TITLE_PSM8 PANEL_PSM6 PANEL_PSM11",
            file=sys.stderr,
        )
        return 2
    language, title6, title8, panel6, panel11 = argv[1:]
    try:
        checks = assert_workbench_ocr(language, (title6, title8), (panel6, panel11))
    except ValueError as exc:
        print(f"WORKBENCH_OCR_ERROR: {exc}", file=sys.stderr)
        return 2
    for name, passed in checks.items():
        print(f"WORKBENCH_OCR_{name.upper().replace(' ', '_')}={'PASS' if passed else 'FAIL'}")
    if not all(checks.values()):
        print("WORKBENCH_OCR_TOKENS_PANEL_PSM6=" + repr(tokens(panel6)))
        print("WORKBENCH_OCR_TOKENS_PANEL_PSM11=" + repr(tokens(panel11)))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
