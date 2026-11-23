#!/usr/bin/env python3
"""Offline generator/verifier regressions for the real Wisp zap client smoke."""
from pathlib import Path
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
        raw.extend(row)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        + _chunk(b"IEND", b"")
    )



def _aim_dot(pitch_degrees: float, delta_y: float, delta_z: float) -> float:
    pitch = math.radians(pitch_degrees)
    length = math.hypot(delta_y, delta_z)
    return (-math.sin(pitch) * delta_y + math.cos(pitch) * delta_z) / length


def main() -> int:
    # BETA26 RenderWisp draws at entity position, while getEyePosition is 0.765 blocks higher.
    old_eye_dot = _aim_dot(29.0, -0.555, 1.0)
    old_render_dot = _aim_dot(29.0, -1.32, 1.0)
    hitbox_center_dot = _aim_dot(41.0, -0.87, 1.0)
    assert old_eye_dot >= 0.99 and old_render_dot < 0.99 and hitbox_center_dot >= 0.99, (
        old_eye_dot, old_render_dot, hitbox_center_dot
    )

    anchor = "  # The initial world is already creative + peaceful, as established"
    fixture = "#!/usr/bin/env bash\nif grep -q ready; then\n  send_chat() {\n    true\n  }\n" + anchor + "\n"
    with patch.object(Path, "read_text", return_value=fixture):
        generated = client_wisp_zap_smoke.render()

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
        "TC_SMOKE_WISP_HURT=YES accepted=true",
        "TC_SMOKE_WISP_TARGET_SET=YES",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES",
        "TC_SMOKE_WISP_RENDERED=YES",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES",
        "WISP_RENDER_FRAME_COMPLETE=YES",
        "wisp_fast_command()",
        "sleep 0.5",
        '/summon thaumcraft:wisp 12.5 149.3 11.5 {Type:"aer"}',
        "grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
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
        "wisp_reaim_camera_at_live_wisp()",
        "wisp_wait_for_attack_frame()",
        "wisp_attack_live_wisp()",
        "for attempt in 1 2 3",
        "WISP_ATTACK_ATTEMPT_REJECTED=",
        "WISP_ATTACK_CAMERA_FRAME=YES",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES",
        "/tp @p 12.5 149 10.5 facing entity @e[type=thaumcraft:wisp,limit=1,sort=nearest] eyes' 0.1 1",
        'open_delay="0.5"',
        'type_delay="2"',
        'sleep "$open_delay"',
        'xdotool type --clearmodifiers --delay "$type_delay" "$command"',
        "0.1 1",
        "WISP_CAMERA_REAIM=LIVE_ENTITY_FACING",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS",
        "targetWisp=true",
        "WISP_RENDER_CAPTURE_TICK=",
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=",
        'attack_camera_source="PREAIMED_FIRST_FRAME"',
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
    )
    for marker in required:
        assert marker in generated, marker
    setup = generated.split("  wisp_command '/difficulty normal'", 1)[1]
    teleport_index = setup.index("wisp_command '/tp @p 10.5 149 8.5 0 22'")
    loaded_index = setup.index("wisp_wait_for_loaded_chunk")
    fill_index = setup.index("wisp_fast_command '/fill 6 148 6 14 148 14 minecraft:stone'")
    fill_wait_index = setup.index('wisp_wait_for_fill "$fill_log_lines"', fill_index)
    fixture_teleport_index = setup.index("wisp_fast_command '/tp @p 12.5 149 10.5 0 41'", fill_wait_index)
    fixture_wait_index = setup.index('wisp_wait_for_teleport "$fixture_teleport_log_lines"', fixture_teleport_index)
    support_index = setup.index("wisp_send_chat '/execute if block 12 148 10 minecraft:stone run say TC_SMOKE_WISP_SUPPORT_BLOCK=YES'", fixture_wait_index)
    baseline_a_index = setup.index("wisp_capture 79-wisp-before-a", support_index)
    baseline_b_index = setup.index("wisp_capture 79-wisp-before-b", baseline_a_index)
    baseline_marker_index = setup.index("WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN", baseline_b_index)
    spawn_snapshot_index = setup.index('aim_log_lines="$(wc -l < client-smoke/client.log)"', baseline_marker_index)
    summon_index = setup.index("wisp_fast_command '/summon thaumcraft:wisp 12.5 149.3 11.5", spawn_snapshot_index)
    rendered_index = setup.index('wisp_wait_for_fresh_render "$aim_log_lines"', summon_index)
    attack_call_index = setup.index("wisp_attack_live_wisp", rendered_index)
    client_attack_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_CLIENT_ATTACK=YES'", attack_call_index)
    hurt_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_HURT=YES accepted=true'", client_attack_index)
    target_index = setup.index("wisp_wait_for_marker 'TC_SMOKE_WISP_TARGET_SET=YES'", hurt_index)
    reaim_snapshot_index = setup.index('reaim_log_lines="$(wc -l < client-smoke/client.log)"', target_index)
    reaim_index = setup.index('wisp_reaim_camera_at_live_wisp "$reaim_log_lines"', reaim_snapshot_index)
    capture_snapshot_index = setup.index('after_hit_log_lines="$(wc -l < client-smoke/client.log)"', reaim_index)
    capture_index = setup.index('wisp_capture_aim_after_hit "$after_hit_log_lines"', capture_snapshot_index)
    telemetry_index = setup.index('wisp_verify_aim_telemetry "$aim_log_lines"', capture_index)
    aim_verify_index = setup.index("python tools/verify_wisp_zap_client.py --aim-only", telemetry_index)
    assert teleport_index < loaded_index < fill_index < fill_wait_index < fixture_teleport_index < fixture_wait_index < support_index < baseline_a_index < baseline_b_index < baseline_marker_index < spawn_snapshot_index < summon_index < rendered_index < attack_call_index < client_attack_index < hurt_index < target_index < reaim_snapshot_index < reaim_index < capture_snapshot_index < capture_index < telemetry_index < aim_verify_index, (
        "the attack helper must re-aim, wait for a matching completed frame, and prove the real hit before post-hit capture"
    )
    attack_body = generated.split("  wisp_attack_live_wisp() {", 1)[1].split("  }", 1)[0]
    preaim_branch = attack_body.split('if [ "$attempt" -eq 1 ]; then', 1)[1].split('\n      else\n        reaim_log_lines=', 1)[0]
    assert "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE" in preaim_branch
    assert "wisp_reaim_camera_at_live_wisp" not in preaim_branch
    fallback_branch = attack_body.split('\n      else\n        reaim_log_lines=', 1)[1].split('\n      fi\n      attack_log_lines', 1)[0]
    assert "wisp_reaim_camera_at_live_wisp" in fallback_branch
    assert "wisp_wait_for_attack_frame" in fallback_branch
    attack_reaim_index = attack_body.index("wisp_reaim_camera_at_live_wisp")
    attack_frame_index = attack_body.index("wisp_wait_for_attack_frame")
    attack_click_index = attack_body.index("xdotool click 1")
    attack_input_index = attack_body.index("TC_SMOKE_WISP_ATTACK_INPUT=YES")
    assert attack_reaim_index < attack_frame_index < attack_click_index < attack_input_index
    between_frame_and_click = attack_body[attack_frame_index:attack_click_index]
    assert "sleep " not in between_frame_and_click, "click immediately after the aligned completed render frame"
    assert "for attempt in 1 2 3" in attack_body, "the attack retry budget must remain bounded"
    assert "WISP_ATTACK_ATTEMPT_REJECTED=" in attack_body, "BLOCK hits must be rejected rather than accepted"
    assert 'attack_camera_source="PREAIMED_FIRST_FRAME"' in preaim_branch
    assert 'attack_camera_detail="id=$camera_id tick=$camera_tick"' in preaim_branch
    assert 'attack_camera_source="LIVE_ENTITY_FACING"' in fallback_branch
    assert 'echo "WISP_ATTACK_CAMERA_REAIM=$attack_camera_source $attack_camera_detail"' in attack_body
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_ATTACK_INPUT=YES'" not in generated
    between_render_and_attack = setup[rendered_index:attack_call_index]
    assert "sleep " not in between_render_and_attack and "wisp_capture" not in between_render_and_attack, (
        "no external capture or delay may be inserted before the live-target retry helper"
    )
    fresh_render_body = generated.split("  wisp_wait_for_fresh_render() {", 1)[1].split("  }", 1)[0]
    frame_marker_index = fresh_render_body.index("TC_SMOKE_WISP_FRAME_COMPLETE=YES")
    first_frame_status_index = fresh_render_body.index("WISP_RENDER_FIRST_FRAME=YES")
    assert frame_marker_index < fresh_render_body.index("WISP_RENDER_FRAME_COMPLETE=YES") < first_frame_status_index
    assert 'wisp_capture "$capture_name"' not in fresh_render_body, "the first-frame wait must not capture before the attack"
    capture_body = generated.split("  wisp_capture_aim_after_hit() {", 1)[1].split("  }", 1)[0]
    capture_frame_index = capture_body.index("TC_SMOKE_WISP_FRAME_COMPLETE=YES")
    capture_aim_guard_index = capture_body.index('awk -v dot="$aim_dot"')
    capture_image_index = capture_body.index("wisp_capture 78-wisp-aim-check")
    assert capture_aim_guard_index < capture_frame_index < capture_image_index < capture_body.index("WISP_RENDER_CAPTURE_AFTER_HIT=YES"), (
        "the visual screenshot must follow an AFTER_LEVEL frame after accepted damage"
    )
    fast_command_body = generated.split("  wisp_fast_command() {", 1)[1].split("  }", 1)[0]
    return_index = fast_command_body.index("xdotool key --clearmodifiers Return")
    assert 'open_delay="0.5"' in fast_command_body and 'type_delay="2"' in fast_command_body
    assert 'sleep "$open_delay"' in fast_command_body
    assert 'xdotool type --clearmodifiers --delay "$type_delay" "$command"' in fast_command_body
    assert "sleep " not in fast_command_body[return_index:], "fast commands must return immediately; callers wait for authoritative markers"
    reaim_body = generated.split("  wisp_reaim_camera_at_live_wisp() {", 1)[1].split("  }", 1)[0]
    assert " 0.1 1" in reaim_body, "attack-time re-aim must use the short chat-open/type delay"
    assert "/tp @p 12.5 149 10.5 facing entity" in reaim_body
    assert "/tp @p ~ ~ ~ facing entity" not in reaim_body
    assert "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM" in reaim_body
    assert "wisp_capture 78-wisp-aim-check" in generated, "the visual capture is intentionally deferred until after accepted damage"
    assert "Motion:[" not in generated
    assert "Successfully filled 81 block(s)" in generated
    assert '/summon thaumcraft:wisp 11.9 149.3 10.5 {Type:"aer",Tags:["tc_wisp_zap_smoke"]}' not in generated
    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in generated
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" not in generated
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in generated
    assert "wisp_verify_aim_telemetry" in generated
    assert "grep -F 'TC_SMOKE_WISP_CAMERA=YES' | head -1" in generated
    assert "WISP_CAMERA_REAIM=LIVE_ENTITY_FACING" in generated
    assert "d <= 3.0" in generated
    assert "wisp_wait_for_marker 'TC_SMOKE_WISP_PRESENT_FOR_AIM=YES'" not in generated
    assert "execute as @p at @s anchored eyes facing entity" not in generated
    assert "run tp @p ~1.5" not in generated
    assert "run tp @s ~ ~ ~ ~ ~" not in generated

    assert "WISP_ZAP_AIM=PREAIMED_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN" in verify_wisp_zap_client.REQUIRED_STATUS
    assert verify_wisp_zap_client.AIM_SCREENSHOTS == ("78-wisp-aim-check.png",)
    assert verify_wisp_zap_client.BASELINE_SCREENSHOTS == ("79-wisp-before-a.png", "79-wisp-before-b.png")
    assert "WISP_ZAP_AIM=PLAYER_FACING_ENTITY" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=PLAYER_CONTEXT_EYES" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_CAMERA_TELEMETRY=PASS" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_RENDER_CAPTURE_TICK=" in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "WISP_ZAP_AIM_TELEPORT=YES" not in verify_wisp_zap_client.REQUIRED_STATUS
    assert "~-1.5 ~ ~ facing entity" not in generated
    assert 'wisp_wait_for_fresh_render "$aim_log_lines"' in generated
    assert "wisp_wait_for_exact_aim_teleport" not in generated
    assert "/fill 8 148 8 12 148 14 minecraft:stone" not in generated
    assert generated.count("  send_chat() {") == 1
    wisp_body = generated.split("  wisp_command() {", 1)[1]
    assert "sleep 4" not in wisp_body
    assert "xdotool mousemove" not in generated
    assert "sleep 0.08" not in generated.split("  wisp_fast_command() {", 1)[1].split("  }", 1)[0]
    syntax = subprocess.run(
        ["bash", "-n"], input=generated, text=True, capture_output=True, check=False
    )
    assert syntax.returncode == 0, syntax.stderr

    log = "\n".join((
        "TC_SMOKE_WISP_CHUNK_LOADED=YES",
        "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=hitbox_center tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=41.0 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=hitbox_center tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=41.0 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=22",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22",
        "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=17 targetType=thaumcraft:wisp hitType=ENTITY distance=1.0440 tick=507",
        "TC_SMOKE_WISP_CLIENT_ATTACK=YES player=1 target=17 tick=21 distance=1.0440",
        "TC_SMOKE_WISP_HURT=YES accepted=true source=17 attacker=1 amount=1.0 health=21.0",
        "TC_SMOKE_WISP_TARGET_SET=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=22",
        "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
        "TC_SMOKE_WISP_ZAP_SENT=YES source=17 target=1 aspect=aer",
        "TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=17 target=1 color=#FFFF7E",
        "TC_SMOKE_WISP_ZAP_RENDERED=YES source=17 target=1 points=9 age=0.25 texture=thaumcraft:textures/misc/essentia.png blend=SRC_ALPHA_ONE depthWrite=OFF",
    ))
    status = "\n".join((
        "WISP_FIXTURE_PLATFORM_FILL=YES [Server thread] [Dev: Successfully filled 81 block(s)]",
        "WISP_FIXTURE_TELEPORT=YES [Server thread] Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "WISP_ZAP_AIM=PREAIMED_SPAWN",
        "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
        "WISP_RENDER_FRAME_COMPLETE=YES id=17 tick=21",
        "WISP_RENDER_FIRST_FRAME_ID=17",
        "WISP_RENDER_FIRST_FRAME_TICK=21",
        "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=hitbox_center tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=41.0 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_RENDER_FIRST_FRAME=YES id=17 tick=21",
        "WISP_RENDER_CAPTURE_ID=17",
        "WISP_RENDER_CAPTURE_TICK=22",
        "WISP_RENDER_CAPTURE_AIM_DOT=0.999",
        "WISP_ATTACK_CAMERA_REAIM=PREAIMED_FIRST_FRAME id=17 tick=21",
        "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=21 distance=1.0440 aimDot=0.999",
        "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=21",
        "WISP_ATTACK_CAMERA_SAMPLE=TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=hitbox_center tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=41.0 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1",
        "WISP_CAMERA_REAIM=LIVE_ENTITY_FACING Teleported Dev to 12.500000, 149.000000, 10.500000",
        "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM",
        "WISP_CAMERA_CAPTURE_TELEMETRY=PASS id=17 tick=22 distance=1.0440 aimDot=0.999",
        "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
        "WISP_ATTACK_TARGET_CONFIRMED=YES",
        "WISP_ATTACK_HURT_ACCEPTED=YES",
        "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
        "TC_SMOKE_WISP_CAMERA=YES id=17 aimTarget=hitbox_center tick=22 playerX=12.5 playerY=149.0 playerZ=10.5 playerYaw=0.0 playerPitch=41.0 playerEyeX=12.5 playerEyeY=150.62 playerEyeZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 wispEyeX=12.5 wispEyeY=150.065 wispEyeZ=11.5 wispHitboxCenterX=12.5 wispHitboxCenterY=149.75 wispHitboxCenterZ=11.5 aimDot=0.999 renderAimDot=0.978 eyeAimDot=0.978",
        "WISP_CAMERA_TELEMETRY=PASS id=17 tick=21 playerX=12.5 playerY=149.0 playerZ=10.5 wispX=12.5 wispY=149.3 wispZ=11.5 distance=1.0440 aimDot=0.999",
        "WISP_AIM_SCREENSHOT_VISIBLE=YES pixels=144 bbox=58,58,69,69",
        "WISP_BASELINE_SCREENSHOT_CLEAR=YES pixels=0",
        "WISP_BASELINE_SCREENSHOT_CLEAR=YES pixels=0",
        "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
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
            else:
                target.write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)
        positive = root / "aim-positive.png"
        write_png(positive)
        aim_errors, count, bbox = verify_wisp_zap_client.verify_aim_screenshot(positive)
        assert aim_errors == [], aim_errors
        assert count == 144 and bbox == (58, 58, 69, 69), (count, bbox)

        off_center = root / "aim-off-center.png"
        write_png(off_center, (448, 320, 460, 332))
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(off_center)
        assert aim_errors and "not near the crosshair" in aim_errors[0], aim_errors

        invisible = root / "aim-invisible.png"
        write_png(invisible, None)
        aim_errors, count, _ = verify_wisp_zap_client.verify_aim_screenshot(invisible)
        assert aim_errors and count == 0, aim_errors

        clean_baseline = root / "baseline-clean.png"
        write_png(clean_baseline, None)
        baseline_errors, baseline_count, _ = verify_wisp_zap_client.verify_baseline_screenshot(clean_baseline)
        assert baseline_errors == [] and baseline_count == 0, (baseline_errors, baseline_count)
        contaminated_baseline = root / "baseline-contaminated.png"
        write_png(contaminated_baseline)
        baseline_errors, baseline_count, _ = verify_wisp_zap_client.verify_baseline_screenshot(contaminated_baseline)
        assert baseline_errors and baseline_count == 144, (baseline_errors, baseline_count)

        errors = verify_wisp_zap_client.verify(log, status, root)
        assert errors == [], errors
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_RENDER_AFTER_PREAIMED_SPAWN=YES", ""), root
        ), "stale pre-spawn renderer callback must fail"
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
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_CAMERA_REAIM=LIVE_ENTITY_FACING", ""), root
        ), "missing post-hit live-entity camera re-aim must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME_USED=YES attempt=1", ""), root
        ), "missing live attack camera frame must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=21 distance=1.0440 aimDot=0.999",
                                "WISP_ATTACK_CAMERA_FRAME=YES id=17 tick=21 distance=1.0440 aimDot=0.8"), root
        ), "misaligned live attack camera frame must fail"
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=21",
                                "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=17 tick=23"), root
        ), "attack camera frame tick mismatch must fail"
        misordered_lines = log.splitlines()
        attack_line_index = next(i for i, line in enumerate(misordered_lines) if line.startswith("TC_SMOKE_WISP_ATTACK_INPUT=YES"))
        attack_frame_index = next(i for i, line in enumerate(misordered_lines) if line == "TC_SMOKE_WISP_FRAME_COMPLETE=YES id=17 tick=21")
        misordered_lines.insert(attack_frame_index, misordered_lines.pop(attack_line_index))
        assert verify_wisp_zap_client.verify("\\n".join(misordered_lines), status, root), (
            "real click before the matching completed camera frame must fail"
        )
        assert verify_wisp_zap_client.verify(
            log, status.replace("WISP_CAMERA_CAPTURE_TELEMETRY=PASS", ""), root
        ), "missing post-hit camera telemetry gate must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_CAMERA=YES id=17", "TC_SMOKE_WISP_CAMERA=YES id=18"), status, root
        ), "camera sample from another Wisp must fail"
        assert verify_wisp_zap_client.verify(
            log.replace("TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=21", "TC_SMOKE_WISP_RENDERED=YES id=17 aspect=aer tick=22"), status, root
        ), "camera and renderer ticks must match"
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
    assert "event.isAttack()" in attack_probe
    assert "minecraft.hitResult" in attack_probe
    assert "TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=" in attack_probe
    assert "AttackEntityEvent" in attack_probe
    assert "TC_SMOKE_WISP_CLIENT_ATTACK=YES" in attack_probe
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
    assert "player.getViewVector(partialTick).dot(toWispHitboxCenter.normalize())" in renderer_registration
    assert "player.getViewVector(partialTick).dot(toWispRenderOrigin.normalize())" in renderer_registration
    assert "player.getViewVector(partialTick).dot(toWisp.normalize())" not in renderer_registration
    assert "aimTarget=hitbox_center" in renderer_registration
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
