#!/usr/bin/env python3
"""Fail-closed verifier for a real two-entity BETA26 Wisp zap client smoke."""
from pathlib import Path
import argparse
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
from fractions import Fraction
from collections import deque


SCREENSHOTS = (
    "78-wisp-aim-check.png",
    "79-wisp-before-a.png",
    "79-wisp-before-b.png",
    "79-wisp-third-person-before-zap.png",
    "80-wisp-zap-frame-a.png",
    "81-wisp-zap-frame-b.png",
    "82-wisp-zap-frame-c.png",
    "83-wisp-zap-after-expiry.png",
)

AIM_SCREENSHOTS = SCREENSHOTS[:1]
BASELINE_SCREENSHOTS = SCREENSHOTS[1:3]
BOLT_BASELINE_SCREENSHOT = "79-wisp-third-person-before-zap.png"
AIM_ROI = (128, 128, 448, 320)
# The smoke viewport is 1024x768 at the left of the 1280x800 X11 root.
# FXBolt is compared with the latest same-camera frame immediately before payload.
BOLT_ROI = (512, 480, 256, 280)  # width, height, left, top
VIEWPORT_WIDTH = 1024
VIEWPORT_HEIGHT = 768

REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_WISP_CHUNK_LOADED=YES",
    "TC_SMOKE_WISP_RENDERED=YES",
    "TC_SMOKE_WISP_FRAME_COMPLETE=YES",
    "TC_SMOKE_WISP_ATTACK_INPUT=YES",
    "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
    "TC_SMOKE_WISP_CAMERA_TRACK=YES",
    "TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES",
    "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES",
    "TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES",
    "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES",
    "TC_SMOKE_WISP_HURT=YES accepted=true",
    "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
    "TC_SMOKE_WISP_NAMED_SOURCE=YES",
    "TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES",
    "TC_SMOKE_WISP_ZAP_FRAME=YES",
    "TC_SMOKE_WISP_ZAP_EXPIRED=YES",
)

REQUIRED_STATUS = (
    "WISP_FIXTURE_PLATFORM_FILL=YES",
    "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
    "WISP_FIXTURE_TELEPORT=YES",
    "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line=",
    "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
    "WISP_ATTACK_CLICK_DISPATCHED=YES",
    "WISP_ZAP_AIM=PREAIMED_SPAWN",
    "WISP_CAMERA_TELEMETRY=PASS",
    "WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL",
    "WISP_CAMERA_CAPTURE_TELEMETRY=PASS",
    "WISP_RENDER_AFTER_PREAIMED_SPAWN=YES",
    "WISP_RENDER_PREATTACK_TRACKING=YES",
    "WISP_RENDER_FRAME_COMPLETE=YES",
    "WISP_RENDER_FIRST_FRAME=YES",
    "WISP_RENDER_FIRST_FRAME_ID=",
    "WISP_RENDER_FIRST_FRAME_TICK=",
    "WISP_RENDER_FIRST_FRAME_LINE_NUMBER=",
    "WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=",
    "WISP_RENDER_CAPTURE_ID=",
    "WISP_RENDER_CAPTURE_TICK=",
    "WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES",
    "WISP_RENDER_CAPTURE_SOURCE=ATTACK_INPUT_FRAME",
    "WISP_RENDER_CAPTURE_METHOD=VIDEO_PRE_ATTACK_FRAME",
    "WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=",
    "WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=",
    "WISP_ATTACK_INPUT_FRAME_VIDEO_OFFSET=",
    "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES",
    "WISP_ATTACK_TARGET_CONFIRMED=YES",
    "WISP_ATTACKABLE_FRAME=YES",
    "WISP_ATTACK_INPUT_FRAME=YES",
    "WISP_ATTACK_INPUT_FRAME_CORRELATION=PASS source=InputEvent+AttackEntityEvent",
    "WISP_ATTACK_CAMERA_REAIM=",
    "WISP_ATTACK_CAMERA_FRAME=YES",
    "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
    "WISP_ATTACK_CAMERA_FRAME_USED=YES",
    "WISP_ATTACK_HURT_ACCEPTED=YES",
    "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
    "WISP_ZAP_TEST_TIME=MIDNIGHT",
    "WISP_CAMERA_SIDE_VIEW=YES id=",
    "WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=",
    "WISP_ZAP_PAYLOAD_RECEIVED=YES source=",
    "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD",
    "WISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES",
    "WISP_ZAP_CAMERA_PERSPECTIVE_RESTORED=FIRST_PERSON",
    "WISP_ZAP_FRAME_OBSERVED=YES",
    "WISP_ZAP_VIDEO_STARTED=YES",
    "WISP_ZAP_VIDEO_STOPPED=YES",
    "WISP_ZAP_FRAME_CAPTURE=YES",
    "WISP_ZAP_VIDEO_FRAME_INDEX=YES",
    "WISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES",
    "WISP_ZAP_EFFECT_EXPIRED=YES",
    "WISP_AIM_SCREENSHOT_VISIBLE=YES",
    "WISP_ESSENTIA_TEXTURE_NO_LOAD_ERRORS=PASS",
    "WISP_ALL_WISP_TEXTURES_NO_LOAD_ERRORS=PASS",
    "WISP_ZAP_REAL_CLIENT=PASS",
)

BAD_LOG_PATTERNS = (
    re.compile(
        r"(?:Missing textures|Unable to load texture|Failed to load texture).*(?:essentia|auranodes|particles)\.png"
        r"|(?:essentia|auranodes|particles)\.png.*(?:missing texture|Unable to load)",
        re.IGNORECASE,
    ),
    re.compile(r'Exception in thread "Render thread"|Minecraft has crashed|Mod loading has failed', re.IGNORECASE),
)




def _is_crosshair_pixel(x: int, y: int, width: int, height: int) -> bool:
    """Identify the fixed 2px-thick vanilla crosshair drawn over the scene."""
    center_x = width // 2 - 1
    center_y = height // 2 - 1
    vertical = center_x - 1 <= x <= center_x and center_y - 9 <= y <= center_y + 8
    horizontal = center_y - 1 <= y <= center_y and center_x - 9 <= x <= center_x + 8
    return vertical or horizontal


def _read_aer_pixels(path: Path) -> tuple[list[str], list[int], list[int]]:
    """Read Aer-tinted scene pixels, excluding the HUD crosshair overlay."""
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
        x = index % width
        y = index // width
        if _is_crosshair_pixel(x, y, width, height):
            continue
        red, green, blue = pixels[index * 3:index * 3 + 3]
        # Aer is #FFFF7E. Permit normal alpha blending against sky/stone;
        # the warm-tinted vanilla crosshair is masked separately above.
        if red >= 160 and green >= 160 and blue <= 240 and (
            red - blue >= 12 or green - blue >= 18
        ):
            xs.append(x)
            ys.append(y)
    return [], xs, ys


def _pixel_bounds(xs: list[int], ys: list[int]) -> tuple[int, int, int, int] | None:
    return (min(xs), min(ys), max(xs), max(ys)) if xs else None


