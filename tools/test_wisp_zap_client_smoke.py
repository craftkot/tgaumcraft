#!/usr/bin/env python3
"""Offline generator/verifier regressions for the real Wisp zap client smoke."""
from pathlib import Path
import os
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
    """The same-ID vanilla hit result is authoritative; tolerate cosine float roundoff."""
    body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    function = "  wisp_wait_for_attack_frame() {\n" + body + "\n  }\n"

    def run_case(dot: str) -> tuple[int, str, list[str], list[str]]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-attack-dot-") as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            clicker = bin_dir / "xdotool"
            clicker.write_text(
                "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$CLICK_LOG\"\nexit 0\n",
                encoding="utf-8",
            )
            clicker.chmod(0o755)

            client_dir = root / "client-smoke"
            client_dir.mkdir()
            camera_line = (
                "TC_SMOKE_WISP_CAMERA=YES id=106 aimTarget=render_safe_quarter tick=1 "
                "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
                "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
                "wispX=12.5 wispY=149.3 wispZ=11.5 "
                "wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 "
                "wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 "
                f"aimDot={dot} renderAimDot=0.9957938147422856 eyeAimDot=0.9479570469041974"
            )
            frame_line = (
                "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=106 tick=1 gameTime=520 playerTick=507 "
                "hitType=ENTITY targetId=106 targetType=thaumcraft:wisp blockPos=none"
            )
            (client_dir / "client.log").write_text(
                camera_line + "\n" + frame_line + "\n", encoding="utf-8"
            )
            status_path = root / "client-smoke-status.txt"
            status_path.write_text("", encoding="utf-8")
            click_log = root / "clicks.log"
            env = os.environ.copy()
            env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
            env["CLICK_LOG"] = str(click_log)
            harness = (
                "set -euo pipefail\n"
                + function
                + "\nwisp_wait_for_attack_frame 0 106 PLATFORM 1\n"
            )
            result = subprocess.run(
                ["bash", "-c", harness],
                cwd=root,
                text=True,
                capture_output=True,
                env=env,
                check=False,
            )
            status = status_path.read_text(encoding="utf-8").splitlines()
            clicks = click_log.read_text(encoding="utf-8").splitlines() if click_log.exists() else []
            return result.returncode, result.stdout + result.stderr, status, clicks

    rc, output, status, clicks = run_case("1.000000011613918")
    assert rc == 0, (rc, output, status)
    assert any(line.startswith("WISP_ATTACKABLE_FRAME=YES id=106") for line in status), status
    assert clicks == ["click 1"], clicks

    rc, output, status, clicks = run_case("1.0002")
    assert rc != 0, (rc, output, status)
    assert any("WISP_ATTACKABLE_FRAME_CAMERA_REJECTED" in line for line in status), status
    assert clicks == ["click 1"], clicks




def _test_attack_input_frame_correlation(generated: str) -> None:
    """The actual click must bind to its same-tick AFTER_LEVEL frame, not an earlier Wisp hit."""
    body = _bash_function_body(generated, "wisp_capture_attack_input_frame")
    function = "  wisp_capture_attack_input_frame() {\n" + body + "\n  }\n"
    attack_input = (
        "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=106 "
        "targetType=thaumcraft:wisp hitType=ENTITY distance=1.0440 "
        "gameTime=553 blockPos=none tick=518"
    )

    def camera_line(tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_CAMERA=YES id=106 aimTarget=render_safe_quarter tick={tick} "
            "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
            "wispX=12.5 wispY=149.3 wispZ=11.5 "
            "wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 "
            "wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 "
            "aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978"
        )

    def frame_line(tick: int, game_time: int, player_tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=106 tick={tick} "
            f"gameTime={game_time} playerTick={player_tick} "
            "hitType=ENTITY targetId=106 targetType=thaumcraft:wisp blockPos=none"
        )

    def run_case(lines: list[str]) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-input-frame-") as directory:
            root = Path(directory)
            client_dir = root / "client-smoke"
            client_dir.mkdir()
            (client_dir / "client.log").write_text("\n".join(lines) + "\n", encoding="utf-8")
            status_path = root / "client-smoke-status.txt"
            status_path.write_text("", encoding="utf-8")
            env = os.environ.copy()
            env["ATTACK_INPUT"] = attack_input
            harness = (
                "set -euo pipefail\n"
                + function
                + '\nwisp_capture_attack_input_frame "$ATTACK_INPUT" 106 PLATFORM\n'
                + 'echo "CAPTURED_TICK=$WISP_ATTACK_CAMERA_TICK GAME_TIME=$WISP_ATTACK_CAMERA_GAME_TIME PLAYER_TICK=$WISP_ATTACK_CAMERA_PLAYER_TICK DISTANCE=$WISP_ATTACK_CAMERA_DISTANCE"\n'
            )
            result = subprocess.run(
                ["bash", "-c", harness],
                cwd=root,
                text=True,
                capture_output=True,
                env=env,
                check=False,
            )
            status = status_path.read_text(encoding="utf-8")
            return result.returncode, result.stdout + result.stderr, status

    base_lines = [
        camera_line(0),
        frame_line(0, 546, 511),
        camera_line(5),
        frame_line(5, 551, 516),
        camera_line(7),
        frame_line(7, 553, 518),
        attack_input,
    ]
    rc, output, status = run_case(base_lines)
    assert rc == 0, (rc, output, status)
    assert "CAPTURED_TICK=7 GAME_TIME=553 PLAYER_TICK=518 DISTANCE=1.0440" in output, output

    duplicate_lines = base_lines[:-1] + [frame_line(7, 553, 518), attack_input]
    rc, output, status = run_case(duplicate_lines)
    assert rc != 0, (rc, output, status)
    assert "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=2" in status, status

    stale_only_lines = base_lines[:4] + [attack_input]
    rc, output, status = run_case(stale_only_lines)
    assert rc != 0, (rc, output, status)
    assert "WISP_ATTACK_INPUT_FRAME_MATCH_COUNT=0" in status, status

