#!/usr/bin/env python3
"""Require authoritative integrated-server feedback for the Peaceful command."""
from pathlib import Path
import sys


def assert_peaceful_response(log: str) -> str:
    lines = log.splitlines()
    server_set = any(
        "[Server thread/INFO]" in line
        and "The difficulty has been set to Peaceful" in line
        for line in lines
    )
    chat_already = any(
        "[Render thread/INFO]" in line
        and "[CHAT]" in line
        and "The difficulty did not change; it is already set to peaceful" in line
        for line in lines
    )
    if any("save-all flush<--[HERE]" in line for line in lines):
        raise AssertionError(
            "integrated singleplayer rejected the dedicated-server-only save-all command"
        )
    if server_set:
        return "SERVER_SET"
    if chat_already:
        return "ALREADY_PEACEFUL"
    raise AssertionError(
        "Minecraft log has no authoritative response confirming Peaceful difficulty"
    )


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "client-smoke/client.log")
    try:
        log = path.read_text(encoding="utf-8", errors="replace")
        confirmation = assert_peaceful_response(log)
    except (OSError, AssertionError) as exc:
        print(f"WORKBENCH_DIFFICULTY_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_DIFFICULTY=PEACEFUL")
    print(f"REAL_MINECRAFT_WORLD_DIFFICULTY_CONFIRMATION={confirmation}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
