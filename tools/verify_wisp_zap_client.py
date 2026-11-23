#!/usr/bin/env python3
"""Fail-closed verifier for a real two-entity BETA26 Wisp zap client smoke."""
from pathlib import Path
import argparse
import math
import re
import subprocess
import sys


SCREENSHOTS = (
    "78-wisp-aim-check.png",
    "79-wisp-before-a.png",
    "79-wisp-before-b.png",
    "80-wisp-zap-frame-a.png",
    "81-wisp-zap-frame-b.png",
    "82-wisp-zap-frame-c.png",
    "83-wisp-zap-after-expiry.png",
)

AIM_SCREENSHOTS = SCREENSHOTS[:1]
BASELINE_SCREENSHOTS = SCREENSHOTS[1:3]
AIM_ROI = (128, 128, 448, 320)

REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_WISP_CHUNK_LOADED=YES",
    "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
    "TC_SMOKE_WISP_RENDERED=YES",
    "TC_SMOKE_WISP_FRAME_COMPLETE=YES",
    "TC_SMOKE_WISP_ATTACK_INPUT=YES",
    "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
    "TC_SMOKE_WISP_CAMERA_TRACK=YES",
    "TC_SMOKE_WISP_HURT=YES accepted=true",
    "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
)

REQUIRED_STATUS = (
    "WISP_FIXTURE_PLATFORM_FILL=YES",
    "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
    "WISP_FIXTURE_TELEPORT=YES",
    "WISP_ZAP_AIM=PREAIMED_SPAWN",
    "WISP_CAMERA_TELEMETRY=PASS",
    "WISP_CAMERA_REAIM=CLIENT_TRACKING",
    "WISP_CAMERA_REANCHORED=FIXTURE_PLATFORM",
    "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
    "WISP_CAMERA_CAPTURE_TELEMETRY=PASS",
    "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
    "WISP_RENDER_FRAME_COMPLETE=YES",
    "WISP_RENDER_FIRST_FRAME=YES",
    "WISP_RENDER_FIRST_FRAME_ID=",
    "WISP_RENDER_FIRST_FRAME_TICK=",
    "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=",
    "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=",
    "WISP_RENDER_CAPTURE_ID=",
    "WISP_RENDER_CAPTURE_TICK=",
    "WISP_RENDER_CAPTURE_AFTER_HIT=YES",
    "WISP_ATTACK_TARGET_CONFIRMED=YES",
    "WISP_ATTACKABLE_FRAME=YES",
    "WISP_ATTACK_CAMERA_REAIM=",
    "WISP_ATTACK_CAMERA_FRAME=YES",
    "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
    "WISP_ATTACK_CAMERA_FRAME_USED=YES",
    "WISP_ATTACK_HURT_ACCEPTED=YES",
    "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
    "WISP_AIM_SCREENSHOT_VISIBLE=YES",
    "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
    "WISP_ZAP_REAL_CLIENT=PASS",
)

BAD_LOG_PATTERNS = (
    re.compile(
        r"(?:Missing textures|Unable to load texture|Failed to load texture).*essentia\.png"
        r"|essentia\.png.*(?:missing texture|Unable to load)",
        re.IGNORECASE,
    ),
    re.compile(r'Exception in thread "Render thread"|Minecraft has crashed|Mod loading has failed', re.IGNORECASE),
)




