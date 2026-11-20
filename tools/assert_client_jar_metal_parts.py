#!/usr/bin/env python3
"""Require real-client pixels for the BETA26 jar lid brace and pipe extension."""
from __future__ import annotations

from pathlib import Path
import sys

from assert_client_jar_liquid_smoke import _decode_png, _encode_test_png

BRACE_MARKER = "TC_SMOKE_JAR_BRACE_RENDERED=YES pos=250,150,251"
EXTENSION_MARKER = (
    "TC_SMOKE_JAR_LID_EXTENSION_RENDERED=YES pos=250,150,251 above=250,151,251"
)


def _count_pixels(width, height, rows, bpp, predicate, *,
                  x_min=0.34, x_max=0.46, y_min=0.40, y_max=0.54):
    left = max(0, int(width * x_min))
    right = min(width, int(width * x_max))
    top = max(0, int(height * y_min))
    bottom = min(height, int(height * y_max))
    count = 0
    for y in range(top, bottom):
        row = rows[y]
        for x in range(left, right):
            offset = x * bpp
            red, green, blue = row[offset:offset + 3]
            if bpp == 4 and row[offset + 3] < 32:
                continue
            if predicate(red, green, blue):
                count += 1
    return count


def _is_b26_brace_pixel(red: int, green: int, blue: int) -> bool:
    # The pinned jarbrine.png lid UV island is gold/burnished metal.
    return (red >= 105 and 55 <= green <= 180 and 15 <= blue <= 100
            and red >= green * 1.10 and green >= blue * 1.15)


def _is_b26_extension_pixel(red: int, green: int, blue: int) -> bool:
    # The pinned jarbrine.png extension UV island is neutral dark metal.
    return (42 <= red <= 110 and 42 <= green <= 110 and 42 <= blue <= 110
            and max(red, green, blue) - min(red, green, blue) <= 18)


def _self_test() -> None:
    width, height = 64, 32
    background = (182, 208, 255)
    pixels = [[background for _ in range(width)] for _ in range(height)]
    for y in range(13, 17):
        for x in range(22, 29):
            pixels[y][x] = (160, 120, 50)
    png = _encode_test_png(width, height, pixels)
    with __import__("tempfile").TemporaryDirectory(prefix="thaumcraft-jar-metal-") as directory:
        path = Path(directory) / "synthetic.png"
        path.write_bytes(png)
        w, h, rows, bpp = _decode_png(path)
        gold = _count_pixels(w, h, rows, bpp, _is_b26_brace_pixel)
        if gold < 8:
            raise AssertionError(f"synthetic brace color cluster missing: {gold}")
        pixels = [[background for _ in range(width)] for _ in range(height)]
        for y in range(13, 17):
            for x in range(22, 29):
                pixels[y][x] = (72, 72, 72)
        path.write_bytes(_encode_test_png(width, height, pixels))
        w, h, rows, bpp = _decode_png(path)
        gray = _count_pixels(w, h, rows, bpp, _is_b26_extension_pixel)
        if gray < 8:
            raise AssertionError(f"synthetic extension color cluster missing: {gray}")
    print("BETA26 jar metal parts PNG parser self-test: PASS")


def _assert_real(log_path: Path, brace_path: Path, extension_path: Path) -> None:
    log = log_path.read_text(encoding="utf-8", errors="replace")
    for marker in (BRACE_MARKER, EXTENSION_MARKER):
        if marker not in log:
            raise SystemExit(f"missing real-client BETA26 jar metal part marker: {marker}")

    for path, predicate, name in (
        (brace_path, _is_b26_brace_pixel, "lid brace"),
        (extension_path, _is_b26_extension_pixel, "lid extension"),
    ):
        if not path.is_file() or path.stat().st_size < 1024:
            raise SystemExit(f"real-client {name} screenshot missing or implausibly small: {path}")
        width, height, rows, bpp = _decode_png(path)
        if width < 1024 or height < 768:
            raise SystemExit(f"real-client {name} screenshot unexpectedly small: {width}x{height}")
        count = _count_pixels(width, height, rows, bpp, predicate)
        if count < 8:
            raise SystemExit(f"real-client {name} source-texture pixels missing: count={count}")
        print(f"BETA26 jar {name} visible source-texture pixels: {count}")

    print("BETA26 jar lid brace and pipe extension real-client evidence: PASS")


def main() -> None:
    if sys.argv[1:] == ["--self-test"]:
        _self_test()
        return
    if len(sys.argv) != 4:
        raise SystemExit(
            "usage: assert_client_jar_metal_parts.py CLIENT_LOG BRACE_PNG EXTENSION_PNG"
        )
    _assert_real(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))


if __name__ == "__main__":
    main()
