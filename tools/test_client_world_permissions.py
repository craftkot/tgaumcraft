#!/usr/bin/env python3
"""Offline regression: refuse fake green Minecraft tests with cheats disabled."""
from pathlib import Path
import gzip
import subprocess
import sys
import tempfile

probe = Path(__file__).with_name("assert_client_world_commands.py")
key = b"\x01\x00\x0dallowCommands"
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    world = root / "World"
    world.mkdir()
    for byte, expected in ((0, 1), (1, 0)):
        (world / "level.dat").write_bytes(gzip.compress(b"\x0a\x00\x00\x0a\x00\x04Data" + key + bytes([byte]) + b"\x00\x00"))
        result = subprocess.run([sys.executable, str(probe), str(root)], capture_output=True, text=True)
        assert result.returncode == expected, (byte, result.stderr, result.stdout)
    (world / "level.dat").write_bytes(gzip.compress(b"no allowCommands tag here"))
    result = subprocess.run([sys.executable, str(probe), str(root)], capture_output=True, text=True)
    assert result.returncode != 0, "missing game NBT falsely permitted cheats"
    (world / "level.dat").unlink()
    result = subprocess.run([sys.executable, str(probe), str(root)], capture_output=True, text=True)
    assert result.returncode != 0, "missing world falsely passed"
print("REAL_WORLD_CHEATS_NBT_GATE_PARITY_OK")
