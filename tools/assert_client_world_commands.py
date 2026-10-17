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
GAME_TYPE_KEY = b"\x03\x00\x08GameType"
DIFFICULTY_KEY = b"\x01\x00\x0aDifficulty"

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

def assert_safe_spawn(blob: bytes) -> None:
    # GameType is NBT TAG_Int; Java Creative is 1.
    if blob.count(GAME_TYPE_KEY) != 1:
        raise AssertionError("Cannot uniquely verify world GameType")
    index = blob.index(GAME_TYPE_KEY) + len(GAME_TYPE_KEY)
    if len(blob) < index + 4 or int.from_bytes(blob[index:index + 4], "big", signed=True) != 1:
        raise AssertionError("REAL_MINECRAFT_WORLD_INITIAL_GAMEMODE=NOT_CREATIVE")
    # Difficulty is NBT TAG_Byte; Peaceful is 0.
    if blob.count(DIFFICULTY_KEY) != 1:
        raise AssertionError("Cannot uniquely verify world Difficulty")
    index = blob.index(DIFFICULTY_KEY) + len(DIFFICULTY_KEY)
    if index >= len(blob) or blob[index] != 0:
        raise AssertionError("REAL_MINECRAFT_WORLD_INITIAL_DIFFICULTY=NOT_PEACEFUL")


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "run/saves")
    worlds = list(root.glob("*/level.dat"))
    if len(worlds) != 1:
        print(f"WORLD_NBT_CHECK_ERROR: expected exactly one new world, found {len(worlds)} in {root}", file=sys.stderr)
        return 1
    try:
        content = gzip.decompress(worlds[0].read_bytes())
        assert_enabled(content)
        assert_safe_spawn(content)
    except (OSError, EOFError, AssertionError) as exc:
        print(f"WORLD_NBT_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON")
    print("REAL_MINECRAFT_WORLD_INITIAL_GAMEMODE=CREATIVE")
    print("REAL_MINECRAFT_WORLD_INITIAL_DIFFICULTY=PEACEFUL")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
