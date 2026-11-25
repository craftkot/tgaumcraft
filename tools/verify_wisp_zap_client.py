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
# The same-camera baseline contains the Wisp billboard; its actual screen-space
# centre is detected from pixels rather than assumed to be the crosshair.
BOLT_ROI = (512, 480, 256, 280)  # width, height, left, top
VIEWPORT_WIDTH = 1024
VIEWPORT_HEIGHT = 768
BOLT_SOURCE_EXCLUSION_RADIUS = 28

REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_WISP_CHUNK_LOADED=YES",
    "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
    "TC_SMOKE_WISP_RENDERED=YES",
    "TC_SMOKE_WISP_FRAME_COMPLETE=YES",
    "TC_SMOKE_WISP_ATTACK_INPUT=YES",
    "TC_SMOKE_WISP_CLIENT_ATTACK=YES",
    "TC_SMOKE_WISP_CAMERA_TRACK=YES",
    "TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES",
    "TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES",
    "TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES",
    "TC_SMOKE_WISP_HURT=YES accepted=true",
    "TC_SMOKE_WISP_SUPPORT_BLOCK=YES",
    "TC_SMOKE_WISP_SOURCE_ELEVATED=YES",
    "TC_SMOKE_WISP_ZAP_FRAME=YES",
    "TC_SMOKE_WISP_ZAP_EXPIRED=YES",
)