def verify_aim_screenshot(path: Path) -> tuple[list[str], int, tuple[int, int, int, int] | None]:
    """Require visible Wisp pixels in the central attack-frame ROI.

    Exact aim is established independently by the same-ID EntityHitResult,
    completed camera frame, aim-dot telemetry and accepted server damage. The
    billboard centroid is not a hitbox: its screen projection can sit well below
    the crosshair even when the client actually selected and hit that entity.
    """
    errors, xs, ys = _read_aer_pixels(path)
    if errors:
        return errors, 0, None

    count = len(xs)
    bbox = _pixel_bounds(xs, ys)
    if count < 12:
        errors.append(
            f"{path.name} attack-frame ROI contains only {count} Aer-tinted pixels; expected at least 12"
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


def _bolt_segment_candidates(
    path: Path, baseline_path: Path, expected_visible: bool
) -> tuple[list[str], list[dict[str, float]]]:
    """Find an Aer-yellow bolt from its additive delta against a same-camera no-zap frame."""
    for candidate_path in (path, baseline_path):
        if not candidate_path.is_file() or candidate_path.stat().st_size < 128:
            return [f"missing or empty FXBolt image: {candidate_path.name}"], []

    roi_width, roi_height, roi_left, roi_top = BOLT_ROI
    pixel_count = roi_width * roi_height
    expected_bytes = pixel_count * 3

    def read_roi(candidate_path: Path) -> tuple[list[str], bytes | None]:
        try:
            result = subprocess.run(
                [
                    "convert", str(candidate_path),
                    "-crop", f"{roi_width}x{roi_height}+{roi_left}+{roi_top}",
                    "+repage", "-depth", "8", "RGB:-",
                ],
                capture_output=True,
                check=False,
            )
        except FileNotFoundError:
            return ["ImageMagick convert is required to inspect FXBolt frames"], None
        if result.returncode != 0:
            detail = result.stderr.decode("utf-8", errors="replace").strip()
            return [f"cannot decode FXBolt image {candidate_path.name}: {detail}"], None
        if len(result.stdout) != expected_bytes:
            return [
                f"unexpected FXBolt ROI byte count for {candidate_path.name}: "
                f"{len(result.stdout)} != {expected_bytes}"
            ], None
        return [], result.stdout

    frame_errors, frame_pixels = read_roi(path)
    baseline_errors, baseline_pixels = read_roi(baseline_path)
    if frame_errors or baseline_errors:
        return frame_errors + baseline_errors, []
    assert frame_pixels is not None and baseline_pixels is not None

    # The Wisp can move several blocks between the melee hit and the delayed
    # 20-tick zap. Do not anchor the bolt to its stale pre-payload billboard
    # position; compare against the latest same-camera frame and detect the
    # elongated additive Aer component itself.
    mask = bytearray(pixel_count)
    for index in range(pixel_count):
        offset = index * 3
        frame_r, frame_g, frame_b = frame_pixels[offset], frame_pixels[offset + 1], frame_pixels[offset + 2]
        base_r, base_g, base_b = baseline_pixels[offset], baseline_pixels[offset + 1], baseline_pixels[offset + 2]
        delta_r = frame_r - base_r
        delta_g = frame_g - base_g
        delta_b = frame_b - base_b

        # BETA26's exact alpha values (2/3, 1/3, 0.1 at ages 1/2/3)
        # are asserted by LegacyWispZapGeometryTest. A screen-space component
        # mean is not a direct alpha measurement: textured UVs and the changing
        # polycone raster coverage vary between ages. Require a clear monotonic
        # pixel-level drop (at least 10%) rather than an arbitrary 20% ratio.
        if (
            delta_r >= 8
            and delta_g >= 8
            and delta_b >= 0
            and delta_r - delta_b >= 4
            and delta_g - delta_b >= 4
            and delta_r >= delta_b * 1.25
            and delta_g >= delta_b * 1.25
            and abs(delta_r - delta_g) <= max(8, int(max(delta_r, delta_g) * 0.35))
        ):
            mask[index] = 1

    visited = bytearray(pixel_count)
    candidates: list[dict[str, float]] = []
    for first in range(pixel_count):
        if not mask[first] or visited[first]:
            continue
        queue = deque([first])
        visited[first] = 1
        count = 0
        sum_x = sum_y = sum_xx = sum_yy = sum_xy = 0.0
        sum_aer_delta = 0.0
        min_x = min_y = 1 << 30
        max_x = max_y = -1
        while queue:
            current = queue.popleft()
            x = current % roi_width
            y = current // roi_width
            count += 1
            offset = current * 3
            sum_aer_delta += (
                (frame_pixels[offset] - baseline_pixels[offset])
                + (frame_pixels[offset + 1] - baseline_pixels[offset + 1])
            ) / 2.0
            sum_x += x
            sum_y += y
            sum_xx += x * x
            sum_yy += y * y
            sum_xy += x * y
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
            for neighbour_y in range(max(0, y - 1), min(roi_height, y + 2)):
                row = neighbour_y * roi_width
                for neighbour_x in range(max(0, x - 1), min(roi_width, x + 2)):
                    neighbour = row + neighbour_x
                    if mask[neighbour] and not visited[neighbour]:
                        visited[neighbour] = 1
                        queue.append(neighbour)

        if count < 64:
            continue
        mean_x = sum_x / count
        mean_y = sum_y / count

        variance_x = max(0.0, sum_xx / count - mean_x * mean_x)
        variance_y = max(0.0, sum_yy / count - mean_y * mean_y)
        covariance = sum_xy / count - mean_x * mean_y
        discriminant = math.sqrt(
            max(0.0, (variance_x - variance_y) ** 2 + 4.0 * covariance ** 2)
        )
        major_eigenvalue = (variance_x + variance_y + discriminant) / 2.0
        minor_eigenvalue = max(
            0.0, (variance_x + variance_y - discriminant) / 2.0
        )
        major_length = math.sqrt(max(0.0, 12.0 * major_eigenvalue))
        minor_width = math.sqrt(max(0.0, 12.0 * minor_eigenvalue))
        elongation = major_eigenvalue / max(minor_eigenvalue, 0.5)
        angle = 0.5 * math.atan2(2.0 * covariance, variance_x - variance_y)
        angle_degrees = math.degrees(angle)

        # Reject compact billboards, short stars and out-of-ROI HUD/text changes.
        # The smoke camera is broadside to the beam, so a valid BETA26 FXBolt can
        # project nearly horizontally. Same-camera additive delta, component
        # size/elongation and the effect ROI distinguish it from static UI.
        if (
            major_length >= 40.0
            and minor_width <= 28.0
            and elongation >= 8.0
            and abs(angle_degrees) <= 78.0
        ):
            candidates.append(
                {
                    "pixels": float(count),
                    "mean_aer_delta": sum_aer_delta / count,
                    "length": major_length,
                    "width": minor_width,
                    "elongation": elongation,
                    "angle_degrees": angle_degrees,
                    "left": float(min_x + roi_left),
                    "top": float(min_y + roi_top),
                    "right": float(max_x + roi_left),
                    "bottom": float(max_y + roi_top),
                }
            )

    return [], candidates


def verify_bolt_segment(
    path: Path, expected_visible: bool, baseline_path: Path
) -> tuple[list[str], dict[str, float] | None]:
    errors, candidates = _bolt_segment_candidates(path, baseline_path, expected_visible)
    if errors:
        return errors, None
    if expected_visible and not candidates:
        return [
            f"{path.name} has no elongated Aer-yellow FXBolt segment in the effect ROI"
        ], None
    if not expected_visible and candidates:
        candidate = max(candidates, key=lambda item: item["length"])
        return [
            f"{path.name} still contains a bolt-like segment after expiry: "
            f"pixels={candidate['pixels']:.0f} length={candidate['length']:.1f}"
        ], candidate
    if not candidates:
        return [], None
    return [], max(candidates, key=lambda item: item["length"])


def verify_bolt_cycle(
    frames: tuple[tuple[int, Path], ...], expiry_path: Path, baseline_path: Path
) -> tuple[list[str], list[tuple[int, dict[str, float]]], dict[str, float] | None]:
    """Require pixel-confirmed FXBolt geometry at each BETA26 particle age 1, 2 and 3."""
    errors: list[str] = []
    visible: list[tuple[int, dict[str, float]]] = []
    frame_ages = [age for age, _ in frames]
    expected_ages = [1, 2, 3]
    if frame_ages != expected_ages:
        errors.append(f"expected exactly FXBolt frames for ages {expected_ages}, got {frame_ages}")
    for age, path in frames:
        frame_errors, candidates = _bolt_segment_candidates(path, baseline_path, True)
        errors.extend(f"FXBolt age={age}: {error}" for error in frame_errors)
        if candidates:
            visible.append((age, max(candidates, key=lambda item: item["length"])))
        else:
            errors.append(f"FXBolt age={age}: no captured framebuffer contains an elongated Aer-yellow segment")
    visible_ages = [age for age, _ in visible]
    if visible_ages != expected_ages:
        errors.append(f"pixel-confirmed FXBolt ages are {visible_ages}; expected {expected_ages}")
    if visible_ages == expected_ages:
        brightness = [candidate["mean_aer_delta"] for _, candidate in visible]
        if not (
            brightness[0] >= brightness[1] * 1.1
            and brightness[1] >= brightness[2] * 1.1
        ):
            errors.append(
                "FXBolt additive brightness does not show the required 10% BETA26 age-1 > age-2 > age-3 fade: "
                + ", ".join(f"age {age}={value:.1f}" for age, value in zip(expected_ages, brightness))
            )
    expiry_errors, expiry_metrics = verify_bolt_segment(expiry_path, False, baseline_path)
    errors.extend(expiry_errors)
    return errors, visible, expiry_metrics

def _first(pattern: str, text: str):
    return re.search(pattern, text)


def _crop_pixel_difference(left: Path, right: Path, temporary: Path) -> int:
    left_crop = temporary / "left-roi.png"
    right_crop = temporary / "right-roi.png"
    for source, target in ((left, left_crop), (right, right_crop)):
        result = subprocess.run(
            ["convert", str(source), "-crop", "360x320+332+340", "+repage", str(target)],
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            detail = result.stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"cannot crop video frame {source.name}: {detail}")
    result = subprocess.run(
        ["compare", "-metric", "AE", "-fuzz", "4%", str(left_crop), str(right_crop), "null:"],
        capture_output=True,
        check=False,
    )
    detail = (result.stderr or result.stdout).decode("utf-8", errors="replace")
    match = re.search(r"\b(\d+)\b", detail)
    if match is None:
        raise RuntimeError(f"ImageMagick returned no AE metric for {left.name}: {detail.strip()}")
    return int(match.group(1))


def _extract_video_frame(video: Path, frame_index: int, output: Path) -> None:
    result = subprocess.run(
        [
            "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(video), "-vf", f"select=eq(n\\,{frame_index})",
            "-fps_mode", "passthrough", "-frames:v", "1", str(output),
        ],
        capture_output=True,
        check=False,
    )
    if result.returncode != 0 or not output.is_file() or output.stat().st_size < 128:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ffmpeg could not extract video frame {frame_index}: {detail}")


def extract_video_frames(status_file: Path, video_file: Path, screenshot_dir: Path) -> list[str]:
    """Extract the three real render ages from the continuous X11 recording."""
    errors: list[str] = []
    status_text = status_file.read_text(encoding="utf-8", errors="replace")
    started = _first(
        r"(?m)^WISP_ZAP_VIDEO_STARTED=YES firstFrameMonotonicNs=(\d+) frameRate=(\d+)$",
        status_text,
    )
    side_view = _first(
        r"(?m)^WISP_CAMERA_SIDE_VIEW=YES id=(\d+) monotonicNs=(\d+)$",
        status_text,
    )
    side_view_rendered = _first(
        r"(?m)^WISP_CAMERA_SIDE_VIEW_RENDERED=YES id=(\d+) beamYaw=([-0-9.]+) yaw=([-0-9.]+) "
        r"pitch=([-0-9.]+) cameraType=THIRD_PERSON_BACK cameraDistance=([0-9.]+) monotonicNs=(\d+)$",
        status_text,
    )
    payload = _first(
        r"(?m)^WISP_ZAP_PAYLOAD_RECEIVED=YES source=(\d+) target=(\d+) createdAt=(\d+) monotonicNs=(\d+)$",
        status_text,
    )
    observed = re.findall(
        r"(?m)^WISP_ZAP_FRAME_OBSERVED=YES source=(\d+) createdAt=(\d+) ageTick=(\d+) "
        r"wispTick=(\d+) observedAtMonotonicNs=(\d+)$",
        status_text,
    )
    expired = _first(
        r"(?m)^WISP_ZAP_EFFECT_EXPIRED=YES source=(\d+) target=(\d+) createdAt=(\d+) monotonicNs=(\d+)$",
        status_text,
    )
    if started is None:
        errors.append("missing first-frame timestamp for continuous Wisp video")
    if "WISP_ZAP_VIDEO_STOPPED=YES" not in status_text:
        errors.append("continuous Wisp video was not stopped cleanly before extraction")
    if len(observed) != 3:
        errors.append(f"expected three timestamped Wisp render observations, got {len(observed)}")
    if side_view is None:
        errors.append("missing attack-event side-view monotonic timestamp")
    if side_view_rendered is None:
        errors.append("missing AFTER_ENTITIES third-person side-view confirmation")
    if payload is None:
        errors.append("missing client zap-payload monotonic timestamp")
    if expired is None:
        errors.append("missing monotonic Wisp expiry timestamp")
    if started is None or side_view is None or side_view_rendered is None or payload is None or expired is None or len(observed) != 3 or errors:
        return errors

    if side_view.group(1) != payload.group(1) or side_view_rendered.group(1) != payload.group(1):
        errors.append("side-view camera and zap payload belong to different Wisp source IDs")
    side_view_ns = int(side_view.group(2))
    side_view_rendered_ns = int(side_view_rendered.group(6))
    payload_ns = int(payload.group(4))
    expired_ns = int(expired.group(4))
    if expired.group(1) != payload.group(1) or expired.group(2) != payload.group(2) or expired.group(3) != payload.group(3):
        errors.append("Wisp expiry timestamp belongs to a different source/target/createdAt event")
    if side_view_ns >= side_view_rendered_ns:
        errors.append("AFTER_ENTITIES side-view confirmation did not follow the AttackEntityEvent camera switch")
    if side_view_rendered_ns >= payload_ns:
        errors.append("third-person side-view frame was not confirmed before zap payload receipt")
    beam_yaw, rendered_yaw, rendered_pitch, camera_distance = map(float, side_view_rendered.groups()[1:5])
    yaw_delta = (rendered_yaw - beam_yaw + 180.0) % 360.0 - 180.0
    if abs(yaw_delta - 50.0) > 1.0:
        errors.append(f"rendered side-view yaw is not 50 degrees from the zap axis: {yaw_delta:.2f}")
    if abs(rendered_pitch) > 1.0:
        errors.append(f"rendered side-view camera pitch is not horizontal: {rendered_pitch:.2f}")
    if camera_distance < 3.0:
        errors.append(f"third-person side-view camera was obstructed/too close: {camera_distance:.2f} blocks")
    if [int(frame[2]) for frame in observed] != [1, 2, 3]:
        errors.append("timestamped Wisp render observations are not ages 1, 2, 3")
    if len({(frame[0], frame[1]) for frame in observed}) != 1:
        errors.append("timestamped Wisp observations do not belong to one source/createdAt event")
    if len({frame[3] for frame in observed}) != 3:
        errors.append("timestamped Wisp observations do not use three distinct Wisp ticks")
    observed_ns = [int(frame[4]) for frame in observed]
    if observed_ns != sorted(observed_ns) or len(set(observed_ns)) != 3:
        errors.append("Wisp render observation timestamps are not strictly increasing")
    if expired_ns <= observed_ns[-1] or expired_ns <= payload_ns:
        errors.append("Wisp expiry timestamp does not follow the payload and all three render ages")
    if errors:
        return errors

    if not video_file.is_file() or video_file.stat().st_size < 1024:
        return ["continuous Wisp video is missing or empty"]
    after_expiry = screenshot_dir / "83-wisp-zap-after-expiry.png"
    if not after_expiry.is_file() or after_expiry.stat().st_size < 128:
        return ["after-expiry screenshot is required to select the closest transient video frames"]

    try:
        probe = subprocess.run(
            [
                "ffprobe", "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=avg_frame_rate,duration,nb_frames",
                "-of", "json", str(video_file),
            ],
            capture_output=True,
            check=False,
        )
        if probe.returncode != 0:
            detail = probe.stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"ffprobe failed: {detail}")
        streams = json.loads(probe.stdout.decode("utf-8", errors="replace")).get("streams", [])
        if not streams:
            raise RuntimeError("video contains no video stream")
        stream = streams[0]
        fps = float(Fraction(stream["avg_frame_rate"]))
        duration = float(stream["duration"])
        frame_count = int(stream.get("nb_frames") or round(duration * fps))
        if fps < 30.0 or duration <= 0.0 or frame_count < 3:
            raise RuntimeError(
                f"video timing is insufficient: fps={fps:.3f} duration={duration:.3f} frames={frame_count}"
            )
        first_frame_ns = int(started.group(1))
        requested_rate = int(started.group(2))
        if requested_rate != 60:
            raise RuntimeError(f"unexpected requested capture rate: {requested_rate}")

        side_view_ns = int(side_view.group(2))
        payload_ns = int(payload.group(4))
        frame_period_ns = 1_000_000_000.0 / fps
        if side_view_rendered_ns <= first_frame_ns:
            raise RuntimeError("render-stage side-view timestamp predates the first recorded video frame")
        if payload_ns - side_view_rendered_ns < frame_period_ns * 3.0:
            raise RuntimeError(
                "no safe pre-payload video window after the actual third-person camera frame"
            )
        baseline_first = max(
            0, int(math.floor((side_view_rendered_ns - first_frame_ns) * fps / 1_000_000_000.0)) + 1
        )
        baseline_last = min(
            frame_count - 1,
            int(math.floor((payload_ns - first_frame_ns) * fps / 1_000_000_000.0)) - 2,
        )
        if baseline_first > baseline_last:
            raise RuntimeError(
                f"no video frame between side-view setup and zap payload: "
                f"first={baseline_first} last={baseline_last}"
            )

        selected: list[tuple[int, int, int, int, int, int, str]] = []
        frame_names = {
            1: "80-wisp-zap-frame-a.png",
            2: "81-wisp-zap-frame-b.png",
            3: "82-wisp-zap-frame-c.png",
        }
        screenshot_dir.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="tc-wisp-video-frames-") as directory:
            temporary = Path(directory)
            baseline_path = temporary / BOLT_BASELINE_SCREENSHOT
            baseline_index = baseline_last
            _extract_video_frame(video_file, baseline_index, baseline_path)
            baseline_monotonic_ns = first_frame_ns + int(round(baseline_index * 1_000_000_000.0 / fps))
            if not side_view_rendered_ns < baseline_monotonic_ns < payload_ns:
                raise RuntimeError(
                    "selected same-camera baseline frame is not strictly after the rendered third-person side view and before zap payload"
                )
            # Use the latest safe pre-payload frame, not the first frame that
            # happens to contain the moving Wisp billboard. The source may fly
            # out of view during BETA26's wind-up; the camera stays fixed and
            # the baseline must match the player/platform scene at zap time.
            shutil.copyfile(baseline_path, screenshot_dir / BOLT_BASELINE_SCREENSHOT)

            previous_index = -1
            window_bounds: list[tuple[int, int, int]] = []
            event_times = [int(frame[4]) for frame in observed]
            event_ends = event_times[1:] + [expired_ns]
            for event_index, (source_id, created_at, age_tick, wisp_tick, observed_at) in enumerate(observed):
                elapsed = (int(observed_at) - first_frame_ns) / 1_000_000_000.0
                end_elapsed = (event_ends[event_index] - first_frame_ns) / 1_000_000_000.0
                if elapsed < 0.0 or elapsed >= duration or end_elapsed <= elapsed or end_elapsed > duration:
                    raise RuntimeError(
                        f"age={age_tick} observation window lies outside the recorded video: "
                        f"start={elapsed:.4f}s end={end_elapsed:.4f}s duration={duration:.4f}s"
                    )
                base_index = int(round(elapsed * fps))
                first_index = max(0, int(math.floor(elapsed * fps)))
                end_index_exclusive = min(frame_count, int(math.ceil(end_elapsed * fps)))
                candidate_start = max(previous_index + 1, first_index)
                window_size = end_index_exclusive - candidate_start
                if window_size <= 0:
                    raise RuntimeError(
                        f"no video frame in Wisp age={age_tick} interval "
                        f"[{candidate_start},{end_index_exclusive})"
                    )
                if window_size > 12:
                    raise RuntimeError(
                        f"Wisp age={age_tick} video interval is too broad: "
                        f"[{candidate_start},{end_index_exclusive}) spans {window_size} frames"
                    )
                candidates = range(candidate_start, end_index_exclusive)
                window_bounds.append((int(age_tick), candidate_start, end_index_exclusive))
                ranked: list[tuple[float, float, int, int, int, Path]] = []
                for index in candidates:
                    candidate = temporary / f"age-{age_tick}-{index}.png"
                    _extract_video_frame(video_file, index, candidate)
                    frame_errors, bolt_candidates = _bolt_segment_candidates(candidate, baseline_path, True)
                    if frame_errors:
                        raise RuntimeError(
                            f"cannot inspect Wisp age={age_tick} video frame {index}: {'; '.join(frame_errors)}"
                        )
                    if not bolt_candidates:
                        continue
                    bolt = max(bolt_candidates, key=lambda item: (item["length"], item["pixels"]))
                    difference = _crop_pixel_difference(candidate, after_expiry, temporary)
                    ranked.append((
                        bolt["length"], bolt["pixels"], difference,
                        -abs(index - base_index), index, candidate
                    ))
                if not ranked:
                    raise RuntimeError(
                        f"no pixel-confirmed additive Aer FXBolt at age={age_tick} "
                        f"in video frame window [{candidate_start},{end_index_exclusive})"
                    )
                _, _, difference, _, selected_index, selected_path = max(
                    ranked, key=lambda item: (item[0], item[1], item[2], item[3])
                )
                selected.append(
                    (int(source_id), int(created_at), int(age_tick), int(wisp_tick), selected_index, difference, str(selected_path))
                )
                previous_index = selected_index
            frame_indices = [frame[4] for frame in selected]
            if frame_indices != sorted(frame_indices) or len(set(frame_indices)) != 3:
                raise RuntimeError(f"selected video frame indices are not distinct/increasing: {frame_indices}")
            capture_markers: list[str] = [
                "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD",
                f"WISP_ZAP_BASELINE_FRAME_FROM_VIDEO=YES source={payload.group(1)} "
                f"frameIndex={baseline_index} monotonicNs={baseline_monotonic_ns}",
            ]
            for age_tick, first_index, end_index_exclusive in window_bounds:
                capture_markers.append(
                    f"WISP_ZAP_VIDEO_AGE_WINDOW=YES source={payload.group(1)} createdAt={payload.group(3)} "
                    f"ageTick={age_tick} firstFrame={first_index} endExclusive={end_index_exclusive}"
                )
            if selected[0][4] <= baseline_index:
                raise RuntimeError(
                    f"pre-payload baseline frame {baseline_index} is not before age-1 frame {selected[0][4]}"
                )
            for source_id, created_at, age_tick, wisp_tick, frame_index, difference, selected_path in selected:
                shutil.copyfile(selected_path, screenshot_dir / frame_names[age_tick])
                capture_markers.append(
                    f"WISP_ZAP_FRAME_CAPTURE=YES source={source_id} createdAt={created_at} "
                    f"ageTick={age_tick} wispTick={wisp_tick}"
                )
                capture_markers.append(
                    f"WISP_ZAP_VIDEO_FRAME_INDEX=YES source={source_id} createdAt={created_at} "
                    f"ageTick={age_tick} frameIndex={frame_index} diff={difference}"
                )
        with status_file.open("a", encoding="utf-8") as status:
            status.write("\n" + "\n".join(capture_markers) + "\n")
        return []
    except (FileNotFoundError, KeyError, ValueError, ZeroDivisionError, RuntimeError, json.JSONDecodeError) as error:
        return [str(error)]


