#!/usr/bin/env python3
"""Wait for authoritative integrated-server feedback for the current difficulty command."""
import argparse
from pathlib import Path
import sys
import time


def assert_peaceful_response(log: str | bytes, start_offset: int = 0) -> str:
    raw = log if isinstance(log, bytes) else log.encode("utf-8")
    if start_offset < 0 or start_offset > len(raw):
        raise AssertionError(
            f"client log offset {start_offset} is outside current size {len(raw)}"
        )
    current_command_log = raw[start_offset:].decode("utf-8", errors="replace")
    lines = current_command_log.splitlines()
    if any("save-all flush<--[HERE]" in line for line in lines):
        raise AssertionError(
            "integrated singleplayer rejected the dedicated-server-only save-all command"
        )
    server_set = any(
        "[Server thread/INFO]" in line
        and "[Dev: The difficulty has been set to Peaceful]" in line
        for line in lines
    )
    chat_already = any(
        "[Render thread/INFO]" in line
        and "[CHAT]" in line
        and (
            "The difficulty did not change; it is already set to peaceful" in line
            or (
                "Сложность не изменилась" in line
                and "уже выставлено значение" in line
                and "peaceful" in line
            )
        )
        for line in lines
    )
    if server_set:
        return "SERVER_SET"
    if chat_already:
        return "ALREADY_PEACEFUL"
    raise AssertionError(
        "no current-command integrated-server response confirming Peaceful difficulty"
    )


def wait_for_peaceful_response(path: Path, start_offset: int, wait_seconds: float) -> str:
    deadline = time.monotonic() + wait_seconds
    last_error: Exception | None = None
    while True:
        try:
            return assert_peaceful_response(path.read_bytes(), start_offset)
        except (OSError, AssertionError) as exc:
            last_error = exc
        if time.monotonic() >= deadline:
            raise AssertionError(
                f"timed out after {wait_seconds:g}s waiting for current-command server feedback: {last_error}"
            ) from last_error
        time.sleep(0.25)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log", nargs="?", default="client-smoke/client.log")
    parser.add_argument("--from-offset", type=int, default=0,
                        help="ignore all log bytes written before the command was sent")
    parser.add_argument("--wait-seconds", type=float, default=0.0,
                        help="bounded polling window for asynchronous server feedback (0..30)")
    args = parser.parse_args()
    if args.from_offset < 0:
        parser.error("--from-offset must be non-negative")
    if not 0.0 <= args.wait_seconds <= 30.0:
        parser.error("--wait-seconds must be between 0 and 30")
    path = Path(args.log)
    try:
        confirmation = wait_for_peaceful_response(
            path, args.from_offset, args.wait_seconds
        )
    except (OSError, AssertionError) as exc:
        print(f"WORKBENCH_DIFFICULTY_CHECK_ERROR: {exc}", file=sys.stderr)
        return 1
    print("REAL_MINECRAFT_WORLD_DIFFICULTY=PEACEFUL")
    print(f"REAL_MINECRAFT_WORLD_DIFFICULTY_CONFIRMATION={confirmation}")
    print(f"REAL_MINECRAFT_WORLD_DIFFICULTY_LOG_OFFSET={args.from_offset}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