def _read_aer_pixels(path: Path) -> tuple[list[str], list[int], list[int]]:
    """Read Aer-tinted pixels from the fixed crosshair ROI."""
    if not path.is_file() or path.stat().st_size < 128:
        return [f"missing or empty aim screenshot: {path.name}"], [], []

    width, height, left, top = AIM_ROI
    try:
        result = subprocess.run(
            [
                "convert", str(path), "-crop", f"{width}x{height}+{left}+{top}",
                "+repage", "-depth", "8", "RGB:-",
            ],
            capture_output=True,
            check=False,
        )
    except FileNotFoundError:
        return ["ImageMagick convert is required to inspect Wisp screenshots"], [], []

    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        return [f"cannot decode Wisp screenshot {path.name}: {detail}"], [], []

    pixels = result.stdout
    expected = width * height * 3
    if len(pixels) != expected:
        return [f"unexpected Wisp ROI byte count: {len(pixels)} != {expected}"], [], []

    xs: list[int] = []
    ys: list[int] = []
    for index in range(width * height):
        red, green, blue = pixels[index * 3:index * 3 + 3]
        # Aer is #FFFF7E. Permit normal alpha blending against sky/stone while
        # rejecting grayscale crosshair, stone and blue sky pixels.
        if red >= 160 and green >= 160 and blue <= 240 and (
            red - blue >= 12 or green - blue >= 18
        ):
            xs.append(index % width)
            ys.append(index // width)
    return [], xs, ys


def _pixel_bounds(xs: list[int], ys: list[int]) -> tuple[int, int, int, int] | None:
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


def verify_aim_screenshot(path: Path) -> tuple[list[str], int, tuple[int, int, int, int] | None]:
    """Require Aer-tinted Wisp pixels near the 1024x768 viewport crosshair."""
    errors, xs, ys = _read_aer_pixels(path)
    if errors:
        return errors, 0, None

    count = len(xs)
    bbox = _pixel_bounds(xs, ys)
    if count < 12:
        errors.append(
            f"{path.name} crosshair ROI contains only {count} Aer-tinted pixels; expected at least 12"
        )
    else:
        center_x = sum(xs) / count
        center_y = sum(ys) / count
        width, height, _, _ = AIM_ROI
        if (center_x - width / 2) ** 2 + (center_y - height / 2) ** 2 > 48 ** 2:
            errors.append(
                f"{path.name} Aer-tinted pixels are not near the crosshair: "
                f"centroid=({center_x:.1f},{center_y:.1f})"
            )
    return errors, count, bbox


def verify_baseline_screenshot(path: Path) -> tuple[list[str], int, tuple[int, int, int, int] | None]:
    """Require a clean crosshair ROI before the Wisp is summoned."""
    errors, xs, ys = _read_aer_pixels(path)
    if errors:
        return errors, 0, None
    bbox = _pixel_bounds(xs, ys)
    if xs:
        errors.append(
            f"{path.name} baseline already contains {len(xs)} Aer-tinted pixels; "
            "capture must happen before Wisp spawn"
        )
    return errors, len(xs), bbox

def _first(pattern: str, text: str):
    return re.search(pattern, text)


def verify(log_text: str, status_text: str, screenshot_dir: Path) -> list[str]:
    errors: list[str] = []

    attack_input = _first(
        r"TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=(\d+) targetType=thaumcraft:wisp hitType=ENTITY distance=([0-9.]+) gameTime=(\d+) blockPos=none tick=(\d+)",
        log_text,
    )
    client_attack = _first(
        r"TC_SMOKE_WISP_CLIENT_ATTACK=YES player=(\d+) target=(\d+) tick=(\d+) distance=([0-9.]+) gameTime=(\d+)",
        log_text,
    )
    hurt = _first(
        r"TC_SMOKE_WISP_HURT=YES accepted=true source=(\d+) attacker=(\d+) amount=([0-9.]+) health=([0-9.]+)",
        log_text,
    )
    hit = _first(
        r"TC_SMOKE_WISP_TARGET_SET=YES source=(\d+) target=(\d+) aspect=aer",
        log_text,
    )
    sent = _first(
        r"TC_SMOKE_WISP_ZAP_SENT=YES source=(\d+) target=(\d+) aspect=aer",
        log_text,
    )
    received = _first(
        r"TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=(\d+) target=(\d+) color=#([0-9A-Fa-f]{6})",
        log_text,
    )
    wisp_camera = _first(
        r"TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=hitbox_center tick=(\d+)",
        log_text,
    )
    wisp_rendered = _first(
        r"TC_SMOKE_WISP_RENDERED=YES id=(\d+) aspect=aer tick=(\d+)",
        log_text,
    )
    wisp_frame_complete = _first(
        r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=(\d+) tick=(\d+)",
        log_text,
    )
    attackable_frame = _first(
        r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=(\d+) tick=(\d+) gameTime=(\d+) playerTick=(\d+) "
        r"hitType=ENTITY targetId=(\d+) targetType=thaumcraft:wisp blockPos=none",
        log_text,
    )
    rendered = _first(
        r"TC_SMOKE_WISP_ZAP_RENDERED=YES source=(\d+) target=(\d+) points=(\d+) age=([0-9.]+)"
        r" texture=thaumcraft:textures/misc/essentia\.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        log_text,
    )

    for label, match in (("client Wisp camera sample", wisp_camera),
                         ("client Wisp entity renderer", wisp_rendered),
                         ("completed level render frame", wisp_frame_complete),
                         ("same-frame Wisp EntityHitResult", attackable_frame),
                         ("client attack input/hit result", attack_input),
                         ("client selected attack target", client_attack),
                         ("server accepted damage", hurt),
                         ("player hit/target", hit), ("server payload send", sent),
                         ("client payload receive", received), ("client additive render", rendered)):
        if match is None:
            errors.append(f"missing real gameplay/render evidence: {label}")

    if wisp_camera and wisp_rendered:
        if wisp_camera.group(1) != wisp_rendered.group(1):
            errors.append("camera sample and rendered Wisp use different entity IDs")
        if wisp_camera.group(2) != wisp_rendered.group(2):
            errors.append("camera sample and rendered Wisp are from different ticks")
    if wisp_rendered and wisp_frame_complete:
        if wisp_rendered.group(1) != wisp_frame_complete.group(1):
            errors.append("completed level frame and rendered Wisp use different entity IDs")
        if wisp_rendered.group(2) != wisp_frame_complete.group(2):
            errors.append("completed level frame and rendered Wisp use different ticks")
    if wisp_rendered and hit and wisp_rendered.group(1) != hit.group(1):
        errors.append("rendered Wisp and player-hit source use different entity IDs")
    if attack_input and wisp_rendered and attack_input.group(1) != wisp_rendered.group(1):
        errors.append("client hit result did not select the rendered Wisp")
    if attackable_frame:
        if attackable_frame.group(1) != attackable_frame.group(5):
            errors.append("AFTER_LEVEL EntityHitResult ID differs from the rendered Wisp ID")
        if wisp_rendered and attackable_frame.group(1) != wisp_rendered.group(1):
            errors.append("attackable AFTER_LEVEL frame belongs to a different Wisp")
        if attack_input:
            if attackable_frame.group(1) != attack_input.group(1):
                errors.append("attackable AFTER_LEVEL frame and real click selected different Wisps")
            game_time_delta = int(attack_input.group(3)) - int(attackable_frame.group(3))
            if game_time_delta < 0 or game_time_delta > 1:
                errors.append(
                    "real click did not follow its actual Wisp EntityHitResult within one game tick: "
                    f"frameGameTime={attackable_frame.group(3)} clickGameTime={attack_input.group(3)}"
                )
            player_tick_delta = int(attack_input.group(4)) - int(attackable_frame.group(4))
            if player_tick_delta < 0 or player_tick_delta > 1:
                errors.append(
                    "real click did not follow its attackable Wisp frame within one player tick: "
                    f"framePlayerTick={attackable_frame.group(4)} clickPlayerTick={attack_input.group(4)}"
                )
            frame_position = log_text.find(attackable_frame.group(0))
            input_position = log_text.find(attack_input.group(0))
            if frame_position < 0 or input_position < frame_position:
                errors.append("real click occurred before the matching attackable AFTER_LEVEL frame")
    if attackable_frame is None:
        errors.append("no completed frame exposed the live Wisp as Minecraft.hitResult")
    if attack_input and client_attack and attack_input.group(1) != client_attack.group(2):
        errors.append("client hit result and AttackEntityEvent selected different entities")
    if attack_input and client_attack:
        client_game_time_delta = int(client_attack.group(5)) - int(attack_input.group(3))
        if client_game_time_delta < 0 or client_game_time_delta > 1:
            errors.append("AttackEntityEvent was not emitted in the same or immediately following game tick")
    if client_attack and hurt and client_attack.group(1, 2) != hurt.group(2, 1):
        errors.append("client AttackEntityEvent and server hurt callback use different player/Wisp IDs")
    if hurt and hit and hurt.group(1, 2) != hit.group(1, 2):
        errors.append("server accepted-damage callback and target acquisition use different IDs")
    if attack_input and float(attack_input.group(2)) > 3.0:
        errors.append("client-selected Wisp is outside the configured melee reach")
    if hit and sent and hit.group(1, 2) != sent.group(1, 2):
        errors.append("server hit and zap payload use different source/target entity IDs")
    if sent and received and sent.group(1, 2) != received.group(1, 2):
        errors.append("server send and client receive use different source/target entity IDs")
    if received and received.group(3).upper() != "FFFF7E":
        errors.append(f"Aer color mismatch: expected FFFF7E, got {received.group(3).upper()}")
    if received and rendered and received.group(1, 2) != rendered.group(1, 2):
        errors.append("client receive and render use different source/target entity IDs")
    if rendered:
        if int(rendered.group(3)) <= 2:
            errors.append("rendered FXBolt has no interior polycone points")
        age = float(rendered.group(4))
        if not 0.0 <= age < 3.0:
            errors.append(f"FXBolt render age outside BETA26 three-tick lifetime: {age}")

    for marker in REQUIRED_LOG_MARKERS:
        if marker not in log_text:
            errors.append(f"missing real server world marker: {marker}")

    for marker in REQUIRED_STATUS:
        if marker not in status_text:
            errors.append(f"missing real-client status marker: {marker}")

    target_set_position = log_text.find("TC_SMOKE_WISP_TARGET_SET=YES")
    knockback_set_position = log_text.find(
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0"
    )
    stabilized_position = status_text.find("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL")
    camera_reaim_position = status_text.find("WISP_CAMERA_REAIM=CLIENT_TRACKING", stabilized_position + 1)
    if target_set_position < 0 or knockback_set_position <= target_set_position:
        errors.append("player knockback resistance was not confirmed after the accepted Wisp hit")
    if stabilized_position < 0 or camera_reaim_position < 0 or stabilized_position > camera_reaim_position:
        errors.append("post-hit player stabilization was not confirmed before client camera tracking")

    telemetry = _first(
        r"(?m)^WISP_CAMERA_TELEMETRY=PASS id=(\d+) tick=(\d+) playerX=([0-9.+-]+) playerY=([0-9.+-]+) playerZ=([0-9.+-]+) wispX=([0-9.+-]+) wispY=([0-9.+-]+) wispZ=([0-9.+-]+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    first_frame_id = _first(r"(?m)^WISP_RENDER_FIRST_FRAME_ID=(\d+)$", status_text)
    first_frame_tick = _first(r"(?m)^WISP_RENDER_FIRST_FRAME_TICK=(\d+)$", status_text)
    capture_tick = _first(r"(?m)^WISP_RENDER_CAPTURE_TICK=(\d+)$", status_text)
    capture_id = _first(r"(?m)^WISP_RENDER_CAPTURE_ID=(\d+)$", status_text)
    first_frame_camera_sample = _first(
        r"(?m)^WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=(TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=hitbox_center tick=(\d+) .*? aimDot=([0-9.+-]+)(?: .*)?)$",
        status_text,
    )
    capture_camera = _first(
        r"(?m)^TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=hitbox_center tick=(\d+) "
        r"playerX=([0-9.+-]+) playerY=([0-9.+-]+) playerZ=([0-9.+-]+).*"
        r"wispX=([0-9.+-]+) wispY=([0-9.+-]+) wispZ=([0-9.+-]+).*"
        r"aimDot=([0-9.+-]+)(?: |$)",
        status_text,
    )
    frame_status = _first(
        r"(?m)^WISP_RENDER_FRAME_COMPLETE=YES id=(\d+) tick=(\d+)$",
        status_text,
    )
    first_frame_line_number = _first(
        r"(?m)^WISP_RENDER_FIRST_FRAME_LINE_NUMBER=(\d+)$", status_text
    )
    attack_camera_frame = _first(
        r"(?m)^WISP_ATTACK_CAMERA_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    attack_camera_complete = _first(
        r"(?m)^WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES id=(\d+) tick=(\d+)$",
        status_text,
    )
    attack_camera_sample = _first(
        r"(?m)^WISP_ATTACK_CAMERA_SAMPLE=(TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=hitbox_center tick=(\d+) .*? aimDot=([0-9.+-]+)(?: .*)?)$",
        status_text,
    )
    attack_camera_reaim = _first(
        r"(?m)^WISP_ATTACK_CAMERA_REAIM=(PREAIMED_FIRST_FRAME|LIVE_ENTITY_FACING)(?: (.+))?$",
        status_text,
    )
    attackable_frame_status = _first(
        r"(?m)^WISP_ATTACKABLE_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) gameTime=(\d+) playerTick=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    if frame_status is None:
        errors.append("missing first post-LevelRenderer completed-frame status")
    else:
        if wisp_rendered and frame_status.group(1) != wisp_rendered.group(1):
            errors.append("first completed-frame status and rendered Wisp use different entity IDs")
        if wisp_rendered and frame_status.group(2) != wisp_rendered.group(2):
            errors.append("first completed-frame status and rendered Wisp use different ticks")
    if first_frame_line_number is None:
        errors.append("missing exact first-frame client-log cursor")
    elif frame_status is not None:
        frame_prefix = (
            f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id={frame_status.group(1)} "
            f"tick={frame_status.group(2)} "
        )
        actual_frame_line = next(
            (index for index, line in enumerate(log_text.splitlines(), start=1)
             if line.startswith(frame_prefix)),
            None,
        )
        if actual_frame_line is None or int(first_frame_line_number.group(1)) != actual_frame_line:
            errors.append(
                "first-frame cursor does not point to the exact completed frame: "
                f"expectedLine={actual_frame_line} actualLine={first_frame_line_number.group(1)}"
            )
    if attack_camera_frame is None:
        errors.append("missing camera frame used for the real Wisp attack")
    if attack_camera_complete is None:
        errors.append("missing completed-frame marker for the real attack camera sample")
    if attack_camera_sample is None:
        errors.append("missing copied live camera sample used for the real attack")
    if attack_camera_reaim is None:
        errors.append("missing provenance for the camera sample used by the real attack")
    if attackable_frame_status is None:
        errors.append("missing status for the actual attackable Wisp frame")
    elif attackable_frame is None or attackable_frame_status.group(1, 2, 3) != attackable_frame.group(1, 2, 5):
        errors.append("attackable-frame status does not match the real AFTER_LEVEL hit-result marker")
    if first_frame_camera_sample is None:
        errors.append("missing camera sample copied from the first completed Wisp frame")
    if attack_camera_frame and attack_camera_complete and attack_camera_sample:
        attack_id, attack_tick = attack_camera_frame.group(1, 2)
        attack_target_id = attack_camera_frame.group(3)
        complete_id, complete_tick = attack_camera_complete.group(1, 2)
        sample_id, sample_tick = attack_camera_sample.group(2, 3)
        attack_distance, attack_aim_dot = map(float, attack_camera_frame.groups()[3:])
        sample_aim_dot = float(attack_camera_sample.group(4))
        if attack_target_id != attack_id:
            errors.append("attack camera status names a different EntityHitResult target")
        if (attack_id, attack_tick) != (complete_id, complete_tick):
            errors.append("attack camera and completed-frame status use different ID/tick")
        if (attack_id, attack_tick) != (sample_id, sample_tick):
            errors.append("attack camera status and copied camera sample use different ID/tick")
        if attackable_frame and (attack_id, attack_tick) != (attackable_frame.group(1), attackable_frame.group(2)):
            errors.append("attack camera sample does not match the AFTER_LEVEL frame that exposed the Wisp hit result")
        if attackable_frame_status and (attack_id, attack_tick) != attackable_frame_status.group(1, 2):
            errors.append("attack camera status does not match the attackable-frame status")
        if attack_input and attack_id != attack_input.group(1):
            errors.append("real attack selected a Wisp different from the attack camera sample")
        if wisp_rendered and attack_id != wisp_rendered.group(1):
            errors.append("attack camera sample and rendered Wisp use different entity IDs")
        if abs(attack_aim_dot - sample_aim_dot) > 0.0001:
            errors.append("attack camera status aimDot differs from the copied camera sample")
        attack_position = _first(
            r"playerX=([0-9.+-]+) playerY=([0-9.+-]+) playerZ=([0-9.+-]+).*"
            r"wispX=([0-9.+-]+) wispY=([0-9.+-]+) wispZ=([0-9.+-]+)",
            attack_camera_sample.group(1),
        )
        if attack_position is None:
            errors.append("attack camera sample lacks player/Wisp positions")
        else:
            attack_px, attack_py, attack_pz, attack_wx, attack_wy, attack_wz = map(float, attack_position.groups())
            measured_attack_distance = math.dist(
                (attack_px, attack_py, attack_pz),
                (attack_wx, attack_wy, attack_wz),
            )
            if not (
                12.0 <= attack_px <= 13.0
                and 148.5 <= attack_py <= 149.5
                and 10.0 <= attack_pz <= 11.0
                and attack_distance <= 3.0
                and abs(attack_distance - measured_attack_distance) <= 0.1
                and math.isfinite(attack_aim_dot)
                and -1.0001 <= attack_aim_dot <= 1.0001
            ):
                errors.append(
                    "real attack camera was outside the fixture/melee bounds or had invalid dot telemetry: "
                    f"player=({attack_px:.3f},{attack_py:.3f},{attack_pz:.3f}) "
                    f"distance={attack_distance:.3f} aimDot={attack_aim_dot:.4f}"
                )
        attack_frame_marker = f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id={attack_id} tick={attack_tick}"
        sample_position = log_text.find(attack_camera_sample.group(1))
        completed_position = log_text.find(attack_frame_marker, sample_position + 1) if sample_position >= 0 else -1
        attack_position_in_log = log_text.find(attack_input.group(0)) if attack_input else -1
        if sample_position < 0:
            errors.append("copied attack camera sample does not occur in the client log")
        elif completed_position < sample_position:
            errors.append("attack camera sample has no later matching completed level frame")
        elif attack_position_in_log < completed_position:
            errors.append("real attack input did not occur after its aligned completed camera frame")
        reaim_position = status_text.find(attack_camera_reaim.group(0)) if attack_camera_reaim else -1
        frame_status_position = status_text.find(attack_camera_frame.group(0))
        if reaim_position < 0 or frame_status_position < reaim_position:
            errors.append("attack camera frame was not recorded after its declared camera source")
        if attack_camera_reaim is not None and attack_camera_reaim.group(1) == "PREAIMED_FIRST_FRAME":
            first_frame_status_position = status_text.find("WISP_RENDER_FIRST_FRAME=YES")
            if first_frame_status_position < 0 or reaim_position < first_frame_status_position:
                errors.append("pre-aimed attack camera was not sourced after the first completed Wisp frame")
            if first_frame_camera_sample is not None:
                if attack_camera_sample.group(2) != first_frame_camera_sample.group(2):
                    errors.append("pre-aimed attack sample uses a different Wisp entity")
                if int(attack_camera_sample.group(3)) < int(first_frame_camera_sample.group(3)):
                    errors.append("pre-aimed attack sample predates the first completed Wisp frame")
        elif attack_camera_reaim is not None and status_text.find("WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION") < 0:
            errors.append("live-reposition attack sample lacks its server-confirmed player teleport marker")

    if telemetry is None:
        errors.append("missing first-frame camera telemetry")
    else:
        camera_id, camera_tick = telemetry.group(1), telemetry.group(2)
        player_x, player_y, player_z, wisp_x, wisp_y, wisp_z, distance, aim_dot = map(float, telemetry.groups()[2:])
        if wisp_rendered and camera_id != wisp_rendered.group(1):
            errors.append("first-frame camera telemetry and rendered Wisp use different entity IDs")
        if wisp_rendered and camera_tick != wisp_rendered.group(2):
            errors.append("first-frame camera telemetry and rendered Wisp use different ticks")
        if first_frame_id is None or first_frame_tick is None:
            errors.append("missing first-frame entity ID/tick status")
        elif (camera_id, camera_tick) != (first_frame_id.group(1), first_frame_tick.group(1)):
            errors.append("first-frame camera telemetry and first-frame status use different ID/tick")
        if capture_id is None or capture_tick is None:
            errors.append("missing post-hit screenshot entity ID/tick")
        elif camera_id != capture_id.group(1):
            errors.append("post-hit screenshot and first-frame camera use different Wisp IDs")
        if capture_camera is None:
            errors.append("missing camera sample copied at post-hit screenshot capture")
        elif capture_id and capture_tick and capture_camera.group(1, 2) != (capture_id.group(1), capture_tick.group(1)):
            errors.append("post-hit screenshot camera sample does not match capture ID/tick")
        if capture_camera is not None:
            capture_player_x, capture_player_y, capture_player_z, capture_wisp_x, capture_wisp_y, capture_wisp_z, capture_aim_dot = map(
                float, capture_camera.groups()[2:]
            )
            capture_distance = math.dist(
                (capture_player_x, capture_player_y, capture_player_z),
                (capture_wisp_x, capture_wisp_y, capture_wisp_z),
            )
            if not (
                12.0 <= capture_player_x <= 13.0
                and 148.5 <= capture_player_y <= 149.5
                and 10.0 <= capture_player_z <= 11.0
                and capture_distance <= 16.0
                and capture_aim_dot >= 0.99
            ):
                errors.append(
                    "post-hit camera re-aim moved the player off the verified platform or lost the live Wisp: "
                    f"player=({capture_player_x:.3f},{capture_player_y:.3f},{capture_player_z:.3f}) "
                    f"distance={capture_distance:.3f} aimDot={capture_aim_dot:.4f}"
                )
        if capture_id and capture_tick:
            capture_frame = f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id={capture_id.group(1)} tick={capture_tick.group(1)}"
            if capture_frame not in log_text:
                errors.append("post-hit screenshot was not taken after its matching completed level frame")
        if first_frame_camera_sample is not None:
            first_sample_id, first_sample_tick = first_frame_camera_sample.group(2, 3)
            if first_frame_id and first_frame_tick and (first_sample_id, first_sample_tick) != (first_frame_id.group(1), first_frame_tick.group(1)):
                errors.append("copied first-frame camera sample has a different Wisp ID/tick")
            first_sample_line = first_frame_camera_sample.group(1)
            first_sample_position = _first(
                r"playerX=([0-9.+-]+) playerY=([0-9.+-]+) playerZ=([0-9.+-]+).*"
                r"wispX=([0-9.+-]+) wispY=([0-9.+-]+) wispZ=([0-9.+-]+)",
                first_sample_line,
            )
            first_sample_aim = float(first_frame_camera_sample.group(4))
            if first_sample_position is None:
                errors.append("first-frame camera sample lacks player/Wisp positions")
            else:
                fx, fy, fz, wx, wy, wz = map(float, first_sample_position.groups())
                fd = math.dist((fx, fy, fz), (wx, wy, wz))
                if not (12.0 <= fx <= 13.0 and 148.5 <= fy <= 149.5 and 10.0 <= fz <= 11.0 and fd <= 3.0 and first_sample_aim >= 0.99):
                    errors.append("first completed Wisp frame was not aligned inside melee reach on the fixture platform")
        measured_distance = math.dist((player_x, player_y, player_z), (wisp_x, wisp_y, wisp_z))
        if not (12.0 <= player_x <= 13.0 and 148.5 <= player_y <= 149.5
                and 10.0 <= player_z <= 11.0 and distance <= 3.0
                and abs(distance - measured_distance) <= 0.1 and aim_dot >= 0.99):
            errors.append(
                "first-frame camera is not on the verified platform, within melee reach and centered on Wisp: "
                f"player=({player_x:.3f},{player_y:.3f},{player_z:.3f}) "
                f"wisp=({wisp_x:.3f},{wisp_y:.3f},{wisp_z:.3f}) "
                f"distance={distance:.3f} aimDot={aim_dot:.4f}"
            )

    if "Successfully filled 81 block(s)" not in status_text:
        errors.append("fixture platform was not confirmed as the exact 81-block absolute fill")

    aim_marker_count = status_text.count("WISP_AIM_SCREENSHOT_VISIBLE=YES")
    if aim_marker_count < len(AIM_SCREENSHOTS):
        errors.append(
            f"only {aim_marker_count}/{len(AIM_SCREENSHOTS)} crosshair-visibility markers were recorded"
        )

    baseline_marker_count = status_text.count("WISP_BASELINE_SCREENSHOT_CLEAR=YES")
    if baseline_marker_count < len(BASELINE_SCREENSHOTS):
        errors.append(
            f"only {baseline_marker_count}/{len(BASELINE_SCREENSHOTS)} pre-spawn baseline markers were recorded"
        )
    for name in BASELINE_SCREENSHOTS:
        baseline_errors, _, _ = verify_baseline_screenshot(screenshot_dir / name)
        errors.extend(baseline_errors)

    metrics = {}
    for key in ("WISP_ZAP_BASELINE_NOISE", "WISP_ZAP_TRANSIENT_PIXEL_DIFF"):
        match = re.search(rf"(?m)^{key}=([0-9]+)$", status_text)
        if not match:
            errors.append(f"missing numeric screenshot evidence: {key}")
        else:
            metrics[key] = int(match.group(1))
    if len(metrics) == 2:
        required_diff = metrics["WISP_ZAP_BASELINE_NOISE"] * 2 + 8
        if metrics["WISP_ZAP_TRANSIENT_PIXEL_DIFF"] < required_diff:
            errors.append(
                "transient zap pixel delta does not exceed baseline animation noise: "
                f"{metrics['WISP_ZAP_TRANSIENT_PIXEL_DIFF']} < {required_diff}"
            )

    for name in SCREENSHOTS:
        path = screenshot_dir / name
        if not path.is_file() or path.stat().st_size < 128:
            errors.append(f"missing or empty real-client screenshot: {name}")

    for name in AIM_SCREENSHOTS:
        aim_errors, _, _ = verify_aim_screenshot(screenshot_dir / name)
        errors.extend(aim_errors)

    for pattern in BAD_LOG_PATTERNS:
        if pattern.search(log_text):
            errors.append(f"client log contains a crash or essentia texture error: {pattern.pattern}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aim-only", type=Path, help="verify one crosshair-centered Aer Wisp screenshot")
    parser.add_argument("--baseline-only", type=Path, help="verify one empty pre-Wisp baseline screenshot")
    parser.add_argument("client_log", type=Path, nargs="?")
    parser.add_argument("status_file", type=Path, nargs="?")
    parser.add_argument("screenshots", type=Path, nargs="?")
    args = parser.parse_args()

    if args.aim_only is not None:
        errors, count, bbox = verify_aim_screenshot(args.aim_only)
        if errors:
            for error in errors:
                print(f"WISP_AIM_SCREENSHOT_ERROR: {error}", file=sys.stderr)
            return 1
        print(f"WISP_AIM_SCREENSHOT_VISIBLE=YES pixels={count} bbox={bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}")
        return 0

    if args.baseline_only is not None:
        errors, count, _ = verify_baseline_screenshot(args.baseline_only)
        if errors:
            for error in errors:
                print(f"WISP_BASELINE_SCREENSHOT_ERROR: {error}", file=sys.stderr)
            return 1
        print(f"WISP_BASELINE_SCREENSHOT_CLEAR=YES pixels={count}")
        return 0

    if args.client_log is None or args.status_file is None or args.screenshots is None:
        parser.error("client_log, status_file and screenshots are required unless --aim-only is used")

    errors = verify(
        args.client_log.read_text(encoding="utf-8", errors="replace"),
        args.status_file.read_text(encoding="utf-8", errors="replace"),
        args.screenshots,
    )
    if errors:
        for error in errors:
            print(f"WISP_ZAP_CLIENT_EVIDENCE_ERROR: {error}", file=sys.stderr)
        return 1
    print("WISP_ZAP_REAL_CLIENT_EVIDENCE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