def _attackable_frame_for_input(log_text: str, attack_input):
    """Select the latest same-ID AFTER_LEVEL EntityHitResult within two ticks before the click."""
    if attack_input is None:
        return None
    pattern = re.compile(
        r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=(\d+) tick=(\d+) gameTime=(\d+) playerTick=(\d+) "
        r"hitType=ENTITY targetId=(\d+) targetType=thaumcraft:wisp blockPos=none"
    )
    candidates = [
        frame
        for frame in pattern.finditer(log_text)
        if frame.group(1) == attack_input.group(1)
        and 0 <= int(attack_input.group(4)) - int(frame.group(3)) <= 2
        and 0 <= int(attack_input.group(5)) - int(frame.group(4)) <= 2
        and frame.start() < attack_input.start()
    ]
    if not candidates:
        return None
    latest_key = max(
        (int(frame.group(3)), int(frame.group(4)), int(frame.group(2)))
        for frame in candidates
    )
    latest = [
        frame
        for frame in candidates
        if (int(frame.group(3)), int(frame.group(4)), int(frame.group(2))) == latest_key
    ]
    return latest[0] if len(latest) == 1 else None


def _input_correlated_frame_for_input(log_text: str, attack_input):
    """Select the unique completed camera frame at the actual InputEvent gameTime/playerTick."""
    if attack_input is None:
        return None
    pattern = re.compile(
        r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=(\d+) tick=(\d+) gameTime=(\d+) playerTick=(\d+) "
    )
    candidates = [
        frame
        for frame in pattern.finditer(log_text)
        if frame.group(1) == attack_input.group(1)
        and frame.group(3) == attack_input.group(4)
        and 0 <= int(attack_input.group(5)) - int(frame.group(4)) <= 1
        and frame.start() < attack_input.start()
    ]
    return candidates[0] if len(candidates) == 1 else None