REQUIRED_STATUS = (
    "WISP_FIXTURE_PLATFORM_FILL=YES",
    "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
    "WISP_FIXTURE_TELEPORT=YES",
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
    "WISP_RENDER_CAPTURE_VALIDATED_AFTER_HIT=YES",
    "WISP_ATTACK_TARGET_CONFIRMED=YES",
    "WISP_ATTACKABLE_FRAME=YES",
    "WISP_ATTACK_INPUT_FRAME=YES",
    "WISP_ATTACK_CAMERA_REAIM=",
    "WISP_ATTACK_CAMERA_FRAME=YES",
    "WISP_ATTACK_CAMERA_FRAME_COMPLETE=YES",
    "WISP_ATTACK_CAMERA_FRAME_USED=YES",
    "WISP_ATTACK_HURT_ACCEPTED=YES",
    "WISP_ZAP_BASELINE_CAPTURE=BEFORE_WISP_SPAWN",
    "WISP_ZAP_TEST_TIME=MIDNIGHT",
    "WISP_CAMERA_SIDE_VIEW=YES id=",
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


def _find_wisp_source_center(
    pixels: bytes, roi_width: int, roi_height: int, roi_left: int, roi_top: int
) -> tuple[float, float] | None:
    """Locate the compact Aer billboard in a real screenshot, excluding the HUD crosshair."""
    mask = bytearray(roi_width * roi_height)
    for index in range(roi_width * roi_height):
        x = index % roi_width
        y = index // roi_width
        if _is_crosshair_pixel(roi_left + x, roi_top + y, VIEWPORT_WIDTH, VIEWPORT_HEIGHT):
            continue
        red, green, blue = pixels[index * 3:index * 3 + 3]
        if red >= 160 and green >= 160 and blue <= 240 and (
            red - blue >= 12 or green - blue >= 18
        ):
            mask[index] = 1

    visited = bytearray(len(mask))
    candidates: list[tuple[float, int, float, float]] = []
    for first in range(len(mask)):
        if not mask[first] or visited[first]:
            continue
        queue = deque([first])
        visited[first] = 1
        count = 0
        sum_x = sum_y = 0.0
        min_x = min_y = 1 << 30
        max_x = max_y = -1
        while queue:
            current = queue.popleft()
            x = current % roi_width
            y = current // roi_width
            count += 1
            sum_x += x
            sum_y += y
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

        box_width = max_x - min_x + 1
        box_height = max_y - min_y + 1
        compactness = count / float(box_width * box_height)
        if (
            count >= 12
            and min(box_width, box_height) >= 3
            and max(box_width, box_height) <= 96
            and compactness >= 0.08
        ):
            candidates.append((
                count * compactness,
                count,
                sum_x / count,
                sum_y / count,
            ))

    if not candidates:
        return None
    _, _, center_x, center_y = max(candidates, key=lambda item: item[0])
    return roi_left + center_x, roi_top + center_y


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

    baseline_source = _find_wisp_source_center(
        baseline_pixels, roi_width, roi_height, roi_left, roi_top
    )
    if baseline_source is None:
        return [f"{baseline_path.name} has no detectable Aer Wisp billboard in the FXBolt ROI"], []
    frame_source = _find_wisp_source_center(
        frame_pixels, roi_width, roi_height, roi_left, roi_top
    ) if not expected_visible else None
    source_center = frame_source or baseline_source
    exclusion_centers = [
        (baseline_source[0] - roi_left, baseline_source[1] - roi_top)
    ]
    if frame_source is not None:
        exclusion_centers.append((frame_source[0] - roi_left, frame_source[1] - roi_top))
    source_x = source_center[0] - roi_left
    source_y = source_center[1] - roi_top
    exclusion_squared = BOLT_SOURCE_EXCLUSION_RADIUS ** 2
    mask = bytearray(pixel_count)
    for index in range(pixel_count):
        x = index % roi_width
        y = index // roi_width
        if any(
            (x - center_x) ** 2 + (y - center_y) ** 2 <= exclusion_squared
            for center_x, center_y in exclusion_centers
        ):
            continue
        offset = index * 3
        frame_r, frame_g, frame_b = frame_pixels[offset], frame_pixels[offset + 1], frame_pixels[offset + 2]
        base_r, base_g, base_b = baseline_pixels[offset], baseline_pixels[offset + 1], baseline_pixels[offset + 2]
        delta_r = frame_r - base_r
        delta_g = frame_g - base_g
        delta_b = frame_b - base_b

        # BETA26 FXBolt alpha is clamp(1 - particleAge / 3, 0.1, 1):
        # age 1/2/3 contributes 2/3, 1/3 and 0.1 of Aer (#FFFF7E)
        # through SRC_ALPHA/ONE additive blending. Absolute RGB thresholds
        # reject a correctly rendered age-3 beam on a dark scene. Compare
        # against a third-person pre-payload frame and retain the yellow
        # channel relationship rather than treating dim pixels as absent.
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
        min_x = min_y = 1 << 30
        max_x = max_y = -1
        min_source_distance = float("inf")
        max_source_distance = 0.0
        while queue:
            current = queue.popleft()
            x = current % roi_width
            y = current // roi_width
            count += 1
            sum_x += x
            sum_y += y
            sum_xx += x * x
            sum_yy += y * y
            sum_xy += x * y
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
            source_distance = math.hypot(x - source_x, y - source_y)
            min_source_distance = min(min_source_distance, source_distance)
            max_source_distance = max(max_source_distance, source_distance)
            for neighbour_y in range(max(0, y - 1), min(roi_height, y + 2)):
                row = neighbour_y * roi_width
                for neighbour_x in range(max(0, x - 1), min(roi_width, x + 2)):
                    neighbour = row + neighbour_x
                    if mask[neighbour] and not visited[neighbour]:
                        visited[neighbour] = 1
                        queue.append(neighbour)

        if count < 16:
            continue
        mean_x = sum_x / count
        mean_y = sum_y / count
        dx = mean_x - source_x
        dy = mean_y - source_y
        distance = math.hypot(dx, dy)
        if distance < 30.0 or distance > 300.0:
            continue
        if min_source_distance > 48.0 or max_source_distance < 60.0:
            continue

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
        alignment = abs(
            (math.cos(angle) * dx + math.sin(angle) * dy) / distance
        )

        # A real bolt is a narrow, elongated component extending radially
        # away from the Wisp source. A compact orb or isolated star cannot pass.
        if (
            major_length >= 24.0
            and minor_width <= 16.0
            and elongation >= 5.0
            and alignment >= 0.65
        ):
            candidates.append(
                {
                    "pixels": float(count),
                    "source_x": source_center[0],
                    "source_y": source_center[1],
                    "length": major_length,
                    "width": minor_width,
                    "distance": distance,
                    "alignment": alignment,
                    "min_source_distance": min_source_distance,
                    "max_source_distance": max_source_distance,
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
            f"{path.name} has no elongated Aer-yellow FXBolt segment outside "
            "the Wisp billboard ROI"
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
    payload = _first(
        r"(?m)^WISP_ZAP_PAYLOAD_RECEIVED=YES source=(\d+) target=(\d+) createdAt=(\d+) monotonicNs=(\d+)$",
        status_text,
    )
    observed = re.findall(
        r"(?m)^WISP_ZAP_FRAME_OBSERVED=YES source=(\d+) createdAt=(\d+) ageTick=(\d+) "
        r"wispTick=(\d+) observedAtMonotonicNs=(\d+)$",
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
    if payload is None:
        errors.append("missing client zap-payload monotonic timestamp")
    if started is None or side_view is None or payload is None or len(observed) != 3 or errors:
        return errors

    if side_view.group(1) != payload.group(1):
        errors.append("side-view camera and zap payload belong to different Wisp source IDs")
    if int(side_view.group(2)) >= int(payload.group(4)):
        errors.append("side-view camera was not established before zap payload receipt")
    if [int(frame[2]) for frame in observed] != [1, 2, 3]:
        errors.append("timestamped Wisp render observations are not ages 1, 2, 3")
    if len({(frame[0], frame[1]) for frame in observed}) != 1:
        errors.append("timestamped Wisp observations do not belong to one source/createdAt event")
    if len({frame[3] for frame in observed}) != 3:
        errors.append("timestamped Wisp observations do not use three distinct Wisp ticks")
    observed_ns = [int(frame[4]) for frame in observed]
    if observed_ns != sorted(observed_ns) or len(set(observed_ns)) != 3:
        errors.append("Wisp render observation timestamps are not strictly increasing")
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
        if side_view_ns <= first_frame_ns:
            raise RuntimeError("side-view camera timestamp predates the first recorded video frame")
        if payload_ns - side_view_ns < frame_period_ns * 3.0:
            raise RuntimeError(
                "no safe pre-payload video window: side-view-to-payload interval is shorter than three frames"
            )
        baseline_first = max(
            0, int(math.floor((side_view_ns - first_frame_ns) * fps / 1_000_000_000.0)) + 1
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
            baseline_index = None
            roi_width, roi_height, roi_left, roi_top = BOLT_ROI
            expected_roi_bytes = roi_width * roi_height * 3
            for index in range(baseline_first, baseline_last + 1):
                candidate = temporary / f"side-view-baseline-{index}.png"
                _extract_video_frame(video_file, index, candidate)
                roi = subprocess.run(
                    [
                        "convert", str(candidate),
                        "-crop", f"{roi_width}x{roi_height}+{roi_left}+{roi_top}",
                        "+repage", "-depth", "8", "RGB:-",
                    ],
                    capture_output=True,
                    check=False,
                )
                if roi.returncode != 0:
                    detail = roi.stderr.decode("utf-8", errors="replace").strip()
                    raise RuntimeError(f"cannot inspect pre-payload side-view frame {index}: {detail}")
                if len(roi.stdout) != expected_roi_bytes:
                    raise RuntimeError(
                        f"unexpected side-view baseline ROI byte count: {len(roi.stdout)} != {expected_roi_bytes}"
                    )
                if _find_wisp_source_center(roi.stdout, roi_width, roi_height, roi_left, roi_top) is not None:
                    shutil.copyfile(candidate, baseline_path)
                    baseline_index = index
                    break
            if baseline_index is None:
                raise RuntimeError(
                    "no pre-payload third-person frame contains a detectable Aer Wisp billboard"
                )
            baseline_monotonic_ns = first_frame_ns + int(round(baseline_index * 1_000_000_000.0 / fps))
            if not side_view_ns < baseline_monotonic_ns < payload_ns:
                raise RuntimeError(
                    "selected Wisp baseline frame is not strictly between side-view setup and zap payload"
                )
            # Persist the selected same-camera baseline before the temporary
            # extraction directory is removed. The later pixel verifier needs
            # this exact frame to locate the Wisp source and reject player/HUD
            # pixels that merely resemble the short-lived FXBolt.
            shutil.copyfile(baseline_path, screenshot_dir / BOLT_BASELINE_SCREENSHOT)

            previous_index = -1
            for source_id, created_at, age_tick, wisp_tick, observed_at in observed:
                elapsed = (int(observed_at) - first_frame_ns) / 1_000_000_000.0
                if elapsed < 0.0 or elapsed >= duration:
                    raise RuntimeError(
                        f"age={age_tick} observation lies outside the recorded video: elapsed={elapsed:.4f}s duration={duration:.4f}s"
                    )
                base_index = int(round(elapsed * fps))
                candidates = sorted({
                    index for index in (base_index - 1, base_index, base_index + 1)
                    if previous_index < index < frame_count
                })
                if not candidates:
                    raise RuntimeError(f"no video frame near Wisp age={age_tick}")
                ranked: list[tuple[int, int, int, Path]] = []
                for index in candidates:
                    candidate = temporary / f"age-{age_tick}-{index}.png"
                    _extract_video_frame(video_file, index, candidate)
                    difference = _crop_pixel_difference(candidate, after_expiry, temporary)
                    ranked.append((difference, -abs(index - base_index), index, candidate))
                difference, _, selected_index, selected_path = max(ranked, key=lambda item: (item[0], item[1]))
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
    """Select the unique completed frame whose vanilla hit result was consumed by this click."""
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
        and frame.group(3) == attack_input.group(3)
        and frame.group(4) == attack_input.group(4)
        and frame.start() < attack_input.start()
    ]
    return candidates[0] if len(candidates) == 1 else None


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
        r"TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=(\d+) yaw=(-?[0-9.]+) "
        r"pitch=(-?[0-9.]+) cameraType=THIRD_PERSON_BACK monotonicNs=(\d+)", log_text
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
    rendered = _first(
        r"TC_SMOKE_WISP_ZAP_RENDERED=YES source=(\d+) target=(\d+) points=(\d+) age=([0-9.]+)"
        r" texture=thaumcraft:textures/misc/essentia\.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        log_text,
    )

    for label, match in (("client Wisp camera sample", wisp_camera),
                         ("pre-attack Wisp camera tracking", pre_attack_track),
                         ("camera aim lock stopped after real hit", camera_stopped),
                         ("third-person-back side-view camera", side_view),
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

    if client_attack and camera_stopped and camera_stopped.group(1) != client_attack.group(2):
        errors.append("camera aim lock stopped for a different Wisp than the real client attack")
    if camera_stopped and side_view and camera_stopped.group(1) != side_view.group(1):
        errors.append("side-view camera sample belongs to a different Wisp than the stopped tracker")
    if side_view and received:
        if side_view.group(1) != received.group(1):
            errors.append("side-view camera and zap payload refer to different source entities")
        if int(side_view.group(4)) >= int(received.group(6)):
            errors.append("side-view camera was selected after the client received the zap payload")
    if side_view:
        yaw = float(side_view.group(2))
        pitch = float(side_view.group(3))
        if abs(abs(yaw) - 90.0) > 5.0 or abs(pitch) > 5.0:
            errors.append(f"camera is not side-on: yaw={yaw:.2f} pitch={pitch:.2f}")
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
                rf"(?m)^WISP_ZAP_EFFECT_EXPIRED=YES source={re.escape(source_id)} createdAt={re.escape(created_at)}$",
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
            capture_for_input_position = status_text.find("WISP_RENDER_CAPTURE_FOR_ATTACK_INPUT=YES")
            attack_target_position = status_text.find("WISP_ATTACK_TARGET_CONFIRMED=YES")
            attack_hurt_position = status_text.find("WISP_ATTACK_HURT_ACCEPTED=YES")
            if not (
                0 <= capture_for_input_position < attack_target_position
                < attack_hurt_position < camera_capture_position
            ):
                errors.append(
                    "aim screenshot must be captured in the attackable frame before click, then validated after accepted damage"
                )
            side_view_position = status_text.find("WISP_CAMERA_SIDE_VIEW=YES id=")
            baseline_capture_position = status_text.find(
                "WISP_ZAP_BASELINE_CAPTURE=THIRD_PERSON_BACK_SIDE_ON_BEFORE_PAYLOAD"
            )
            if not (
                0 <= capture_for_input_position < attack_target_position
                < side_view_position < attack_hurt_position < camera_capture_position
            ):
                errors.append(
                    "side-view camera must be selected by AttackEntityEvent after target selection and before accepted damage"
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

    for marker in REQUIRED_STATUS:
        if marker not in status_text:
            errors.append(f"missing real-client status marker: {marker}")

    target_set_position = log_text.find("TC_SMOKE_WISP_TARGET_SET=YES")
    knockback_set_position = log_text.find(
        "Base value for attribute Knockback Resistance for entity Dev set to 1.0"
    )
    stabilized_position = status_text.find("WISP_CAMERA_KNOCKBACK_RESISTANCE=100_PERCENT_HORIZONTAL")
    first_zap_capture_position = status_text.find("WISP_ZAP_FRAME_CAPTURE=YES")
    if target_set_position < 0 or knockback_set_position <= target_set_position:
        errors.append("player knockback resistance was not confirmed after the accepted Wisp hit")
    if (
        stabilized_position < 0
        or first_zap_capture_position < 0
        or stabilized_position > first_zap_capture_position
    ):
        errors.append("post-hit player stabilization was not confirmed before FXBolt frame capture")

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
    attack_trigger_frame_status = _first(
        r"(?m)^WISP_ATTACKABLE_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) position=(PLATFORM|LIVE_WISP) gameTime=(\d+) playerTick=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
        status_text,
    )
    attackable_frame_status = _first(
        r"(?m)^WISP_ATTACK_INPUT_FRAME=YES id=(\d+) tick=(\d+) hitType=ENTITY targetId=(\d+) gameTime=(\d+) playerTick=(\d+) distance=([0-9.+-]+) aimDot=([0-9.+-]+)$",
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
    if attackable_frame_status is None:
        errors.append("missing status for the actual attackable Wisp input frame")
    elif attackable_frame is None or attackable_frame_status.group(1, 2, 3) != attackable_frame.group(1, 2, 5):
        errors.append("input-frame status does not match the real AFTER_LEVEL hit-result marker")
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
            if not (
                12.0 <= capture_player_x <= 13.0
                and 148.5 <= capture_player_y <= 149.5
                and 10.0 <= capture_player_z <= 11.0
                and capture_distance <= 16.0
                and capture_aim_dot >= 0.99
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
    for age, name in ((1, "80-wisp-zap-frame-a.png"), (2, "81-wisp-zap-frame-b.png"), (3, "82-wisp-zap-frame-c.png")):
        bolt_errors, bolt_metrics = verify_bolt_segment(
            screenshot_dir / name, expected_visible=True, baseline_path=bolt_baseline
        )
        errors.extend(f"FXBolt age={age}: {error}" for error in bolt_errors)
        if bolt_metrics is not None:
            print(
                f"WISP_FXBOLT_PIXEL_COMPONENT=YES age={age} screenshot={name} "
                f"pixels={bolt_metrics['pixels']:.0f} length={bolt_metrics['length']:.1f} "
                f"width={bolt_metrics['width']:.1f} "
                f"sourceRange={bolt_metrics['min_source_distance']:.1f}..{bolt_metrics['max_source_distance']:.1f} "
                f"alignment={bolt_metrics['alignment']:.3f} "
                f"bbox={bolt_metrics['left']:.0f},{bolt_metrics['top']:.0f},"
                f"{bolt_metrics['right']:.0f},{bolt_metrics['bottom']:.0f}"
            )
    after_expiry_errors, after_expiry_metrics = verify_bolt_segment(
        screenshot_dir / "83-wisp-zap-after-expiry.png",
        expected_visible=False,
        baseline_path=bolt_baseline,
    )
    errors.extend(after_expiry_errors)
    if not after_expiry_errors:
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
    print("WISP_FXBOLT_VISUAL_ACCEPTANCE=PASS ages=1,2,3 afterExpiry=clear")
    print("WISP_ZAP_REAL_CLIENT_EVIDENCE_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
