#!/usr/bin/env python3
"""Verify the real Minecraft world used by the isolated Arcane Workbench smoke.

The generated copy requires authoritative level.dat evidence before any
scripted interaction: cheats enabled, Creative game mode and Peaceful
difficulty. The world-creation UI sets these options before the integrated
server starts, so no redundant difficulty command or chat-log heuristic is
needed. The same NBT gate can be used after shutdown.
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


def assert_workbench_world(blob: bytes, require_peaceful: bool = False):
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
        if require_peaceful:
            raise AssertionError(f"NBT Difficulty occurrence count={len(difficulty_positions)}")
        return None
    value_index = difficulty_positions[0] + len(DIFFICULTY)
    if value_index >= len(blob):
        if require_peaceful:
            raise AssertionError("Truncated NBT Difficulty")
        return None
    value = blob[value_index]
    if value > 3:
        raise AssertionError(f"Invalid NBT Difficulty code {value}")
    if require_peaceful and value != 0:
        raise AssertionError(f"REAL_MINECRAFT_WORLD_DIFFICULTY_NOT_PEACEFUL={value}")
    return value


def main() -> int:
    args = sys.argv[1:]
    require_peaceful = "--require-peaceful" in args
    positional = [arg for arg in args if arg != "--require-peaceful"]
    root = Path(positional[0] if positional else "run/saves")
    if len(positional) > 1:
        print("WORKBENCH_WORLD_NBT_CHECK_ERROR: unexpected arguments", file=sys.stderr)
        return 1
    worlds = list(root.glob("*/level.dat"))
    if len(worlds) != 1:
        print(f"WORKBENCH_WORLD_NBT_CHECK_ERROR: expected exactly one new world, found {len(worlds)} in {root}", file=sys.stderr)
        return 1
    try:
        difficulty = assert_workbench_world(
            gzip.decompress(worlds[0].read_bytes()),
            require_peaceful=require_peaceful,
        )
    except (OSError, EOFError, AssertionError) as exc:
        print(f"WORKBENCH_WORLD_NBT_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON")
    print("REAL_MINECRAFT_WORLD_INITIAL_GAMEMODE=CREATIVE")
    print("REAL_MINECRAFT_WORLD_INITIAL_DIFFICULTY_CODE="
          + (str(difficulty) if difficulty is not None else "UNAVAILABLE"))
    if require_peaceful:
        print("REAL_MINECRAFT_WORLD_DIFFICULTY=PEACEFUL")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
