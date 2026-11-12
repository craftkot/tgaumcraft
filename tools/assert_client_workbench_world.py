#!/usr/bin/env python3
"""Fail closed on the two safety properties required by the workbench GUI smoke.

This lane creates a short Creative world and the common bootstrap explicitly
sets Peaceful before scripted interactions. Do not require Difficulty to have
already been flushed to level.dat at the instant the first login marker appears.
The raw Difficulty byte is retained as a diagnostic, not a gate.
"""
from pathlib import Path
import gzip
import sys

ALLOW_COMMANDS = b"\x01\x00\x0dallowCommands"
GAME_TYPE = b"\x03\x00\x08GameType"
DIFFICULTY = b"\x01\x00\x0aDifficulty"


def _unique_offset(blob: bytes, key: bytes, label: str) -> int:
    positions = []
    start = 0
    while (index := blob.find(key, start)) != -1:
        positions.append(index)
        start = index + len(key)
    if len(positions) != 1:
        raise AssertionError(f"NBT {label} occurrence count={len(positions)}")
    return positions[0] + len(key)


def assert_workbench_world(blob: bytes):
    allow_index = _unique_offset(blob, ALLOW_COMMANDS, "allowCommands")
    if allow_index >= len(blob) or blob[allow_index] != 1:
        raise AssertionError("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=OFF")

    game_index = _unique_offset(blob, GAME_TYPE, "GameType")
    if len(blob) < game_index + 4:
        raise AssertionError("Truncated NBT GameType")
    game_type = int.from_bytes(blob[game_index:game_index + 4], "big", signed=True)
    if game_type != 1:
        raise AssertionError(f"REAL_MINECRAFT_WORLD_INITIAL_GAMEMODE={game_type}, expected Creative")

    difficulty_positions = []
    start = 0
    while (index := blob.find(DIFFICULTY, start)) != -1:
        difficulty_positions.append(index)
        start = index + len(DIFFICULTY)
    if len(difficulty_positions) != 1:
        return None
    value_index = difficulty_positions[0] + len(DIFFICULTY)
    if value_index >= len(blob):
        return None
    value = blob[value_index]
    if value > 3:
        raise AssertionError(f"Invalid NBT Difficulty code {value}")
    return value


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "run/saves")
    worlds = list(root.glob("*/level.dat"))
    if len(worlds) != 1:
        print(f"WORKBENCH_WORLD_NBT_CHECK_ERROR: expected exactly one new world, found {len(worlds)} in {root}", file=sys.stderr)
        return 1
    try:
        difficulty = assert_workbench_world(gzip.decompress(worlds[0].read_bytes()))
    except (OSError, EOFError, AssertionError) as exc:
        print(f"WORKBENCH_WORLD_NBT_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON")
    print("REAL_MINECRAFT_WORLD_INITIAL_GAMEMODE=CREATIVE")
    print("REAL_MINECRAFT_WORLD_INITIAL_DIFFICULTY_CODE="
          + (str(difficulty) if difficulty is not None else "UNAVAILABLE"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
