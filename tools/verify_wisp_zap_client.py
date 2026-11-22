#!/usr/bin/env python3
"""Fail-closed verifier for a real two-entity BETA26 Wisp zap client smoke."""
from pathlib import Path
import argparse
import re
import sys


SCREENSHOTS = (
    "79-wisp-before-a.png",
    "79-wisp-before-b.png",
    "80-wisp-zap-frame-a.png",
    "81-wisp-zap-frame-b.png",
    "82-wisp-zap-frame-c.png",
    "83-wisp-zap-after-expiry.png",
)

REQUIRED_LOG_MARKERS = (
    "TC_SMOKE_WISP_PLATFORM_READY=YES",
    "TC_SMOKE_WISP_PLAYER_SURVIVAL=YES",
    "TC_SMOKE_WISP_SPAWNED=YES",
    "TC_SMOKE_WISP_ASPECT_AER=YES",
    "TC_SMOKE_WISP_RENDERED=YES",
)

REQUIRED_STATUS = (
    "WISP_ZAP_ATTACK_INPUT=REAL_CLIENT_LEFT_CLICK",
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

    for pattern in BAD_LOG_PATTERNS:
        if pattern.search(log_text):
            errors.append(f"client log contains a crash or essentia texture error: {pattern.pattern}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("client_log", type=Path)
    parser.add_argument("status_file", type=Path)
    parser.add_argument("screenshots", type=Path)
    args = parser.parse_args()

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
