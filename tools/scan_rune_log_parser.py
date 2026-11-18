"""Parse the actual scan-rune diagnostics emitted by ScanRuneParticle."""
from __future__ import annotations

import re

_NUMBER = r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[Ee][+-]?\d+)?"

_CREATED = re.compile(
    r"TC_SMOKE_SCAN_RUNE_CREATED=YES face=(\d+) lifetime=(\d+) "
    r"x=(" + _NUMBER + r") y=(" + _NUMBER + r") z=(" + _NUMBER + r") "
    r"localX=(" + _NUMBER + r") localY=(" + _NUMBER + r") localZ=(" + _NUMBER + r")"
)
_RENDERED = re.compile(
    r"TC_SMOKE_SCAN_RUNE_RENDERED=YES face=(\d+) alpha=(" + _NUMBER + r") size=(" + _NUMBER + r") "
    r"cameraYaw=(" + _NUMBER + r") lookX=(" + _NUMBER + r") lookY=(" + _NUMBER + r") "
    r"lookZ=(" + _NUMBER + r") worldX=(" + _NUMBER + r") worldY=(" + _NUMBER + r") "
    r"worldZ=(" + _NUMBER + r") frontDot=(" + _NUMBER + r") "
    r"quadFacingDot=(" + _NUMBER + r")"
)
_EXPIRED = re.compile(
    r"TC_SMOKE_SCAN_RUNE_EXPIRED=YES face=(\d+) age=(\d+) lifetime=(\d+) "
    r"drift=(" + _NUMBER + r")"
)


_ENTITY_TARGET = re.compile(
    r"TC_SMOKE_SCAN_RUNE_ENTITY_TARGET=YES type=([^ ]+) height=(" + _NUMBER + r") "
    r"units=(\d+) x=(" + _NUMBER + r") y=(" + _NUMBER + r") "
    r"eyeHeight=(" + _NUMBER + r") z=(" + _NUMBER + r")"
)


def parse_scan_rune_log(log: str) -> tuple[list[tuple[str, ...]], list[tuple[str, ...]], list[tuple[str, ...]]]:
    """Return (created, rendered, expired) marker fields in emitted order."""
    return (
        [match.groups() for match in _CREATED.finditer(log)],
        [match.groups() for match in _RENDERED.finditer(log)],
        [match.groups() for match in _EXPIRED.finditer(log)],
    )

def parse_scan_rune_entity_targets(log: str) -> list[tuple[str, ...]]:
    """Return real entity targets selected by the client Thaumometer use path."""
    return [match.groups() for match in _ENTITY_TARGET.finditer(log)]


def visible_rune_render_samples(rendered: list[tuple[str, ...]]) -> list[tuple[int | float, ...]]:
    """Keep rendered runes with visible alpha and any non-zero source scale.

    BETA26 uses particleScale = 1 + gaussian * 0.1 and renders width
    0.3 * particleScale without a clamp. A valid rune can be smaller than
    0.25 blocks; a negative Gaussian tail mirrors the quad but still has area.
    """
    visible = []
    for row in rendered:
        if len(row) != 12:
            raise ValueError(f"Expected 12 scan-rune render fields, got {len(row)}")
        face = int(row[0])
        values = tuple(float(value) for value in row[1:])
        alpha, size = values[0], values[1]
        if alpha >= 0.25 and size != 0.0:
            visible.append((face, *values))
    return visible

_SPARKLE_CREATED = re.compile(
    r"TC_SMOKE_THAUMOMETER_SPARKLE_CREATED=YES id=(\\d+) firstFrame=(\\d+) "
    r"lifetime=(\\d+) delay=(\\d+) alphaKeys=(\\d+) red=(\\d+) green=(\\d+) "
    r"blue=(\\d+) scale=(" + _NUMBER + r") moonPhase=(\\d+) windRoll=(\\d+) "
    r"windX=(" + _NUMBER + r") windZ=(" + _NUMBER + r")"
)
_SPARKLE_RENDERED = re.compile(
    r"TC_SMOKE_THAUMOMETER_SPARKLE_RENDERED=YES id=(\\d+) age=(\\d+) frame=(\\d+) "
    r"alpha=(" + _NUMBER + r") scale=(" + _NUMBER + r")"
)
_SPARKLE_EXPIRED = re.compile(
    r"TC_SMOKE_THAUMOMETER_SPARKLE_EXPIRED=YES id=(\\d+) age=(\\d+) lifetime=(\\d+)"
)
_SPARKLE_BLEND = re.compile(
    r"TC_SMOKE_THAUMOMETER_SPARKLE_ADDITIVE_BLEND_BEGIN=YES src=(\\d+) dst=(\\d+)"
)


def parse_thaumometer_sparkle_log(log: str) -> tuple[
    list[tuple[str, ...]], list[tuple[str, ...]], list[tuple[str, ...]], list[tuple[str, ...]]
]:
    """Return actual particle construction, render, expiry and additive-blend markers."""
    return (
        [match.groups() for match in _SPARKLE_CREATED.finditer(log)],
        [match.groups() for match in _SPARKLE_RENDERED.finditer(log)],
        [match.groups() for match in _SPARKLE_EXPIRED.finditer(log)],
        [match.groups() for match in _SPARKLE_BLEND.finditer(log)],
    )
