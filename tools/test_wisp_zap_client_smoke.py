#!/usr/bin/env python3
"""Offline generator/verifier regressions for the real Wisp zap client smoke."""
from pathlib import Path
import os
import re
import shutil
import sys
import time
from unittest.mock import patch
import subprocess
import struct
import math
import hashlib
import tempfile
import zlib

import client_wisp_zap_smoke
import verify_wisp_zap_client

def _chunk(kind: bytes, data: bytes) -> bytes:
    payload = kind + data
    return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)


def write_png(path: Path, patch: tuple[int, int, int, int] | None = (506, 378, 518, 390)) -> None:
    width, height = 1280, 800
    background = bytes((96, 96, 96))
    aer = bytes((255, 255, 126))
    crosshair = bytes((213, 213, 190))
    roi_width, roi_height, roi_left, roi_top = verify_wisp_zap_client.AIM_ROI
    crosshair_x = roi_left + roi_width // 2 - 1
    crosshair_y = roi_top + roi_height // 2 - 1
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        row = bytearray(background * width)
        if patch is not None:
            x0, y0, x1, y1 = patch
            if y0 <= y < y1:
                for x in range(x0, x1):
                    offset = x * 3
                    row[offset:offset + 3] = aer
        # The real 1024x768 client capture draws this warm-tinted HUD crosshair
        # over the scene. Keep it in every fixture so it cannot satisfy Aer tests.
        if crosshair_y - 9 <= y <= crosshair_y + 8:
            for x in range(crosshair_x - 1, crosshair_x + 1):
                offset = x * 3
                row[offset:offset + 3] = crosshair
        if crosshair_y - 1 <= y <= crosshair_y:
            for x in range(crosshair_x - 9, crosshair_x + 9):
                offset = x * 3
                row[offset:offset + 3] = crosshair
        raw.extend(row)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        + _chunk(b"IEND", b"")
    )



def _bash_function_body(script: str, name: str) -> str:
    """Extract a generated Bash function without mistaking nested braces for its end."""
    signature = f"  {name}() {{\n"
    if script.count(signature) != 1:
        raise AssertionError(f"expected exactly one generated function {name!r}")
    remainder = script.split(signature, 1)[1]
    closing = "\n  }\n"
    if closing not in remainder:
        raise AssertionError(f"generated function {name!r} has no top-level closing line")
    return remainder.split(closing, 1)[0]


def _test_bash_function_body_extraction() -> None:
    nested = (
        "  probe() {\n"
        "    awk 'BEGIN {\n"
        "      print \"nested\"\n"
        "    }'\n"
        "    echo after-nested-block\n"
        "  }\n"
        "  next() { true; }\n"
    )
    body = _bash_function_body(nested, "probe")
    assert "print \"nested\"" in body
    assert body.endswith("echo after-nested-block")



def _test_streaming_click_dispatch() -> None:
    """The live-frame reader must click before returning to its caller, once only."""
    pattern = (
        r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=42 .*hitType=ENTITY "
        r"targetId=42 targetType=thaumcraft:wisp blockPos=none"
    )
    first = (
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=42 tick=10 gameTime=100 "
        "playerTick=90 hitType=ENTITY targetId=42 targetType=thaumcraft:wisp blockPos=none"
    )
    second = (
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=42 tick=11 gameTime=101 "
        "playerTick=91 hitType=ENTITY targetId=42 targetType=thaumcraft:wisp blockPos=none"
    )
    watcher = r'''set -euo pipefail
log_path="$1"
pattern="$2"
frame_line=""
watcher_status=0
after_lines="$3"
exec {frame_fd}< <(tail --sleep-interval=0.01 -n +"$((after_lines + 1))" -f "$log_path")
frame_tail_pid=$!
deadline=$((SECONDS + 2))
while :; do
  remaining=$((deadline - SECONDS))
  if [ "$remaining" -le 0 ]; then break; fi
  if ! IFS= read -r -t "$remaining" -u "$frame_fd" candidate_line; then break; fi
  if [[ "$candidate_line" =~ $pattern ]]; then
    frame_line="$candidate_line"
    if ! xdotool click 1; then
      watcher_status=1
    else
      printf 'CLICK_DISPATCHED\n' >> "$EVENT_LOG"
    fi
    break
  fi
done
kill "$frame_tail_pid" 2>/dev/null || true
wait "$frame_tail_pid" 2>/dev/null || true
exec {frame_fd}<&-
if [ "$watcher_status" -ne 0 ]; then exit 2; fi
if [ -z "$frame_line" ]; then exit 3; fi
printf 'PARENT_AFTER_WATCHER\n' >> "$EVENT_LOG"
printf '%s\n' "$frame_line"
'''

    def run_case(
        click_exit: int, after_lines: int = 1, append_second: bool = True
    ) -> tuple[int, str, list[str], list[str]]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            clicker = bin_dir / "xdotool"
            clicker.write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$*\" >> \"$CLICK_LOG\"\n"
                "exit \"$CLICK_EXIT\"\n",
                encoding="utf-8",
            )
            clicker.chmod(0o755)
            log_path = root / "client.log"
            # The first matching frame is already present before the watcher starts.
            log_path.write_text(first + "\n", encoding="utf-8")
            click_log = root / "clicks.log"
            event_log = root / "events.log"
            env = os.environ.copy()
            env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
            env["CLICK_LOG"] = str(click_log)
            env["EVENT_LOG"] = str(event_log)
            env["CLICK_EXIT"] = str(click_exit)
            process = subprocess.Popen(
                ["bash", "-c", watcher, "_", str(log_path), pattern, str(after_lines)],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=env,
            )
            if append_second:
                time.sleep(0.1)
                with log_path.open("a", encoding="utf-8") as log_file:
                    log_file.write(second + "\n")
            try:
                stdout, stderr = process.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()
                raise AssertionError("streaming Wisp watcher did not terminate")
            clicks = click_log.read_text(encoding="utf-8").splitlines() if click_log.exists() else []
            events = event_log.read_text(encoding="utf-8").splitlines() if event_log.exists() else []
            return process.returncode, stdout.strip(), clicks, events

    rc, output, clicks, events = run_case(0)
    assert rc == 0, rc
    assert output == second, output
    assert clicks == ["click 1"], clicks
    assert events == ["CLICK_DISPATCHED", "PARENT_AFTER_WATCHER"], events

    rc, output, clicks, events = run_case(1)
    assert rc == 2, rc
    assert output == "", output
    assert clicks == ["click 1"], clicks
    assert events == [], events

    # The first completed frame may already be attackable when the watcher starts.
    # Cursor 0 is inclusive and must click that existing line without waiting for another tick.
    rc, output, clicks, events = run_case(0, after_lines=0, append_second=False)
    assert rc == 0, rc
    assert output == first, output
    assert clicks == ["click 1"], clicks
    assert events == ["CLICK_DISPATCHED", "PARENT_AFTER_WATCHER"], events





def _test_attack_frame_dot_tolerance(generated: str) -> None:
    """The real click is immediate; entity-origin distance cannot overrule a vanilla hit result."""
    body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    function = "  wisp_wait_for_attack_frame() {\n" + body + "\n  }\n"

    def run_case(dot: str, *, click_exit: int = 0, wisp_x: str = "15.5", wisp_y: str = "151.3", wisp_z: str = "10.5") -> tuple[int, str, list[str], list[str], list[str]]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-attack-dot-") as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            clicker = bin_dir / "xdotool"
            clicker.write_text(
                "#!/bin/sh\n"
                "printf '%s\\n' \"$*\" >> \"$CLICK_LOG\"\n"
                "printf 'CLICK %s\\n' \"$*\" >> \"$EVENT_LOG\"\n"
                "exit \"$CLICK_EXIT\"\n",
                encoding="utf-8",
            )
            clicker.chmod(0o755)
            client_dir = root / "client-smoke"
            client_dir.mkdir()
            camera_line = (
                "TC_SMOKE_WISP_CAMERA=YES id=106 aimTarget=render_safe_quarter tick=1 "
                "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
                "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
                f"wispX={wisp_x} wispY={wisp_y} wispZ={wisp_z} "
                f"wispEyeX={wisp_x} wispEyeY={float(wisp_y) + 0.765} wispEyeZ={wisp_z} "
                f"wispHitboxCenterX={wisp_x} wispHitboxCenterY={float(wisp_y) + 0.45} wispHitboxCenterZ={wisp_z} "
                f"aimDot={dot} renderAimDot=0.9957938147422856 eyeAimDot=0.9479570469041974"
            )
            frame_line = (
                "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=106 tick=1 gameTime=520 playerTick=507 "
                "hitType=ENTITY targetId=106 targetType=thaumcraft:wisp blockPos=none"
            )
            (client_dir / "client.log").write_text(camera_line + "\n" + frame_line + "\n", encoding="utf-8")
            status_path = root / "client-smoke-status.txt"
            status_path.write_text("", encoding="utf-8")
            click_log = root / "clicks.log"
            event_log = root / "events.log"
            env = os.environ.copy()
            env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
            env["CLICK_LOG"] = str(click_log)
            env["EVENT_LOG"] = str(event_log)
            env["CLICK_EXIT"] = str(click_exit)
            harness = (
                "set -euo pipefail\n"
                "wisp_capture() { printf 'CAPTURE %s\\n' \"$1\" >> \"$EVENT_LOG\"; }\n"
                + function
                + "\nwisp_wait_for_attack_frame 0 106 PLATFORM 1\n"
            )
            result = subprocess.run(["bash", "-c", harness], cwd=root, text=True, capture_output=True, env=env, check=False)
            status = status_path.read_text(encoding="utf-8").splitlines()
            clicks = click_log.read_text(encoding="utf-8").splitlines() if click_log.exists() else []
            events = event_log.read_text(encoding="utf-8").splitlines() if event_log.exists() else []
            return result.returncode, result.stdout + result.stderr, status, clicks, events

    rc, output, status, clicks, events = run_case("1.000000011613918")
    assert rc == 0, (rc, output, status)
    assert any(line.startswith("WISP_ATTACKABLE_FRAME=YES id=106") for line in status), status
    assert clicks == ["click --delay 0 1"], clicks
    assert events == ["CLICK click --delay 0 1"], events

    rc, output, status, clicks, events = run_case("1.0002")
    assert rc != 0, (rc, output, status)
    assert any("WISP_ATTACKABLE_FRAME_CAMERA_REJECTED" in line for line in status), status
    assert clicks == ["click --delay 0 1"], clicks
    assert events == ["CLICK click --delay 0 1"], events

    rc, output, status, clicks, events = run_case("1.000000011613918", click_exit=1)
    assert rc != 0, (rc, output, status)
    assert any("WISP_ATTACK_CLICK_DISPATCH_FAILED=YES id=106" in line for line in status), status
    assert not any(line.startswith("WISP_ATTACKABLE_FRAME=YES") for line in status), status
    assert events == ["CLICK click --delay 0 1"], events

    rc, output, status, clicks, events = run_case("1.000000011613918", wisp_x="20.0")
    assert rc != 0, (rc, output, status)
    assert any("WISP_ATTACKABLE_FRAME_CAMERA_REJECTED" in line for line in status), status
    assert events == ["CLICK click --delay 0 1"], events


def _test_attack_input_frame_correlation(generated: str) -> None:
    """Correlate InputEvent with the unique same-game-time frame no more than one player tick behind."""
    body = _bash_function_body(generated, "wisp_capture_attack_input_frame")
    function = "  wisp_capture_attack_input_frame() {\n" + body + "\n  }\n"
    attack_input = (
        "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=106 "
        "targetType=thaumcraft:wisp hitType=ENTITY distance=3.78 hitDistance=2.9 "
        "gameTime=553 blockPos=none tick=518 monotonicNs=2000000000"
    )

    def camera_line(tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_CAMERA=YES id=106 aimTarget=render_safe_quarter tick={tick} "
            "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
            "wispX=15.5 wispY=151.3 wispZ=10.5 "
            "wispEyeX=15.5 wispEyeY=152.065 wispEyeZ=10.5 "
            "wispHitboxCenterX=15.5 wispHitboxCenterY=151.75 wispHitboxCenterZ=10.5 "
            "aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978"
        )

    def frame_line(tick: int, game_time: int, player_tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=106 tick={tick} "
            f"gameTime={game_time} playerTick={player_tick} "
            "hitType=ENTITY targetId=106 targetType=thaumcraft:wisp blockPos=none"
        )

    def run_case(lines: list[str], *, attack_line: str = attack_input, click_cursor: int = 0) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-input-frame-") as directory:
            root = Path(directory)
            client_dir = root / "client-smoke"
            client_dir.mkdir()
            (client_dir / "client.log").write_text("\n".join(lines[:-1] + [attack_line]) + "\n", encoding="utf-8")
            status_path = root / "client-smoke-status.txt"
            status_path.write_text("", encoding="utf-8")
            env = os.environ.copy()
            env["ATTACK_INPUT"] = attack_line
            env["WISP_ATTACK_INPUT_LOG_LINES"] = str(click_cursor)
            harness = (
                "set -euo pipefail\n"
                + function
                + '\nwisp_capture_attack_input_frame "$ATTACK_INPUT" 106 PLATFORM\n'
                + 'echo "CAPTURED_TICK=$WISP_ATTACK_CAMERA_TICK GAME_TIME=$WISP_ATTACK_CAMERA_GAME_TIME '
                + 'PLAYER_TICK=$WISP_ATTACK_CAMERA_PLAYER_TICK DISTANCE=$WISP_ATTACK_CAMERA_DISTANCE '
                + 'HIT_DISTANCE=$WISP_ATTACK_INPUT_HIT_DISTANCE MONOTONIC_NS=$WISP_ATTACK_INPUT_MONOTONIC_NS"\n'
            )
            result = subprocess.run(["bash", "-c", harness], cwd=root, text=True, capture_output=True, env=env, check=False)
            return result.returncode, result.stdout + result.stderr, status_path.read_text(encoding="utf-8")

    base_lines = [
        camera_line(0), frame_line(0, 546, 511),
        camera_line(5), frame_line(5, 551, 516),
        camera_line(7), frame_line(7, 553, 518), attack_input,
    ]
    rc, output, status = run_case(base_lines)
    assert rc == 0, (rc, output, status)
    captured = re.search(
        r"(?m)^CAPTURED_TICK=7 GAME_TIME=553 PLAYER_TICK=518 DISTANCE=([0-9]+[.][0-9]+) "
        r"HIT_DISTANCE=2[.]9 MONOTONIC_NS=2000000000$",
        output,
    )
    assert captured is not None, output
    expected_distance = math.sqrt(3.0 ** 2 + 2.3 ** 2)
    assert math.isclose(float(captured.group(1)), expected_distance, abs_tol=0.00005), output
    assert "WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=2000000000" in status, status
    assert "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=2.9" in status, status

    # The render-stage sample may be one client player tick behind InputEvent
    # while both still observe the same level gameTime. It remains valid only
    # when it is after the click cursor and before the InputEvent line.
    one_tick_behind = attack_input.replace(" tick=518 monotonicNs=", " tick=519 monotonicNs=")
    rc, output, status = run_case(base_lines, attack_line=one_tick_behind, click_cursor=5)
    assert rc == 0, (rc, output, status)
    assert "CAPTURED_TICK=7 GAME_TIME=553 PLAYER_TICK=518" in output, output

    two_ticks_behind = attack_input.replace(" tick=518 monotonicNs=", " tick=520 monotonicNs=")
    rc, output, status = run_case(base_lines, attack_line=two_ticks_behind, click_cursor=5)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=0" in status, (output, status)

    future_frame = attack_input.replace(" tick=518 monotonicNs=", " tick=517 monotonicNs=")
    rc, output, status = run_case(base_lines, attack_line=future_frame, click_cursor=5)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=0" in status, (output, status)

    frame_before_click_cursor = 6
    rc, output, status = run_case(base_lines, click_cursor=frame_before_click_cursor)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=0" in status, (output, status)

    missing_timestamp = attack_input.replace(" monotonicNs=2000000000", "")
    rc, output, status = run_case(base_lines, attack_line=missing_timestamp)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_TELEMETRY_INVALID=YES" in status, (output, status)

    out_of_reach = attack_input.replace("hitDistance=2.9", "hitDistance=3.02")
    rc, output, status = run_case(base_lines, attack_line=out_of_reach)
    rejected = re.search(
        r"(?m)^WISP_ATTACK_INPUT_FRAME_CAMERA_REJECTED=id=106 tick=7 "
        r"position=PLATFORM distance=([0-9]+[.][0-9]+) "
        r"aimDot=(-?[0-9]+[.][0-9]+)$",
        status,
    )
    assert rc != 0 and rejected is not None, (output, status)
    assert math.isclose(float(rejected.group(1)), expected_distance, abs_tol=0.00005), status
    assert math.isclose(float(rejected.group(2)), 0.999, abs_tol=1e-12), status
    assert "WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=" not in status, status
    assert "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=" not in status, status

    duplicate_lines = base_lines[:-1] + [frame_line(7, 553, 518), attack_input]
    rc, output, status = run_case(duplicate_lines)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=2" in status, (output, status)

    stale_only_lines = base_lines[:4] + [attack_input]
    rc, output, status = run_case(stale_only_lines)
    assert rc != 0 and "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=0" in status, (output, status)


