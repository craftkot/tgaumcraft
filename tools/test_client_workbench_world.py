#!/usr/bin/env python3
"""Regression tests for the workbench-only world safety gate."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_world.py")
spec = importlib.util.spec_from_file_location("assert_client_workbench_world", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def byte_tag(name: str, value: int) -> bytes:
    encoded = name.encode("utf-8")
    return b"\x01" + len(encoded).to_bytes(2, "big") + encoded + bytes([value])


def int_tag(name: str, value: int) -> bytes:
    encoded = name.encode("utf-8")
    return b"\x03" + len(encoded).to_bytes(2, "big") + encoded + value.to_bytes(4, "big", signed=True)


checks = 0


def expect(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


valid = byte_tag("allowCommands", 1) + int_tag("GameType", 1) + byte_tag("Difficulty", 2)
expect(module.assert_workbench_world(valid) == 2,
       "Creative+commands world with a not-yet-peaceful persisted byte must pass and report it")

no_difficulty = byte_tag("allowCommands", 1) + int_tag("GameType", 1)
expect(module.assert_workbench_world(no_difficulty) is None,
       "difficulty is diagnostic-only for the workbench smoke")

for blob, message in [
    (byte_tag("allowCommands", 0) + int_tag("GameType", 1), "commands-off world passed"),
    (byte_tag("allowCommands", 1) + int_tag("GameType", 0), "non-Creative world passed"),
    (byte_tag("allowCommands", 1) + byte_tag("allowCommands", 1) + int_tag("GameType", 1),
     "ambiguous allowCommands tag passed"),
    (byte_tag("allowCommands", 1) + int_tag("GameType", 1) + byte_tag("Difficulty", 4),
     "invalid difficulty diagnostic passed"),
]:
    try:
        module.assert_workbench_world(blob)
    except AssertionError:
        expect(True, message)
    else:
        expect(False, message)

print(f"Workbench world gate: OK ({checks} assertions)")