def verify(log_text: str, status_text: str, screenshot_dir: Path) -> list[str]:
    errors: list[str] = []

    attack_input = _first(
        r"TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=true targetId=(\d+) targetType=thaumcraft:wisp hitType=ENTITY distance=([0-9.]+) hitDistance=([0-9.]+) gameTime=(\d+) blockPos=none tick=(\d+) monotonicNs=(\d+)",
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
        r"TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=(\d+) target=(\d+) color=#([0-9A-Fa-f]{6}) "
        r"createdAt=(\d+) clientGameTime=(\d+) monotonicNs=(\d+)",
        log_text,
    )
    wisp_camera = _first(
        r"TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=render_safe_quarter tick=(\d+)",
        log_text,
    )
    camera_stopped = _first(
        r"TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES id=(\d+)", log_text
    )
    side_view = _first(
        r"TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=(\d+) beamYaw=(-?[0-9.]+) yaw=(-?[0-9.]+) "
        r"pitch=(-?[0-9.]+) cameraType=THIRD_PERSON_BACK monotonicNs=(\d+)", log_text
    )
    side_view_rendered = _first(
        r"TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES id=(\d+) beamYaw=(-?[0-9.]+) yaw=(-?[0-9.]+) "
        r"pitch=(-?[0-9.]+) cameraType=THIRD_PERSON_BACK cameraDistance=([0-9.]+) monotonicNs=(\d+)", log_text
    )
    pre_attack_track = _first(
        r"TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=(\d+) tick=(\d+) gameTime=(\d+)",
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
    attackable_frame = _attackable_frame_for_input(log_text, attack_input)
    input_correlated_frame = _input_correlated_frame_for_input(log_text, attack_input)
    rendered = _first(
        r"TC_SMOKE_WISP_ZAP_RENDERED=YES source=(\d+) target=(\d+) points=(\d+) age=([0-9.]+)"
        r" texture=thaumcraft:textures/misc/essentia\.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        log_text,
    )

    for label, match in (("client Wisp camera sample", wisp_camera),
                         ("pre-attack Wisp camera tracking", pre_attack_track),
                         ("camera aim lock stopped after real hit", camera_stopped),
                         ("third-person-back side-view camera", side_view),
                         ("rendered third-person side-view frame", side_view_rendered),
                         ("client Wisp entity renderer", wisp_rendered),
                         ("completed level render frame", wisp_frame_complete),
                         ("recent pre-input Wisp EntityHitResult frame", attackable_frame),
                         ("completed camera frame correlated to the exact InputEvent", input_correlated_frame),
                         ("client attack input/hit result", attack_input),
                         ("client selected attack target", client_attack),
                         ("server accepted damage", hurt),
                         ("player hit/target", hit), ("server payload send", sent),
                         ("client payload receive", received), ("client additive render", rendered)):
        if match is None:
            errors.append(f"missing real gameplay/render evidence: {label}")

    if client_attack and camera_stopped and camera_stopped.group(1) != client_attack.group(2):
        errors.append("camera aim lock stopped for a different Wisp than the real client attack")
    if camera_stopped and side_view and camera_stopped.group(1) != side_view.group(1):
        errors.append("side-view camera sample belongs to a different Wisp than the stopped tracker")
    if side_view and received:
        if side_view.group(1) != received.group(1):
            errors.append("side-view camera and zap payload refer to different source entities")
        if int(side_view.group(5)) >= int(received.group(6)):
            errors.append("side-view camera was selected after the client received the zap payload")
    if side_view and side_view_rendered:
        if side_view.group(1) != side_view_rendered.group(1):
            errors.append("rendered side-view frame belongs to a different Wisp than the attack camera switch")
        if int(side_view.group(5)) >= int(side_view_rendered.group(6)):
            errors.append("rendered third-person side-view frame predates the attack camera switch")
    if side_view_rendered and received and int(side_view_rendered.group(6)) >= int(received.group(6)):
        errors.append("rendered third-person side-view frame was selected after the client received the zap payload")
    if side_view:
        beam_yaw = float(side_view.group(2))
        yaw = float(side_view.group(3))
        pitch = float(side_view.group(4))
        yaw_delta = (yaw - beam_yaw + 180.0) % 360.0 - 180.0
        if abs(yaw_delta - 50.0) > 1.0 or abs(pitch) > 1.0:
            errors.append(
                f"camera is not side-on to the zap: beamYaw={beam_yaw:.2f} "
                f"yaw={yaw:.2f} pitch={pitch:.2f}"
            )
    if side_view_rendered:
        rendered_beam_yaw = float(side_view_rendered.group(2))
        rendered_yaw = float(side_view_rendered.group(3))
        rendered_pitch = float(side_view_rendered.group(4))
        rendered_distance = float(side_view_rendered.group(5))
        rendered_delta = (rendered_yaw - rendered_beam_yaw + 180.0) % 360.0 - 180.0
        if abs(rendered_delta - 50.0) > 1.0 or abs(rendered_pitch) > 1.0:
            errors.append("render-stage camera did not preserve the 50-degree horizontal side view")
        if rendered_distance < 3.0:
            errors.append(f"render-stage third-person camera distance is too short: {rendered_distance:.2f}")
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
                errors.append("pre-click AFTER_LEVEL frame and real InputEvent selected different Wisps")
            game_time_delta = int(attack_input.group(4)) - int(attackable_frame.group(3))
            if game_time_delta < 0 or game_time_delta > 2:
                errors.append(
                    "real InputEvent did not follow a same-ID Wisp EntityHitResult within two game ticks: "
                    f"frameGameTime={attackable_frame.group(3)} clickGameTime={attack_input.group(4)}"
                )
            player_tick_delta = int(attack_input.group(5)) - int(attackable_frame.group(4))
            if player_tick_delta < 0 or player_tick_delta > 2:
                errors.append(
                    "real InputEvent did not follow its attackable Wisp frame within two player ticks: "
                    f"framePlayerTick={attackable_frame.group(4)} clickPlayerTick={attack_input.group(5)}"
                )
            frame_position = log_text.find(attackable_frame.group(0))
            input_position = log_text.find(attack_input.group(0))
            if frame_position < 0 or input_position < frame_position:
                errors.append("real click occurred before the matching pre-click attackable AFTER_LEVEL frame")
    if attackable_frame is None:
        errors.append("no recent completed frame exposed the live Wisp before the real click")
    if input_correlated_frame is None:
        errors.append("no completed camera frame correlated to the exact attack InputEvent")
    elif attack_input:
        if input_correlated_frame.group(1) != attack_input.group(1):
            errors.append("InputEvent-correlated camera frame belongs to a different Wisp")
        if input_correlated_frame.group(3) != attack_input.group(4):
            errors.append("InputEvent-correlated camera frame has a different level gameTime")
        input_frame_tick_delta = int(attack_input.group(5)) - int(input_correlated_frame.group(4))
        if input_frame_tick_delta < 0 or input_frame_tick_delta > 1:
            errors.append("InputEvent-correlated camera frame is more than one player tick away")
        frame_position = log_text.find(input_correlated_frame.group(0))
        input_position = log_text.find(attack_input.group(0))
        if frame_position < 0 or input_position < frame_position:
            errors.append("real InputEvent occurred before its correlated completed camera frame")
        if client_attack and (
            input_correlated_frame.group(2) != client_attack.group(3)
            or input_correlated_frame.group(3) != client_attack.group(5)
        ):
            errors.append("completed camera frame and AttackEntityEvent use different Wisp ticks/gameTime")
    if attack_input and client_attack and attack_input.group(1) != client_attack.group(2):
        errors.append("client hit result and AttackEntityEvent selected different entities")
    if attack_input and client_attack:
        client_game_time_delta = int(client_attack.group(5)) - int(attack_input.group(4))
        if client_game_time_delta < 0 or client_game_time_delta > 1:
            errors.append("AttackEntityEvent was not emitted in the same or immediately following game tick")
    if client_attack and hurt and client_attack.group(1, 2) != hurt.group(2, 1):
        errors.append("client AttackEntityEvent and server hurt callback use different player/Wisp IDs")
    if hurt and hit and hurt.group(1, 2) != hit.group(1, 2):
        errors.append("server accepted-damage callback and target acquisition use different IDs")
    if attack_input and float(attack_input.group(3)) > 3.01:
        errors.append("vanilla eye-to-hit-point distance exceeds the 3-block melee reach")
    if attack_input and side_view and int(attack_input.group(6)) >= int(side_view.group(5)):
        errors.append("attack-input timestamp must precede the smoke camera side-view switch")
    if attack_input:
        input_ns_status = _first(r"(?m)^WISP_ATTACK_INPUT_FRAME_MONOTONIC_NS=(\d+)$", status_text)
        hit_distance_status = _first(r"(?m)^WISP_ATTACK_INPUT_FRAME_HIT_DISTANCE=([0-9.]+)$", status_text)
        video_offset_status = _first(r"(?m)^WISP_ATTACK_INPUT_FRAME_VIDEO_OFFSET=([0-9.]+)$", status_text)
        if input_ns_status is None or input_ns_status.group(1) != attack_input.group(6):
            errors.append("video screenshot timestamp does not match the real attack InputEvent")
        if hit_distance_status is None or abs(float(hit_distance_status.group(1)) - float(attack_input.group(3))) > 0.001:
            errors.append("status hit distance does not match the real attack InputEvent")
        if video_offset_status is None or float(video_offset_status.group(1)) < 0.0:
            errors.append("video-derived attack frame has no valid non-negative offset")
        video_started = _first(
            r"(?m)^WISP_ZAP_VIDEO_STARTED=YES firstFrameMonotonicNs=(\d+) frameRate=(\d+)$",
            status_text,
        )
        if input_ns_status is not None and video_offset_status is not None and video_started is not None:
            expected_offset = (
                int(input_ns_status.group(1)) - int(video_started.group(1))
            ) / 1_000_000_000.0 - 1.0 / int(video_started.group(2))
            if abs(float(video_offset_status.group(1)) - expected_offset) > 0.001:
                errors.append(
                    "attack-input screenshot offset does not identify the final video frame before the real click: "
                    f"actual={float(video_offset_status.group(1)):.6f} expected={expected_offset:.6f}"
                )
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
        if not 0.0 <= age < 4.0:
            errors.append(f"FXBolt render age outside BETA26 three-tick lifetime: {age}")

    zap_frame_pattern = re.compile(
        r"TC_SMOKE_WISP_ZAP_FRAME=YES source=(\d+) target=(\d+) createdAt=(\d+) "
        r"gameTime=(\d+) wispTick=(\d+) ageTick=(\d+) age=([0-9.]+) points=(\d+) "
        r"cameraType=([A-Z_]+)"
    )
    zap_frames = list(zap_frame_pattern.finditer(log_text))
    captured_frames = re.findall(
        r"(?m)^WISP_ZAP_FRAME_CAPTURE=YES source=(\d+) createdAt=(\d+) ageTick=(\d+) wispTick=(\d+)$",
        status_text,
    )
    observed_frames = re.findall(
        r"(?m)^WISP_ZAP_FRAME_OBSERVED=YES source=(\d+) createdAt=(\d+) ageTick=(\d+) wispTick=(\d+) observedAtMonotonicNs=(\d+)$",
        status_text,
    )
    video_indices = re.findall(
        r"(?m)^WISP_ZAP_VIDEO_FRAME_INDEX=YES source=(\d+) createdAt=(\d+) ageTick=(\d+) frameIndex=(\d+) diff=(\d+)$",
        status_text,
    )
    if len(captured_frames) != 3:
        errors.append(f"expected exactly three extracted FXBolt video frames, got {len(captured_frames)}")
    if len(observed_frames) != 3:
        errors.append(f"expected exactly three timestamped FXBolt render observations, got {len(observed_frames)}")
    if len(video_indices) != 3:
        errors.append(f"expected exactly three video frame-index records, got {len(video_indices)}")
    if len(captured_frames) == 3 and len(observed_frames) == 3:
        if [frame[:4] for frame in captured_frames] != [frame[:4] for frame in observed_frames]:
            errors.append("extracted FXBolt screenshots do not match the observed render ages/source/ticks")
        if [int(frame[2]) for frame in observed_frames] != [1, 2, 3]:
            errors.append("timestamped FXBolt observations are not consecutive ages 1, 2, 3")
        if len({(frame[0], frame[1]) for frame in observed_frames}) != 1:
            errors.append("timestamped FXBolt observations do not belong to one source/createdAt event")
        if len({frame[3] for frame in observed_frames}) != 3:
            errors.append("timestamped FXBolt observations do not use three distinct Wisp ticks")
        observed_times = [int(frame[4]) for frame in observed_frames]
        if observed_times != sorted(observed_times) or len(set(observed_times)) != 3:
            errors.append("timestamped FXBolt observations are not strictly ordered")
    if len(video_indices) == 3 and len(captured_frames) == 3:
        if [frame[:3] for frame in video_indices] != [frame[:3] for frame in captured_frames]:
            errors.append("video frame indices do not match the extracted FXBolt screenshots")
        frame_numbers = [int(frame[3]) for frame in video_indices]
        if frame_numbers != sorted(frame_numbers) or len(set(frame_numbers)) != 3:
            errors.append("extracted FXBolt video frames are not distinct/increasing")
        for _, _, age_tick, _, difference in video_indices:
            if int(difference) <= 0:
                errors.append(
                    f"age-{age_tick} video frame has no pixel difference from the post-expiry scene"
                )
    if len(captured_frames) == 3:
        expected_ages = [1, 2, 3]
        actual_ages = [int(frame[2]) for frame in captured_frames]
        if actual_ages != expected_ages:
            errors.append(f"captured FXBolt ages are not consecutive BETA26 ticks: {actual_ages}")
        if len({(frame[0], frame[1]) for frame in captured_frames}) != 1:
            errors.append("captured FXBolt frames do not belong to one source/createdAt event")
        if len({frame[3] for frame in captured_frames}) != 3:
            errors.append("captured FXBolt frames do not use three distinct completed Wisp ticks")
        for source_id, created_at, age_tick, wisp_tick in captured_frames:
            matching_frame = next(
                (
                    frame for frame in zap_frames
                    if frame.group(1) == source_id
                    and frame.group(3) == created_at
                    and frame.group(5) == wisp_tick
                    and frame.group(6) == age_tick
                ),
                None,
            )
            if matching_frame is None:
                errors.append(
                    f"captured FXBolt screenshot has no matching rendered age={age_tick} frame "
                    f"for source={source_id} createdAt={created_at} wispTick={wisp_tick}"
                )
            elif int(matching_frame.group(8)) <= 2:
                errors.append(f"FXBolt age={age_tick} frame has no interior polycone points")
            elif matching_frame.group(9) != "THIRD_PERSON_BACK":
                errors.append(
                    f"FXBolt age={age_tick} was rendered in camera mode "
                    f"{matching_frame.group(9)}, expected THIRD_PERSON_BACK"
                )

        if sent is not None:
            source_id, target_id = captured_frames[0][0], sent.group(2)
            created_at = captured_frames[0][1]
            payload_for_event = re.search(
                rf"TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source={re.escape(source_id)} "
                rf"target={re.escape(target_id)} color=#FFFF7E createdAt={re.escape(created_at)} "
                rf"clientGameTime=\d+ monotonicNs=\d+",
                log_text,
            )
            rendered_for_event = re.search(
                rf"TC_SMOKE_WISP_ZAP_RENDERED=YES source={re.escape(source_id)} "
                rf"target={re.escape(target_id)} points=(\d+) age=([0-9.]+) "
                rf"texture=thaumcraft:textures/misc/essentia\.png blend=SRC_ALPHA_ONE depthWrite=OFF "
                rf"createdAt={re.escape(created_at)} gameTime=\d+ ageTick=\d+",
                log_text,
            )
            expired_for_event = re.search(
                rf"TC_SMOKE_WISP_ZAP_EXPIRED=YES source={re.escape(source_id)} "
                rf"target={re.escape(target_id)} createdAt={re.escape(created_at)} "
                rf"gameTime=(\d+) ageTicks=(\d+)",
                log_text,
            )
            status_expired = re.search(
                rf"(?m)^WISP_ZAP_EFFECT_EXPIRED=YES source={re.escape(source_id)} "
                rf"target={re.escape(target_id)} createdAt={re.escape(created_at)} monotonicNs=(\\d+)$",
                status_text,
            )
            if payload_for_event is None:
                errors.append("captured FXBolt cycle has no matching client payload receipt")
            if rendered_for_event is None:
                errors.append("captured FXBolt cycle has no matching completed additive render batch")
            if expired_for_event is None or status_expired is None:
                errors.append("captured FXBolt cycle has no matching three-tick expiry evidence")
            else:
                expiry_game_time = int(expired_for_event.group(1))
                expiry_age_ticks = int(expired_for_event.group(2))
                status_expiry_ns = int(status_expired.group(1))
                if status_expiry_ns <= int(received.group(6)) if received is not None else True:
                    errors.append("client-observed expiry timestamp does not follow the matching payload receipt")
                event_frames = [
                    frame for frame in zap_frames
                    if frame.group(1) == source_id and frame.group(3) == created_at
                ]
                if expiry_age_ticks < 4:
                    errors.append("FXBolt expired before the BETA26 particleMaxAge=3 boundary")
                if expiry_game_time - int(created_at) != expiry_age_ticks:
                    errors.append("FXBolt expiry gameTime and elapsed-age evidence disagree")
                late_render_frames = [
                    frame for frame in event_frames
                    if int(frame.group(6)) > 3 or float(frame.group(7)) >= 4.0
                ]
                if late_render_frames:
                    errors.append("FXBolt produced a render frame at or after its BETA26 expiry boundary")
                if event_frames and expiry_game_time <= max(int(frame.group(4)) for frame in event_frames):
                    errors.append("FXBolt cleanup was observed before its last visible render frame")

            if rendered_for_event is not None and int(rendered_for_event.group(1)) <= 2:
                errors.append("captured FXBolt render batch has no interior polycone points")

            last_capture_position = status_text.find(
                f"WISP_ZAP_FRAME_CAPTURE=YES source={source_id} createdAt={created_at} ageTick=3 "
            )
            camera_capture_position = status_text.find("WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES")
            attack_input_frame_position = status_text.find("WISP_ATTACK_INPUT_FRAME=YES")
            attack_target_position = status_text.find("WISP_ATTACK_TARGET_CONFIRMED=YES")
            attack_hurt_position = status_text.find("WISP_ATTACK_HURT_ACCEPTED=YES")
            if not (
                0 <= attack_input_frame_position < attack_target_position
                < attack_hurt_position < camera_capture_position
            ):
                errors.append(
                    "real attackable-frame input must precede target/damage, and post-hoc screenshot validation must follow damage"
                )
            side_view_position = status_text.find("WISP_CAMERA_SIDE_VIEW=YES id=")
            baseline_capture_position = status_text.find(
                "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD"
            )
            if not (
                0 <= attack_input_frame_position < attack_target_position
                < side_view_position < attack_hurt_position < camera_capture_position
            ):
                errors.append(
                    "side-view camera must be selected after the real attack target and before accepted damage"
                )
            if (
                baseline_capture_position < 0
                or last_capture_position < 0
                or baseline_capture_position > last_capture_position
            ):
                errors.append("FXBolt frames were not extracted after the same-camera pre-payload baseline")

            baseline_time_position = status_text.find("WISP_ZAP_TEST_TIME=MIDNIGHT")
            baseline_capture_position = status_text.find("WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN")
            if baseline_time_position < 0 or baseline_capture_position < 0 or baseline_time_position > baseline_capture_position:
                errors.append("night fixture time was not established before baseline screenshots")

    for marker in REQUIRED_LOG_MARKERS:
        if marker not in log_text:
            errors.append(f"missing real server world marker: {marker}")

    named_source_position = log_text.find("TC_SMOKE_WISP_NAMED_SOURCE=YES")
    base_clear_position = log_text.find("TC_SMOKE_WISP_SOURCE_BASE_CLEAR=YES")
    if named_source_position < 0 or base_clear_position < named_source_position:
        errors.append("Wisp named source was not resolved before base-cell clearance check")

    for marker in REQUIRED_STATUS:
        if marker not in status_text:
            errors.append(f"missing real-client status marker: {marker}")

    survival_confirmation_position = status_text.find(
        "WISP_SERVER_OR_CLIENT_MARKER_CONFIRMED=Set own game mode to Survival Mode line="
    )
    survival_status_position = status_text.find("TC_SMOKE_WISP_PLAYER_SURVIVAL=YES")
    attack_click_position = status_text.find("WISP_ATTACK_CLICK_DISPATCHED=YES")
    attack_input_status_position = status_text.find("WISP_ATTACK_INPUT_FRAME=YES")
    if not (
        0 <= survival_confirmation_position
        < survival_status_position
        < attack_click_position
        < attack_input_status_position
    ):
        errors.append(
            "fresh server-confirmed Survival transition must precede the real click and its EntityHitResult"
        )

    attack_input_position = log_text.find(attack_input.group(0)) if attack_input else -1
    knockback_set_position = log_text.find(
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0"
    )
    stabilized_position = status_text.find("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL")
    attack_target_position = status_text.find("WISP_ATTACK_TARGET_CONFIRMED=YES")
    first_zap_capture_position = status_text.find("WISP_ZAP_FRAME_CAPTURE=YES")
    if knockback_set_position < 0 or attack_input_position < 0 or knockback_set_position >= attack_input_position:
        errors.append("player knockback resistance was not established before the real Survival attack")
    if (
        stabilized_position < 0
        or attack_target_position < 0
        or stabilized_position >= attack_target_position
    ):
        errors.append("player stabilization status was not recorded before the real attack target confirmation")
    if (
        stabilized_position < 0
        or first_zap_capture_position < 0
        or stabilized_position > first_zap_capture_position
    ):
        errors.append("player stabilization was not confirmed before FXBolt frame capture")

    telemetry = _first(
        r"(?m)^WISP_CAMERA_TELEMETRY=PASS id=(\d+) tick=(\d+) playerX=([0-9.+-]+) playerY=([0-9.+-]+) playerZ=([0-9.+-]+) wispX=([0-9.+-]+) wispY=([0-9.+-]+) wispZ=([0-9.+-]+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    first_frame_id = _first(r"(?m)^WISP_RENDER_FIRST_FRAME_ID=(\d+)$", status_text)
    first_frame_tick = _first(r"(?m)^WISP_RENDER_FIRST_FRAME_TICK=(\d+)$", status_text)
    pre_attack_track_status = _first(
        r"(?m)^WISP_RENDER_PREATTACK_TRACKING=YES id=(\d+) tick=(\d+) line=(\d+)$",
        status_text,
    )
    capture_tick = _first(r"(?m)^WISP_RENDER_CAPTURE_TICK=(\d+)$", status_text)
    capture_id = _first(r"(?m)^WISP_RENDER_CAPTURE_ID=(\d+)$", status_text)
    first_frame_camera_sample = _first(
        r"(?m)^WISP_RENDER_FIRST_FRAME_CAMERA_SAMPLE=(TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=render_safe_quarter tick=(\d+) .*? aimDot=([0-9.+-]+)(?: .*)?)$",
        status_text,
    )
    capture_camera = _first(
        r"(?m)^TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=render_safe_quarter tick=(\d+) "
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
        r"(?m)^WISP_ATTACK_CAMERA_SAMPLE=(TC_SMOKE_WISP_CAMERA=YES id=(\d+) aimTarget=render_safe_quarter tick=(\d+) .*? aimDot=([0-9.+-]+)(?: .*)?)$",
        status_text,
    )
    attack_camera_reaim = _first(
        r"(?m)^WISP_ATTACK_CAMERA_REAIM=(PREAIMED_NEXT_FRAME|LIVE_ENTITY_FACING)(?: (.+))?$",
        status_text,
    )
    live_reposition_pattern = re.compile(
        r"(?m)^WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION .*?Teleported Dev to ([0-9.+-]+), ([0-9.+-]+), ([0-9.+-]+)"
    )
    live_reposition_matches = list(live_reposition_pattern.finditer(status_text))
    prior_live_repositions = [
        match for match in live_reposition_matches
        if attack_camera_reaim is not None and match.start() < attack_camera_reaim.start()
    ]
    live_reposition = prior_live_repositions[-1] if prior_live_repositions else None
    attack_trigger_frame_status = _first(
        r"(?m)^WISP_ATTACKABLE_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) position=(PLATFORM|LIVE_WISP) gameTime=(\d+) playerTick=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    attackable_frame_status = _first(
        r"(?m)^WISP_ATTACK_INPUT_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) gameTime=(\d+) playerTick=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    input_correlation_status = _first(
        r"(?m)^WISP_ATTACK_INPUT_FRAME_CORRELATION=PASS source=InputEvent[+]AttackEntityEvent id=(\d+) tick=(\d+) gameTime=(\d+) playerTick=(\d+)$",
        status_text,
    )
    if pre_attack_track is not None and pre_attack_track_status is not None:
        if pre_attack_track.group(1) != pre_attack_track_status.group(1):
            errors.append("pre-attack tracker log and status use different Wisp IDs")
        if pre_attack_track.group(2) != pre_attack_track_status.group(2):
            errors.append("pre-attack tracker log and status use different Wisp ticks")
        log_lines = log_text.splitlines()
        actual_track_line = next(
            (index for index, line in enumerate(log_lines, start=1) if pre_attack_track.group(0) in line),
            None,
        )
        if actual_track_line is None or actual_track_line != int(pre_attack_track_status.group(3)):
            errors.append("pre-attack tracker status does not point to its exact client-log line")
        if first_frame_id is not None and pre_attack_track.group(1) != first_frame_id.group(1):
            errors.append("pre-attack camera tracker and first rendered Wisp use different IDs")
        if first_frame_tick is not None and int(pre_attack_track.group(2)) > int(first_frame_tick.group(1)):
            errors.append("first tracked Wisp tick occurs after the first accepted render frame")
    else:
        errors.append("missing exact pre-attack Wisp camera tracking evidence")

    if frame_status is None:
        errors.append("missing first post-LevelRenderer completed-frame status")
    else:
        if pre_attack_track_status is not None and first_frame_line_number is not None:
            if int(pre_attack_track_status.group(3)) >= int(first_frame_line_number.group(1)):
                errors.append("first completed render frame did not occur after pre-attack camera tracking")
        if first_frame_id is not None and frame_status.group(1) != first_frame_id.group(1):
            errors.append("first completed-frame status and selected first-frame status use different entity IDs")
        if first_frame_tick is not None and frame_status.group(2) != first_frame_tick.group(1):
            errors.append("first completed-frame status and selected first-frame status use different ticks")
        if wisp_rendered and first_frame_id is not None and wisp_rendered.group(1) != first_frame_id.group(1):
            errors.append("first rendered Wisp and selected tracked Wisp use different entity IDs")
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
    if (
        first_frame_id is not None
        and first_frame_tick is not None
        and pre_attack_track_status is not None
        and first_frame_line_number is not None
    ):
        log_lines = log_text.splitlines()
        track_line_number = int(pre_attack_track_status.group(3))
        completed_frame_line_number = int(first_frame_line_number.group(1))
        selected_render_prefix = (
            f"TC_SMOKE_WISP_RENDERED=YES id={first_frame_id.group(1)} "
            f"aspect=aer tick={first_frame_tick.group(1)}"
        )
        selected_render_lines = [
            index
            for index, line in enumerate(log_lines, start=1)
            if track_line_number < index < completed_frame_line_number
            and line.startswith(selected_render_prefix)
        ]
        if len(selected_render_lines) != 1:
            errors.append(
                "selected first tracked frame must have exactly one matching Wisp renderer callback: "
                f"id={first_frame_id.group(1)} tick={first_frame_tick.group(1)} "
                f"found={len(selected_render_lines)}"
            )
        if first_frame_camera_sample is not None:
            if first_frame_camera_sample.group(2, 3) != (
                first_frame_id.group(1),
                first_frame_tick.group(1),
            ):
                errors.append("selected first-frame camera sample does not match the tracked Wisp renderer tick")
    if attack_camera_frame is None:
        errors.append("missing camera frame used for the real Wisp attack")
    if attack_camera_complete is None:
        errors.append("missing completed-frame marker for the real attack camera sample")
    if attack_camera_sample is None:
        errors.append("missing copied live camera sample used for the real attack")
    if attack_camera_reaim is None:
        errors.append("missing provenance for the camera sample used by the real attack")
    if attack_trigger_frame_status is None:
        errors.append("missing status for the AFTER_LEVEL frame that dispatched the real click")
    elif attack_input is not None:
        if attack_trigger_frame_status.group(1) != attack_input.group(1) or attack_trigger_frame_status.group(3) != attack_input.group(1):
            errors.append("click-trigger frame and real input selected different Wisp IDs")
        trigger_pattern = re.compile(
            r"TC_SMOKE_WISP_FRAME_COMPLETE=YES id=(\d+) tick=(\d+) gameTime=(\d+) playerTick=(\d+) "
            r"hitType=ENTITY targetId=(\d+) targetType=thaumcraft:wisp blockPos=none"
        )
        trigger_matches = [
            frame
            for frame in trigger_pattern.finditer(log_text)
            if frame.group(1) == attack_trigger_frame_status.group(1)
            and frame.group(2) == attack_trigger_frame_status.group(2)
            and frame.group(3) == attack_trigger_frame_status.group(5)
            and frame.group(4) == attack_trigger_frame_status.group(6)
            and frame.start() < attack_input.start()
        ]
        if len(trigger_matches) != 1:
            errors.append("click-trigger status does not point to one real same-ID AFTER_LEVEL frame before input")
        elif attackable_frame is not None and trigger_matches[0].start() > attackable_frame.start():
            errors.append("click-trigger frame occurs after the frame correlated with the actual input")
    if input_correlation_status is None:
        errors.append("missing explicit InputEvent/AttackEntityEvent correlation status")
    elif attack_input and client_attack and input_correlation_status.group(1, 2, 3, 4) != (
        attack_input.group(1), client_attack.group(3), attack_input.group(4), attack_input.group(5)
    ):
        errors.append("explicit InputEvent/AttackEntityEvent correlation status does not match the real events")
    if attackable_frame_status is None:
        errors.append("missing status for the actual InputEvent-correlated Wisp camera frame")
    elif input_correlated_frame is None or attackable_frame_status.group(1, 2) != input_correlated_frame.group(1, 2):
        errors.append("input-frame status does not match the completed camera frame correlated to InputEvent")
    elif attackable_frame_status.group(4, 5) != input_correlated_frame.group(3, 4):
        errors.append("input-frame status gameTime/playerTick do not match the InputEvent-correlated frame")
    elif attack_input and (
        attackable_frame_status.group(3) != attack_input.group(1)
        or attackable_frame_status.group(4) != attack_input.group(4)
        or abs(int(attackable_frame_status.group(5)) - int(attack_input.group(5))) > 1
    ):
        errors.append("input-frame status does not match the exact same-ID InputEvent")
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
        if input_correlated_frame and (attack_id, attack_tick) != (input_correlated_frame.group(1), input_correlated_frame.group(2)):
            errors.append("attack camera sample does not match the completed frame correlated to InputEvent")
        if attackable_frame_status and (attack_id, attack_tick) != attackable_frame_status.group(1, 2):
            errors.append("attack camera status does not match the InputEvent-correlated camera-frame status")
        if client_attack and (attack_id, attack_tick) != (client_attack.group(2), client_attack.group(3)):
            errors.append("attack camera frame and AttackEntityEvent use different target/tick")
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
            if attack_camera_reaim is not None and attack_camera_reaim.group(1) == "LIVE_ENTITY_FACING":
                if live_reposition is None:
                    errors.append("live-reposition attack sample lacks a parseable server-confirmed teleport position")
                else:
                    reposition_x, reposition_y, reposition_z = map(float, live_reposition.groups())
                    reaim_position = _first(
                        r"Teleported Dev to ([0-9.+-]+), ([0-9.+-]+), ([0-9.+-]+)",
                        attack_camera_reaim.group(2) or "",
                    )
                    if reaim_position is None or any(
                        abs(actual - expected) > 0.05
                        for actual, expected in zip(
                            map(float, reaim_position.groups()),
                            map(float, live_reposition.groups()),
                        )
                    ):
                        errors.append("live-reposition attack re-aim coordinates differ from the server-confirmed teleport")
                    attack_hit_distance = float(attack_input.group(3)) if attack_input is not None else math.inf
                    # The vanilla ray can intersect the Wisp AABB within 3 blocks
                    # even when the entity-origin distance is greater than 3.
                    # Validate the actual hit point, not the earlier staging teleport.
                    if not (
                        attack_distance <= 6.0
                        and abs(attack_distance - measured_attack_distance) <= 0.1
                        and attack_input is not None
                        and 0.0 <= attack_hit_distance <= 3.01
                        and math.isfinite(attack_aim_dot)
                        and -1.0001 <= attack_aim_dot <= 1.0001
                    ):
                        errors.append(
                            "live-reposition attack camera sample is not backed by the exact same-ID in-reach InputEvent and AttackEntityEvent: "
                            f"player=({attack_px:.3f},{attack_py:.3f},{attack_pz:.3f}) "
                            f"distance={attack_distance:.3f} hitDistance={attack_hit_distance:.3f} "
                            f"aimDot={attack_aim_dot:.4f}"
                        )
            elif not (
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
        if attack_camera_reaim is not None and attack_camera_reaim.group(1) == "PREAIMED_NEXT_FRAME":
            first_frame_status_position = status_text.find("WISP_RENDER_FIRST_FRAME=YES")
            if first_frame_status_position < 0 or reaim_position < first_frame_status_position:
                errors.append("pre-aimed attack camera was not sourced after the first completed Wisp frame")
            if first_frame_camera_sample is not None:
                if attack_camera_sample.group(2) != first_frame_camera_sample.group(2):
                    errors.append("pre-aimed attack sample uses a different Wisp entity")
                if int(attack_camera_sample.group(3)) <= int(first_frame_camera_sample.group(3)):
                    errors.append("pre-aimed attack sample must be later than the first completed Wisp frame")
        elif attack_camera_reaim is not None and status_text.find("WISP_CAMERA_REPOSITION=LIVE_WISP_MELEE_POSITION") < 0:
            errors.append("live-reposition attack sample lacks its server-confirmed player teleport marker")

    if telemetry is None:
        errors.append("missing first-frame camera telemetry")
    else:
        camera_id, camera_tick = telemetry.group(1), telemetry.group(2)
        player_x, player_y, player_z, wisp_x, wisp_y, wisp_z, distance, aim_dot = map(float, telemetry.groups()[2:])
        if first_frame_id is None or first_frame_tick is None:
            errors.append("missing first-frame entity ID/tick status")
        elif (camera_id, camera_tick) != (first_frame_id.group(1), first_frame_tick.group(1)):
            errors.append("first-frame camera telemetry and first-frame status use different ID/tick")
        if capture_id is None or capture_tick is None:
            errors.append("missing attack-input screenshot entity ID/tick")
        elif camera_id != capture_id.group(1):
            errors.append("attack-input screenshot and first-frame camera use different Wisp IDs")
        if capture_camera is None:
            errors.append("missing camera sample copied at attack-input screenshot capture")
        elif capture_id and capture_tick and capture_camera.group(1, 2) != (capture_id.group(1), capture_tick.group(1)):
            errors.append("attack-input screenshot camera sample does not match capture ID/tick")
        if capture_id is not None and capture_tick is not None and attack_camera_frame is not None:
            if (capture_id.group(1), capture_tick.group(1)) != (
                attack_camera_frame.group(1), attack_camera_frame.group(2)
            ):
                errors.append("attack-input aim capture does not reuse the exact attack-input camera frame")
        if capture_camera is not None:
            capture_player_x, capture_player_y, capture_player_z, capture_wisp_x, capture_wisp_y, capture_wisp_z, capture_aim_dot = map(
                float, capture_camera.groups()[2:]
            )
            capture_distance = math.dist(
                (capture_player_x, capture_player_y, capture_player_z),
                (capture_wisp_x, capture_wisp_y, capture_wisp_z),
            )
            if attack_camera_reaim is not None and attack_camera_reaim.group(1) == "LIVE_ENTITY_FACING":
                if live_reposition is None:
                    errors.append("live attack-input camera sample lacks a server-confirmed teleport position")
                else:
                    capture_hit_distance = float(attack_input.group(3)) if attack_input is not None else math.inf
                    # This is the exact ID/tick already tied to the real InputEvent.
                    # A moving Wisp may no longer be at the original staging point.
                    if not (
                        capture_distance <= 6.0
                        and attack_input is not None
                        and 0.0 <= capture_hit_distance <= 3.01
                        and attack_camera_frame is not None
                        and abs(capture_distance - attack_distance) <= 0.1
                        and math.isfinite(capture_aim_dot)
                        and -1.0001 <= capture_aim_dot <= 1.0001
                    ):
                        errors.append(
                            "live attack-input camera sample is not tied to a same-tick camera frame and in-reach InputEvent EntityHitResult: "
                            f"player=({capture_player_x:.3f},{capture_player_y:.3f},{capture_player_z:.3f}) "
                            f"distance={capture_distance:.3f} hitDistance={capture_hit_distance:.3f} "
                            f"aimDot={capture_aim_dot:.4f}"
                        )
            elif not (
                12.0 <= capture_player_x <= 13.0
                and 148.5 <= capture_player_y <= 149.5
                and 10.0 <= capture_player_z <= 11.0
                and capture_distance <= 16.0
                and math.isfinite(capture_aim_dot)
                and -1.0001 <= capture_aim_dot <= 1.0001
            ):
                errors.append(
                    "attack-input camera sample was outside the verified platform/melee bounds: "
                    f"player=({capture_player_x:.3f},{capture_player_y:.3f},{capture_player_z:.3f}) "
                    f"distance={capture_distance:.3f} aimDot={capture_aim_dot:.4f}"
                )
        if capture_id and capture_tick:
            capture_frame = f"TC_SMOKE_WISP_FRAME_COMPLETE=YES id={capture_id.group(1)} tick={capture_tick.group(1)}"
            if capture_frame not in log_text:
                errors.append("attack-input screenshot was not taken after its matching completed level frame")
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

    bolt_baseline = screenshot_dir / BOLT_BASELINE_SCREENSHOT
    bolt_frames = (
        (1, screenshot_dir / "80-wisp-zap-frame-a.png"),
        (2, screenshot_dir / "81-wisp-zap-frame-b.png"),
        (3, screenshot_dir / "82-wisp-zap-frame-c.png"),
    )
    bolt_cycle_errors, visible_bolt_frames, _ = verify_bolt_cycle(
        bolt_frames,
        screenshot_dir / "83-wisp-zap-after-expiry.png",
        bolt_baseline,
    )
    errors.extend(bolt_cycle_errors)
    for age, bolt_metrics in visible_bolt_frames:
        print(
            f"WISP_FXBOLT_PIXEL_COMPONENT=YES age={age} "
            f"pixels={bolt_metrics['pixels']:.0f} length={bolt_metrics['length']:.1f} "
            f"width={bolt_metrics['width']:.1f} angleDegrees={bolt_metrics['angle_degrees']:.1f} "
            f"bbox={bolt_metrics['left']:.0f},{bolt_metrics['top']:.0f},"
            f"{bolt_metrics['right']:.0f},{bolt_metrics['bottom']:.0f}"
        )
    if not bolt_cycle_errors:
        print("WISP_FXBOLT_AFTER_EXPIRY=PASS visibleComponents=0")

    for pattern in BAD_LOG_PATTERNS:
        if pattern.search(log_text):
            errors.append(f"client log contains a crash or essentia texture error: {pattern.pattern}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aim-only", type=Path, help="verify one crosshair-centered Aer Wisp screenshot")
    parser.add_argument("--baseline-only", type=Path, help="verify one empty pre-Wisp baseline screenshot")
    parser.add_argument("--extract-video-frames-only", action="store_true")
    parser.add_argument("--video-status", type=Path)
    parser.add_argument("--video-file", type=Path)
    parser.add_argument("--video-screenshots", type=Path)
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

    if args.extract_video_frames_only:
        if args.video_status is None or args.video_file is None or args.video_screenshots is None:
            parser.error("--extract-video-frames-only requires --video-status, --video-file and --video-screenshots")
        errors = extract_video_frames(args.video_status, args.video_file, args.video_screenshots)
        if errors:
            for error in errors:
                print(f"WISP_ZAP_VIDEO_EXTRACTION_ERROR: {error}", file=sys.stderr)
            return 1
        print("WISP_ZAP_VIDEO_FRAMES_EXTRACTED=YES")
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
    print("WISP_FXBOLT_VISUAL_ACCEPTANCE=PASS afterExpiry=clear")
    print("WISP_ZAP_REAL_CLIENT_EVIDENCE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
