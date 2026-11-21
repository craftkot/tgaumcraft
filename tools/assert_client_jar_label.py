#!/usr/bin/env python3
"""Fail-closed pixel and runtime-marker gate for the BETA26 jar aspect label."""
from pathlib import Path
import struct
import sys
import zlib

from assert_client_jar_liquid_smoke import _decode_png


def _count_pixels(width, height, rows, bpp, bounds, predicate):
    x_min, y_min, x_max, y_max = bounds
    count = 0
    for y in range(max(0, y_min), min(height, y_max)):
        row = rows[y]
        for x in range(max(0, x_min), min(width, x_max)):
            offset = x * bpp
            red, green, blue = row[offset:offset + 3]
            if bpp == 4 and row[offset + 3] < 32:
                continue
            if predicate(red, green, blue):
                count += 1
    return count


def inspect(image_path: Path) -> tuple[int, int]:
    if not image_path.is_file() or image_path.stat().st_size < 1024:
        raise SystemExit(f"JAR_LABEL_SCREENSHOT_MISSING_OR_SMALL={image_path}")
    width, height, rows, bpp = _decode_png(image_path)
    if (width, height) != (1280, 800):
        raise SystemExit(f"JAR_LABEL_SCREEN_SIZE_UNEXPECTED={width}x{height}")

    # The fixture camera is fixed at 1280x800; these bounds enclose the front jar face.
    warm_pixels = _count_pixels(
        width, height, rows, bpp, (450, 390, 575, 505),
        lambda red, green, blue: (
            red >= 145 and green >= 120 and red - green >= 6
            and green - blue >= 18 and red - blue >= 35
        ),
    )
    dark_pixels = _count_pixels(
        width, height, rows, bpp, (480, 420, 545, 475),
        lambda red, green, blue: max(red, green, blue) <= 100,
    )
    return warm_pixels, dark_pixels


def verify(log_path: Path, image_path: Path, expected_crooked: int = 0) -> None:
    log = log_path.read_text(encoding="utf-8", errors="replace")
    if expected_crooked not in (-2, -1, 0, 1):
        raise SystemExit(f"JAR_LABEL_EXPECTED_CROOKED_OUT_OF_RANGE={expected_crooked}")
    required = (
        f"TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=terra facing=2 crooked={expected_crooked}",
        f"TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=terra crooked={expected_crooked} texture=thaumcraft:textures/aspects/terra.png",
        "JAR_ASPECT_LABEL_FIXTURE_READY",
    )
    for marker in required:
        if marker not in log:
            raise SystemExit(f"JAR_LABEL_REQUIRED_MARKER_MISSING={marker}")

    warm_pixels, dark_pixels = inspect(image_path)
    print(f"BETA26 jar label paper pixels: {warm_pixels}; Terra glyph dark pixels: {dark_pixels}")
    if warm_pixels < 800:
        raise SystemExit(f"JAR_LABEL_PAPER_PIXEL_GATE_FAILED={warm_pixels}")
    if dark_pixels < 100:
        raise SystemExit(f"JAR_LABEL_ASPECT_GLYPH_PIXEL_GATE_FAILED={dark_pixels}")
    print("BETA26 jar aspect label real-client evidence: PASS")


def _png_chunk(kind: bytes, payload: bytes) -> bytes:
    crc = zlib.crc32(kind + payload) & 0xFFFFFFFF
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", crc)


def _write_synthetic_png(path: Path) -> None:
    width, height = 1280, 800
    compressor = zlib.compressobj()
    compressed = bytearray()
    for y in range(height):
        row = bytearray((35, 40, 45) * width)
        if 410 <= y < 490:
            for x in range(470, 555):
                offset = x * 3
                row[offset:offset + 3] = bytes((245, 225, 168))
        if 430 <= y < 466:
            for x in range(495, 531):
                offset = x * 3
                row[offset:offset + 3] = bytes((45, 42, 34))
        compressed.extend(compressor.compress(b"\x00" + row))
    compressed.extend(compressor.flush())
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _png_chunk(b"IHDR", ihdr)
        + _png_chunk(b"IDAT", bytes(compressed))
        + _png_chunk(b"IEND", b"")
    )


def self_test() -> None:
    from tempfile import TemporaryDirectory
    with TemporaryDirectory(prefix="thaumcraft-jar-label-") as tmp:
        root = Path(tmp)
        image_path = root / "synthetic.png"
        log_path = root / "client.log"
        _write_synthetic_png(image_path)
        log_path.write_text(
            "TC_SMOKE_JAR_LABEL_RENDERED=YES pos=250,150,251 aspect=terra facing=2 crooked=0 "
            "texture=thaumcraft:textures/models/label.png\n"
            "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=250,150,251 aspect=terra crooked=0 "
            "texture=thaumcraft:textures/aspects/terra.png\n"
            "JAR_ASPECT_LABEL_FIXTURE_READY\n",
            encoding="utf-8",
        )
        verify(log_path, image_path)


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
    elif len(sys.argv) == 3:
        verify(Path(sys.argv[1]), Path(sys.argv[2]), 0)
    elif len(sys.argv) == 4:
        verify(Path(sys.argv[1]), Path(sys.argv[2]), int(sys.argv[3]))
    else:
        raise SystemExit("usage: assert_client_jar_label.py <client.log> <100-jar-aspect-label.png> [expected_crooked] | --self-test")
