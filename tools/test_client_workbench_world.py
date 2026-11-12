#!/usr/bin/env python3
"""Regression tests for the workbench-only world safety gate."""
import importlib.util
from pathlib import Path

path = Path(__file__).with_name("assert_client_workbench_world.py")
spec = importlib.util.spec_from_file_location("assert_client_workbench_world", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

difficulty_path = Path(__file__).with_name("assert_client_workbench_difficulty.py")
difficulty_spec = importlib.util.spec_from_file_location("assert_client_workbench_difficulty", difficulty_path)
difficulty_module = importlib.util.module_from_spec(difficulty_spec)
difficulty_spec.loader.exec_module(difficulty_module)


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


def expect_rejected(blob: bytes, message: str, *, require_peaceful: bool = False) -> None:
    try:
        module.assert_workbench_world(blob, require_peaceful=require_peaceful)
    except AssertionError:
        expect(True, message)
    else:
        expect(False, message)


commands = byte_tag("allowCommands", 1)
creative = int_tag("GameType", 1)
normal = byte_tag("Difficulty", 2)
peaceful = byte_tag("Difficulty", 0)

expect(module.assert_workbench_world(commands + creative + normal) == 2,
       "initial Creative world must pass before its first save even if difficulty is still Normal")
expect(module.assert_workbench_world(commands + creative) is None,
       "initial world gate must not require Difficulty to have been flushed")
expect(module.assert_workbench_world(commands + creative + peaceful, require_peaceful=True) == 0,
       "final gate must accept the persisted Peaceful byte")
expect_rejected(commands + creative + normal,
                "final gate accepted a world that remained Normal", require_peaceful=True)
expect_rejected(commands + creative,
                "final gate accepted a missing Difficulty byte", require_peaceful=True)
expect_rejected(commands + creative + peaceful + normal,
                "final gate accepted ambiguous Difficulty tags", require_peaceful=True)

expect_rejected(byte_tag("allowCommands", 0) + creative,
                "commands-off world passed")
expect_rejected(commands + int_tag("GameType", 0),
                "non-Creative world passed")
expect_rejected(commands + commands + creative,
                "ambiguous allowCommands tag passed")
expect_rejected(commands + creative + byte_tag("Difficulty", 4),
                "invalid difficulty diagnostic passed")
expect_rejected(commands + b"\\x03\\x00\\x08GameType\\x00\\x00",
                "truncated GameType passed")

# Integrated-server difficulty command evidence (save-all is dedicated-server-only).
def expect_difficulty(log: str, accepted: bool, message: str) -> None:
    try:
        difficulty_module.assert_peaceful_response(log)
    except AssertionError:
        expect(not accepted, message)
    else:
        expect(accepted, message)


server_set_log = (
    "[09:13:35] [Server thread/INFO] [minecraft/MinecraftServer]: "
    "[Dev: The difficulty has been set to Peaceful]\n"
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty has been set to Peaceful\n"
)
already_peaceful_log = (
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty did not change; it is already set to peaceful\n"
)
client_only_log = (
    "[09:13:35] [Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] The difficulty has been set to Peaceful\n"
)
expect_difficulty(server_set_log, True, "server-confirmed Peaceful command was rejected")
expect_difficulty(already_peaceful_log, True, "already-Peaceful server response was rejected")
expect_difficulty(client_only_log, False, "client-only echo was trusted without server confirmation")
expect_difficulty("CLIENT_SENT_DIFFICULTY=YES\n", False, "typed-command marker was trusted")
expect_difficulty(
    server_set_log + "[Render thread/INFO] [minecraft/ChatComponent]: "
    "[System] [CHAT] save-all flush<--[HERE]\n",
    False,
    "rejected singleplayer save-all command was ignored",
)
expect_difficulty(
    "[09:13:35] [Server thread/INFO] [minecraft/MinecraftServer]: "
    "[Dev: The difficulty has been set to Hard]\n",
    False,
    "Hard difficulty was accepted as Peaceful",
)

generator = Path(__file__).with_name("client_workbench_gui_smoke.py").read_text(encoding="utf-8")
expect("send_chat '/save-all flush'" not in generator,
       "GUI generator still sends the unavailable singleplayer save-all command")
expect("assert_client_workbench_difficulty.py client-smoke/client.log" in generator,
       "GUI generator does not verify live integrated-server difficulty feedback")
expect(
    generator.index("send_chat '/difficulty peaceful'")
    < generator.index("assert_client_workbench_difficulty.py client-smoke/client.log"),
    "Peaceful feedback is checked before the difficulty command",
)
expect("assert_client_workbench_world.py run/saves --require-peaceful" not in generator,
       "GUI generator still reads the stale pre-shutdown difficulty byte")

print(f"Workbench world gate: OK ({checks} assertions)")
