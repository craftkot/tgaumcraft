#!/usr/bin/env python3
"""Offline regression tests for the isolated real-client tube smoke."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, str(Path("tools").resolve()))
import client_tube_texture_smoke
import verify_tube_texture_client_log


def main() -> int:
    generated = client_tube_texture_smoke.render()
    required_shell = (
        "/forceload add 0 0",
        "/fill 8 148 8 12 148 14 minecraft:stone",
        "TC_SMOKE_TUBE_PLATFORM_READY=YES",
        "TC_SMOKE_TUBE_PLAYER_ON_PLATFORM=YES",
        "/tp @p 10.5 149 8.5 0 18",
        "tube_send_chat_quick '/setblock 9 149 12 thaumcraft:tube_valve'",
        "tube_send_chat_quick '/setblock 10 149 12 thaumcraft:tube'",
        "tube_send_chat_quick '/setblock 11 149 12 thaumcraft:tube_restrict'",
        "tube_core_valve",
        "tube_side_restrict",
        "TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES",
        "TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES",
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

    assert r"\${metric" not in generated, "shell metric expansion must not be escaped"
    assert generated.count("awk 'NR == 1 {print $1}'") == 2

    def run_shell_diff(name: str, args: str, metric: str) -> subprocess.CompletedProcess[str]:
        pattern = r"(?ms)^  " + re.escape(name) + r"\(\) \{\n.*?^  \}"
        match = re.search(pattern, generated)
        assert match, f"missing shell function {name}"
        with tempfile.TemporaryDirectory(prefix="tc-tube-diff-test-") as directory:
            fake_bin = Path(directory) / "bin"
            fake_bin.mkdir()
            compare = fake_bin / "compare"
            compare.write_text(
                "#!/bin/sh\nprintf '%s\\n' \"$FAKE_COMPARE_METRIC\" >&2\nexit 1\n",
                encoding="utf-8",
            )
            compare.chmod(0o755)
            env = os.environ.copy()
            env["PATH"] = f"{fake_bin}:{env['PATH']}"
            env["FAKE_COMPARE_METRIC"] = metric
            return subprocess.run(
                ["bash", "-c", match.group(0) + "\n" + name + " " + args],
                text=True,
                capture_output=True,
                env=env,
                check=False,
            )

    pixel_pass = run_shell_diff("tube_pixel_diff", "connected left right", "300 (0.1)")
    assert pixel_pass.returncode == 0, pixel_pass.stderr
    assert "TUBE_BLOCK_RENDER_DIFF=300" in pixel_pass.stdout
    pixel_fail = run_shell_diff("tube_pixel_diff", "connected left right", "299 (0.1)")
    assert pixel_fail.returncode != 0, "299 pixels must fail the 300-pixel threshold"

    particle_pass = run_shell_diff(
        "tube_particle_diff", "VALVE late early-a early-b early-c", "20 (0.1)"
    )
    assert particle_pass.returncode == 0, particle_pass.stderr
    assert "TUBE_VALVE_PARTICLE_DIFF=20" in particle_pass.stdout
    particle_fail = run_shell_diff(
        "tube_particle_diff", "VALVE late early-a early-b early-c", "19 (0.1)"
    )
    assert particle_fail.returncode != 0, "19 pixels must fail the 20-pixel threshold"

    print("TUBE_CLIENT_SMOKE_GENERATOR_AND_EVIDENCE_TESTS_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