def _test_first_frame_cursor_is_exclusive(generated: str) -> None:
    """The attack watcher must ignore the visibility frame it has already consumed."""
    attack_body = _bash_function_body(generated, "wisp_attack_live_wisp")
    expected = 'frame_log_lines="$WISP_RENDER_FIRST_FRAME_LINE_NUMBER"'
    assert expected in attack_body, "attack watcher must start after the consumed visibility frame"
    assert 'frame_log_lines="$((WISP_RENDER_FIRST_FRAME_LINE_NUMBER - 1))"' not in attack_body

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
            "#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$CLICK_LOG\"\nexit 0\n",
            encoding="utf-8",
        )
        clicker.chmod(0o755)
        client_dir = root / "client-smoke"
        client_dir.mkdir()
        # The first completed frame is stale by the time the attack watcher starts.
        # Only the later frame is eligible to dispatch the click.
        (client_dir / "client.log").write_text(
            "\n".join((camera_line(1), frame_line(1), camera_line(2), frame_line(2))) + "\n",
            encoding="utf-8",
        )
        status_path = root / "client-smoke-status.txt"
        status_path.write_text("", encoding="utf-8")
        click_log = root / "clicks.log"
        env = os.environ.copy()
        env["PATH"] = str(bin_dir) + os.pathsep + env.get("PATH", "")
        env["CLICK_LOG"] = str(click_log)
        harness = (
            "set -euo pipefail\n"
            + function
            + "\nwisp_wait_for_attack_frame 2 106 PLATFORM 1\n"
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
        assert clicks == ["click 1"], clicks


def _test_first_frame_telemetry_uses_selected_frame(generated: str) -> None:
    """Ignore the pre-render tracking sample and select the exact completed-frame camera tick."""
    body = _bash_function_body(generated, "wisp_verify_aim_telemetry")
    function = "  wisp_verify_aim_telemetry() {\n" + body + "\n  }\n"

    def camera_line(tick: int) -> str:
        return (
            f"TC_SMOKE_WISP_CAMERA=YES id=157 aimTarget=render_safe_quarter tick={tick} "
            "playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 "
            "playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 "
            "wispX=12.5 wispY=149.3 wispZ=11.5 "
            "wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 "
            "wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 "
            "aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978"
        )

    def run_case(selected_samples: int) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-first-frame-telemetry-") as directory:
            root = Path(directory)
            client_dir = root / "client-smoke"
            client_dir.mkdir()
            log_lines = [
                camera_line(0),
                "TC_SMOKE_WISP_RENDERED=YES id=157 aspect=aer tick=3",
            ]
            if selected_samples >= 1:
                log_lines.append(camera_line(3))
            if selected_samples >= 2:
                log_lines.append(camera_line(3))
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
                camera_line(124),
            ]
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

    rc, output, status = run_case(0)
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_FIRST_FRAME_SAMPLE_COUNT=0 expected=1 id=157 tick=3" in status, status

    rc, output, status = run_case(2)
    assert rc != 0, (rc, output, status)
    assert "WISP_CAMERA_FIRST_FRAME_SAMPLE_COUNT=2 expected=1 id=157 tick=3" in status, status


