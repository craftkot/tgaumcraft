#!/usr/bin/env python3
"""Offline regression tests for the isolated real-client tube smoke."""
from pathlib import Path
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path("tools").resolve()))
import client_tube_texture_smoke
import verify_tube_texture_client_log


def main() -> int:
    generated = client_tube_texture_smoke.render()
    required_shell = (
        "thaumcraft:tube_valve",
        "thaumcraft:tube_restrict",
        "tube_core_valve",
        "tube_side_restrict",
        "TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES",
        "TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES",
        "minecraft:air destroy",
        "TUBE_NO_MISSING_MODEL_TEXTURES=PASS",
    )
    for marker in required_shell:
        assert marker in generated, marker

    syntax = subprocess.run(
        ["bash", "-n"],
        input=generated,
        text=True,
        capture_output=True,
        check=False,
    )
    assert syntax.returncode == 0, syntax.stderr

    with tempfile.TemporaryDirectory(prefix="tc-tube-client-test-") as directory:
        root = Path(directory)
        screenshots = root / "screenshots"
        screenshots.mkdir()
        for name in verify_tube_texture_client_log.SCREENSHOTS:
            (screenshots / name).write_bytes(b"\x89PNG\r\n\x1a\n" + b"x" * 256)

        log = "\n".join(verify_tube_texture_client_log.REQUIRED_LOG_MARKERS)
        status = "\n".join((
            "TUBE_BLOCK_RENDER_DIFF=1200",
            "TUBE_VALVE_PARTICLE_DIFF=85",
            "TUBE_RESTRICT_PARTICLE_DIFF=67",
            *verify_tube_texture_client_log.REQUIRED_STATUS_MARKERS,
        ))
        assert verify_tube_texture_client_log.verify(log, status, screenshots) == []

        assert verify_tube_texture_client_log.verify(
            log.replace("TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES", ""),
            status,
            screenshots,
        ), "missing server-side connection marker must fail"

        assert verify_tube_texture_client_log.verify(
            log + "\nMissing textures in model thaumcraft:block/tube_core_valve: [base_metal]",
            status,
            screenshots,
        ), "missing base_metal model sprite must fail"

        assert verify_tube_texture_client_log.verify(
            log,
            status.replace("TUBE_VALVE_PARTICLE_DIFF=85", "TUBE_VALVE_PARTICLE_DIFF=0"),
            screenshots,
        ), "empty transient-particle delta must fail"

    print("TUBE_CLIENT_SMOKE_GENERATOR_AND_EVIDENCE_TESTS_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
