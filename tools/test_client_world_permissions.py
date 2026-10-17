#!/usr/bin/env python3
"""Offline regression: reject a non-creative/non-peaceful Minecraft world."""
from pathlib import Path
import gzip
import subprocess
import sys
import tempfile

probe = Path(__file__).with_name("assert_client_world_commands.py")
commands = b"\x01\x00\x0dallowCommands"
game_type = b"\x03\x00\x08GameType"
difficulty = b"\x01\x00\x0aDifficulty"

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    world = root / "World"
    world.mkdir()

    def check(cheats: int, mode: int, difficulty_level: int, allowed: bool) -> None:
        blob = (
            b"\x0a\x00\x00\x0a\x00\x04Data"
            + commands + bytes([cheats])
            + game_type + mode.to_bytes(4, "big", signed=True)
            + difficulty + bytes([difficulty_level])
            + b"\x00\x00"
        )
        (world / "level.dat").write_bytes(gzip.compress(blob))
        result = subprocess.run(
            [sys.executable, str(probe), str(root)], capture_output=True, text=True)
        assert (result.returncode == 0) == allowed, (
            (cheats, mode, difficulty_level), result.stderr, result.stdout)

    for cheats in (0, 1):
        for mode in (0, 1, 2, 3):
            for setting in (0, 1, 2, 3):
                check(cheats, mode, setting,
                      cheats == 1 and mode == 1 and setting == 0)
    (world / "level.dat").write_bytes(gzip.compress(b"missing tags"))
    result = subprocess.run(
        [sys.executable, str(probe), str(root)], capture_output=True, text=True)
    assert result.returncode != 0, "broken level.dat falsely approved"
    (world / "level.dat").unlink()
    result = subprocess.run(
        [sys.executable, str(probe), str(root)], capture_output=True, text=True)
    assert result.returncode != 0, "missing world falsely approved"
print("REAL_WORLD_SAFE_SPAWN_NBT_GATE_PARITY_OK")
