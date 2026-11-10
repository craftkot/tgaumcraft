#!/usr/bin/env python3
"""Verify actual Minecraft-client tube model and transient particle evidence."""
from pathlib import Path
import argparse
import re
import sys


REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_TUBE_PLATFORM_READY=YES",
    "TC_SMOKE_TUBE_PLAYER_ON_PLATFORM=YES",
    "TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES",
    "TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES",
    "TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES",
    "TC_SMOKE_TUBE_VALVE_DESTROYED=YES",
    "TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES",
)
REQUIRED_STATUS_MARKERS = (
    "TUBE_NO_MISSING_MODEL_TEXTURES=PASS",
    "TUBE_REAL_CLIENT_TEXTURE_SMOKE=PASS",
    "TUBE_SERVER_COMMAND_CONFIRMED=Changed the block at 9, 149, 12",
    "TUBE_SERVER_COMMAND_CONFIRMED=Changed the block at 10, 149, 12",
    "TUBE_SERVER_COMMAND_CONFIRMED=Changed the block at 11, 149, 12",
    "TUBE_SERVER_COMMAND_CONFIRMED=TC_SMOKE_TUBE_VALVE_DESTROYED=YES",
    "TUBE_SERVER_COMMAND_CONFIRMED=TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES",
)
SCREENSHOTS = (
    "70-tubes-before.png",
    "71-tubes-connected.png",
    "72-valve-break-early-a.png",
    "73-valve-break-early-b.png",
    "74-valve-break-early-c.png",
    "75-valve-break-late.png",
    "76-restrict-break-early-a.png",
    "77-restrict-break-early-b.png",
    "78-restrict-break-early-c.png",
    "79-restrict-break-late.png",
)
BAD_LOG_PATTERNS = (
    re.compile(
        r"Missing textures in model.*(?:tube_core_valve|tube_side_restrict|base_metal)",
        re.IGNORECASE,
    ),
    re.compile(
        r"Unable to load (?:model|texture).*(?:tube_core_valve|tube_side_restrict|base_metal)",
        re.IGNORECASE,
    ),
    re.compile(
        r"(?:tube_core_valve|tube_side_restrict|base_metal).*(?:missing texture|Unable to load)",
        re.IGNORECASE,
    ),
)


def verify(log_text: str, status_text: str, screenshot_dir: Path) -> list[str]:
    errors: list[str] = []
    for marker in REQUIRED_LOG_MARKERS:
        if marker not in log_text:
            errors.append(f"missing real server blockstate marker: {marker}")

    for pattern in BAD_LOG_PATTERNS:
        if pattern.search(log_text):
            errors.append(f"client log contains a tube/base_metal model warning: {pattern.pattern}")

    for marker in REQUIRED_STATUS_MARKERS:
        if marker not in status_text:
            errors.append(f"missing client status marker: {marker}")

    metrics = (
        ("TUBE_BLOCK_RENDER_DIFF=", 300),
        ("TUBE_VALVE_PARTICLE_DIFF=", 20),
        ("TUBE_RESTRICT_PARTICLE_DIFF=", 20),
    )
    for prefix, minimum in metrics:
        match = re.search(rf"(?m)^{re.escape(prefix)}([0-9]+)$", status_text)
        if not match:
            errors.append(f"missing numeric evidence: {prefix}")
        elif int(match.group(1)) < minimum:
            errors.append(f"evidence below threshold: {prefix}{match.group(1)} < {minimum}")

    for name in SCREENSHOTS:
        path = screenshot_dir / name
        if not path.is_file() or path.stat().st_size < 128:
            errors.append(f"missing or empty real-client screenshot: {name}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("client_log", type=Path)
    parser.add_argument("status_file", type=Path)
    parser.add_argument("screenshots", type=Path)
    args = parser.parse_args()

    errors = verify(
        args.client_log.read_text(encoding="utf-8", errors="replace"),
        args.status_file.read_text(encoding="utf-8", errors="replace"),
        args.screenshots,
    )
    if errors:
        for error in errors:
            print(f"TUBE_CLIENT_EVIDENCE_ERROR: {error}", file=sys.stderr)
        return 1
    print("TUBE_REAL_CLIENT_EVIDENCE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
