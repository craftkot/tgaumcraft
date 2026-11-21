#!/usr/bin/env python3
"""Verify that the real Flux Rift shader sampled the intended camera orientation."""
from __future__ import annotations

import argparse
import math
import re
import sys
from dataclasses import dataclass
from pathlib import Path

MARKER = "TC_SMOKE_RIFT_SHADER_ACTIVE=YES"
EXPECTED_SAMPLER = "Sampler0"
EXPECTED_TEXTURE = "minecraft:textures/entity/end_portal.png"
_MARKER_PATTERN = re.compile(
    r"TC_SMOKE_RIFT_SHADER_ACTIVE=YES\s+time=(?P<time>-?\d+)"
    r"\s+yaw=(?P<yaw>\S+)\s+pitch=(?P<pitch>\S+)"
    r"\s+sampler=(?P<sampler>\S+)\s+texture=(?P<texture>\S+)"
)


@dataclass(frozen=True)
class ShaderFrame:
    time: int
    yaw: float
    pitch: float
    sampler: str
    texture: str


def parse_shader_frame(log_text: str) -> ShaderFrame:
    marker_lines = [line for line in log_text.splitlines() if MARKER in line]
    if len(marker_lines) != 1:
        raise ValueError(f"expected exactly one shader camera marker, found {len(marker_lines)}")

    match = _MARKER_PATTERN.search(marker_lines[0])
    if match is None:
        raise ValueError("shader camera marker is malformed")

    try:
        yaw = float(match.group("yaw"))
        pitch = float(match.group("pitch"))
    except ValueError as error:
        raise ValueError("shader camera marker is malformed: angles must be numeric") from error

    frame = ShaderFrame(
        time=int(match.group("time")),
        yaw=yaw,
        pitch=pitch,
        sampler=match.group("sampler"),
        texture=match.group("texture"),
    )
    if not math.isfinite(frame.yaw) or not math.isfinite(frame.pitch):
        raise ValueError("shader camera angles must be finite")
    return frame


def _angular_error(actual: float, expected: float) -> float:
    return abs(math.atan2(math.sin(actual - expected), math.cos(actual - expected)))


def verify_shader_camera(
    log_text: str,
    *,
    yaw_degrees: float,
    pitch_degrees: float,
    tolerance_degrees: float = 1.0,
) -> str:
    frame = parse_shader_frame(log_text)
    if frame.sampler != EXPECTED_SAMPLER:
        raise ValueError(f"expected sampler {EXPECTED_SAMPLER}, found {frame.sampler}")
    if frame.texture != EXPECTED_TEXTURE:
        raise ValueError(f"expected texture {EXPECTED_TEXTURE}, found {frame.texture}")

    expected_yaw = math.radians(yaw_degrees)
    # FluxRiftRenderer intentionally negates the player's X rotation.
    expected_pitch = -math.radians(pitch_degrees)
    tolerance = math.radians(tolerance_degrees)
    yaw_error = _angular_error(frame.yaw, expected_yaw)
    pitch_error = abs(frame.pitch - expected_pitch)
    if yaw_error > tolerance:
        raise ValueError(
            f"yaw mismatch: actual={frame.yaw:.7f} expected={expected_yaw:.7f} "
            f"error_degrees={math.degrees(yaw_error):.3f}"
        )
    if pitch_error > tolerance:
        raise ValueError(
            f"pitch mismatch: actual={frame.pitch:.7f} expected={expected_pitch:.7f} "
            f"error_degrees={math.degrees(pitch_error):.3f}"
        )

    return (
        f"RIFT_SHADER_CAMERA=PASS time={frame.time} "
        f"yaw={frame.yaw:.7f} pitch={frame.pitch:.7f} "
        f"expected_yaw={expected_yaw:.7f} expected_pitch={expected_pitch:.7f}"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("client_log", type=Path)
    parser.add_argument("--yaw-degrees", type=float, required=True)
    parser.add_argument("--pitch-degrees", type=float, required=True)
    parser.add_argument("--tolerance-degrees", type=float, default=1.0)
    args = parser.parse_args(argv)

    try:
        log_text = args.client_log.read_text(encoding="utf-8", errors="replace")
        print(verify_shader_camera(
            log_text,
            yaw_degrees=args.yaw_degrees,
            pitch_degrees=args.pitch_degrees,
            tolerance_degrees=args.tolerance_degrees,
        ))
    except (OSError, ValueError) as error:
        print(f"RIFT_SHADER_CAMERA=FAIL {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
