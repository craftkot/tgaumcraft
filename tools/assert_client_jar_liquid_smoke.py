#!/usr/bin/env python3
"""Fail unless a real Minecraft client visibly rendered both BETA26 jar liquids."""
from __future__ import annotations

from pathlib import Path
import struct
import sys
import tempfile
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
COMPLETION_MARKER = "JAR_LIQUID_REAL_CLIENT_RENDERER_AND_SCREENSHOT=PASS"

REQUIRED_LOG_MARKERS = (
    "JAR_LIQUID_AER_FIXTURE_READY",
    "JAR_LIQUID_IGNIS_FIXTURE_READY",
    "JAR_LIQUID_EMPTY_FIXTURE_READY",
    "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=249,150,251 amount=125 aspect=aer rgb=FFFF7E topY=0.3750 texture=thaumcraft:block/animatedglow",
    "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=250,150,251 amount=250 aspect=ignis rgb=FF5A01 topY=0.6875 texture=thaumcraft:block/animatedglow",
    "TC_SMOKE_JAR_LIQUID_EMPTY_SKIPPED=YES pos=251,150,251 amount=0",
)


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def _decode_png(path: Path) -> tuple[int, int, list[bytes], int]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise SystemExit("real-client screenshot is not a PNG")

    offset = len(PNG_SIGNATURE)
    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    saw_iend = False

    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4:offset + 8]
        payload_start = offset + 8
        payload_end = payload_start + length
        crc_end = payload_end + 4
        if crc_end > len(data):
            raise SystemExit("truncated PNG chunk")
        payload = data[payload_start:payload_end]
        expected_crc = struct.unpack_from(">I", data, payload_end)[0]
        actual_crc = zlib.crc32(chunk_type + payload) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            raise SystemExit(f"PNG CRC mismatch in {chunk_type.decode('ascii', 'replace')}")

        if chunk_type == b"IHDR":
            if length != 13:
                raise SystemExit("invalid PNG IHDR length")
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if compression != 0 or filter_method != 0:
                raise SystemExit("unsupported PNG compression/filter method")
        elif chunk_type == b"IDAT":
            compressed.extend(payload)
        elif chunk_type == b"IEND":
            saw_iend = True
            offset = crc_end
            break
        offset = crc_end

    if not saw_iend or width is None or height is None:
        raise SystemExit("PNG is missing IHDR or IEND")
    if width <= 0 or height <= 0:
        raise SystemExit(f"invalid screenshot dimensions: {width}x{height}")
    if bit_depth != 8 or color_type not in (2, 6) or interlace != 0:
        raise SystemExit(
            f"unsupported screenshot PNG layout: bit_depth={bit_depth} "
            f"color_type={color_type} interlace={interlace}"
        )

    bytes_per_pixel = 3 if color_type == 2 else 4
    stride = width * bytes_per_pixel
    raw = zlib.decompress(bytes(compressed))
    expected_size = height * (stride + 1)
    if len(raw) != expected_size:
        raise SystemExit(f"PNG scanline size mismatch: expected={expected_size} actual={len(raw)}")

    rows: list[bytes] = []
    previous = bytearray(stride)
    cursor = 0
    for _y in range(height):
        filter_type = raw[cursor]
        cursor += 1
        encoded = raw[cursor:cursor + stride]
        cursor += stride
        decoded = bytearray(stride)

        for i, value in enumerate(encoded):
            left = decoded[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
            up = previous[i]
            upper_left = previous[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = up
            elif filter_type == 3:
                predictor = (left + up) // 2
            elif filter_type == 4:
                predictor = _paeth(left, up, upper_left)
            else:
                raise SystemExit(f"unsupported PNG row filter: {filter_type}")
            decoded[i] = (value + predictor) & 0xFF

        rows.append(bytes(decoded))
        previous = decoded

    return width, height, rows, bytes_per_pixel


def _is_aer_tint(red: int, green: int, blue: int) -> bool:
    # Aer is BETA26 #FFFF7E. Allow lighting, texture alpha and glass blending,
    # but require the original yellow-green channel relationship.
    high = min(red, green)
    return (
        high >= 72
        and high >= blue * 1.28
        and abs(red - green) <= max(28, max(red, green) * 0.42)
    )


def _is_ignis_tint(red: int, green: int, blue: int) -> bool:
    # Ignis is BETA26 #FF5A01. Keep red dominant and green above blue.
    return red >= 72 and green >= 18 and red >= green * 1.28 and green >= blue * 1.18


def _centroid(
    width: int,
    height: int,
    rows: list[bytes],
    bytes_per_pixel: int,
    x_min_fraction: float,
    x_max_fraction: float,
    predicate,
) -> tuple[int, float, float]:
    x_min = max(0, int(width * x_min_fraction))
    x_max = min(width, int(width * x_max_fraction))
    y_min = max(0, int(height * 0.16))
    y_max = min(height, int(height * 0.90))
    count = sum_x = sum_y = 0

    for y in range(y_min, y_max):
        row = rows[y]
        for x in range(x_min, x_max):
            offset = x * bytes_per_pixel
            red, green, blue = row[offset:offset + 3]
            if bytes_per_pixel == 4 and row[offset + 3] < 32:
                continue
            if predicate(red, green, blue):
                count += 1
                sum_x += x
                sum_y += y

    if count == 0:
        return 0, 0.0, 0.0
    return count, sum_x / count, sum_y / count


def _assert_visible_liquid_pixels(
    width: int,
    height: int,
    rows: list[bytes],
    bytes_per_pixel: int,
    *,
    min_pixels: int = 12,
    min_dimensions: tuple[int, int] = (1024, 768),
    min_separation: float | None = None,
    y_tolerance: float | None = None,
) -> tuple[tuple[int, float, float], tuple[int, float, float]]:
    min_width, min_height = min_dimensions
    if width < min_width or height < min_height:
        raise SystemExit(
            f"real-client screenshot is unexpectedly small: {width}x{height}, "
            f"required at least {min_width}x{min_height}"
        )

    # The fixture places Aer left of Ignis. These broad screen bands tolerate
    # the window decoration and FOV while preventing unrelated HUD pixels
    # outside the jar row from satisfying the check.
    aer = _centroid(width, height, rows, bytes_per_pixel, 0.10, 0.44, _is_aer_tint)
    ignis = _centroid(width, height, rows, bytes_per_pixel, 0.34, 0.72, _is_ignis_tint)

    if aer[0] < min_pixels:
        raise SystemExit(f"visible Aer liquid pixels missing: count={aer[0]}")
    if ignis[0] < min_pixels:
        raise SystemExit(f"visible Ignis liquid pixels missing: count={ignis[0]}")

    separation = min_separation if min_separation is not None else max(8.0, width * 0.08)
    if ignis[1] - aer[1] < separation:
        raise SystemExit(
            f"jar liquid color clusters are not spatially separated: "
            f"aer_x={aer[1]:.1f} ignis_x={ignis[1]:.1f} required={separation:.1f}"
        )

    allowed_y = y_tolerance if y_tolerance is not None else max(16.0, height * 0.20)
    if abs(aer[2] - ignis[2]) > allowed_y:
        raise SystemExit(
            f"jar liquid clusters are not on the same fixture row: "
            f"aer_y={aer[2]:.1f} ignis_y={ignis[2]:.1f} tolerance={allowed_y:.1f}"
        )

    return aer, ignis


def _png_chunk(kind: bytes, payload: bytes) -> bytes:
    crc = zlib.crc32(kind + payload) & 0xFFFFFFFF
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", crc)


def _encode_test_png(width: int, height: int, pixels: list[list[tuple[int, int, int]]]) -> bytes:
    bpp = 3
    previous = bytearray(width * bpp)
    scanlines = bytearray()
    for y in range(height):
        raw = bytes(channel for pixel in pixels[y] for channel in pixel)
        filter_type = y % 5
        encoded = bytearray(len(raw))
        for i, value in enumerate(raw):
            left = raw[i - bpp] if i >= bpp else 0
            up = previous[i]
            upper_left = previous[i - bpp] if i >= bpp else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = up
            elif filter_type == 3:
                predictor = (left + up) // 2
            else:
                predictor = _paeth(left, up, upper_left)
            encoded[i] = (value - predictor) & 0xFF
        scanlines.append(filter_type)
        scanlines.extend(encoded)
        previous = bytearray(raw)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (
        PNG_SIGNATURE
        + _png_chunk(b"IHDR", ihdr)
        + _png_chunk(b"IDAT", zlib.compress(bytes(scanlines)))
        + _png_chunk(b"IEND", b"")
    )


def _self_test() -> None:
    width, height = 64, 32
    pixels = [[(20, 20, 20) for _ in range(width)] for _ in range(height)]
    for y in range(8, 21):
        for x in range(12, 21):
            pixels[y][x] = (190, 190, 90)
    for y in range(9, 22):
        for x in range(30, 39):
            pixels[y][x] = (220, 75, 5)

    with tempfile.TemporaryDirectory(prefix="thaumcraft-jar-png-") as directory:
        path = Path(directory) / "synthetic.png"
        path.write_bytes(_encode_test_png(width, height, pixels))
        decoded_width, decoded_height, rows, bpp = _decode_png(path)
        if (decoded_width, decoded_height, bpp) != (width, height, 3):
            raise AssertionError("PNG header decode mismatch")
        for y in range(height):
            for x in range(width):
                offset = x * bpp
                actual = tuple(rows[y][offset:offset + 3])
                if actual != pixels[y][x]:
                    raise AssertionError(f"PNG filter round-trip failed at {x},{y}: {actual}")
        aer, ignis = _assert_visible_liquid_pixels(
            width, height, rows, bpp,
            min_pixels=8, min_dimensions=(1, 1), min_separation=8, y_tolerance=8,
        )
        if COMPLETION_MARKER in REQUIRED_LOG_MARKERS:
            raise AssertionError("completion marker is emitted after this assertion and must not be required here")
        if aer[0] < 100 or ignis[0] < 100:
            raise AssertionError("synthetic color clusters were not fully detected")

    print("Jar-liquid screenshot parser self-test: PASS (PNG filters 0..4, CRC, RGB decode, Aer/Ignis clusters)")


def _assert_client(log_path: Path, screenshot: Path) -> None:
    log = log_path.read_text(encoding="utf-8", errors="replace")
    for marker in REQUIRED_LOG_MARKERS:
        if marker not in log:
            raise SystemExit(f"missing real-client jar liquid marker: {marker}")
    if "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=251,150,251" in log:
        raise SystemExit("empty jar incorrectly emitted liquid geometry")
    if "Missing BETA26 essentia jar liquid atlas sprite" in log:
        raise SystemExit("animatedglow sprite was missing from the block atlas")
    if not screenshot.is_file() or screenshot.stat().st_size < 1024:
        raise SystemExit(f"real-client screenshot missing or implausibly small: {screenshot}")

    width, height, rows, bpp = _decode_png(screenshot)
    aer, ignis = _assert_visible_liquid_pixels(width, height, rows, bpp)
    print(
        "BETA26 jar liquid visible pixels: "
        f"Aer={aer[0]} centroid=({aer[1]:.1f},{aer[2]:.1f}); "
        f"Ignis={ignis[0]} centroid=({ignis[1]:.1f},{ignis[2]:.1f})"
    )
    print("BETA26 jar liquid real-client markers and pixel evidence: PASS")


def main() -> None:
    if sys.argv[1:] == ["--self-test"]:
        _self_test()
        return
    if len(sys.argv) != 3:
        raise SystemExit("usage: assert_client_jar_liquid_smoke.py CLIENT_LOG SCREENSHOT_PNG")
    _assert_client(Path(sys.argv[1]), Path(sys.argv[2]))


if __name__ == "__main__":
    main()
