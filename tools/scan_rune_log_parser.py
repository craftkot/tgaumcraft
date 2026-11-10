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
    r"worldZ=(" + _NUMBER + r") frontDot=(" + _NUMBER + r")"
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