def _test_attack_cursor_starts_after_live_reposition(generated: str) -> None:
    """The attack watcher must start after the server-confirmed Survival transition, not stale pre-Survival frames."""
    attack_body = _bash_function_body(generated, "wisp_attack_live_wisp")
    assert 'WISP_ATTACK_INPUT_LOG_LINES="$click_log_lines"' in attack_body
    expected = 'frame_log_lines="$(wc -l < client-smoke/client.log)"'
    assert expected in attack_body, "attack watcher must snapshot the log after Survival is server-confirmed"
    assert 'frame_log_lines="$WISP_ATTACK_REPOSITION_LINE_NUMBER"' not in attack_body
    assert 'frame_log_lines="$WISP_RENDER_FIRST_FRAME_LINE_NUMBER"' not in attack_body

    reposition_body = _bash_function_body(generated, "wisp_move_player_to_live_wisp")
    assert "run tp @p ~1.0 ~-1.0 ~ facing entity @s eyes" in reposition_body
    assert "run tp @p ~1.0 149 ~ facing entity @s eyes" not in reposition_body
    assert "wisp_fast_command" in reposition_body and "0.1 1" in reposition_body
    assert 'tail -n +"$((after_lines + 1))" client-smoke/client.log' in reposition_body
    assert 'WISP_ATTACK_REPOSITION_LINE_NUMBER="$((after_lines + relative_line))"' in reposition_body
    assert 'WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION' in reposition_body

    reposition_function = "  wisp_move_player_to_live_wisp() {\n" + reposition_body + "\n  }\n"
    fresh_marker_body = _bash_function_body(generated, "wisp_wait_for_fresh_log_marker")
    fresh_marker_function = "  wisp_wait_for_fresh_log_marker() {\n" + fresh_marker_body + "\n  }\n"
    with tempfile.TemporaryDirectory(prefix="tc-wisp-live-reposition-cursor-") as directory:
        root = Path(directory)
        client_dir = root / "client-smoke"
        client_dir.mkdir()
        (client_dir / "client.log").write_text("PREEXISTING_FRAME=YES\n", encoding="utf-8")
        (root / "client-smoke-status.txt").write_text("", encoding="utf-8")
        commands_path = root / "commands.log"
        harness = (
            "set -euo pipefail\n"
            "wisp_fast_command() {\n"
            "  printf '%s\\n' \"$1\" >> commands.log\n"
            "  case \"$1\" in\n"
            "    *\"run tp @p\"*) printf 'Teleported Dev to 13.886421, 153.206460, 10.687089\\n' >> client-smoke/client.log ;;\n"
            "    /fill\\ *) printf 'Successfully filled 25 block(s)\\n' >> client-smoke/client.log ;;\n"
            "    /execute\\ if\\ block\\ *) printf 'TC_SMOKE_WISP_MELEE_SUPPORT=YES\\n' >> client-smoke/client.log ;;\n"
            "  esac\n"
            "}\n"
            + fresh_marker_function
            + reposition_function
            + "\nwisp_move_player_to_live_wisp 1\n"
            + 'printf "REPOSITION_LINE=%s\\n" "$WISP_ATTACK_REPOSITION_LINE_NUMBER"\n'
        )
        result = subprocess.run(
            ["bash", "-c", harness],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
            timeout=5,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        assert "REPOSITION_LINE=2" in result.stdout, result.stdout
        commands = commands_path.read_text(encoding="utf-8").splitlines()
        assert commands == [
            "/execute as @e[type=thaumcraft:wisp,name=tc_wisp_zap_smoke,limit=1] at @s run tp @p ~1.0 ~-1.0 ~ facing entity @s eyes",
            "/fill 11 152 8 15 152 12 minecraft:stone",
            "/execute if block 13 152 10 minecraft:stone run say TC_SMOKE_WISP_MELEE_SUPPORT=YES",
        ], commands
        status = (root / "client-smoke-status.txt").read_text(encoding="utf-8")
        assert "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 13.886421, 153.206460, 10.687089" in status, status
        assert "WISP_CAMERA_SUPPORT_PLATFORM=YES x=13 y=152 z=10" in status, status
        assert "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=TC_SMOKE_WISP_MELEE_SUPPORT=YES" in status, status

    # A successful fill response is insufficient if the expected support block
    # cannot be confirmed at the exact player footing; the smoke must fail closed.
    with tempfile.TemporaryDirectory(prefix="tc-wisp-live-reposition-no-support-") as directory:
        root = Path(directory)
        client_dir = root / "client-smoke"
        client_dir.mkdir()
        (client_dir / "client.log").write_text("PREEXISTING_FRAME=YES\n", encoding="utf-8")
        (root / "client-smoke-status.txt").write_text("", encoding="utf-8")
        harness = (
            "set -euo pipefail\n"
            "wisp_fast_command() {\n"
            "  case \"$1\" in\n"
            "    *\"run tp @p\"*) printf 'Teleported Dev to 13.886421, 153.206460, 10.687089\\n' >> client-smoke/client.log ;;\n"
            "    /fill\\ *) printf 'Successfully filled 25 block(s)\\n' >> client-smoke/client.log ;;\n"
            "  esac\n"
            "}\n"
            + fresh_marker_function
            + reposition_function
            + "\nwisp_move_player_to_live_wisp 1\n"
        )
        result = subprocess.run(
            ["bash", "-c", harness],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
            timeout=5,
        )
        status = (root / "client-smoke-status.txt").read_text(encoding="utf-8")
        assert result.returncode != 0, result.stdout + result.stderr
        assert "WISP_FRESH_MARKER_TIMEOUT=TC_SMOKE_WISP_MELEE_SUPPORT=YES" in status, status

    fresh_marker_body = _bash_function_body(generated, "wisp_wait_for_fresh_log_marker")
    fresh_marker_function = "  wisp_wait_for_fresh_log_marker() {\n" + fresh_marker_body + "\n  }\n"
    with tempfile.TemporaryDirectory(prefix="tc-wisp-fresh-survival-marker-") as directory:
        root = Path(directory)
        client_dir = root / "client-smoke"
        client_dir.mkdir()
        (client_dir / "client.log").write_text(
            "BOOT=YES\nSet own game mode to Survival Mode\nSet own game mode to Survival Mode\n",
            encoding="utf-8",
        )
        status_path = root / "client-smoke-status.txt"
        status_path.write_text("", encoding="utf-8")
        harness = (
            "set -euo pipefail\n"
            + fresh_marker_function
            + "\nwisp_wait_for_fresh_log_marker 2 'Set own game mode to Survival Mode'\n"
        )
        result = subprocess.run(
            ["bash", "-c", harness],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
            timeout=3,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        status = status_path.read_text(encoding="utf-8")
        assert "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=3" in status, status

    wait_body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    function = "  wisp_wait_for_attack_frame() {\n" + wait_body + "\n  }\n"

    def camera_line(tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_CAMERA=YES id=106 aimTarget=render_safe_quarter tick={tick} "
            "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
            "wispX=12.5 wispY=149.3 wispZ=11.5 "
            "wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 "
            "wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 "
            "aimDot=1.000000011613918 renderAimDot=0.9957938147422856 eyeAimDot=0.9479570469041974"
        )

    def frame_line(tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=106 tick={tick} "
            f"gameTime={519 + tick} playerTick={506 + tick} "
            "hitType=ENTITY targetId=106 targetType=thaumcraft:wisp blockPos=none"
        )

    with tempfile.TemporaryDirectory(prefix="tc-wisp-fresh-attack-frame-") as directory:
        root = Path(directory)
        bin_dir = root / "bin"
        bin_dir.mkdir()
        clicker = bin_dir / "xdotool"
        clicker.write_text(
            "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$CLICK_LOG\"\nprintf 'CLICK %s\\n' \"$*\" >> \"$EVENT_LOG\"\nexit 0\n",
            encoding="utf-8",
        )
        clicker.chmod(0o755)
        client_dir = root / "client-smoke"
        client_dir.mkdir()
        screenshots_dir = client_dir / "screenshots"
        screenshots_dir.mkdir()
        write_png(screenshots_dir / "78-wisp-aim-check.png")
        # The first completed frame predates the server-confirmed live reposition.
        # Only a same-ID EntityHitResult logged after that teleport may dispatch the click.
        reposition_line = "Teleported Dev to 12.500000, 149.000000, 10.500000"
        (client_dir / "client.log").write_text(
            "\n".join((camera_line(1), frame_line(1), reposition_line, camera_line(2), frame_line(2))) + "\n",
            encoding="utf-8",
        )
        status_path = root / "client-smoke-status.txt"
        status_path.write_text("", encoding="utf-8")
        click_log = root / "clicks.log"
        event_log = root / "events.log"
        env = os.environ.copy()
        env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
        env["CLICK_LOG"] = str(click_log)
        env["EVENT_LOG"] = str(event_log)
        harness = (
            "set -euo pipefail\n"
            "wisp_capture() { printf 'CAPTURE %s\\n' \"$1\" >> \"$EVENT_LOG\"; return 0; }\n"
            + function
            + "\nwisp_wait_for_attack_frame 3 106 LIVE_WISP 1\n"
        )
        result = subprocess.run(
            ["bash", "-c", harness],
            cwd=root,
            text=True,
            capture_output=True,
            env=env,
            check=False,
            timeout=3,
        )
        assert result.returncode == 0, result.stdout + result.stderr
        status = status_path.read_text(encoding="utf-8")
        assert "WISP_ATTACKABLE_FRAME=YES id=106 tick=2" in status, status
        assert "WISP_ATTACKABLE_FRAME=YES id=106 tick=1" not in status, status
        clicks = click_log.read_text(encoding="utf-8").splitlines() if click_log.exists() else []
        assert clicks == ["click --delay 0 1"], clicks
        events = event_log.read_text(encoding="utf-8").splitlines() if event_log.exists() else []
        assert events == ["CLICK click --delay 0 1"], events


def _test_first_frame_telemetry_uses_selected_frame(generated: str) -> None:
    """Ignore the pre-render tracking sample and select the exact completed-frame camera tick."""
    body = _bash_function_body(generated, "wisp_verify_aim_telemetry")
    function = "  wisp_verify_aim_telemetry() {\n" + body + "\n  }\n"

    def camera_line(
        tick: int,
        aim_dot: str = "0.999",
        player: tuple[float, float, float] = (12.5, 149.0, 10.5),
        wisp: tuple[float, float, float] = (12.5, 149.3, 11.5),
    ) -> str:
        px, py, pz = player
        wx, wy, wz = wisp
        return (
            f"TC_SMOKE_WISP_CAMERA=YES id=157 aimTarget=render_safe_quarter tick={tick} "
            f"playerX={px} playerY={py} playerZ={pz} playerYaw=0.0 playerPitch=47.59636 "
            f"playerEyeX={px} playerEyeY={py + 1.62} playerEyeZ={pz} "
            f"wispX={wx} wispY={wy} wispZ={wz} "
            f"wispEyeX={wx} wispEyeY={wy + 0.765} wispEyeZ={wz} "
            f"wispHitboxCenterX={wx} wispHitboxCenterY={wy + 0.45} wispHitboxCenterZ={wz} "
            f"aimDot={aim_dot} renderAimDot=0.978 eyeAimDot=0.978"
        )

    def run_case(
        selected_samples: int,
        capture_aim_dot: str = "0.999",
        first_frame_aim_dot: str = "0.999",
        capture_player: tuple[float, float, float] = (12.5, 149.0, 10.5),
        capture_wisp: tuple[float, float, float] = (12.5, 149.3, 11.5),
        live_reposition: tuple[float, float, float] | None = None,
        capture_hit_distance: str = "2.9",
    ) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-first-frame-telemetry-") as directory:
            root = Path(directory)
            client_dir = root / "client-smoke"
            client_dir.mkdir()
            log_lines = [
                camera_line(0, first_frame_aim_dot),
                "TC_SMOKE_WISP_RENDERED=YES id=157 aspect=aer tick=3",
            ]
            if selected_samples >= 1:
                log_lines.append(camera_line(3, first_frame_aim_dot))
            if selected_samples >= 2:
                log_lines.append(camera_line(3, first_frame_aim_dot))
            log_lines.append(
                "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=157 tick=3 "
                "gameTime=514 playerTick=481 hitType=BLOCK targetId=-1 targetType=none blockPos=12,148,12"
            )
            (client_dir / "client.log").write_text("\n".join(log_lines) + "\n", encoding="utf-8")
            status_lines = [
                "WISP_RENDER_FIRST_FRAME_ID=157",
                "WISP_RENDER_FIRST_FRAME_TICK=3",
                f"WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE={camera_line(3)}",
                "WISP_RENDER_CAPTURE_ID=157",
                "WISP_RENDER_CAPTURE_TICK=124",
                f"WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE={capture_hit_distance}",
            ]
            if live_reposition is not None:
                rx, ry, rz = live_reposition
                teleport = f"Teleported Dev to {rx:.6f}, {ry:.6f}, {rz:.6f}"
                status_lines.extend((
                    f"WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION {teleport}",
                    f"WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING {teleport}",
                ))
            status_lines.append(camera_line(124, capture_aim_dot, capture_player, capture_wisp))
            (root / "client-smoke-status.txt").write_text("\n".join(status_lines) + "\n", encoding="utf-8")
            result = subprocess.run(
                ["bash", "-c", "set -euo pipefail\n" + function + "\nwisp_verify_aim_telemetry 0\n"],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
                timeout=3,
            )
            status = (root / "client-smoke-status.txt").read_text(encoding="utf-8")
            return result.returncode, result.stdout + result.stderr, status

    rc, output, status = run_case(1)
    assert rc == 0, (rc, output, status)
    assert "WISP_CAMERA_TELEMETRY=PASS id=157 tick=3 " in status, status
    assert "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=157 tick=124 " in status, status

    live_player = (14.361148, 149.0, 11.954202)
    live_wisp = (13.531006, 152.136149, 11.861735)
    rc, output, status = run_case(
        1,
        capture_aim_dot="0.9932838195434261",
        capture_player=live_player,
        capture_wisp=live_wisp,
        live_reposition=live_player,
    )
    assert rc == 0, (rc, output, status)
    assert "WISP_CAMERA_CAPTURE_POSITION_MODE=LIVE_WISP" in status, status
    assert "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=157 tick=124 " in status, status

    drifted_player = (8.373697, 149.0, 9.370661)
    drifted_wisp = (7.451660, 150.489421, 12.449951)
    rc, output, status = run_case(
        1,
        capture_aim_dot="0.9978064321315476",
        capture_player=drifted_player,
        capture_wisp=drifted_wisp,
        live_reposition=(9.272854, 152.138287, 9.370661),
        capture_hit_distance="2.745773807242083",
    )
    assert rc == 0, (rc, output, status)
    assert "WISP_CAMERA_CAPTURE_POSITION_MODE=LIVE_WISP" in status, status

    rc, output, status = run_case(
        1,
        capture_aim_dot="0.9932838195434261",
        capture_player=live_player,
        capture_wisp=live_wisp,
        live_reposition=live_player,
        capture_hit_distance="3.02",
    )
    assert rc != 0 and "WISP_CAMERA_CAPTURE_TELEMETRY_FAILED mode=LIVE_WISP" in status, (output, status)

    rc, output, status = run_case(1, capture_aim_dot="0.989224")
    assert rc == 0, (rc, output, status)
    assert "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=157 tick=124 " in status, status

    rc, output, status = run_case(1, capture_aim_dot="1.001")
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_CAPTURE_TELEMETRY_FAILED" in status, status

    rc, output, status = run_case(1, first_frame_aim_dot="0.989")
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_TELEMETRY_FAILED" in status, status

    rc, output, status = run_case(0)
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_FIRST_FRAME_SAMPLE_COUNT=0 expected=1 id=157 tick=3" in status, status

    rc, output, status = run_case(2)
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_FIRST_FRAME_SAMPLE_COUNT=2 expected=1 id=157 tick=3" in status, status


def _test_attack_aim_capture_is_taken_at_input_and_validated_after_hit(generated: str) -> None:
    """Validate the attack-input screenshot after hit confirmation without recapturing a moving Wisp."""
    body = _bash_function_body(generated, "wisp_verify_attack_input_aim_capture")
    function = "  wisp_verify_attack_input_aim_capture() {\n" + body + "\n  }\n"

    def run_case(
        attack_id: int,
        camera_id: int,
        camera_tick: int,
        line_tick: int,
        aim_dot: str,
        include_screenshot: bool = True,
        include_input_marker: bool = True,
    ) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-attack-aim-") as temporary:
            root = Path(temporary)
            status_path = root / "client-smoke-status.txt"
            status_lines = [f"WISP_RENDER_FIRST_FRAME_ID={camera_id}"]
            if include_input_marker:
                status_lines.append("WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES")
                status_lines.append("WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME")
            status_path.write_text("\n".join(status_lines) + "\n", encoding="utf-8")
            screenshots = root / "client-smoke" / "screenshots"
            screenshots.mkdir(parents=True)
            if include_screenshot:
                (screenshots / "78-wisp-aim-check.png").write_bytes(b"png evidence")
            camera_line = (
                f"TC_SMOKE_WISP_CAMERA=YES id={camera_id} aimTarget=render_safe_quarter tick={line_tick} "
                "playerX=12.5 playerY=149.0 playerZ=10.5 "
                "wispX=12.5 wispY=149.3 wispZ=11.5 "
                f"aimDot={aim_dot}"
            )
            script = (
                "set -euo pipefail\n"
                f"WISP_ATTACK_CAMERA_ID={attack_id}\n"
                f"WISP_ATTACK_CAMERA_TICK={camera_tick}\n"
                f"WISP_ATTACK_CAMERA_AIM_DOT={aim_dot}\n"
                f"WISP_ATTACK_CAMERA_LINE='{camera_line}'\n"
                + function
                + "if wisp_verify_attack_input_aim_capture; then result=0; else result=$?; fi\n"
                + 'echo "RESULT=$result"\n'
            )
            process = subprocess.run(
                ["bash", "-c", script], cwd=root, capture_output=True, text=True, check=False
            )
            status = status_path.read_text(encoding="utf-8")
            return process.returncode, process.stdout + process.stderr, status

    rc, output, status = run_case(157, 157, 124, 124, "0.999")
    assert rc == 0, (rc, output)
    assert "RESULT=0" in output, output
    assert "WISP_RENDER_CAPTURE_ID=157" in status, status
    assert "WISP_RENDER_CAPTURE_TICK=124" in status, status
    assert "WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME" in status, status
    assert "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME" in status, status
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" in status, status

    rc, output, status = run_case(157, 157, 124, 124, "0.989224")
    assert rc == 0, (rc, output)
    assert "RESULT=0" in output, output
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" in status, status

    rc, output, status = run_case(157, 157, 124, 124, "1.001")
    assert rc == 0, (rc, output)
    assert "RESULT=1" in output, output
    assert "WISP_RENDER_CAPTURE_INPUT_FRAME_AIM_INVALID=YES" in status, status
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" not in status, status

    rc, output, status = run_case(158, 157, 124, 124, "0.999")
    assert rc == 0, (rc, output)
    assert "RESULT=1" in output, output
    assert "WISP_RENDER_CAPTURE_INPUT_FRAME_INVALID" in status, status
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" not in status, status

    rc, output, status = run_case(157, 157, 124, 125, "0.999")
    assert rc == 0, (rc, output)
    assert "RESULT=1" in output, output
    assert "WISP_RENDER_CAPTURE_INPUT_FRAME_CAMERA_MISMATCH=YES" in status, status
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" not in status, status

    rc, output, status = run_case(157, 157, 124, 124, "0.999", include_screenshot=False)
    assert rc == 0, (rc, output)
    assert "RESULT=1" in output, output
    assert "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT_MISSING=YES" in status, status

    rc, output, status = run_case(157, 157, 124, 124, "0.999", include_input_marker=False)
    assert rc == 0, (rc, output)
    assert "RESULT=1" in output, output
    assert "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT_MISSING=YES" in status, status


def _test_wisp_zap_frame_capture_sequence(generated: str) -> None:
    names = (
        "wisp_wait_for_zap_frame",
        "wisp_capture_zap_frames",
    )
    functions = [
        "  " + name + "() {\n" + _bash_function_body(generated, name) + "\n  }\n"
        for name in names
    ]
    wait_body = _bash_function_body(generated, "wisp_wait_for_zap_frame")
    assert "wisp_wait_for_completed_wisp_frame" not in generated
    assert "WISP_ZAP_FRAME_COMPLETE_LINE_NUMBER" not in generated
    executable_wait_body = "\n".join(
        line.split("#", 1)[0] for line in wait_body.splitlines()
    )
    assert "tail -f" not in executable_wait_body, "the live marker watcher must not leave a follow process blocking command substitution"
    assert "timeout 5s bash -c" not in executable_wait_body, "the live marker watcher must not wait for a timeout after finding a frame"
    assert "for attempt in $(seq 1 1000)" in wait_body, "the marker watcher must use bounded non-blocking polling"
    # The bolt renderer remains visible even when the source Wisp is frustum-culled.
    # Deliberately omit post-zap Wisp entity-frame markers; FXBolt markers are authoritative.
    log_lines = [
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK monotonicNs=1000000000",
        "TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK cameraDistance=4.0 monotonicNs=1100000000",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E createdAt=100 clientGameTime=520 monotonicNs=1200000000",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=1.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF createdAt=100 gameTime=521 ageTick=1",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=521 wispTick=24 ageTick=1 age=1.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=522 wispTick=25 ageTick=2 age=2.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=523 wispTick=26 ageTick=3 age=3.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=100 gameTime=524 ageTicks=4",
    ]
    with tempfile.TemporaryDirectory(prefix="tc-wisp-zap-frame-sequence-") as directory:
        root = Path(directory)
        (root / "client-smoke").mkdir()
        (root / "client-smoke" / "client.log").write_text("\n".join(log_lines) + "\n", encoding="utf-8")
        (root / "client-smoke-status.txt").write_text("", encoding="utf-8")
        harness = (
            "set -euo pipefail\n"
            "wisp_wait_for_marker() { grep -Fq -- \"$1\" client-smoke/client.log; }\n"
            "wisp_stop_zap_video_capture() { echo VIDEO_STOP; }\n"
            "wisp_capture() { printf 'CAPTURE=%s\\n' \"$1\"; }\n"
            "python() { printf 'WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=1 wispTick=24\\nWISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=2 wispTick=25\\nWISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=3 wispTick=26\\nWISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES source=17 frameIndex=9 monotonicNs=1050000000\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=1 frameIndex=30 diff=18\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=2 frameIndex=12 diff=15\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=3 frameIndex=18 diff=12\\nWISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES\\n'; }\n"
            + "".join(functions)
            + "wisp_capture_zap_frames 0 17\n"
        )
        result = subprocess.run(
            ["bash", "-c", harness],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
            timeout=5,
        )
        assert result.returncode == 0, result.stderr + result.stdout
        captures = [line for line in result.stdout.splitlines() if line.startswith("CAPTURE=")]
        assert captures == ["CAPTURE=83-wisp-zap-after-expiry"], captures
        status = (root / "client-smoke-status.txt").read_text(encoding="utf-8")
        assert [f"WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick={age} " in status for age in (1, 2, 3)] == [True, True, True], status
        assert "VIDEO_STOP" in result.stdout, result.stdout
        assert "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=1 wispTick=24" in status, status
        assert "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=3 frameIndex=18 diff=12" in status, status
        assert "WISP_ZAP_EFFECT_EXPIRED=YES source=17 createdAt=100" in status, status


def _aim_dot(pitch_degrees: float, delta_y: float, delta_z: float) -> float:
    pitch = math.radians(pitch_degrees)
    length = math.hypot(delta_y, delta_z)
    return (-math.sin(pitch) * delta_y + math.cos(pitch) * delta_z) / length


def _test_video_frame_extraction(generated: str) -> None:
    with tempfile.TemporaryDirectory(prefix="tc-wisp-video-extraction-") as directory:
        root = Path(directory)
        screenshots = root / "screenshots"
        screenshots.mkdir()
        video = root / "wisp-zap-video.mp4"
        after = screenshots / "83-wisp-zap-after-expiry.png"
        write_png(after, None)
        for command in ("ffmpeg", "ffprobe", "convert", "compare"):
            if shutil.which(command) is None:
                raise AssertionError(f"required video extraction tool is missing: {command}")
        subprocess.run(
            [
                "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=1280x800:rate=60:duration=1",
                "-an", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28",
                "-pix_fmt", "yuv420p", str(video),
            ],
            check=True,
            capture_output=True,
        )
        status_file = root / "status.txt"
        status_file.write_text("\n".join((
            "WISP_ZAP_VIDEO_STARTED=YES firstFrameMonotonicNs=1000000000 frameRate=60",
            "WISP_CAMERA_SIDE_VIEW=YES id=17 monotonicNs=1050000000",
            "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK cameraDistance=4.0 monotonicNs=1100000000",
            "WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 createdAt=100 monotonicNs=1250000000",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=1 wispTick=24 observedAtMonotonicNs=1400000000",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=2 wispTick=25 observedAtMonotonicNs=1500000000",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=3 wispTick=26 observedAtMonotonicNs=1600000000",
            "WISP_ZAP_VIDEO_STOPPED=YES",
        )) + "\n", encoding="utf-8")
        result = subprocess.run(
            [
                sys.executable,
                str(Path(__file__).with_name("verify_wisp_zap_client.py")),
                "--extract-video-frames-only",
                "--video-status", str(status_file),
                "--video-file", str(video),
                "--video-screenshots", str(screenshots),
            ],
            check=False,
            capture_output=True,
            text=True,
            timeout=30,
        )
        assert result.returncode == 0, result.stderr + result.stdout
        assert result.stdout.strip() == "WISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES", result.stdout
        for name in ("80-wisp-zap-frame-a.png", "81-wisp-zap-frame-b.png", "82-wisp-zap-frame-c.png"):
            assert (screenshots / name).is_file() and (screenshots / name).stat().st_size > 128, name
        baseline = screenshots / verify_wisp_zap_client.BOLT_BASELINE_SCREENSHOT
        assert baseline.is_file() and baseline.stat().st_size > 128, (
            "the selected third-person Wisp baseline must survive temporary-directory cleanup"
        )
        extracted = status_file.read_text(encoding="utf-8")
        baseline_match = re.search(
            r"(?m)^WISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES source=17 frameIndex=(\d+) monotonicNs=(\d+)$",
            extracted,
        )
        age_one_match = re.search(
            r"(?m)^WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=1 frameIndex=(\d+) diff=\d+$",
            extracted,
        )
        assert baseline_match is not None and age_one_match is not None, extracted
        assert int(baseline_match.group(1)) == 13, extracted
        assert int(baseline_match.group(1)) < int(age_one_match.group(1)), extracted
        rendered_view = re.search(r"(?m)^WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=17 .* monotonicNs=(\d+)$", status_file.read_text(encoding="utf-8"))
        assert rendered_view is not None and int(baseline_match.group(2)) > int(rendered_view.group(1)), extracted
        assert extracted.count("WISP_ZAP_FRAME_CAPTURE=YES") == 3, extracted
        assert extracted.count("WISP_ZAP_VIDEO_FRAME_INDEX=YES") == 3, extracted
        assert [f"ageTick={age} " in extracted for age in (1, 2, 3)] == [True, True, True], extracted
        # The same continuous video must also provide the pre-click aim frame
        # after the live attack has completed; this extraction cannot delay input.
        client_dir = root / "client-smoke"
        client_screenshots = client_dir / "screenshots"
        client_screenshots.mkdir(parents=True)
        shutil.copyfile(video, client_dir / "wisp-zap-video.mp4")
        client_status = root / "client-smoke-status.txt"
        client_status.write_text("", encoding="utf-8")
        attack_capture_body = _bash_function_body(generated, "wisp_extract_attack_input_frame")
        attack_capture_function = "  wisp_extract_attack_input_frame() {\n" + attack_capture_body + "\n  }\n"
        attack_harness = (
            "set -euo pipefail\n"
            "WISP_ATTACK_INPUT_MONOTONIC_NS=2000000000\n"
            "WISP_ZAP_VIDEO_FIRST_FRAME_NS=1000000000\n"
            + attack_capture_function
            + "wisp_extract_attack_input_frame\n"
        )
        attack_result = subprocess.run(
            ["bash", "-c", attack_harness], cwd=root, capture_output=True, text=True, check=False, timeout=30
        )
        assert attack_result.returncode == 0, attack_result.stdout + attack_result.stderr
        attack_png = client_screenshots / "78-wisp-aim-check.png"
        assert attack_png.is_file() and attack_png.stat().st_size > 128, attack_png
        attack_status = client_status.read_text(encoding="utf-8")
        assert "WISP_ATTACK_INPUT_FRAME_VIDEO_OFFSET=0.983333" in attack_status, attack_status
        assert "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES" in attack_status, attack_status
        assert "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME" in attack_status, attack_status

        invalid_harness = (
            "set -euo pipefail\n"
            "WISP_ATTACK_INPUT_MONOTONIC_NS=900000000\n"
            "WISP_ZAP_VIDEO_FIRST_FRAME_NS=1000000000\n"
            + attack_capture_function
            + "if wisp_extract_attack_input_frame; then exit 9; else test $? -eq 1; fi\n"
        )
        invalid_result = subprocess.run(
            ["bash", "-c", invalid_harness], cwd=root, capture_output=True, text=True, check=False, timeout=10
        )
        assert invalid_result.returncode == 0, invalid_result.stdout + invalid_result.stderr
        assert "WISP_ATTACK_INPUT_VIDEO_TIMESTAMP_INVALID=YES" in client_status.read_text(encoding="utf-8")


def _test_wisp_render_profile(generated: str) -> None:
    expected = [
        "renderDistance:2",
        "simulationDistance:5",
        "graphicsMode:0",
        "particles:2",
        "entityShadows:false",
        "mipmapLevels:0",
        "biomeBlendRadius:0",
        "enableVsync:false",
        "maxFps:260",
        "renderClouds:false",
        "ao:false",
    ]
    assert generated.count("  wisp_verify_render_profile() {") == 1
    assert generated.count("  wisp_verify_render_profile\n") == 1
    body = _bash_function_body(generated, "wisp_verify_render_profile")
    bash = (
        "set -euo pipefail\n"
        "wisp_verify_render_profile() {\n"
        + body
        + "\n}\n"
        "wisp_verify_render_profile\n"
    )
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        run_dir = root / "run"
        run_dir.mkdir()
        options_file = run_dir / "options.txt"
        status_file = root / "client-smoke-status.txt"

        options_file.write_text("\n".join(expected) + "\n", encoding="utf-8")
        passed = subprocess.run(
            ["bash", "-c", bash],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
        )
        assert passed.returncode == 0, passed.stdout + passed.stderr
        assert "WISP_CLIENT_RENDER_PROFILE_APPLIED=YES" in passed.stdout
        assert status_file.is_file()

        options_file.write_text("\n".join(expected[:-1]) + "\n", encoding="utf-8")
        rejected = subprocess.run(
            ["bash", "-c", bash],
            cwd=root,
            text=True,
            capture_output=True,
            check=False,
        )
        assert rejected.returncode != 0
        assert "WISP_CLIENT_RENDER_OPTION_MISSING=ao:false" in rejected.stdout



def write_bolt_png(
    path: Path,
    line: tuple[int, int, int, int] | None = (512, 432, 548, 572),
    color: tuple[int, int, int] = (255, 255, 126),
    alpha: float = 1.0,
    source_center: tuple[int, int] | None = (512, 400),
) -> None:
    """Write a real-size synthetic viewport with a Wisp orb and optional bolt."""
    width, height = 1280, 800
    background = bytes((8, 8, 16))
    orb = bytes((255, 255, 126))
    rows = [bytearray(background * width) for _ in range(height)]

    def put(x: int, y: int, pixel: bytes) -> None:
        if 0 <= x < width and 0 <= y < height:
            offset = x * 3
            rows[y][offset:offset + 3] = pixel

    if source_center is not None:
        source_x, source_y = source_center
        for y in range(source_y - 16, source_y + 17):
            for x in range(source_x - 16, source_x + 17):
                if (x - source_x) ** 2 + (y - source_y) ** 2 <= 16 ** 2:
                    put(x, y, orb)

    if line is not None:
        x0, y0, x1, y1 = line
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        line_pixels: set[tuple[int, int]] = set()
        for step in range(steps + 1):
            t = step / steps
            x = round(x0 + (x1 - x0) * t)
            y = round(y0 + (y1 - y0) * t)
            for offset_y in range(-1, 2):
                for offset_x in range(-1, 2):
                    line_pixels.add((x + offset_x, y + offset_y))
        for x, y in line_pixels:
            # Clip the synthetic beam to the viewport before reading the
            # destination pixel. A beam entering from x=0 creates x=-1
            # thickness pixels; Python's negative slice is empty here.
            if not (0 <= x < width and 0 <= y < height):
                continue
            offset = x * 3
            current = tuple(rows[y][offset:offset + 3])
            blended = tuple(
                min(255, current[channel] + round(color[channel] * alpha))
                for channel in range(3)
            )
            put(x, y, bytes(blended))

    # Reproduce the warm-tinted vanilla crosshair overlay at the 1024x768 viewport centre.
    crosshair = bytes((213, 213, 190))
    crosshair_x, crosshair_y = 511, 383
    for y in range(crosshair_y - 9, crosshair_y + 9):
        for x in range(crosshair_x - 1, crosshair_x + 1):
            put(x, y, crosshair)
    for y in range(crosshair_y - 1, crosshair_y + 1):
        for x in range(crosshair_x - 9, crosshair_x + 9):
            put(x, y, crosshair)

    # A non-yellow player silhouette at the beam endpoint must not satisfy the mask.
    for y in range(580, 720):
        for x in range(492, 533):
            put(x, y, bytes((70, 110, 180)))

    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw.extend(row)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        + _chunk(b"IEND", b"")
    )


def _test_fxbbolt_segment_detector() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        baseline = root / "third-person-before-zap.png"
        source_only = root / "source-only.png"
        age_one = root / "age-one.png"
        side_baseline = root / "side-baseline.png"
        side_age = root / "side-age.png"
        no_orb_baseline = root / "no-orb-baseline.png"
        age_two = root / "age-two.png"
        age_three = root / "age-three.png"
        blue = root / "blue-line.png"
        too_steep = root / "too-steep.png"
        horizontal_offscreen = root / "horizontal-offscreen.png"
        horizontal_outside_roi = root / "horizontal-outside-roi.png"
        short = root / "short.png"
        too_dim = root / "below-visible-threshold.png"
        write_bolt_png(baseline, line=None)
        write_bolt_png(source_only, line=None)
        write_bolt_png(age_one, alpha=2.0 / 3.0)
        write_bolt_png(age_two, alpha=1.0 / 3.0)
        write_bolt_png(age_three, alpha=0.1)
        write_bolt_png(blue, color=(60, 160, 255))
        write_bolt_png(too_steep, line=(512, 400, 512, 600))
        write_bolt_png(horizontal_offscreen, line=(0, 700, 470, 700), alpha=2.0 / 3.0, source_center=None)
        write_bolt_png(horizontal_outside_roi, line=(0, 100, 470, 100), alpha=2.0 / 3.0, source_center=None)
        write_bolt_png(short, line=(512, 432, 515, 445))
        write_bolt_png(too_dim, alpha=0.005)
        write_bolt_png(side_baseline, line=None, source_center=(700, 400))
        write_bolt_png(side_age, line=(700, 432, 512, 572), alpha=0.1, source_center=(700, 400))
        write_bolt_png(no_orb_baseline, line=None, source_center=None)

        side_errors, side_metrics = verify_wisp_zap_client.verify_bolt_segment(
            side_age, True, side_baseline
        )
        assert not side_errors and side_metrics is not None, (side_errors, side_metrics)
        assert 30.0 <= abs(side_metrics["angle_degrees"]) <= 45.0, side_metrics

        no_orb_errors, no_orb_metrics = verify_wisp_zap_client.verify_bolt_segment(
            age_one, True, no_orb_baseline
        )
        assert not no_orb_errors and no_orb_metrics is not None, (no_orb_errors, no_orb_metrics)

        # A broadside camera can project the BETA26 bolt almost horizontally.
        # It remains a valid long, thin additive component even when its source
        # is off-screen; the same line outside the effect ROI must be rejected.
        horizontal_errors, horizontal_metrics = verify_wisp_zap_client.verify_bolt_segment(
            horizontal_offscreen, True, no_orb_baseline
        )
        assert not horizontal_errors and horizontal_metrics is not None, (
            horizontal_errors, horizontal_metrics
        )
        assert horizontal_metrics["length"] >= 100
        assert abs(horizontal_metrics["angle_degrees"]) <= 5.0, horizontal_metrics
        outside_errors, outside_metrics = verify_wisp_zap_client.verify_bolt_segment(
            horizontal_outside_roi, True, no_orb_baseline
        )
        assert outside_errors and outside_metrics is None, (outside_errors, outside_metrics)

        for age_frame in (age_one, age_two, age_three):
            errors, metrics = verify_wisp_zap_client.verify_bolt_segment(
                age_frame, True, baseline
            )
            assert not errors, (age_frame.name, errors)
            assert metrics is not None and metrics["length"] >= 40 and metrics["elongation"] >= 8
            assert abs(metrics["angle_degrees"]) <= 78, metrics


        # The Wisp can leave its original screen position during the 20-tick
        # wind-up. The latest same-camera baseline may contain only the player;
        # a low-FPS capture may also miss age 1 while still showing ages 2 and 3.
        moved_baseline = root / "moved-source-baseline.png"
        moved_age_one = root / "moved-source-age-one.png"
        moved_age_two = root / "moved-source-age-two.png"
        moved_age_three = root / "moved-source-age-three.png"
        moved_expiry = root / "moved-source-expiry.png"
        moved_expiry_bolt = root / "moved-source-expiry-bolt.png"
        write_bolt_png(moved_baseline, line=None, source_center=None)
        write_bolt_png(moved_age_one, line=None, source_center=None)
        write_bolt_png(moved_age_two, line=(0, 300, 480, 600), alpha=1.0 / 3.0, source_center=None)
        write_bolt_png(moved_age_three, line=(0, 300, 480, 600), alpha=0.1, source_center=None)
        write_bolt_png(moved_expiry, line=None, source_center=None)
        write_bolt_png(moved_expiry_bolt, line=(0, 300, 480, 600), alpha=0.1, source_center=None)
        cycle_errors, visible_ages, _ = verify_wisp_zap_client.verify_bolt_cycle(
            ((1, moved_age_one), (2, moved_age_two), (3, moved_age_three)),
            moved_expiry,
            moved_baseline,
        )
        assert not cycle_errors and [age for age, _ in visible_ages] == [2, 3], (
            cycle_errors, [age for age, _ in visible_ages]
        )
        empty_cycle_errors, empty_visible, _ = verify_wisp_zap_client.verify_bolt_cycle(
            ((1, moved_age_one), (2, moved_age_one), (3, moved_age_one)),
            moved_expiry,
            moved_baseline,
        )
        assert empty_cycle_errors and not empty_visible, empty_cycle_errors
        expiry_cycle_errors, _, _ = verify_wisp_zap_client.verify_bolt_cycle(
            ((1, moved_age_one), (2, moved_age_two), (3, moved_age_three)),
            moved_expiry_bolt,
            moved_baseline,
        )
        assert any("after expiry" in error for error in expiry_cycle_errors), expiry_cycle_errors

        for rejected in (source_only, blue, too_steep, short, too_dim):
            errors, metrics = verify_wisp_zap_client.verify_bolt_segment(
                rejected, True, baseline
            )
            assert errors and metrics is None, (rejected.name, errors, metrics)

        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(
            source_only, False, baseline
        )
        assert not errors and metrics is None, (errors, metrics)
        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(
            age_three, False, baseline
        )
        assert errors and metrics is not None, (errors, metrics)
        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(
            age_one, True, root / "missing-baseline.png"
        )
        assert errors and metrics is None, (errors, metrics)

def _test_wisp_zap_fixture_elevation(generated: str) -> None:
    """Keep the real BETA26 base-to-base bolt above the smoke platform."""
    spawn = re.search(
        r'wisp_fast_command "/summon thaumcraft:wisp 12\.5 ([0-9.]+) 11\.5 ',
        generated,
    )
    player = re.search(
        r"wisp_fast_command '/tp @p 12\.5 ([0-9.]+) 10\.5 0 (-?[0-9.]+)'",
        generated,
    )
    assert spawn is not None, "the tagged Wisp spawn must keep its elevated fixture coordinates"
    assert player is not None, "the player must remain on the verified platform"
    source_y = float(spawn.group(1))
    player_y = float(player.group(1))
    pitch = float(player.group(2))
    platform_top = 149.0
    assert source_y - platform_top >= 2.0, (source_y, platform_top)
    assert player_y == platform_top, (player_y, platform_top)
    expected_pitch = -math.degrees(math.atan2(source_y + 0.225 - (player_y + 1.62), 1.0))
    assert abs(pitch - expected_pitch) <= 1.5, (pitch, expected_pitch)
    assert "run tp @p ~1.0 ~-1.0 ~ facing entity @s eyes" in generated
    assert "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES" in generated
    assert "TC_SMOKE_WISP_NAMED_SOURCE=YES" in generated
    assert "TC_SMOKE_WISP_NAMED_SOURCE=YES" in verify_wisp_zap_client.REQUIRED_LOG_MARKERS
    assert "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES" in generated
    assert "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES" in verify_wisp_zap_client.REQUIRED_LOG_MARKERS
    assert generated.count("name=tc_wisp_zap_smoke,limit=1") == 3
    assert "tag=tc_wisp_zap_smoke" not in generated
    assert "if block ~ ~ ~ minecraft:air run say TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES" in generated
    assert "if block ~ ~-1 ~ minecraft:air" not in generated


def main() -> int:
    _test_bash_function_body_extraction()
    _test_fxbbolt_segment_detector()
    render_source = Path("src/neoforge/java/dev/ehtid/thaumcraft/client/WispZapClientEffects.java").read_text(encoding="utf-8")
    assert 'minecraft.options.getCameraType().name()' in render_source
    _test_streaming_click_dispatch()
    # BETA26 RenderWisp's billboard is centred at entity position. A point one
    # quarter up the 0.9-block hitbox stays inside the attack AABB while moving
    # the camera toward the visible quad instead of the AABB's vertical centre.
    old_eye_dot = _aim_dot(29.0, -0.555, 1.0)
    old_render_dot = _aim_dot(41.023269, -1.32, 1.0)
    hitbox_center_dot = _aim_dot(41.023269, -0.87, 1.0)
    render_safe_pitch = math.degrees(math.atan2(1.095, 1.0))
    render_safe_quarter_dot = _aim_dot(render_safe_pitch, -1.095, 1.0)
    render_safe_quarter_hitbox_dot = _aim_dot(render_safe_pitch, -0.87, 1.0)
    assert (
        old_eye_dot >= 0.99
        and old_render_dot < 0.99
        and hitbox_center_dot >= 0.99
        and render_safe_quarter_dot >= 0.99
        and render_safe_quarter_hitbox_dot >= 0.99
    ), (
        old_eye_dot, old_render_dot, hitbox_center_dot,
        render_safe_quarter_dot, render_safe_quarter_hitbox_dot
    )

    anchor = "  # The initial world is already creative + peaceful, as established"
    fixture = (
        "#!/usr/bin/env bash\n"
        "printf 'guiScale:2\\n' > run/options.txt\n"
        "if grep -q ready; then\n"
        "  send_chat() {\n"
        "    true\n"
        "  }\n"
        + anchor + "\n"
    )
    with patch.object(Path, "read_text", return_value=fixture):
        generated = client_wisp_zap_smoke.render()
    _test_attack_frame_dot_tolerance(generated)
    _test_attack_input_frame_correlation(generated)
    _test_attack_cursor_starts_after_live_reposition(generated)
    _test_first_frame_telemetry_uses_selected_frame(generated)
    _test_attack_aim_capture_is_taken_at_input_and_validated_after_hit(generated)
    _test_wisp_zap_frame_capture_sequence(generated)
    _test_video_frame_extraction(generated)
    _test_wisp_render_profile(generated)
    _test_wisp_zap_fixture_elevation(generated)

    required = (
        "wisp_send_chat()",
        "/difficulty normal",
        "/gamemode survival",
        "minecraft:resistance infinite 4 true",
        "wisp_wait_for_loaded_chunk()",
        "wisp_wait_for_fill()",
        "Successfully filled 81 block(s)",
        "WISP_FIXTURE_PLATFORM_FILL=YES",
        '/fill 6 148 6 14 148 14 minecraft:stone',
        '/tp @p 12.5 149 10.5 0 -42',
        '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES',
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "TC_SMOKE_WISP_NAMED_SOURCE=YES",
        "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES",
        "run tp @p ~1.0 ~-1.0 ~ facing entity @s eyes",
        "summon thaumcraft:wisp 12.5 151.3 11.5",
        "/execute if loaded 10 148 10 run say TC_SMOKE_WISP_CHUNK_LOADED=YES",
        "TC_SMOKE_WISP_ATTACK_INPUT=YES",
        "hitDistance=",
        "monotonicNs=",
        "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES",
        "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=",
        "WISP_CAMERA_SIDE_VIEW=YES id=",
        "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=",
        "TC_SMOKE_WISP_HURT=YES accepted=true",
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "minecraft:generic.knockback_resistance base set 1",
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0",
        "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
        "WISP_ZAP_PAYLOAD_RECEIVED=YES source=",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=",
        "firstFrameMonotonicNs=",
        "observedAtMonotonicNs=",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON",
        "wisp_start_zap_video_capture",
        "wisp_stop_zap_video_capture",
        "wisp_capture_zap_frames",
        "wisp_wait_for_zap_frame",
        "WISP_ZAP_VIDEO_STARTED=YES",
        "WISP_ZAP_VIDEO_STOPPED=YES",
        "WISP_ZAP_FRAME_OBSERVED=YES",
        "--extract-video-frames-only",
        "--video-status client-smoke-status.txt",
        "--video-file client-smoke/wisp-zap-video.mp4",
        "--video-screenshots client-smoke/screenshots",
        "ffmpeg",
        "TC_SMOKE_WISP_ZAP_FRAME=YES",
        "TC_SMOKE_WISP_ZAP_EXPIRED=YES",
        "WISP_ZAP_EFFECT_EXPIRED=YES",
        "wisp_send_chat '/time set midnight' 'Set the time to 18000'",
        "WISP_ZAP_TEST_TIME=MIDNIGHT",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES",
        "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES",

        "WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME",
        "TC_SMOKE_WISP_ZAP_SENT=YES",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES",
        "TC_SMOKE_WISP_RENDERED=YES",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES",
        "hitType=ENTITY targetId=$wisp_id targetType=thaumcraft:wisp blockPos=none",
        "WISP_ATTACKABLE_FRAME=YES",
        'exec {frame_fd}< <(tail --sleep-interval=0.01 -n +"$((after_lines + 1))" -f client-smoke/client.log)',
        'deadline=$((SECONDS + 5))',
        'if ! IFS= read -r -t "$remaining" -u "$frame_fd" candidate_line; then',
        'kill "$frame_tail_pid" 2>/dev/null || true',
        'WISP_ATTACK_CLICK_DISPATCHED=YES',
        'WISP_ATTACK_CLICK_DISPATCH_FAILED=YES',
        "WISP_RENDER_FRAME_COMPLETE=YES",
        "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=",
        "wisp_fast_command()",
        "sleep 0.5",
        'wisp_fast_command "/summon thaumcraft:wisp',
        "CustomNameVisible:0b",
        'grep -F -- "$frame_camera_marker" | head -1',
        'grep -F -c -- "$frame_camera_marker" || true',
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=",
        "TC_SMOKE_WISP_HURT=YES accepted=true",
        "d <= 3.0",
        "wisp_verify_aim_telemetry",
        "WISP_CAMERA_TELEMETRY=PASS",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_ZAP_AIM=PREAIMED_SPAWN",
        "wisp_wait_for_teleport",
        'wisp_wait_for_fresh_render "$aim_log_lines"',
        "wisp_verify_attack_input_aim_capture",
        "wisp_wait_for_attack_frame()",
        "wisp_attack_live_wisp()",
        "for attempt in 1 2 3",
        "WISP_ATTACK_ATTEMPT_REJECTED=",
        "WISP_ATTACK_CAMERA_FRAME=YES",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES",
        'open_delay="0.5"',
        'type_delay="2"',
        'sleep "$open_delay"',
        'xdotool type --clearmodifiers --delay "$type_delay" "$command"',
        "open_delay=\"0.5\"",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS",
        "targetWisp=true",
        "WISP_RENDER_CAPTURE_TICK=",
        "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "WISP_RENDER_PREATTACK_TRACKING=YES",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=",
        'attack_camera_source="LIVE_ENTITY_FACING"',
        'echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail"',
        "WISP_FIXTURE_TELEPORT=YES",
        "python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png",
        "python tools/verify_wisp_zap_client.py --baseline-only client-smoke/screenshots/79-wisp-before-a.png",
        "python tools/verify_wisp_zap_client.py --baseline-only client-smoke/screenshots/79-wisp-before-b.png",
        "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
        "wisp_capture 79-wisp-before-a",
        "wisp_capture 79-wisp-before-b",
        "WISP_FIXTURE_PLATFORM_FILL=YES",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF",
        "WISP_ZAP_REAL_CLIENT=PASS",
        "WISP_ALL_WISP_TEXTURES_NO_LOAD_ERRORS=PASS",
    )
    for marker in required:
        assert marker in generated, marker
    summon_position = generated.find('wisp_fast_command "/summon thaumcraft:wisp')
    recorder_position = generated.find("wisp_start_zap_video_capture\n  aim_log_lines")
    if summon_position < 0 or recorder_position < 0 or recorder_position > summon_position:
        raise AssertionError("continuous Wisp video recorder must start before the test Wisp is summoned")
    capture_function = generated.split("  wisp_capture_zap_frames() {", 1)[1].split("\n  }\n", 1)[0]
    if 'wisp_capture "$frame_name"' in capture_function or "wisp_capture 83-wisp-zap-after-expiry" not in capture_function:
        raise AssertionError("per-age desktop captures must be replaced by post-expiry video-frame extraction")
    stop_index = capture_function.index("wisp_stop_zap_video_capture")
    extract_index = capture_function.index("python tools/verify_wisp_zap_client.py --extract-video-frames-only")
    if stop_index > extract_index:
        raise AssertionError("video extraction must run only after the recorder has stopped")
    stabilization_command = (
        "wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1' "
        "'Base value for attribute Knockback Resistance for entity Dev set to 1.0'"
    )
    assert generated.count(stabilization_command) == 1
    assert "Base value of attribute Knockback Resistance for player Dev is now 1.0" not in generated
    setup = generated.split("  wisp_command '/difficulty normal'", 1)[1]
    teleport_index = setup.index("wisp_command '/tp @p 10.5 149 8.5 0 22'")
    loaded_index = setup.index("wisp_wait_for_loaded_chunk")
    fill_index = setup.index("wisp_fast_command '/fill 6 148 6 14 148 14 minecraft:stone'")
    fill_wait_index = setup.index('wisp_wait_for_fill "$fill_log_lines"', fill_index)
    fixture_teleport_index = setup.index("wisp_fast_command '/tp @p 12.5 149 10.5 0 -42'", fill_wait_index)
    fixture_wait_index = setup.index('wisp_wait_for_teleport "$fixture_teleport_log_lines"', fixture_teleport_index)
    support_index = setup.index("wisp_send_chat '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES'", fixture_wait_index)
    creative_marker_index = setup.index("wisp_send_chat '/execute if entity @p[gamemode=creative] run say TC_SMOKE_WISP_PLAYER_CREATIVE=YES'", support_index)
    night_index = setup.index("wisp_send_chat '/time set midnight' 'Set the time to 18000'")
    baseline_a_index = setup.index("wisp_capture 79-wisp-before-a", creative_marker_index)
    assert night_index < baseline_a_index, "night lighting must be fixed before baseline screenshots"
    baseline_b_index = setup.index("wisp_capture 79-wisp-before-b", baseline_a_index)
    pre_spawn_baseline_marker_index = setup.index("WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN", baseline_b_index)
    spawn_snapshot_index = setup.index('aim_log_lines="$(wc -l < client-smoke/client.log)"', pre_spawn_baseline_marker_index)
    summon_index = setup.index('wisp_fast_command "/summon thaumcraft:wisp', spawn_snapshot_index)
    rendered_index = setup.index('wisp_wait_for_fresh_render "$aim_log_lines"', summon_index)
    preposition_index = setup.index('wisp_move_player_to_live_wisp "$reanchor_log_lines"', rendered_index)
    preposition_marker_index = setup.index("WISP_ATTACK_PREPOSITIONED_WHILE_CREATIVE=YES", preposition_index)
    zap_cursor_index = setup.index('zap_log_lines="$(wc -l < client-smoke/client.log)"', preposition_marker_index)
    attack_call_index = setup.index("wisp_attack_live_wisp", zap_cursor_index)
    client_attack_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_CLIENT_ATTACK=YES'", attack_call_index)
    camera_stopped_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES'", client_attack_index)
    side_view_wait_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES'", camera_stopped_index)
    side_view_status_index = setup.index('echo "WISP_CAMERA_SIDE_VIEW=YES id=$side_view_id monotonicNs=$side_view_monotonic_ns"', side_view_wait_index)
    side_view_rendered_wait_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES'", side_view_status_index)
    side_view_rendered_status_index = setup.index('echo "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=$side_view_rendered_id', side_view_rendered_wait_index)
    hurt_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_HURT=YES accepted=true'", side_view_rendered_status_index)
    target_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'", hurt_index)
    stabilize_index = setup.index("wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1'", rendered_index)
    stabilize_marker_index = setup.index("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL", stabilize_index)
    zap_capture_index = setup.index('wisp_capture_zap_frames "$zap_log_lines" "$wisp_id"', target_index)
    attack_frame_extract_index = setup.index("wisp_extract_attack_input_frame", zap_capture_index)
    capture_aim_index = setup.index("wisp_verify_attack_input_aim_capture", attack_frame_extract_index)
    telemetry_index = setup.index('wisp_verify_aim_telemetry "$aim_log_lines"', capture_aim_index)
    aim_verify_index = setup.index("python tools/verify_wisp_zap_client.py --aim-only", telemetry_index)
    perspective_restore_index = setup.index("xdotool key --clearmodifiers F5", aim_verify_index)
    perspective_restore_second_index = setup.index("xdotool key --clearmodifiers F5", perspective_restore_index + 1)
    restore_marker_index = setup.index("WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON", perspective_restore_second_index)
    assert night_index < teleport_index < loaded_index < fill_index < fill_wait_index < fixture_teleport_index < fixture_wait_index < support_index < creative_marker_index < baseline_a_index < baseline_b_index < pre_spawn_baseline_marker_index < spawn_snapshot_index < summon_index < rendered_index < stabilize_index < stabilize_marker_index < preposition_index < preposition_marker_index < zap_cursor_index < attack_call_index < client_attack_index < camera_stopped_index < side_view_wait_index < side_view_status_index < side_view_rendered_wait_index < side_view_rendered_status_index < hurt_index < target_index < zap_capture_index < attack_frame_extract_index < capture_aim_index < telemetry_index < aim_verify_index < perspective_restore_index < perspective_restore_second_index < restore_marker_index, (
        "keep the player Creative through Wisp spawn, preposition at the live entity, then enter Survival for the real hit before the zap payload"
    )
    main_before_attack = setup[rendered_index:attack_call_index]
    assert "wisp_command '/gamemode survival'" not in main_before_attack, "Survival must not begin before the live melee position is staged"
    assert "wisp_move_player_to_live_wisp" in main_before_attack
    assert "wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1'" in main_before_attack
    assert main_before_attack.index("wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1'") < main_before_attack.index("wisp_move_player_to_live_wisp"), "knockback setup must finish before the final live-Wisp teleport"
    assert "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL" in main_before_attack
    assert "wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1'" not in setup[preposition_marker_index:attack_call_index], "no slow setup command may follow the final live-Wisp reposition"
    assert "wisp_fast_command '/tp @p ~ ~ ~ 90 0' 0.1 1" not in setup
    assert "wisp_capture 79-wisp-third-person-before-zap" not in setup
    assert "wisp_verify_attack_input_aim_capture" not in setup[restore_marker_index:]
    assert "wisp_verify_attack_input_aim_capture_with_retries" not in generated
    assert "wisp_reanchor_player_for_capture" not in generated
    attack_frame_body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    assert 'exec {frame_fd}< <(tail --sleep-interval=0.01 -n +"$((after_lines + 1))" -f client-smoke/client.log)' in attack_frame_body
    assert 'if ! IFS= read -r -t "$remaining" -u "$frame_fd" candidate_line; then' in attack_frame_body
    assert 'if [[ "$candidate_line" =~ $frame_pattern ]]; then' in attack_frame_body
    click_command = "xdotool click --delay 0 1"
    assert attack_frame_body.count(click_command) == 1
    assert attack_frame_body.index('if [[ "$candidate_line" =~ $frame_pattern ]]') < attack_frame_body.index(click_command) < attack_frame_body.index('kill "$frame_tail_pid"')
    assert attack_frame_body.index('WISP_ATTACK_INPUT_LOG_LINES="$click_log_lines"') < attack_frame_body.index(click_command)
    assert "timeout 5s bash -c" not in attack_frame_body
    assert 'if [ "$watcher_status" -ne 0 ]; then' in attack_frame_body
    assert 'exit 1' in attack_frame_body
    attack_body = _bash_function_body(generated, "wisp_attack_live_wisp")
    assert "for attempt in 1 2 3" in attack_body, "the attack retry budget must remain bounded"
    assert 'if [ "$attempt" -gt 1 ]; then' in attack_body
    retry_branch = attack_body.split('if [ "$attempt" -gt 1 ]; then', 1)[1].split("\n      fi", 1)[0]
    creative_command_index = retry_branch.index("wisp_command '/gamemode creative'")
    creative_marker_index = retry_branch.index("TC_SMOKE_WISP_PLAYER_CREATIVE=YES")
    retry_sleep_index = retry_branch.index("sleep 0.15")
    retry_reposition_index = retry_branch.index("wisp_move_player_to_live_wisp")
    assert creative_command_index < creative_marker_index < retry_sleep_index < retry_reposition_index, (
        "a rejected attempt must return to Creative and clear autonomous targeting before repositioning"
    )
    survival_cursor_index = attack_body.index('survival_log_lines="$(wc -l < client-smoke/client.log)"')
    survival_command_index = attack_body.index("wisp_fast_command '/gamemode survival' 0.1 1")
    survival_marker_index = attack_body.index("wisp_wait_for_fresh_log_marker")
    survival_status_index = attack_body.index("TC_SMOKE_WISP_PLAYER_SURVIVAL=YES")
    frame_cursor_index = attack_body.index('frame_log_lines="$(wc -l < client-smoke/client.log)"')
    attack_frame_index = attack_body.index("wisp_wait_for_attack_frame")
    assert survival_cursor_index < survival_command_index < survival_marker_index < survival_status_index < frame_cursor_index < attack_frame_index, (
        "the real attack cursor must begin after the fresh server-confirmed Survival transition"
    )
    assert "wisp_command '/gamemode survival'" not in attack_body
    assert "wisp_send_chat '/execute if entity @p[gamemode=survival]" not in attack_body
    assert 'frame_log_lines="$WISP_ATTACK_REPOSITION_LINE_NUMBER"' not in attack_body
    assert "attack_position_mode=LIVE_WISP" in attack_body
    assert 'attack_camera_source="LIVE_ENTITY_FACING"' in attack_body
    assert 'attack_camera_detail="$WISP_ATTACK_REPOSITION_LINE"' in attack_body
    assert 'wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id" LIVE_WISP "$attempt"' in attack_body
    assert "PREAIMED_NEXT_FRAME" not in attack_body
    assert "WISP_RENDER_FIRST_FRAME_LINE_NUMBER" not in attack_body
    assert "WISP_ATTACK_ATTEMPT_REJECTED=" in attack_body, "BLOCK hits must be rejected rather than accepted"
    assert "xdotool click --delay 0 1" not in attack_body, "the caller must not delay the click after frame parsing"
    assert 'attack_log_lines="$WISP_ATTACK_INPUT_LOG_LINES"' in attack_body
    assert attack_frame_index < attack_body.index("TC_SMOKE_WISP_ATTACK_INPUT=YES")
    assert 'echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail"' in attack_body
    between_render_and_attack = setup[rendered_index:attack_call_index]
    assert "sleep " not in between_render_and_attack and "wisp_capture" not in between_render_and_attack, (
        "no external capture or delay may be inserted before the live-target retry helper"
    )
    fresh_render_body = _bash_function_body(generated, "wisp_wait_for_fresh_render")
    track_marker_index = fresh_render_body.index("TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=")
    track_line_number_index = fresh_render_body.index('track_line_number="$(grep -n -F -- "$track_line"')
    render_after_track_index = fresh_render_body.index('render_line="$(tail -n +"$((track_line_number + 1))"')
    camera_after_track_index = fresh_render_body.index('camera_line="$(tail -n +"$((track_line_number + 1))"')
    frame_after_track_index = fresh_render_body.index('frame_line="$(tail -n +"$((track_line_number + 1))"')
    cursor_capture_index = fresh_render_body.index('WISP_RENDER_FIRST_FRAME_LINE_NUMBER="$frame_line_number"')
    first_frame_status_index = fresh_render_body.index("WISP_RENDER_FIRST_FRAME=YES")
    assert track_marker_index < track_line_number_index < render_after_track_index < camera_after_track_index < frame_after_track_index < cursor_capture_index < fresh_render_body.index("WISP_RENDER_FRAME_COMPLETE=YES") < first_frame_status_index
    assert 'wisp_capture "$capture_name"' not in fresh_render_body, "the first-frame wait must not capture before the attack"
    capture_body = _bash_function_body(generated, "wisp_verify_attack_input_aim_capture")
    assert 'wisp_id="${WISP_ATTACK_CAMERA_ID:-}"' in capture_body
    assert 'camera_tick="${WISP_ATTACK_CAMERA_TICK:-}"' in capture_body
    assert 'camera_line="${WISP_ATTACK_CAMERA_LINE:-}"' in capture_body
    assert 'grep -Fq "TC_SMOKE_WISP_CAMERA=YES id=$wisp_id aimTarget=render_safe_quarter tick=$camera_tick "' in capture_body
    assert 'awk -v dot="$aim_dot"' in capture_body
    assert "[ ! -s client-smoke/screenshots/78-wisp-aim-check.png ]" in capture_body, (
        "the attack-aim verifier must fail closed when its captured PNG is absent"
    )
    assert "wisp_capture 78-wisp-aim-check" not in capture_body
    assert 'WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME' in capture_body
    assert 'WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME' in capture_body
    assert 'echo "$camera_line" | tee -a client-smoke-status.txt' in capture_body
    assert "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES" in capture_body
    assert "WISP_RENDER_CAPTURE_AFTER_HIT_TIMEOUT=YES" not in capture_body
    input_frame_body = _bash_function_body(generated, "wisp_capture_attack_input_frame")
    assert "wisp_capture 78-wisp-aim-check" not in input_frame_body
    assert 'WISP_ATTACK_INPUT_MONOTONIC_NS="$input_monotonic_ns"' in input_frame_body
    assert 'WISP_ATTACK_INPUT_HIT_DISTANCE="$input_hit_distance"' in input_frame_body
    wait_attack_body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    assert "wisp_capture 78-wisp-aim-check" not in wait_attack_body
    assert "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES" not in wait_attack_body
    click_log_snapshot_index = wait_attack_body.index('click_log_lines="$(wc -l < client-smoke/client.log)"')
    frame_wait_index = wait_attack_body.index('exec {frame_fd}< <(tail --sleep-interval=0.01 -n +')
    assert "--sleep-interval=0.01" in wait_attack_body, "keep fallback tail polling below one tick"
    camera_line_index = wait_attack_body.index('camera_line="$(grep -F')
    attack_click_index = wait_attack_body.index("xdotool click --delay 0 1")
    diagnostics_index = wait_attack_body.index('distance="$(awk')
    assert click_log_snapshot_index < frame_wait_index < attack_click_index < camera_line_index < diagnostics_index, (
        "dispatch the real click as soon as the matching AFTER_LEVEL EntityHitResult arrives, before camera parsing"
    )
    assert wait_attack_body.count("xdotool click --delay 0 1") == 1
    assert "return 1" not in wait_attack_body[wait_attack_body.index("WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK attempt=$attempt"):], (
        "after dispatching a real click, telemetry failures must abort rather than retry with a second attack"
    )
    assert 'WISP_ATTACK_INPUT_LOG_LINES="$click_log_lines"' in wait_attack_body
    assert 'echo "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK attempt=$attempt"' in wait_attack_body
    assert "exit 1" in wait_attack_body[attack_click_index:], "post-click diagnostics must fail closed rather than retry with a second click"
    assert "timeout 5s bash -c" not in wait_attack_body
    assert "timeout 5s bash -c" in attack_body
    assert 'position_mode="${3:-PLATFORM}"' in wait_attack_body
    assert '-v mode="$position_mode"' in wait_attack_body, "pass the requested position mode into the native frame guard"
    assert "dot >= 0.99" not in wait_attack_body, "the actual same-ID EntityHitResult is authoritative"
    assert "dot >= -1.0001 && dot <= 1.0001" in wait_attack_body, "allow only small floating-point cosine roundoff"
    assert 'position=$position_mode' in wait_attack_body, "record the selected position mode in native diagnostics"
    assert 'mode == "PLATFORM" ? on_platform : y >= 0.0' in wait_attack_body
    assert "d <= 6.0" in wait_attack_body, "entity-origin distance is only a broad sanity bound"
    assert "hitType=ENTITY targetId=$wisp_id targetType=thaumcraft:wisp blockPos=none" in wait_attack_body
    assert "WISP_ATTACKABLE_FRAME=YES" in wait_attack_body
    capture_input_body = _bash_function_body(generated, "wisp_capture_attack_input_frame")
    assert 'index($0, "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=" id " ") == 1' in capture_input_body
    assert 'index($0, "gameTime=" game_time " ") > 0' in capture_input_body
    assert 'index($0, "playerTick=" player_tick " ") > 0' in capture_input_body
    assert "NR < limit" in capture_input_body
    assert 'WISP_ATTACK_INPUT_FRAME=YES id=$WISP_ATTACK_CAMERA_ID' in attack_body
    assert "hit_d <= 3.01" in capture_input_body
    extract_body = _bash_function_body(generated, "wisp_extract_attack_input_frame")
    assert '-i client-smoke/wisp-zap-video.mp4 -ss "$offset_s"' in extract_body
    assert "WISP_ATTACK_INPUT_FRAME_VIDEO_OFFSET=$offset_s" in extract_body
    assert "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME" in extract_body
    capture_call_index = generated.index('  wisp_capture_zap_frames "$zap_log_lines" "$wisp_id"\n')
    extract_call_index = generated.index("  wisp_extract_attack_input_frame\n", capture_call_index)
    verify_call_index = generated.index("  wisp_verify_attack_input_aim_capture\n", extract_call_index)
    assert capture_call_index < extract_call_index < verify_call_index
    assert attack_body.index("wisp_capture_attack_input_frame") < attack_body.index("WISP_ATTACK_INPUT_FRAME=YES") < attack_body.index("WISP_ATTACK_CAMERA_FRAME=YES")
    assert "for _ in $(seq 1 200)" not in wait_attack_body
    fast_command_body = _bash_function_body(generated, "wisp_fast_command")
    return_index = fast_command_body.index("xdotool key --clearmodifiers Return")
    assert 'open_delay="0.5"' in fast_command_body and 'type_delay="2"' in fast_command_body
    assert 'sleep "$open_delay"' in fast_command_body
    assert 'xdotool type --clearmodifiers --delay "$type_delay" "$command"' in fast_command_body
    assert "sleep " not in fast_command_body[return_index:], "fast commands must return immediately; callers wait for authoritative markers"
    assert "wisp_reanchor_player_for_capture" not in generated
    assert "wisp_verify_attack_input_aim_capture_with_retries" not in generated
    move_body = _bash_function_body(generated, "wisp_move_player_to_live_wisp")
    assert "run tp @p ~1.0 ~-1.0 ~ facing entity @s eyes" in move_body
    assert "run tp @p ~1.0 149 ~ facing entity @s eyes" not in move_body
    assert "run tp @p ~1.0 ~ ~ facing entity @s eyes" not in move_body
    assert "Teleported Dev to " in move_body
    assert "WISP_CAMERA_REANCHORED=LIVE_WISP_MELEE_POSITION" in move_body
    assert 'output="client-smoke/screenshots/78-wisp-aim-check.png"' in extract_body
    assert ' "$output"; then' in extract_body
    assert "wisp_capture 78-wisp-aim-check" not in generated
    assert "python tools/verify_wisp_zap_client.py --aim-only client-smoke/screenshots/78-wisp-aim-check.png" in generated, (
        "the extracted pre-attack video frame is the image checked by the aim verifier"
    )
    assert "Motion:[" not in generated
    assert "Successfully filled 81 block(s)" in generated
    assert "CustomName:'" in generated and "CustomNameVisible:0b" in generated
    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in generated
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" not in generated
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in generated
    assert "wisp_verify_aim_telemetry" in generated
    assert 'grep -F -- "$frame_camera_marker" | head -1' in generated
    assert 'grep -F -c -- "$frame_camera_marker" || true' in generated
    assert "d <= 3.0" in generated
    assert "capture_position_mode=LIVE_WISP" in generated
    assert "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION" in generated
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES'" in generated
    assert "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=$side_view_rendered_id" in generated
    assert "d <= 6.0" in generated
    assert "hit_d <= 3.01" in generated
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'" not in generated
    assert "execute as @p at @s anchored eyes facing entity" not in generated
    assert "run tp @p ~1.5" not in generated
    assert "run tp @s ~ ~ ~ ~ ~" not in generated

    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_TEST_TIME=MIDNIGHT" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_FRAME_CAPTURE=YES" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_EFFECT_EXPIRED=YES" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ALL_WISP_TEXTURES_NO_LOAD_ERRORS=PASS" in verify_wisp_zap_client.REQUIRED_STATUS
    assert verify_wisp_zap_client.AIM_SCREENSHOTS == ("78-wisp-aim-check.png",)
    assert verify_wisp_zap_client.BASELINE_SCREENSHOTS == ("79-wisp-before-a.png", "79-wisp-before-b.png")
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_CAMERA_TELEMETRY=PASS" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_RENDER_CAPTURE_TICK=" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM_TELEPORT=YES" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "~-1.5 ~ ~ facing entity" not in generated
    assert 'wisp_wait_for_fresh_render "$aim_log_lines"' in generated
    assert "wisp_wait_for_exact_aim_teleport" not in generated
    assert "/fill 8 148 8 12 148 14 minecraft:stone" not in generated
    assert generated.count("  send_chat() {") == 1
    wisp_body = _bash_function_body(generated, "wisp_command")
    assert "sleep 4" not in wisp_body
    assert "xdotool mousemove" not in generated
    assert "sleep 0.08" not in _bash_function_body(generated, "wisp_fast_command")
    syntax = subprocess.run(
        ["bash", "-n"], input=generated, text=True, capture_output=True, check=False
    )
    assert syntax.returncode == 0, syntax.stderr

    log = "\n".join((
        "TC_SMOKE_WISP_CHUNK_LOADED=YES",
        "TC_SMOKE_WISP_PLAYER_CREATIVE=YES",
        "WISP_ATTACK_PREPOSITIONED_WHILE_CREATIVE=YES",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=20 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=20",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=20 gameTime=518 playerTick=505 hitType=BLOCK targetId=-1 targetType=none blockPos=12,148,12",
        "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=17 tick=20 gameTime=518",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21 gameTime=519 playerTick=506 hitType=BLOCK targetId=-1 targetType=none blockPos=12,148,12",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=22",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22 gameTime=520 playerTick=507 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0",
        "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=17 targetType=thaumcraft:wisp hitType=ENTITY distance=1.0440 hitDistance=2.9 gameTime=520 blockPos=none tick=507 monotonicNs=900000000",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=1.0440 gameTime=520",
        "TC_SMOKE_WISP_CAMERA_TRACK=YES id=17",
        "TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES id=17",
        "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK monotonicNs=1000000000",
        "TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK cameraDistance=4.0 monotonicNs=1100000000",
        "TC_SMOKE_WISP_HURT=YES accepted=true source=17 attacker=1 amount=1.0 health=21.0",
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=23 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=23 gameTime=521 playerTick=508 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "TC_SMOKE_WISP_NAMED_SOURCE=YES",
        "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E createdAt=520 clientGameTime=520 monotonicNs=1200000000",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=1.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF createdAt=520 gameTime=521 ageTick=1",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=521 wispTick=24 ageTick=1 age=1.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=24 gameTime=521 playerTick=508 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=522 wispTick=25 ageTick=2 age=2.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=25 gameTime=522 playerTick=509 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=523 wispTick=26 ageTick=3 age=3.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=26 gameTime=523 playerTick=510 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=524 ageTicks=4",
    ))
    # These absolute line numbers correspond to the exact camera-track marker (7)
    # and the selected completed render frame (10) in the synthetic log above.
    status = "\n".join((
        "WISP_FIXTURE_PLATFORM_FILL=YES [Server thread] [Dev: Successfully filled 81 block(s)]",
        "WISP_FIXTURE_TELEPORT=YES [Server thread] Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_AIM=PREAIMED_SPAWN",
        "WISP_ZAP_TEST_TIME=MIDNIGHT",
        "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
        "WISP_ZAP_VIDEO_STARTED=YES firstFrameMonotonicNs=800000000 frameRate=60",
        "WISP_RENDER_PREATTACK_TRACKING=YES id=17 tick=20 line=7",
        "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
        "WISP_RENDER_FIRST_FRAME_ID=17",
        "WISP_RENDER_FIRST_FRAME_TICK=21",
        "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=10",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_RENDER_FIRST_FRAME=YES id=17 tick=21",
        "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=950",
        "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
        "WISP_ATTACK_CLICK_DISPATCHED=YES id=17",
        "WISP_ATTACK_CAMERA_REAIM=PREAIMED_NEXT_FRAME id=17 triggerTick=22 triggerGameTime=520 inputTick=22 inputGameTime=520",
        "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=22",
        "WISP_ATTACK_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1",
        "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
        "WISP_ATTACKABLE_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 position=PLATFORM gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_INPUT_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=900000000",
        "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=2.9",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_CAMERA_SIDE_VIEW=YES id=17 monotonicNs=1000000000",
        "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK cameraDistance=4.0 monotonicNs=1100000000",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 createdAt=520 monotonicNs=1200000000",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=1 wispTick=24 observedAtMonotonicNs=1400000000",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=2 wispTick=25 observedAtMonotonicNs=1500000000",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=3 wispTick=26 observedAtMonotonicNs=1600000000",
        "WISP_ZAP_EFFECT_EXPIRED=YES source=17 createdAt=520",
        "WISP_ZAP_VIDEO_STOPPED=YES",
        "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD",
        "WISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES source=17 frameIndex=19 monotonicNs=1116666667",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=1 wispTick=24",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=2 wispTick=25",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=3 wispTick=26",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=1 frameIndex=6 diff=18",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=2 frameIndex=12 diff=15",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=3 frameIndex=18 diff=12",
        "WISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES",
        "WISP_ATTACK_INPUT_FRAME_VIDEO_OFFSET=0.083333",
        "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES",
        "WISP_RENDER_CAPTURE_ID=17",
        "WISP_RENDER_CAPTURE_TICK=22",
        "WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME",
        "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=17 tick=22 distance=1.0440 aimDot=0.999",
        "WISP_CAMERA_TELEMETRY=PASS id=17 tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 distance=1.0440 aimDot=0.999",
        "WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "WISP_AIM_SCREENSHOT_VISIBLE=YES pixels=100 bbox=58,58,69,69",
        "WISP_BASELINE_SCREENSHOT_CLEAR=YES pixels=0",
        "WISP_BASELINE_SCREENSHOT_CLEAR=YES pixels=0",
        "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
        "WISP_ALL_WISP_TEXTURES_NO_LOAD_ERRORS=PASS",
        "WISP_ZAP_REAL_CLIENT=PASS",
        "WISP_ZAP_BASELINE_NOISE=3",
        "WISP_ZAP_TRANSIENT_PIXEL_DIFF=18",
    ))
    with tempfile.TemporaryDirectory(prefix="tc-wisp-zap-client-") as directory:
        root = Path(directory)
        for name in verify_wisp_zap_client.SCREENSHOTS:
            target = root / name
            if name in verify_wisp_zap_client.AIM_SCREENSHOTS:
                write_png(target)
            elif name in verify_wisp_zap_client.BASELINE_SCREENSHOTS:
                write_png(target, None)
            elif name in ("80-wisp-zap-frame-a.png", "81-wisp-zap-frame-b.png", "82-wisp-zap-frame-c.png"):
                write_bolt_png(target)
            elif name == verify_wisp_zap_client.BOLT_BASELINE_SCREENSHOT:
                # The latest pre-payload control can be off the moving Wisp; it must match the camera/scene.
                write_bolt_png(target, line=None, source_center=None)
            elif name == "83-wisp-zap-after-expiry.png":
                write_bolt_png(target, line=None)
            else:
                target.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        positive = root / "aim-positive.png"
        write_png(positive)
        aim_errors, count, bbox = verify_wisp_zap_client.verify_aim_screenshot(positive)
        assert aim_errors == [], aim_errors
        assert count == 100 and bbox == (58, 58, 69, 69), (count, bbox)

        # The billboard is not the entity hitbox. A visible source near the edge
        # of the central ROI is valid when the same-ID EntityHitResult and camera
        # telemetry prove the actual Survival click separately.
        off_center_visible = root / "aim-visible-off-center.png"
        write_png(off_center_visible, (560, 432, 572, 444))
        aim_errors, count, bbox = verify_wisp_zap_client.verify_aim_screenshot(off_center_visible)
        assert aim_errors == [] and count == 144 and bbox == (112, 112, 123, 123), (
            aim_errors, count, bbox
        )

        outside_roi = root / "aim-outside-roi.png"
        write_png(outside_roi, (432, 304, 444, 316))
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(outside_roi)
        assert aim_errors and count == 0, ("off-ROI pixels must not satisfy attack-frame visibility", aim_errors)

        invisible = root / "aim-invisible.png"
        write_png(invisible, None)
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(invisible)
        assert aim_errors and count == 0, ("vanilla crosshair alone must not satisfy Aer visibility", aim_errors)

        clean_baseline = root / "baseline-clean.png"
        write_png(clean_baseline, None)
        baseline_errors, baseline_count, _ = verify_wisp_zap_client.verify_baseline_screenshot(clean_baseline)
        assert baseline_errors == [] and baseline_count == 0, (baseline_errors, baseline_count)
        contaminated_baseline = root / "baseline-contaminated.png"
        write_png(contaminated_baseline)
        baseline_errors, baseline_count, _ = verify_wisp_zap_client.verify_baseline_screenshot(contaminated_baseline)
        assert baseline_errors and baseline_count == 100, (baseline_errors, baseline_count)

        # The third-person control image is a real PNG, not merely a PNG signature.
        baseline_path = root / verify_wisp_zap_client.BOLT_BASELINE_SCREENSHOT
        valid_baseline = baseline_path.read_bytes()
        baseline_path.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        invalid_baseline_errors = verify_wisp_zap_client.verify(log, status, root)
        assert any("cannot decode FXBolt image" in error for error in invalid_baseline_errors), invalid_baseline_errors
        baseline_path.write_bytes(valid_baseline)

        missing_capture_status = status.replace(
            "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD\n", "", 1
        )
        missing_capture_errors = verify_wisp_zap_client.verify(log, missing_capture_status, root)
        assert any("missing real-client status marker: WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD" in error
                   for error in missing_capture_errors), missing_capture_errors

        missing_rendered_side_view = status.replace(
            "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=17 beamYaw=0.0 yaw=50.0 pitch=0.0 cameraType=THIRD_PERSON_BACK cameraDistance=4.0 monotonicNs=1100000000\n",
            "",
            1,
        )
        missing_rendered_errors = verify_wisp_zap_client.verify(log, missing_rendered_side_view, root)
        assert any("missing real-client status marker: WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=" in error
                   for error in missing_rendered_errors), missing_rendered_errors

        missing_survival_status = status.replace(
            "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES\n", "", 1
        )
        log_only_survival_errors = verify_wisp_zap_client.verify(
            log + "\nTC_SMOKE_WISP_PLAYER_SURVIVAL=YES\n", missing_survival_status, root
        )
        assert any(
            "missing real-client status marker: TC_SMOKE_WISP_PLAYER_SURVIVAL=YES" in error
            for error in log_only_survival_errors
        ), log_only_survival_errors

        missing_survival_confirmation = status.replace(
            "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=950\n",
            "",
            1,
        )
        missing_confirmation_errors = verify_wisp_zap_client.verify(
            log, missing_survival_confirmation, root
        )
        assert any(
            "missing real-client status marker: WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=" in error
            for error in missing_confirmation_errors
        ), missing_confirmation_errors

        reversed_survival_status = status.replace(
            "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=950\n"
            "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES\n"
            "WISP_ATTACK_CLICK_DISPATCHED=YES id=17",
            "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES\n"
            "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=950\n"
            "WISP_ATTACK_CLICK_DISPATCHED=YES id=17",
            1,
        )
        reversed_survival_errors = verify_wisp_zap_client.verify(log, reversed_survival_status, root)
        assert any(
            "fresh server-confirmed Survival transition must precede the real click" in error
            for error in reversed_survival_errors
        ), reversed_survival_errors
        missing_named_source_errors = verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_NAMED_SOURCE=YES\n", "", 1), status, root
        )
        assert any(
            "missing real server world marker: TC_SMOKE_WISP_NAMED_SOURCE=YES" in error
            for error in missing_named_source_errors
        ), missing_named_source_errors

        missing_base_clear_errors = verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES\n", "", 1), status, root
        )
        assert any(
            "missing real server world marker: TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES" in error
            for error in missing_base_clear_errors
        ), missing_base_clear_errors

        reversed_source_checks = log.replace(
            "TC_SMOKE_WISP_NAMED_SOURCE=YES\nTC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES",
            "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES\nTC_SMOKE_WISP_NAMED_SOURCE=YES",
            1,
        )
        reversed_source_errors = verify_wisp_zap_client.verify(reversed_source_checks, status, root)
        assert any("Wisp named source was not resolved before base-cell clearance check" in error
                   for error in reversed_source_errors), reversed_source_errors

        errors = verify_wisp_zap_client.verify(log, status, root)
        assert errors == [], errors
        wrong_side_view_errors = verify_wisp_zap_client.verify(
            log.replace("yaw=50.0", "yaw=0.0", 1), status, root
        )
        assert any("camera is not side-on" in error for error in wrong_side_view_errors), wrong_side_view_errors
        misaligned_side_view_errors = verify_wisp_zap_client.verify(
            log.replace("beamYaw=0.0 yaw=50.0", "beamYaw=45.0 yaw=50.0", 1), status, root
        )
        assert any("camera is not side-on" in error for error in misaligned_side_view_errors), misaligned_side_view_errors
        wrapped_side_view_errors = verify_wisp_zap_client.verify(
            log.replace("beamYaw=0.0 yaw=50.0", "beamYaw=170.0 yaw=-140.0", 1), status, root
        )
        assert wrapped_side_view_errors == [], wrapped_side_view_errors
        first_person_errors = verify_wisp_zap_client.verify(
            log.replace("cameraType=THIRD_PERSON_BACK", "cameraType=FIRST_PERSON"), status, root
        )
        assert any("expected THIRD_PERSON_BACK" in error for error in first_person_errors), first_person_errors
        late_side_view_errors = verify_wisp_zap_client.verify(
            log.replace("monotonicNs=1000000000", "monotonicNs=1300000000", 1), status, root
        )
        assert any("side-view camera was selected after the client received the zap payload" in error
                   for error in late_side_view_errors), late_side_view_errors
        late_side_view_status = status.replace(
            "WISP_CAMERA_SIDE_VIEW=YES id=17 monotonicNs=1000000000\n", "", 1
        ).replace(
            "WISP_ATTACK_HURT_ACCEPTED=YES\n",
            "WISP_ATTACK_HURT_ACCEPTED=YES\nWISP_CAMERA_SIDE_VIEW=YES id=17 monotonicNs=1000000000\n",
            1,
        )
        late_side_view_status_errors = verify_wisp_zap_client.verify(
            log, late_side_view_status, root
        )
        assert any(
            "side-view camera must be selected after the real attack target and before accepted damage"
            in error
            for error in late_side_view_status_errors
        ), late_side_view_status_errors

        legacy_timestamp_status = status.replace(
            "observedAtMonotonicNs=", "observedAtNs="
        )
        legacy_timestamp_errors = verify_wisp_zap_client.verify(
            log, legacy_timestamp_status, root
        )
        assert any(
            "expected exactly three timestamped FXBolt render observations, got 0" in error
            for error in legacy_timestamp_errors
        ), legacy_timestamp_errors
        wrong_side_view_id_errors = verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=17", "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=18", 1),
            status, root
        )
        assert any("side-view camera and zap payload refer to different source entities" in error
                   for error in wrong_side_view_id_errors), wrong_side_view_id_errors

        # A client may skip the first expiry tick while stalled. Cleanup may be
        # observed later, but no FXBolt frame may render at age >= 4.
        late_cleanup_log = log.replace(
            "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=524 ageTicks=4",
            "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=525 ageTicks=5",
            1,
        )
        late_cleanup_errors = verify_wisp_zap_client.verify(late_cleanup_log, status, root)
        assert late_cleanup_errors == [], late_cleanup_errors

        early_cleanup_log = log.replace(
            "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=524 ageTicks=4",
            "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=523 ageTicks=3",
            1,
        )
        early_cleanup_errors = verify_wisp_zap_client.verify(early_cleanup_log, status, root)
        assert any("expired before the BETA26 particleMaxAge=3 boundary" in error
                   for error in early_cleanup_errors), early_cleanup_errors

        inconsistent_cleanup_log = late_cleanup_log.replace(
            "gameTime=525 ageTicks=5", "gameTime=525 ageTicks=4", 1
        )
        inconsistent_errors = verify_wisp_zap_client.verify(inconsistent_cleanup_log, status, root)
        assert any("gameTime and elapsed-age evidence disagree" in error
                   for error in inconsistent_errors), inconsistent_errors

        age_four_render_log = late_cleanup_log + "\n" + (
            "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 "
            "gameTime=524 wispTick=27 ageTick=4 age=4.0 points=9 "
            "cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF"
        )
        age_four_errors = verify_wisp_zap_client.verify(age_four_render_log, status, root)
        assert any("render frame at or after its BETA26 expiry boundary" in error
                   for error in age_four_errors), age_four_errors

        same_first_frame_log = log.replace(
            "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=21",
            "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22",
            1,
        ).replace(
            "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21",
            "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=22",
            1,
        ).replace(
            "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21 gameTime=519 playerTick=506 hitType=BLOCK",
            "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22 gameTime=520 playerTick=507 hitType=BLOCK",
            1,
        )
        same_first_frame_status = status.replace(
            "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
            "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=22",
            1,
        ).replace(
            "WISP_RENDER_FIRST_FRAME_TICK=21",
            "WISP_RENDER_FIRST_FRAME_TICK=22",
            1,
        ).replace(
            "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=21",
            "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22",
            1,
        ).replace(
            "WISP_RENDER_FIRST_FRAME=YES id=17 tick=21",
            "WISP_RENDER_FIRST_FRAME=YES id=17 tick=22",
            1,
        )
        same_frame_errors = verify_wisp_zap_client.verify(
            same_first_frame_log, same_first_frame_status, root
        )
        assert any(
            "pre-aimed attack sample must be later than the first completed Wisp frame" in error
            for error in same_frame_errors
        ), same_frame_errors
        for age, frame_index, difference in ((1, 6, 18), (2, 12, 15), (3, 18, 12)):
            frame_marker = (
                f"WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 "
                f"ageTick={age} frameIndex={frame_index} diff={difference}"
            )
            blank_frame_status = status.replace(frame_marker, frame_marker.rsplit("=", 1)[0] + "=0")
            assert verify_wisp_zap_client.verify(log, blank_frame_status, root), (
                f"age-{age} video frame must visibly differ from the post-expiry scene"
            )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_AFTER_PREAIMED_SPAWN=YES", ""), root
        ), "stale pre-spawn renderer callback must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL", ""), root
        ), "missing post-hit player stabilization must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("Base value for attribute Knockback Resistance for entity Dev set to 1.0", ""), status, root
        ), "missing server confirmation of knockback resistance must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN", ""), root
        ), "baseline must be explicitly captured before Wisp spawn"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_BASELINE_SCREENSHOT_CLEAR=YES", "", 1), root
        ), "both pre-spawn baseline screenshots must be checked"
        write_png(root / verify_wisp_zap_client.BASELINE_SCREENSHOTS[0])
        assert verify_wisp_zap_client.verify(log, status, root), "a Wisp-colored baseline must fail closed"
        write_png(root / verify_wisp_zap_client.BASELINE_SCREENSHOTS[0], None)
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=17 tick=20 gameTime=518", ""), status, root
        ), "missing pre-attack camera tracking must fail"
        pre_attack_status_lines = [
            line for line in status.splitlines()
            if line.startswith("WISP_RENDER_PREATTACK_TRACKING=YES ")
        ]
        first_frame_line_values = [
            line.split("=", 1)[1]
            for line in status.splitlines()
            if line.startswith("WISP_RENDER_FIRST_FRAME_LINE_NUMBER=")
        ]
        assert len(pre_attack_status_lines) == 1, pre_attack_status_lines
        assert len(first_frame_line_values) == 1, first_frame_line_values
        pre_attack_status = pre_attack_status_lines[0]
        first_frame_line_number = first_frame_line_values[0]

        missing_pre_attack_status = status.replace(pre_attack_status, "", 1)
        assert missing_pre_attack_status != status, "fixture mutation must remove the tracking marker"
        assert verify_wisp_zap_client.verify(
            log, missing_pre_attack_status, root
        ), "missing pre-attack tracking status must fail"

        late_pre_attack_status = (
            pre_attack_status.rsplit(" line=", 1)[0] + f" line={first_frame_line_number}"
        )
        late_tracking_status = status.replace(pre_attack_status, late_pre_attack_status, 1)
        assert late_tracking_status != status, "fixture mutation must move the tracking marker"
        assert verify_wisp_zap_client.verify(
            log, late_tracking_status, root
        ), "first tracked frame must occur after the pre-attack marker"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21", ""), status, root
        ), "missing AFTER_LEVEL frame completion must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21", ""), root
        ), "missing completed-frame status must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
                                "WISP_RENDER_FRAME_COMPLETE=YES id=18 tick=21"), root
        ), "completed-frame entity ID mismatch must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
                                "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=22"), root
        ), "completed-frame tick mismatch must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ZAP_AIM=PREAIMED_SPAWN", ""), root
        ), "missing pre-aimed spawn mode must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=", ""), root
        ), "missing exact first-frame camera sample must fail"
        first_frame_line = next(
            (
                index
                for index, line in enumerate(log.splitlines(), start=1)
                if line.startswith("TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21 ")
            ),
            None,
        )
        assert first_frame_line is not None, "synthetic first completed frame is missing"
        first_frame_cursor = f"WISP_RENDER_FIRST_FRAME_LINE_NUMBER={first_frame_line}"
        assert status.count(first_frame_cursor) == 1, (
            "synthetic first-frame cursor must point to exactly one completed frame",
            first_frame_line,
        )
        wrong_first_frame_cursor = status.replace(
            first_frame_cursor,
            f"WISP_RENDER_FIRST_FRAME_LINE_NUMBER={first_frame_line + 1}",
            1,
        )
        assert verify_wisp_zap_client.verify(log, wrong_first_frame_cursor, root), (
            "first-frame cursor must identify the exact consumed completed frame"
        )
        missing_first_frame_cursor = status.replace(first_frame_cursor, "", 1)
        assert verify_wisp_zap_client.verify(log, missing_first_frame_cursor, root), (
            "missing first-frame cursor must fail"
        )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME", ""), root
        ), "missing attack-input provenance for the post-hit aim capture must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1", ""), root
        ), "missing live attack camera frame must fail"
        live_camera_line = (
            "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 "
            "playerX=14.361148 playerY=149.0 playerZ=11.954202 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=14.361148 playerEyeY=150.62 playerEyeZ=11.954202 "
            "wispX=13.531006 wispY=152.136149 wispZ=11.861735 "
            "wispEyeX=13.531006 wispEyeY=152.901149 wispEyeZ=11.861735 "
            "wispHitboxCenterX=13.531006 wispHitboxCenterY=152.586149 wispHitboxCenterZ=11.861735 "
            "aimDot=0.9932838195434261 renderAimDot=0.9860156895460689 eyeAimDot=0.9976360381532536"
        )
        original_attack_camera_line = (
            "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 "
            "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
            "wispX=12.5 wispY=149.3 wispZ=11.5 "
            "wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 "
            "wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 "
            "aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978"
        )
        live_log = log.replace(original_attack_camera_line, live_camera_line, 1)
        live_log = live_log.replace(
            "distance=1.0440 hitDistance=2.9",
            "distance=3.2455 hitDistance=1.6816",
            1,
        ).replace(
            "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=1.0440 gameTime=520",
            "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=3.2455 gameTime=520",
            1,
        )
        fallback_status = status.replace(
            "WISP_ATTACK_CAMERA_REAIM=PREAIMED_NEXT_FRAME id=17 triggerTick=22 triggerGameTime=520 inputTick=22 inputGameTime=520",
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.361148, 149.000000, 11.954202\n"
            "WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING Teleported Dev to 14.361148, 149.000000, 11.954202",
        ).replace(original_attack_camera_line, live_camera_line)
        fallback_status = fallback_status.replace(
            "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 distance=1.0440 aimDot=0.999",
            "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 distance=3.2455 aimDot=0.9932838195434261",
        ).replace(
            "WISP_ATTACKABLE_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 position=PLATFORM gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
            "WISP_ATTACKABLE_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 position=LIVE_WISP gameTime=520 playerTick=507 distance=3.2455 aimDot=0.9932838195434261",
        ).replace(
            "WISP_ATTACK_INPUT_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
            "WISP_ATTACK_INPUT_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 gameTime=520 playerTick=507 distance=3.2455 aimDot=0.9932838195434261",
        ).replace(
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=2.9",
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=1.6816",
        ).replace(
            "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=17 tick=22 distance=1.0440 aimDot=0.999",
            "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=17 tick=22 distance=3.2455 aimDot=0.9932838195434261",
        )
        assert verify_wisp_zap_client.verify(live_log, fallback_status, root) == [], (
            "a server-confirmed live-Wisp re-anchor and a 1.68-block eye-to-hit point must pass "
            "even though the Wisp entity origin is 3.25 blocks away",
            verify_wisp_zap_client.verify(live_log, fallback_status, root),
        )

        drifted_camera_line = (
            "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 "
            "playerX=8.373697 playerY=149.0 playerZ=9.370661 playerYaw=19.60189 playerPitch=-4.050436 "
            "playerEyeX=8.373697 playerEyeY=150.62 playerEyeZ=9.370661 "
            "wispX=7.451660 wispY=150.489421 wispZ=12.449951 "
            "wispEyeX=7.294556 wispEyeY=151.377760 wispEyeZ=12.449951 "
            "wispHitboxCenterX=7.451660 wispHitboxCenterY=150.939421 wispHitboxCenterZ=12.449951 "
            "aimDot=0.9978064321315476 renderAimDot=0.9924763453707298 eyeAimDot=0.9875694611221074"
        )
        drifted_log = live_log.replace(live_camera_line, drifted_camera_line, 1)
        drifted_log = drifted_log.replace(
            "distance=3.2455 hitDistance=1.6816",
            "distance=3.5427 hitDistance=2.7458",
            1,
        ).replace(
            "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=3.2455 gameTime=520",
            "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=3.5427 gameTime=520",
            1,
        )
        drifted_status = fallback_status.replace(live_camera_line, drifted_camera_line)
        drifted_status = drifted_status.replace(
            "distance=3.2455 aimDot=0.9932838195434261",
            "distance=3.5427 aimDot=0.9978064321315476",
        ).replace(
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=1.6816",
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=2.7458",
            1,
        )
        assert verify_wisp_zap_client.verify(drifted_log, drifted_status, root) == [], (
            "the attack-frame camera may drift from the staging teleport; the same-ID hit point "
            "within 3.01 blocks, not entity-origin distance to the old teleport, proves reach",
            verify_wisp_zap_client.verify(drifted_log, drifted_status, root),
        )
        multi_reposition_status = fallback_status.replace(
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.361148, 149.000000, 11.954202",
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 10.249775, 149.000000, 9.106951\n"
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.361148, 149.000000, 11.954202",
            1,
        )
        assert verify_wisp_zap_client.verify(live_log, multi_reposition_status, root) == [], (
            "the verifier must associate a successful attack with the latest of multiple live repositions",
            verify_wisp_zap_client.verify(live_log, multi_reposition_status, root),
        )
        stale_first_match_latest_mismatch = fallback_status.replace(
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.361148, 149.000000, 11.954202",
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.361148, 149.000000, 11.954202\n"
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 14.500000, 149.000000, 11.954202",
            1,
        )
        latest_mismatch_errors = verify_wisp_zap_client.verify(live_log, stale_first_match_latest_mismatch, root)
        assert any("live-reposition attack re-aim coordinates differ from the server-confirmed teleport" in error
                   for error in latest_mismatch_errors), latest_mismatch_errors
        mismatched_reposition = fallback_status.replace(
            "Teleported Dev to 14.361148, 149.000000, 11.954202",
            "Teleported Dev to 14.500000, 149.000000, 11.954202",
            1,
        )
        mismatch_errors = verify_wisp_zap_client.verify(live_log, mismatched_reposition, root)
        assert any("live-reposition attack re-aim coordinates differ from the server-confirmed teleport" in error for error in mismatch_errors), mismatch_errors
        out_of_reach_live_log = live_log.replace(
            "hitDistance=1.6816",
            "hitDistance=3.02",
            1,
        )
        out_of_reach_live_status = fallback_status.replace(
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=1.6816",
            "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=3.02",
            1,
        )
        out_of_reach_errors = verify_wisp_zap_client.verify(
            out_of_reach_live_log, out_of_reach_live_status, root
        )
        assert any("vanilla eye-to-hit-point distance exceeds the 3-block melee reach" in error for error in out_of_reach_errors), out_of_reach_errors
        missing_attack_reaim_status = fallback_status.replace(
            "WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING Teleported Dev to 14.361148, 149.000000, 11.954202",
            "",
        )
        missing_attack_reaim_errors = verify_wisp_zap_client.verify(
            log, missing_attack_reaim_status, root
        )
        assert any(
            error == "missing provenance for the camera sample used by the real attack"
            for error in missing_attack_reaim_errors
        ), "missing attack-camera provenance must fail closed without raising"
        assert verify_wisp_zap_client.verify(
            log, fallback_status.replace("WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION", ""), root
        ), "live-reposition attack without its own teleport marker must fail"
        low_dot_log = "\n".join(
            line.replace("aimDot=0.999", "aimDot=0.8")
            if line.startswith("TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 ")
            else line
            for line in log.splitlines()
        )
        low_dot_status = "\n".join(
            line.replace("aimDot=0.999", "aimDot=0.8")
            if line.startswith(("WISP_ATTACK_CAMERA_FRAME=", "WISP_ATTACK_CAMERA_SAMPLE=", "WISP_ATTACKABLE_FRAME=", "WISP_ATTACK_INPUT_FRAME="))
            else line
            for line in status.splitlines()
        )
        assert verify_wisp_zap_client.verify(low_dot_log, low_dot_status, root) == [], (
            "the same-ID vanilla EntityHitResult and captured PNG, not the diagnostic aim-point dot, "
            "decide whether the attack frame is valid"
        )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=22",
                                "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=23"), root
        ), "attack camera frame tick mismatch must fail"
        misordered_lines = log.splitlines()
        attack_line_index = next(i for i, line in enumerate(misordered_lines) if line.startswith("TC_SMOKE_WISP_ATTACK_INPUT=YES"))
        attack_frame_index = next(i for i, line in enumerate(misordered_lines) if line.startswith("TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22 gameTime="))
        misordered_lines.insert(attack_frame_index, misordered_lines.pop(attack_line_index))
        assert verify_wisp_zap_client.verify("\\n".join(misordered_lines), status, root), (
            "real click before the matching completed camera frame must fail"
        )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_CAMERA_CAPTURE_TELEMETRY=PASS", ""), root
        ), "missing post-hit camera telemetry gate must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
                        "hitType=BLOCK targetId=-1 targetType=none blockPos=12,148,12", 1), status, root
        ), "a BLOCK AFTER_LEVEL frame must never authorize the click"
        assert verify_wisp_zap_client.verify(
            log.replace("hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
                        "hitType=ENTITY targetId=18 targetType=thaumcraft:wisp blockPos=none", 1), status, root
        ), "an EntityHitResult for another entity must never authorize the click"
        assert verify_wisp_zap_client.verify(
            log.replace("tick=22 gameTime=520 playerTick=507 hitType=ENTITY",
                        "tick=22 gameTime=517 playerTick=504 hitType=ENTITY", 1), status, root
        ), "a click more than one game tick after the actual hit-result frame must fail"
        one_player_tick_lag = log.replace(
            "hitDistance=2.9 gameTime=520 blockPos=none tick=507 monotonicNs=900000000",
            "hitDistance=2.9 gameTime=520 blockPos=none tick=508 monotonicNs=900000000",
            1,
        )
        assert verify_wisp_zap_client.verify(one_player_tick_lag, status, root) == [], (
            "the immediately preceding same-game-time frame must be accepted when one client player tick behind"
        )
        two_player_ticks_lag = log.replace(
            "hitDistance=2.9 gameTime=520 blockPos=none tick=507 monotonicNs=900000000",
            "hitDistance=2.9 gameTime=520 blockPos=none tick=509 monotonicNs=900000000",
            1,
        )
        assert verify_wisp_zap_client.verify(two_player_ticks_lag, status, root), (
            "a completed frame two player ticks behind the InputEvent must fail closed"
        )
        assert verify_wisp_zap_client.verify(
            log.replace("tick=22 gameTime=520 playerTick=507 hitType=ENTITY",
                        "tick=22 gameTime=520 playerTick=504 hitType=ENTITY", 1), status, root
        ), "a click more than one player tick after the actual hit-result frame must fail"
        stale_hit_log = log.replace(
            "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=20 gameTime=518 playerTick=505 hitType=BLOCK targetId=-1 targetType=none blockPos=12,148,12",
            "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=20 gameTime=518 playerTick=505 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
            1,
        )
        assert verify_wisp_zap_client.verify(stale_hit_log, status, root) == [], (
            "a stale pre-tracking Wisp hit frame must not shadow the unique frame at the actual click tick"
        )
        duplicate_input_frame_log = log.replace(
            "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true",
            "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22 gameTime=520 playerTick=507 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none\n"
            "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true",
            1,
        )
        duplicate_errors = verify_wisp_zap_client.verify(duplicate_input_frame_log, status, root)
        assert any("no completed frame exposed the live Wisp" in error for error in duplicate_errors), (
            "duplicate same-tick hit frames must fail closed"
        )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACKABLE_FRAME=YES", "WISP_ATTACKABLE_FRAME=NO"), root
        ), "missing click-trigger frame status must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_INPUT_FRAME=YES", "WISP_ATTACK_INPUT_FRAME=NO"), root
        ), "missing input-correlated frame status must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_CAMERA=YES id=17", "TC_SMOKE_WISP_CAMERA=YES id=18"), status, root
        ), "camera sample from another Wisp must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21", "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=22"), status, root
        ), "camera and renderer ticks must match"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21", ""), status, root
        ), "missing selected-frame renderer callback must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_CAPTURE_TICK=22", "WISP_RENDER_CAPTURE_TICK=23"), root
        ), "post-hit capture tick must match its copied camera sample"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_CAPTURE_ID=17", "WISP_RENDER_CAPTURE_ID=18"), root
        ), "post-hit capture ID must match the rendered Wisp"
        assert verify_wisp_zap_client.verify(
            log.replace("target=1", "target=2", 1), status, root
        ), "mismatched server target must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("playerY=149.0", "playerY=138.19"), root
        ), "player falling below the verified platform must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_CAMERA_TELEMETRY=PASS id=17 tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 distance=1.0440 aimDot=0.999",
                                "WISP_CAMERA_TELEMETRY=PASS id=17 tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 distance=1.0440 aimDot=0.8"), root
        ), "initial camera not facing the Wisp must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("wispHitboxCenterZ=11.5 aimDot=0.999",
                                "wispHitboxCenterZ=11.5 aimDot=0.8"), root
        ), "post-hit capture camera not facing the live Wisp must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("playerEyeZ=10.5 wispX=12.5",
                                "playerEyeZ=10.5 wispX=40.5"), root
        ), "post-hit capture camera outside the Wisp tracking radius must fail"
        assert not verify_wisp_zap_client.verify(
            log.replace("hitType=ENTITY distance=1.0440 hitDistance=2.9", "hitType=ENTITY distance=3.78 hitDistance=2.9", 1), status, root
        ), "entity-origin distance may exceed three blocks when the actual hit point is in reach"
        assert verify_wisp_zap_client.verify(
            log.replace("hitDistance=2.9", "hitDistance=3.2", 1), status, root
        ), "actual eye-to-hit-point distance beyond melee reach must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=900000000", "WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=900000001"), root
        ), "video frame timestamp must match the real InputEvent"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_SUPPORT_BLOCK=YES", ""), status, root
        ), "missing exact support block must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("blend=SRC_ALPHA_ONE", "blend=ALPHA"), status, root
        ), "non-additive renderer must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ZAP_TRANSIENT_PIXEL_DIFF=18", "WISP_ZAP_TRANSIENT_PIXEL_DIFF=10"), root
        ), "pixel delta below baseline noise must fail"
        write_png(root / verify_wisp_zap_client.AIM_SCREENSHOTS[0], None)
        assert verify_wisp_zap_client.verify(log, status, root), "invisible Wisp screenshot must fail"
        (root / verify_wisp_zap_client.SCREENSHOTS[-1]).unlink()
        assert verify_wisp_zap_client.verify(log, status, root), "missing screenshot must fail"

    wisp_entity = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/entity/WispEntity.java"
    ).read_text(encoding="utf-8")
    assert "TC_SMOKE_WISP_HURT=YES accepted=" in wisp_entity
    assert wisp_entity.index("TC_SMOKE_WISP_HURT=YES accepted=") < wisp_entity.index("TC_SMOKE_WISP_TARGET_SET=YES")
    attack_probe = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispAttackSmokeProbe.java"
    ).read_text(encoding="utf-8")
    assert "InputEvent.InteractionKeyMappingTriggered" in attack_probe
    assert "BlockHitResult" in attack_probe
    assert "gameTime=" in attack_probe
    assert "blockPos=" in attack_probe
    assert "event.isAttack()" in attack_probe
    assert "minecraft.hitResult" in attack_probe
    assert "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=" in attack_probe
    assert "AttackEntityEvent" in attack_probe
    assert "TC_SMOKE_WISP_CLIENT_ATTACK=YES" in attack_probe
    assert "ClientTickEvent.Pre" in attack_probe
    assert 'private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));' in attack_probe
    assert 'private static final String SMOKE_TARGET_NAME = "tc_wisp_zap_smoke";' in attack_probe
    assert "if (!ENABLED)" in attack_probe
    assert "getEntitiesOfClass( WispEntity.class," in " ".join(attack_probe.split())
    assert "candidate.hasCustomName()" in attack_probe
    assert "SMOKE_TARGET_NAME.equals(candidate.getCustomName().getString())" in attack_probe
    assert "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=" in attack_probe
    assert "if (trackedWispId < 0)" in attack_probe
    assert "trackedWispId = smokeTarget.getId()" in attack_probe
    assert "trackedWispId = wisp.getId()" in attack_probe
    assert "minecraft.level.getEntity(trackedWispId)" in attack_probe
    assert "EntityAnchorArgument.Anchor.EYES" in attack_probe
    assert "cameraAimPoint(wisp)" in attack_probe
    assert "static Vec3 cameraAimPoint(WispEntity wisp)" in attack_probe
    assert "bounds.minY + (bounds.maxY - bounds.minY) * 0.25D" in attack_probe
    assert "TC_SMOKE_WISP_CAMERA_TRACK=YES id=" in attack_probe
    assert "aimCameraAtWisp = false" in attack_probe
    assert "TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES id=" in attack_probe
    assert "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=" in attack_probe
    assert "TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES id=" in attack_probe
    assert "minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK)" in attack_probe
    assert "Vec3 toWisp = wisp.position().subtract(minecraft.player.position());" in attack_probe
    assert "Math.atan2(-toWisp.x, toWisp.z)" in attack_probe
    assert "SIDE_VIEW_OFFSET_DEGREES = 50.0F" in attack_probe
    assert "sideViewYaw = Mth.wrapDegrees(beamYaw + SIDE_VIEW_OFFSET_DEGREES);" in attack_probe
    assert "RenderLevelStageEvent.Stage.AFTER_ENTITIES" in attack_probe
    assert "MIN_SIDE_VIEW_CAMERA_DISTANCE = 3.0D" in attack_probe
    assert "TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES" in attack_probe
    assert "minecraft.player.setYRot(sideViewYaw)" in attack_probe
    assert "minecraft.player.setYRot(90.0F)" not in attack_probe
    assert ' + " beamYaw=" + beamYawForSideView' in attack_probe
    assert "System.nanoTime()" in attack_probe
    beam_offset = math.radians(50.0)
    camera_distance = 3.0
    source_distance = 2.8
    horizontal_fov = 2.0 * math.degrees(math.atan(math.tan(math.radians(70.0 / 2.0)) * 4.0 / 3.0))
    apparent_source_angle = math.degrees(math.atan2(source_distance * math.sin(beam_offset), camera_distance + source_distance * math.cos(beam_offset)))
    source_x = verify_wisp_zap_client.VIEWPORT_WIDTH / 2.0 - math.tan(math.radians(apparent_source_angle)) / math.tan(math.radians(horizontal_fov / 2.0)) * verify_wisp_zap_client.VIEWPORT_WIDTH / 2.0
    roi_width, _, roi_left, _ = verify_wisp_zap_client.BOLT_ROI
    assert roi_left <= source_x <= roi_left + roi_width, (source_x, horizontal_fov)
    assert roi_left <= verify_wisp_zap_client.VIEWPORT_WIDTH - source_x <= roi_left + roi_width
    assert math.sin(beam_offset) >= 0.65, math.sin(beam_offset)
    assert "hitDistance=" in attack_probe
    assert "minecraft.player.getEyePosition().distanceTo(hit.getLocation())" in attack_probe
    assert "monotonicNs=" in attack_probe
    assert "event.setCanceled" not in attack_probe
    renderer = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispZapClientEffects.java"
    ).read_text(encoding="utf-8")
    assert "TC_SMOKE_WISP_TARGET_SET=YES" in wisp_entity
    assert "TC_SMOKE_WISP_HURT=YES accepted=" in wisp_entity
    assert "TC_SMOKE_WISP_ZAP_SENT=YES" in wisp_entity
    assert "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES" in renderer
    assert "TC_SMOKE_WISP_ZAP_RENDERED=YES" in renderer
    renderer_registration = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispRendererRegistration.java"
    ).read_text(encoding="utf-8")
    assert "bus = EventBusSubscriber.Bus.MOD" in renderer_registration
    assert "TC_SMOKE_WISP_RENDERED=YES" in renderer_registration
    assert "WispRenderSmokeProbe.recordRendered(entity.getId(), entity.tickCount)" in renderer_registration
    assert "import java.util.WeakHashMap;" in renderer_registration
    assert "Map<WispEntity, Integer> SMOKE_RENDERED_TICK = new WeakHashMap<>()" in renderer_registration
    assert "SMOKE_RENDERED_TICK.put(entity, entity.tickCount)" in renderer_registration
    assert "previousTick.intValue() != entity.tickCount" in renderer_registration
    assert "import net.minecraft.client.Minecraft;" in renderer_registration
    assert "import net.minecraft.world.phys.Vec3;" in renderer_registration
    assert "TC_SMOKE_WISP_CAMERA=YES id=" in renderer_registration
    assert "player.getEyePosition(partialTick)" in renderer_registration
    assert "entity.getEyePosition(partialTick)" in renderer_registration
    assert "Vec3 wispRenderOrigin = new Vec3(entity.getX(), entity.getY(), entity.getZ());" in renderer_registration
    assert "entity.getBoundingBox().getCenter()" in renderer_registration
    assert "WispAttackSmokeProbe.cameraAimPoint(entity)" in renderer_registration
    assert "wispAimPointY=" in renderer_registration
    assert "player.getViewVector(partialTick).dot(toWispAimPoint.normalize())" in renderer_registration
    assert "player.getViewVector(partialTick).dot(toWispRenderOrigin.normalize())" in renderer_registration
    assert "player.getViewVector(partialTick).dot(toWisp.normalize())" not in renderer_registration
    assert "aimTarget=render_safe_quarter" in renderer_registration
    assert "aimTarget=render_origin" not in renderer_registration
    assert "eyeAimDot=" in renderer_registration
    assert "player.getViewVector(partialTick)" in renderer_registration
    assert "aimDot=" in renderer_registration
    assert renderer_registration.index("TC_SMOKE_WISP_CAMERA=YES id=") < renderer_registration.index("TC_SMOKE_WISP_RENDERED=YES id="), (
        "camera telemetry must be written before the fresh-render marker"
    )
    assert "SMOKE_RENDERED.add(entity.getId())" not in renderer_registration
    assert 'textures/misc/auranodes.png' in renderer_registration
    assert 'WispRenderTypes.additive("thaumcraft:wisp_particle_additive", PARTICLE_ATLAS)' in renderer_registration
    assert 'WispRenderTypes.additive("thaumcraft:wisp_node_additive", NODE_ATLAS)' in renderer_registration
    assert "buffers.getBuffer(NODE_LAYER)" in renderer_registration
    assert "LegacyWispRenderAtlas.OUTER_BASE_FRAME" in renderer_registration
    assert "LegacyWispRenderAtlas.INNER_BASE_FRAME" in renderer_registration
    assert "LegacyWispRenderAtlas.ASPECT_BASE_FRAME" in renderer_registration
    assert "LegacyWispRenderAtlas.PARTICLE_GRID" in renderer_registration
    assert "LegacyWispRenderAtlas.NODE_GRID" in renderer_registration
    assert "LegacyWispRenderAtlas.frameUv(frame, gridSize)" in renderer_registration
    assert "frame / 64" not in renderer_registration
    assert "1.0F / 64.0F" not in renderer_registration
    assert "WispRenderTypes.additive(PARTICLE_ATLAS)" not in renderer_registration
    assert "RenderType.entityTranslucent(PARTICLE_ATLAS)" not in renderer_registration
    render_probe = Path(
        "src/neoforge/java/dev/ehtid/thaumcraft/client/WispRenderSmokeProbe.java"
    ).read_text(encoding="utf-8")
    assert "bus = EventBusSubscriber.Bus.GAME" in render_probe
    assert "RenderLevelStageEvent.Stage.AFTER_LEVEL" in render_probe
    assert "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=" in render_probe
    assert "minecraft.hitResult" in render_probe
    assert "hitType=" in render_probe
    assert "targetType=" in render_probe
    assert "blockPos=" in render_probe
    assert "gameTime=" in render_probe
    assert ".setTransparencyState(LIGHTNING_TRANSPARENCY)" in renderer_registration
    assert ".setWriteMaskState(COLOR_WRITE)" in renderer_registration
    assert ".setCullState(NO_CULL)" in renderer_registration
    assert ".setDepthTestState(LEQUAL_DEPTH_TEST)" in renderer_registration
    assert ".setTransparencyState(LIGHTNING_TRANSPARENCY)" in renderer
    assert ".setWriteMaskState(COLOR_WRITE)" in renderer
    assert "buffers.endBatch(BETA26_ADDITIVE_BOLT)" in renderer

    # Verify the committed node atlas is the exact pinned BETA26 Git blob.
    node_atlas = Path(
        "src/main/resources/assets/thaumcraft/textures/misc/auranodes.png"
    ).read_bytes()
    assert node_atlas[:8] == b"\x89PNG\r\n\x1a\n"
    width, height = struct.unpack(">II", node_atlas[16:24])
    assert (width, height) == (2048, 2048), (width, height)
    blob_sha = hashlib.sha1(
        b"blob " + str(len(node_atlas)).encode("ascii") + b"\0" + node_atlas
    ).hexdigest()
    assert blob_sha == "411d52fefb272574dfbb0466e3437e377e2cb907", blob_sha
    assert hashlib.sha256(node_atlas).hexdigest() == (
        "3b46124e4a48422bbb64d7948707c3d4422e9399970b7637e255726e5156bda4"
    )

    print("WISP_ZAP_CLIENT_SMOKE_TESTS=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