def _test_capture_reanchor_retry(generated: str) -> None:
    """Post-hit capture retries while the client tracker follows the moving Wisp."""
    body = _bash_function_body(generated, "wisp_capture_aim_after_hit_with_retries")
    function = "  wisp_capture_aim_after_hit_with_retries() {\n" + body + "\n  }\n"

    def run_case(success_on: int) -> tuple[int, str, str]:
        with tempfile.TemporaryDirectory(prefix="tc-wisp-reaim-retry-") as temporary:
            root = Path(temporary)
            (root / "client-smoke").mkdir()
            (root / "client-smoke" / "client.log").write_text("", encoding="utf-8")
            script = (
                "set -euo pipefail\n"
                'WISP_CAMERA_REANCHOR_LINE_NUMBER=""\n'
                "reanchor_calls=0\n"
                "capture_calls=0\n"
                "wisp_reanchor_player_for_capture() { reanchor_calls=$((reanchor_calls + 1)); WISP_CAMERA_REANCHOR_LINE_NUMBER=$((100 + reanchor_calls)); }\n"
                'wisp_capture_aim_after_hit() { capture_calls=$((capture_calls + 1)); [ "$1" = "$WISP_CAMERA_REANCHOR_LINE_NUMBER" ] || return 90; if [ "$capture_calls" -ge '
                + str(success_on)
                + ' ]; then return 0; fi; return 1; }\n'
                + function
                + "if wisp_capture_aim_after_hit_with_retries; then result=0; else result=$?; fi\n"
                + 'echo "RESULT=$result REANCHORS=$reanchor_calls CAPTURES=$capture_calls"\n'
            )
            process = subprocess.run(
                ["bash", "-c", script],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            status_path = root / "client-smoke-status.txt"
            status = status_path.read_text(encoding="utf-8") if status_path.exists() else ""
            return process.returncode, process.stdout, status

    rc, output, status = run_case(2)
    assert rc == 0, (rc, output)
    assert "RESULT=0 REANCHORS=2 CAPTURES=2" in output, output
    assert "WISP_RENDER_CAPTURE_RETRY=1" in status, status
    assert "WISP_RENDER_CAPTURE_REANCHOR_ATTEMPT=2" in status, status

    rc, output, status = run_case(99)
    assert rc == 0, (rc, output)
    assert "RESULT=1 REANCHORS=3 CAPTURES=3" in output, output
    assert "WISP_RENDER_CAPTURE_RETRIES_EXHAUSTED=YES" in status, status
def _test_wisp_zap_frame_capture_sequence(generated: str) -> None:
    names = (
        "wisp_wait_for_zap_frame",
        "wisp_wait_for_completed_wisp_frame",
        "wisp_capture_zap_frames",
    )
    functions = [
        "  " + name + "() {\n" + _bash_function_body(generated, name) + "\n  }\n"
        for name in names
    ]
    wait_body = _bash_function_body(generated, "wisp_wait_for_zap_frame")
    executable_wait_body = "\n".join(
        line.split("#", 1)[0] for line in wait_body.splitlines()
    )
    assert "tail -f" not in executable_wait_body, "the live marker watcher must not leave a follow process blocking command substitution"
    assert "timeout 5s bash -c" not in executable_wait_body, "the live marker watcher must not wait for a timeout after finding a frame"
    assert "for attempt in $(seq 1 1000)" in wait_body, "the marker watcher must use bounded non-blocking polling"
    log_lines = [
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E createdAt=100 clientGameTime=520",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=1.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF createdAt=100 gameTime=521 ageTick=1",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=521 wispTick=24 ageTick=1 age=1.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=24 gameTime=521 playerTick=508 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=522 wispTick=25 ageTick=2 age=2.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=25 gameTime=522 playerTick=509 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=100 gameTime=523 wispTick=26 ageTick=3 age=3.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=26 gameTime=523 playerTick=510 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
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
            "python() { printf 'WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=1 wispTick=24\\nWISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=2 wispTick=25\\nWISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=100 ageTick=3 wispTick=26\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=1 frameIndex=6 diff=18\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=2 frameIndex=12 diff=15\\nWISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=100 ageTick=3 frameIndex=18 diff=12\\nWISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES\\n'; }\n"
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


def _test_video_frame_extraction() -> None:
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
            "WISP_ZAP_VIDEO_STARTED=YES firstFrameEpochNs=1000000000 frameRate=60",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=1 wispTick=24 observedAtNs=1100000000",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=2 wispTick=25 observedAtNs=1200000000",
            "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=100 ageTick=3 wispTick=26 observedAtNs=1300000000",
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
        extracted = status_file.read_text(encoding="utf-8")
        assert extracted.count("WISP_ZAP_FRAME_CAPTURE=YES") == 3, extracted
        assert extracted.count("WISP_ZAP_VIDEO_FRAME_INDEX=YES") == 3, extracted
        assert [f"ageTick={age} " in extracted for age in (1, 2, 3)] == [True, True, True], extracted


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

    for y in range(384, 417):
        for x in range(496, 529):
            if (x - 512) ** 2 + (y - 400) ** 2 <= 16 ** 2:
                put(x, y, orb)

    if line is not None:
        x0, y0, x1, y1 = line
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for step in range(steps + 1):
            t = step / steps
            x = round(x0 + (x1 - x0) * t)
            y = round(y0 + (y1 - y0) * t)
            for offset_y in range(-1, 2):
                for offset_x in range(-1, 2):
                    put(x + offset_x, y + offset_y, bytes(color))

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
        positive = root / "bolt.png"
        source_only = root / "source-only.png"
        blue = root / "blue-line.png"
        misaligned = root / "misaligned.png"
        short = root / "short.png"
        write_bolt_png(positive)
        write_bolt_png(source_only, line=None)
        write_bolt_png(blue, color=(60, 160, 255))
        write_bolt_png(misaligned, line=(412, 430, 612, 430))
        write_bolt_png(short, line=(512, 432, 515, 445))

        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(positive, True)
        assert not errors, errors
        assert metrics is not None and metrics["length"] >= 24 and metrics["alignment"] >= 0.65

        for rejected in (source_only, blue, misaligned, short):
            errors, metrics = verify_wisp_zap_client.verify_bolt_segment(rejected, True)
            assert errors and metrics is None, (rejected.name, errors, metrics)

        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(source_only, False)
        assert not errors and metrics is None, (errors, metrics)
        errors, metrics = verify_wisp_zap_client.verify_bolt_segment(positive, False)
        assert errors and metrics is not None, (errors, metrics)

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
    _test_first_frame_cursor_is_exclusive(generated)
    _test_first_frame_telemetry_uses_selected_frame(generated)
    _test_capture_reanchor_retry(generated)
    _test_wisp_zap_frame_capture_sequence(generated)
    _test_video_frame_extraction()
    _test_wisp_render_profile(generated)

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
        '/tp @p 12.5 149 10.5 0 41',
        '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES',
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "/execute if loaded 10 148 10 run say TC_SMOKE_WISP_CHUNK_LOADED=YES",
        "TC_SMOKE_WISP_ATTACK_INPUT=YES",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACK=YES",
        "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=",
        "TC_SMOKE_WISP_HURT=YES accepted=true",
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "minecraft:generic.knockback_resistance base set 1",
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0",
        "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
        "WISP_ZAP_CAMERA_PERSPECTIVE_REQUESTED=THIRD_PERSON_BACK",
        "WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON",
        "wisp_start_zap_video_capture",
        "wisp_stop_zap_video_capture",
        "wisp_capture_zap_frames",
        "wisp_wait_for_zap_frame",
        "wisp_wait_for_completed_wisp_frame",
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
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
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
        "wisp_capture_aim_after_hit",
        "wisp_capture_aim_after_hit_with_retries",
        "WISP_RENDER_CAPTURE_RETRY=",
        "WISP_RENDER_CAPTURE_RETRIES_EXHAUSTED=YES",
        "wisp_reanchor_player_for_capture()",
        "wisp_wait_for_attack_frame()",
        "wisp_attack_live_wisp()",
        "for attempt in 1 2 3",
        "WISP_ATTACK_ATTEMPT_REJECTED=",
        "WISP_ATTACK_CAMERA_FRAME=YES",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES",
        "/execute as @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] at @s run tp @p 12.5 149 10.5'",
        'open_delay="0.5"',
        'type_delay="2"',
        'sleep "$open_delay"',
        'xdotool type --clearmodifiers --delay "$type_delay" "$command"',
        "open_delay=\"0.5\"",
        "WISP_CAMERA_REAIM=CLIENT_TRACKING",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS",
        "targetWisp=true",
        "WISP_RENDER_CAPTURE_TICK=",
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "WISP_RENDER_PREATTACK_TRACKING=YES",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=",
        'attack_camera_source="PREAIMED_NEXT_FRAME"',
        'attack_camera_source="LIVE_ENTITY_FACING"',
        'echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail"',
        "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM",
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
    fixture_teleport_index = setup.index("wisp_fast_command '/tp @p 12.5 149 10.5 0 41'", fill_wait_index)
    fixture_wait_index = setup.index('wisp_wait_for_teleport "$fixture_teleport_log_lines"', fixture_teleport_index)
    support_index = setup.index("wisp_send_chat '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES'", fixture_wait_index)
    night_index = setup.index("wisp_send_chat '/time set midnight' 'Set the time to 18000'")
    baseline_a_index = setup.index("wisp_capture 79-wisp-before-a", support_index)
    assert night_index < baseline_a_index, "night lighting must be fixed before baseline screenshots"
    baseline_b_index = setup.index("wisp_capture 79-wisp-before-b", baseline_a_index)
    baseline_marker_index = setup.index("WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN", baseline_b_index)
    spawn_snapshot_index = setup.index('aim_log_lines="$(wc -l < client-smoke/client.log)"', baseline_marker_index)
    summon_index = setup.index('wisp_fast_command "/summon thaumcraft:wisp', spawn_snapshot_index)
    rendered_index = setup.index('wisp_wait_for_fresh_render "$aim_log_lines"', summon_index)
    attack_call_index = setup.index("wisp_attack_live_wisp", rendered_index)
    client_attack_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_CLIENT_ATTACK=YES'", attack_call_index)
    hurt_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_HURT=YES accepted=true'", client_attack_index)
    perspective_index = setup.index("xdotool key --clearmodifiers F5", client_attack_index)
    perspective_marker_index = setup.index("WISP_ZAP_CAMERA_PERSPECTIVE_REQUESTED=THIRD_PERSON_BACK", perspective_index)
    target_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'", hurt_index)
    zap_cursor_index = setup.index('zap_log_lines="$(wc -l < client-smoke/client.log)"', target_index)
    stabilize_index = setup.index("wisp_send_chat '/attribute @p minecraft:generic.knockback_resistance base set 1'", zap_cursor_index)
    stabilize_marker_index = setup.index("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL", stabilize_index)
    zap_capture_index = setup.index('wisp_capture_zap_frames "$zap_log_lines" "$wisp_id"', stabilize_marker_index)
    perspective_restore_index = setup.index("xdotool key --clearmodifiers F5", zap_capture_index)
    restore_marker_index = setup.index("WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON", perspective_restore_index)
    capture_retry_index = setup.index("wisp_capture_aim_after_hit_with_retries", restore_marker_index)
    telemetry_index = setup.index('wisp_verify_aim_telemetry "$aim_log_lines"', capture_retry_index)
    aim_verify_index = setup.index("python tools/verify_wisp_zap_client.py --aim-only", telemetry_index)
    assert night_index < teleport_index < loaded_index < fill_index < fill_wait_index < fixture_teleport_index < fixture_wait_index < support_index < baseline_a_index < baseline_b_index < baseline_marker_index < spawn_snapshot_index < summon_index < rendered_index < attack_call_index < client_attack_index < perspective_index < perspective_marker_index < hurt_index < target_index < zap_cursor_index < stabilize_index < stabilize_marker_index < zap_capture_index < perspective_restore_index < restore_marker_index < capture_retry_index < telemetry_index < aim_verify_index, (
        "after the real hit, capture all three FXBolt ages before the later Wisp camera re-anchor"
    )
    attack_frame_body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    assert 'exec {frame_fd}< <(tail --sleep-interval=0.01 -n +"$((after_lines + 1))" -f client-smoke/client.log)' in attack_frame_body
    assert 'if ! IFS= read -r -t "$remaining" -u "$frame_fd" candidate_line; then' in attack_frame_body
    assert 'if [[ "$candidate_line" =~ $frame_pattern ]]; then' in attack_frame_body
    assert attack_frame_body.count("xdotool click 1") == 1
    assert attack_frame_body.index('if [[ "$candidate_line" =~ $frame_pattern ]]') < attack_frame_body.index("xdotool click 1") < attack_frame_body.index('kill "$frame_tail_pid"')
    assert attack_frame_body.index('WISP_ATTACK_INPUT_LOG_LINES="$click_log_lines"') < attack_frame_body.index("xdotool click 1")
    assert "timeout 5s bash -c" not in attack_frame_body
    assert 'if [ "$watcher_status" -ne 0 ]; then' in attack_frame_body
    assert 'exit 1' in attack_frame_body
    attack_body = _bash_function_body(generated, "wisp_attack_live_wisp")
    preaim_branch = attack_body.split('if [ "$attempt" -eq 1 ]; then', 1)[1].split('\n      else\n        reanchor_log_lines=', 1)[0]
    assert "wisp_wait_for_attack_frame" in preaim_branch
    fresh_cursor = 'frame_log_lines="$WISP_RENDER_FIRST_FRAME_LINE_NUMBER"'
    assert fresh_cursor in preaim_branch, (
        "the attack watcher must wait for a frame newer than the consumed visibility frame"
    )
    assert 'frame_log_lines="$((WISP_RENDER_FIRST_FRAME_LINE_NUMBER - 1))"' not in preaim_branch
    assert 'frame_log_lines="$(wc -l < client-smoke/client.log)"' not in preaim_branch, (
        "a new tail snapshot can skip an attackable frame written during the hand-off"
    )
    assert preaim_branch.index(fresh_cursor) < preaim_branch.index("wisp_wait_for_attack_frame")
    assert 'frame_log_lines="$aim_log_lines"' not in preaim_branch
    assert "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE" not in preaim_branch
    assert "wisp_reanchor_player_for_capture" not in preaim_branch
    fallback_branch = attack_body.split('\n      else\n        reanchor_log_lines=', 1)[1].split('\n      fi\n      attack_log_lines', 1)[0]
    assert "wisp_move_player_to_live_wisp" in fallback_branch
    assert "wisp_wait_for_attack_frame" in fallback_branch
    assert "xdotool click 1" not in attack_body, "the caller must not delay the click after frame parsing"
    assert 'attack_log_lines="$WISP_ATTACK_INPUT_LOG_LINES"' in attack_body
    attack_frame_index = attack_body.index("wisp_wait_for_attack_frame")
    attack_input_index = attack_body.index("TC_SMOKE_WISP_ATTACK_INPUT=YES")
    assert attack_frame_index < attack_input_index
    fallback_reaim_index = fallback_branch.index("wisp_move_player_to_live_wisp")
    fallback_frame_index = fallback_branch.index('wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id" LIVE_WISP')
    assert fallback_reaim_index < fallback_frame_index
    assert "for attempt in 1 2 3" in attack_body, "the attack retry budget must remain bounded"
    assert "WISP_ATTACK_ATTEMPT_REJECTED=" in attack_body, "BLOCK hits must be rejected rather than accepted"
    assert 'attack_camera_source="PREAIMED_NEXT_FRAME"' in preaim_branch
    assert 'attack_camera_detail="id=$wisp_id triggerTick=$WISP_ATTACK_CAMERA_TICK triggerGameTime=$WISP_ATTACK_CAMERA_GAME_TIME"' in preaim_branch
    assert 'attack_camera_source="LIVE_ENTITY_FACING"' in fallback_branch
    assert 'attack_camera_detail="$WISP_ATTACK_REPOSITION_LINE"' in fallback_branch
    assert 'frame_log_lines="$WISP_ATTACK_REPOSITION_LINE_NUMBER"' in fallback_branch
    assert 'wisp_wait_for_attack_frame "$frame_log_lines" "$wisp_id" PLATFORM' in preaim_branch
    assert 'echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail"' in attack_body
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_ATTACK_INPUT=YES'" not in generated
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
    capture_body = _bash_function_body(generated, "wisp_capture_aim_after_hit")
    capture_frame_index = capture_body.index("TC_SMOKE_WISP_FRAME_COMPLETE=YES")
    capture_aim_guard_index = capture_body.index('awk -v dot="$aim_dot"')
    capture_image_index = capture_body.index("wisp_capture 78-wisp-aim-check")
    assert capture_aim_guard_index < capture_frame_index < capture_image_index < capture_body.index("WISP_RENDER_CAPTURE_AFTER_HIT=YES"), (
        "the visual screenshot must follow an AFTER_LEVEL frame after accepted damage"
    )
    timeout_index = capture_body.index("WISP_RENDER_CAPTURE_AFTER_HIT_TIMEOUT=YES")
    assert "return 1" in capture_body[timeout_index:] and "exit 1" not in capture_body[timeout_index:], (
        "a missed camera alignment must return to the bounded re-aim loop, not abort immediately"
    )
    retry_body = _bash_function_body(generated, "wisp_capture_aim_after_hit_with_retries")
    assert "for attempt in 1 2 3" in retry_body
    assert 'wisp_reanchor_player_for_capture "$reanchor_log_lines"' in retry_body
    assert 'wisp_capture_aim_after_hit "$after_hit_log_lines"' in retry_body
    assert "WISP_RENDER_CAPTURE_RETRY=$attempt" in retry_body
    assert "WISP_RENDER_CAPTURE_RETRIES_EXHAUSTED=YES" in retry_body
    wait_attack_body = _bash_function_body(generated, "wisp_wait_for_attack_frame")
    click_log_snapshot_index = wait_attack_body.index('click_log_lines="$(wc -l < client-smoke/client.log)"')
    frame_wait_index = wait_attack_body.index('exec {frame_fd}< <(tail --sleep-interval=0.01 -n +')
    assert "--sleep-interval=0.01" in wait_attack_body, "keep fallback tail polling below one tick"
    camera_line_index = wait_attack_body.index('camera_line="$(grep -F')
    attack_click_index = wait_attack_body.index("xdotool click 1")
    diagnostics_index = wait_attack_body.index('distance="$(awk')
    assert click_log_snapshot_index < frame_wait_index < attack_click_index < camera_line_index < diagnostics_index, (
        "dispatch the real click as soon as the matching AFTER_LEVEL EntityHitResult arrives, before camera parsing"
    )
    assert wait_attack_body.count("xdotool click 1") == 1
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
    assert "hitType=ENTITY targetId=$wisp_id targetType=thaumcraft:wisp blockPos=none" in wait_attack_body
    assert "WISP_ATTACKABLE_FRAME=YES" in wait_attack_body
    capture_input_body = _bash_function_body(generated, "wisp_capture_attack_input_frame")
    assert 'index($0, "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=" id " ") == 1' in capture_input_body
    assert 'index($0, "gameTime=" game_time " ") > 0' in capture_input_body
    assert 'index($0, "playerTick=" player_tick " ") > 0' in capture_input_body
    assert "NR < limit" in capture_input_body
    assert 'WISP_ATTACK_INPUT_FRAME=YES id=$WISP_ATTACK_CAMERA_ID' in attack_body
    assert attack_body.index("wisp_capture_attack_input_frame") < attack_body.index("WISP_ATTACK_INPUT_FRAME=YES") < attack_body.index("WISP_ATTACK_CAMERA_FRAME=YES")
    assert "for _ in $(seq 1 200)" not in wait_attack_body
    fast_command_body = _bash_function_body(generated, "wisp_fast_command")
    return_index = fast_command_body.index("xdotool key --clearmodifiers Return")
    assert 'open_delay="0.5"' in fast_command_body and 'type_delay="2"' in fast_command_body
    assert 'sleep "$open_delay"' in fast_command_body
    assert 'xdotool type --clearmodifiers --delay "$type_delay" "$command"' in fast_command_body
    assert "sleep " not in fast_command_body[return_index:], "fast commands must return immediately; callers wait for authoritative markers"
    reanchor_body = _bash_function_body(generated, "wisp_reanchor_player_for_capture")
    assert " 0.1 1" not in reanchor_body, "capture re-anchor must keep the reliable default chat-open/type pacing"
    assert "wisp_fast_command '/execute as @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] at @s run tp @p 12.5 149 10.5'" in reanchor_body
    assert "facing " not in reanchor_body, "client tick tracking, not a one-shot command, owns capture aim"
    assert "tag=tc_wisp_zap_smoke" in reanchor_body
    move_body = _bash_function_body(generated, "wisp_move_player_to_live_wisp")
    assert "run tp @p ~1.0 ~ ~ facing entity @s eyes" in move_body
    assert "Teleported Dev to " in move_body
    assert "WISP_CAMERA_REANCHORED=LIVE_WISP_MELEE_POSITION" in move_body
    assert "/execute as @e[type=thaumcraft:wisp,tag=tc_wisp_zap_smoke,limit=1] at @s run tp @p 12.5 149 10.5" in reanchor_body
    assert "facing entity @s eyes" not in reanchor_body, "post-hit aim must remain client-tracked"
    assert "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM" in reanchor_body
    assert reanchor_body.count("WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM") == 1
    assert "WISP_CAMERA_REAIM=CLIENT_TRACKING" in reanchor_body
    assert "wisp_capture 78-wisp-aim-check" in generated, "the visual capture is intentionally deferred until after accepted damage"
    assert "Motion:[" not in generated
    assert "Successfully filled 81 block(s)" in generated
    assert "CustomName:'" in generated and "CustomNameVisible:0b" in generated
    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in generated
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" not in generated
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in generated
    assert "wisp_verify_aim_telemetry" in generated
    assert 'grep -F -- "$frame_camera_marker" | head -1' in generated
    assert 'grep -F -c -- "$frame_camera_marker" || true' in generated
    assert "WISP_CAMERA_REAIM=CLIENT_TRACKING" in generated
    assert "d <= 3.0" in generated
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'" not in generated
    assert "execute as @p at @s anchored eyes facing entity" not in generated
    assert "run tp @p ~1.5" not in generated
    assert "run tp @s ~ ~ ~ ~ ~" not in generated

    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
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
        "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
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
        "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=17 targetType=thaumcraft:wisp hitType=ENTITY distance=1.0440 gameTime=520 blockPos=none tick=507",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=22 distance=1.0440 gameTime=520",
        "TC_SMOKE_WISP_CAMERA_TRACK=YES id=17",
        "TC_SMOKE_WISP_HURT=YES accepted=true source=17 attacker=1 amount=1.0 health=21.0",
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=23 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=23 gameTime=521 playerTick=508 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E createdAt=520 clientGameTime=520",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=1.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF createdAt=520 gameTime=521 ageTick=1",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=521 wispTick=24 ageTick=1 age=1.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=24 gameTime=521 playerTick=508 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=522 wispTick=25 ageTick=2 age=2.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=25 gameTime=522 playerTick=509 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_FRAME=YES source=17 target=1 createdAt=520 gameTime=523 wispTick=26 ageTick=3 age=3.25 points=9 cameraType=THIRD_PERSON_BACK texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=26 gameTime=523 playerTick=510 hitType=ENTITY targetId=17 targetType=thaumcraft:wisp blockPos=none",
        "TC_SMOKE_WISP_ZAP_EXPIRED=YES source=17 target=1 createdAt=520 gameTime=524 ageTicks=4",
    ))
    status = "\n".join((
        "WISP_FIXTURE_PLATFORM_FILL=YES [Server thread] [Dev: Successfully filled 81 block(s)]",
        "WISP_FIXTURE_TELEPORT=YES [Server thread] Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_AIM=PREAIMED_SPAWN",
        "WISP_ZAP_TEST_TIME=MIDNIGHT",
        "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
        "WISP_RENDER_PREATTACK_TRACKING=YES id=17 tick=20 line=6",
        "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
        "WISP_RENDER_FIRST_FRAME_ID=17",
        "WISP_RENDER_FIRST_FRAME_TICK=21",
        "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=9",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_RENDER_FIRST_FRAME=YES id=17 tick=21",
        "WISP_RENDER_CAPTURE_ID=17",
        "WISP_RENDER_CAPTURE_TICK=23",
        "WISP_RENDER_CAPTURE_AIM_DOT=0.999",
        "WISP_ATTACK_CAMERA_REAIM=PREAIMED_NEXT_FRAME id=17 triggerTick=22 triggerGameTime=520 inputTick=22 inputGameTime=520",
        "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=22",
        "WISP_ATTACK_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1",
        "WISP_ATTACKABLE_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 position=PLATFORM gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_INPUT_FRAME=YES id=17 tick=22 hitType=ENTITY targetId=17 gameTime=520 playerTick=507 distance=1.0440 aimDot=0.999",
        "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
        "WISP_ZAP_VIDEO_STARTED=YES firstFrameEpochNs=1000000000 frameRate=60",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=1 wispTick=24 observedAtNs=1100000000",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=2 wispTick=25 observedAtNs=1200000000",
        "WISP_ZAP_FRAME_OBSERVED=YES source=17 createdAt=520 ageTick=3 wispTick=26 observedAtNs=1300000000",
        "WISP_ZAP_VIDEO_STOPPED=YES",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=1 wispTick=24",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=2 wispTick=25",
        "WISP_ZAP_FRAME_CAPTURE=YES source=17 createdAt=520 ageTick=3 wispTick=26",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=1 frameIndex=6 diff=18",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=2 frameIndex=12 diff=15",
        "WISP_ZAP_VIDEO_FRAME_INDEX=YES source=17 createdAt=520 ageTick=3 frameIndex=18 diff=12",
        "WISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES",
        "WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON",
        "WISP_ZAP_EFFECT_EXPIRED=YES source=17 createdAt=520",
        "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_CAMERA_REAIM=CLIENT_TRACKING",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=17 tick=23 distance=1.0440 aimDot=0.999",
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
        "WISP_ZAP_CAMERA_PERSPECTIVE_REQUESTED=THIRD_PERSON_BACK",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=render_safe_quarter tick=23 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=47.59636 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_CAMERA_TELEMETRY=PASS id=17 tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 distance=1.0440 aimDot=0.999",
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
            elif name == "83-wisp-zap-after-expiry.png":
                write_bolt_png(target, line=None)
            else:
                target.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        positive = root / "aim-positive.png"
        write_png(positive)
        aim_errors, count, bbox = verify_wisp_zap_client.verify_aim_screenshot(positive)
        assert aim_errors == [], aim_errors
        assert count == 100 and bbox == (58, 58, 69, 69), (count, bbox)

        off_center = root / "aim-off-center.png"
        write_png(off_center, (448, 320, 460, 332))
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(off_center)
        assert aim_errors and "not near the crosshair" in aim_errors[0], aim_errors

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

        errors = verify_wisp_zap_client.verify(log, status, root)
        assert errors == [], errors
        first_person_errors = verify_wisp_zap_client.verify(
            log.replace("cameraType=THIRD_PERSON_BACK", "cameraType=FIRST_PERSON"), status, root
        )
        assert any("expected THIRD_PERSON_BACK" in error for error in first_person_errors), first_person_errors

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
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_PREATTACK_TRACKING=YES id=17 tick=20 line=6", ""), root
        ), "missing pre-attack tracking status must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_PREATTACK_TRACKING=YES id=17 tick=20 line=6", "WISP_RENDER_PREATTACK_TRACKING=YES id=17 tick=20 line=9"), root
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
            log, status.replace("WISP_CAMERA_REAIM=CLIENT_TRACKING", ""), root
        ), "missing post-hit client camera tracking must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1", ""), root
        ), "missing live attack camera frame must fail"
        fallback_status = status.replace(
            "WISP_ATTACK_CAMERA_REAIM=PREAIMED_NEXT_FRAME id=17 triggerTick=22 triggerGameTime=520 inputTick=22 inputGameTime=520",
            "WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION Teleported Dev to 12.500000, 149.000000, 10.500000\n"
            "WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING Teleported Dev to 12.500000, 149.000000, 10.500000",
        )
        assert not verify_wisp_zap_client.verify(log, fallback_status, root), (
            "live-reposition attack path with its server teleport marker must remain valid"
        )
        missing_attack_reaim_status = fallback_status.replace(
            "WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING Teleported Dev to 12.500000, 149.000000, 10.500000",
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
        assert not verify_wisp_zap_client.verify(low_dot_log, low_dot_status, root), (
            "same-ID ENTITY hit must remain authoritative when the render-safe aim-point dot is below 0.99"
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
            log, status.replace("WISP_RENDER_CAPTURE_TICK=23", "WISP_RENDER_CAPTURE_TICK=24"), root
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
        assert verify_wisp_zap_client.verify(
            log, status.replace("distance=1.0440", "distance=6.7"), root
        ), "Wisp outside the 3-block melee reach must fail"
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
