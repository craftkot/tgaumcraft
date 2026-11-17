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


def parse_scan_rune_log(log: str) -> tuple[list[tuple[str, ...]], list[tuple[str, ...]], list[tuple[str, ...]]]:
    """Return (created, rendered, expired) marker fields in emitted order."""
    return (
        [match.groups() for match in _CREATED.finditer(log)],
        [match.groups() for match in _RENDERED.finditer(log)],
        [match.groups() for match in _EXPIRED.finditer(log)],
    )

def visible_rune_render_samples(rendered: list[tuple[str, ...]]) -> list[tuple[int | float, ...]]:
    """Keep rendered runes with visible alpha and any positive source scale.

    BETA26 uses particleScale = 1 + gaussian * 0.1 and renders width
    0.3 * particleScale without a lower clamp. A valid rune can therefore
    be smaller than 0.25 blocks; only zero/negative size is invisible.
    """
    visible = []
    for row in rendered:
        if len(row) != 12:
            raise ValueError(f"Expected 12 scan-rune render fields, got {len(row)}")
        face = int(row[0])
        values = tuple(float(value) for value in row[1:])
        alpha, size = values[0], values[1]
        if alpha >= 0.25 and size > 0.0:
            visible.append((face, *values))
    return visible
