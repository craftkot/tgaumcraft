#!/usr/bin/env python3
"""Fail-closed verifier for a real two-entity BETA26 Wisp zap client smoke."""
from pathlib import Path
import argparse
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

AIM_SCREENSHOTS = SCREENSHOTS[:3]
AIM_ROI = (128, 128, 448, 320)

REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_WISP_CHUNK_LOADED=YES",
    "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
    "TC_SMOKE_WISP_SPAWNED=YES",
    "TC_SMOKE_WISP_ASPECT_AER=YES",
    "TC_SMOKE_WISP_RENDERED=YES",
    "TC_SMOKE_WISP_PRESENT_FOR_AIM=YES",
)

REQUIRED_STATUS = (
    "WISP_FIXTURE_PLATFORM_FILL=YES",
    "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
    "WISP_ZAP_AIM=LIVE_ENTITY_RELATIVE",
    "WISP_ZAP_AIM_TELEPORT=YES",
    "WISP_RENDER_AFTER_AIM=YES",
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




def verify_aim_screenshot(path: Path) -> tuple[list[str], int, tuple[int, int, int, int] | None]:
    """Require Aer-tinted Wisp pixels near the 1024x768 viewport crosshair.

    ImageMagick is already installed by the isolated client workflow. Reading
    only the 128x128 crosshair ROI excludes the player's hand, HUD and most of
    the animated platform/background, so a renderer callback alone cannot pass.
    """
    if not path.is_file() or path.stat().st_size < 128:
        return [f"missing or empty aim screenshot: {path.name}"], 0, None

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
        return ["ImageMagick convert is required to inspect Wisp aim screenshots"], 0, None

    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        return [f"cannot decode Wisp aim screenshot {path.name}: {detail}"], 0, None

    pixels = result.stdout
    expected = width * height * 3
    if len(pixels) != expected:
        return [f"unexpected Wisp aim ROI byte count: {len(pixels)} != {expected}"], 0, None

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

    count = len(xs)
    bbox = (min(xs), min(ys), max(xs), max(ys)) if count else None
    errors: list[str] = []
    if count < 12:
        errors.append(
            f"{path.name} crosshair ROI contains only {count} Aer-tinted pixels; expected at least 12"
        )
    else:
        center_x = sum(xs) / count
        center_y = sum(ys) / count
        if (center_x - width / 2) ** 2 + (center_y - height / 2) ** 2 > 48 ** 2:
            errors.append(
                f"{path.name} Aer-tinted pixels are not near the crosshair: "
                f"centroid=({center_x:.1f},{center_y:.1f})"
            )
    return errors, count, bbox


def _first(pattern: str, text: str):
    return re.search(pattern, text)


def verify(log_text: str, status_text: str, screenshot_dir: Path) -> list[str]:
    errors: list[str] = []

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
    wisp_rendered = _first(
        r"TC_SMOKE_WISP_RENDERED=YES id=(\d+) aspect=aer",
        log_text,
    )
    rendered = _first(
        r"TC_SMOKE_WISP_ZAP_RENDERED=YES source=(\d+) target=(\d+) points=(\d+) age=([0-9.]+)"
        r" texture=thaumcraft:textures/misc/essentia\.png blend=SRC_ALPHA_ONE depthWrite=OFF",
        log_text,
    )

    for label, match in (("client Wisp entity renderer", wisp_rendered),
                         ("player hit/target", hit), ("server payload send", sent),
                         ("client payload receive", received), ("client additive render", rendered)):
        if match is None:
            errors.append(f"missing real gameplay/render evidence: {label}")

    if wisp_rendered and hit and wisp_rendered.group(1) != hit.group(1):
        errors.append("rendered Wisp and player-hit source use different entity IDs")
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

    aim_teleport = _first(
        r"(?m)^WISP_ZAP_AIM_TELEPORT=YES .*Teleported Dev to ([0-9.+-]+), ([0-9.+-]+), ([0-9.+-]+)",
        status_text,
    )
    if aim_teleport is None:
        errors.append("missing live-entity-relative player teleport result")

    aim_marker_count = status_text.count("WISP_AIM_SCREENSHOT_VISIBLE=YES")
    if aim_marker_count < len(AIM_SCREENSHOTS):
        errors.append(
            f"only {aim_marker_count}/{len(AIM_SCREENSHOTS)} crosshair-visibility markers were recorded"
        )

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
