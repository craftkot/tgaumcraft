#!/usr/bin/env python3
"""Fail-closed OCR check for the complete localized available-Vis label.

Minecraft bitmap OCR may substitute one or two glyphs, but a clipped prefix is
not accepted: at least all but one character of the localized word must remain.
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


def contains_full_label(value: str, expected: str) -> bool:
    return any(
        len(word) >= len(expected) - 1 and edit_distance(word, expected) <= 2
        for word in tokens(value)
    )


def assert_workbench_ocr(
    language: str,
    available_variants: tuple[str, ...],
) -> dict[str, bool]:
    if language == "en_us":
        available_label = "available"
    elif language == "ru_ru":
        available_label = "доступно"
    else:
        raise ValueError(f"unsupported Minecraft language: {language}")
    return {
        "localized available label": any(
            contains_full_label(text, available_label)
            for text in available_variants
        )
    }


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(
            "usage: assert_client_workbench_gui_ocr.py LANG AVAILABLE_PSM7 AVAILABLE_PSM11",
            file=sys.stderr,
        )
        return 2
    language, available7, available11 = argv[1:]
    try:
        checks = assert_workbench_ocr(language, (available7, available11))
    except ValueError as exc:
        print(f"WORKBENCH_OCR_ERROR: {exc}", file=sys.stderr)
        return 2
    for name, passed in checks.items():
        print(f"WORKBENCH_OCR_{name.upper().replace(' ', '_')}={'PASS' if passed else 'FAIL'}")
    if not all(checks.values()):
        print("WORKBENCH_OCR_TOKENS_AVAILABLE_PSM7=" + repr(tokens(available7)))
        print("WORKBENCH_OCR_TOKENS_AVAILABLE_PSM11=" + repr(tokens(available11)))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
