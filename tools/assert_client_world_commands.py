#!/usr/bin/env python3
"""Fail closed if the *actual Minecraft-created* world's allowCommands is off.

Pure Python stdlib. The game writes Data/allowCommands as TAG_Byte in
run/saves/<world>/level.dat (gzip NBT). This is a real postcondition of the
Create World UI, unlike our self-asserted CLIENT_SENT_* markers.
"""
from pathlib import Path
import gzip
import sys

KEY = b"\x01\x00\x0dallowCommands"

def assert_enabled(blob: bytes) -> None:
    positions = []
    start = 0
    while (index := blob.find(KEY, start)) != -1:
        positions.append(index)
        start = index + len(KEY)
    if len(positions) != 1:
        raise AssertionError(
            f"NBT allowCommands TAG_Byte occurrence count={len(positions)}; "
            "cannot establish a trustworthy cheats-on game session"
        )
    idx = positions[0] + len(KEY)
    if idx >= len(blob) or blob[idx] != 1:
        raise AssertionError("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=OFF; scripted gameplay DID NOT RUN")

def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "run/saves")
    worlds = list(root.glob("*/level.dat"))
    if len(worlds) != 1:
        print(f"WORLD_NBT_CHECK_ERROR: expected exactly one new world, found {len(worlds)} in {root}", file=sys.stderr)
        return 1
    try:
        content = gzip.decompress(worlds[0].read_bytes())
        assert_enabled(content)
    except (OSError, EOFError, AssertionError) as exc:
        print(f"WORLD_NBT_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
