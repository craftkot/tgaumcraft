#!/usr/bin/env python3
"""Fail-closed OCR checks for the localized BETA26 Arcane Workbench screenshot.

Minecraft's bitmap font and deliberately clipped side-panel labels are not
reliably recognized as complete words by Tesseract. The checks therefore use
short, bounded title/label prefixes and require the 20-vis cost number and
localized Vis token on the same OCR line.
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


def contains_prefix_near(value: str, expected: str, maximum_distance: int) -> bool:
    return any(
        len(word) >= len(expected)
        and edit_distance(word[:len(expected)], expected) <= maximum_distance
        for word in tokens(value)
    )


def contains_label_prefix(value: str, expected: str) -> bool:
    return any(
        len(word) >= len(expected) and edit_distance(word, expected) <= 1
        for word in tokens(value)
    )


def has_cost_pair(value: str, number_tokens: set[str], cost_tokens: set[str]) -> bool:
    for line in value.splitlines():
        words = set(tokens(line))
        if words & number_tokens and words & cost_tokens:
            return True
    return False


def assert_workbench_ocr(
    language: str,
    title_variants: tuple[str, ...],
    panel_variants: tuple[str, ...],
) -> dict[str, bool]:
    if language == "en_us":
        title_first, title_second = "arcane", "workb"
        available_prefix = "availab"
        number_tokens, cost_tokens = {"20", "2h", "26", "2"}, {"vis", "wis", "ws"}
    elif language == "ru_ru":
        title_first, title_second = "магический", "верст"
        available_prefix = "дост"
        number_tokens, cost_tokens = {"20", "2"}, {"вис", "ес", "емс"}
    else:
        raise ValueError(f"unsupported Minecraft language: {language}")

    title_ok = any(
        contains_prefix_near(text, title_first, 1)
        and contains_prefix_near(text, title_second, 1)
        for text in title_variants
    )
    available_ok = any(
        contains_label_prefix(text, available_prefix)
        for text in panel_variants
    )
    cost_ok = any(
        has_cost_pair(text, number_tokens, cost_tokens)
        for text in panel_variants
    )
    return {
        "localized title": title_ok,
        "localized available label": available_ok,
        "20-vis recipe preview": cost_ok,
    }


def main(argv: list[str]) -> int:
    if len(argv) != 6:
        print(
            "usage: assert_client_workbench_gui_ocr.py "
            "LANG TITLE_PSM6 TITLE_PSM11 PANEL_PSM6 PANEL_PSM11",
            file=sys.stderr,
        )
        return 2
    language, title6, title11, panel6, panel11 = argv[1:]
    try:
        checks = assert_workbench_ocr(language, (title6, title11), (panel6, panel11))
    except ValueError as exc:
        print(f"WORKBENCH_OCR_ERROR: {exc}", file=sys.stderr)
        return 2
    for name, passed in checks.items():
        print(f"WORKBENCH_OCR_{name.upper().replace(' ', '_')}={'PASS' if passed else 'FAIL'}")
    if not all(checks.values()):
        print("WORKBENCH_OCR_TOKENS_TITLE_PSM6=" + repr(tokens(title6)))
        print("WORKBENCH_OCR_TOKENS_TITLE_PSM11=" + repr(tokens(title11)))
        print("WORKBENCH_OCR_TOKENS_PANEL_PSM6=" + repr(tokens(panel6)))
        print("WORKBENCH_OCR_TOKENS_PANEL_PSM11=" + repr(tokens(panel11)))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
