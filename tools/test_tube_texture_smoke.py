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
        "tube_send_chat '/setblock 9 149 12 thaumcraft:tube_valve' 'Changed the block at 9, 149, 12'",
        "tube_send_chat '/setblock 10 149 12 thaumcraft:tube' 'Changed the block at 10, 149, 12'",
        "tube_send_chat '/setblock 11 149 12 thaumcraft:tube_restrict' 'Changed the block at 11, 149, 12'",
        "tube_send_chat '/tp @p 9.5 149 8.5 0 16' 'Teleported Dev to 9.500000, 149.000000, 8.500000'",
        "tube_send_chat '/tp @p 11.5 149 8.5 0 16' 'Teleported Dev to 11.500000, 149.000000, 8.500000'",
        "xdotool mousemove --sync 513 404 click 1",
        "TUBE_VALVE_BREAK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "TUBE_RESTRICT_BREAK_INPUT=REAL_CLIENT_LEFT_CLICK",
        "TUBE_EVIDENCE_ROI=420x150+300+290",
        "tube_send_chat '/execute if block 9 149 12 minecraft:air run say TC_SMOKE_TUBE_VALVE_DESTROYED=YES' 'TC_SMOKE_TUBE_VALVE_DESTROYED=YES'",
        "tube_send_chat '/execute if block 11 149 12 minecraft:air run say TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES' 'TC_SMOKE_TUBE_RESTRICT_DESTROYED=YES'",
        "sleep 1",
        "--delay 24",
        "seq 1 80",
        "tube_core_valve",
        "tube_side_restrict",
        "TC_SMOKE_TUBE_VALVE_EAST_CONNECTED=YES",
        "TC_SMOKE_TUBE_MIDDLE_BOTH_SIDES_CONNECTED=YES",
        "TC_SMOKE_TUBE_RESTRICT_WEST_CONNECTED=YES",
        "TC_SMOKE_TUBE_VALVE_ONLY_DESTROYED=YES",
        "TC_SMOKE_TUBE_RESTRICT_ONLY_DESTROYED=YES",
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

    helper_match = re.search(r"(?ms)^  tube_send_chat\(\) \{\n.*?^  \}", generated)
    assert helper_match, "missing acknowledgement-gated server chat helper"
    first_tube_call = generated.index("  tube_send_chat '/gamerule")
    assert generated.index(helper_match.group(0)) < first_tube_call, (
        "tube_send_chat must be defined before the first setup command; Bash does not hoist functions"
    )
    assert generated.count("  tube_send_chat() {") == 1, "chat helper must be defined exactly once"
    assert "minecraft:air destroy" not in generated, "tube destruction must use real client input"
    assert "TUBE_EVIDENCE_ROI=420x150+300+290" in generated
    assert "tube_send_chat_quick" not in generated, "racy chat helper must not return"
    assert "tube_send_chat_fast" not in generated, "fast chat helper must not return"
    with tempfile.TemporaryDirectory(prefix="tc-tube-command-ack-") as directory:
        root = Path(directory)
        fake_bin = root / "bin"
        fake_bin.mkdir()
        xdotool = fake_bin / "xdotool"
        xdotool.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
        xdotool.chmod(0o755)
        seq = fake_bin / "seq"
        seq.write_text("#!/bin/sh\nprintf '1\\n'\n", encoding="utf-8")
        seq.chmod(0o755)
        (root / "client-smoke").mkdir()
        client_log = root / "client-smoke" / "client.log"
        helper_script = helper_match.group(0) + "\ntube_send_chat '/test' 'EXPECTED_MARKER=YES'\n"
        env = os.environ.copy()
        env["PATH"] = f"{fake_bin}:{env['PATH']}"
        client_log.write_text("EXPECTED_MARKER=YES\n", encoding="utf-8")
        passed = subprocess.run(
            ["bash", "-c", helper_script], cwd=root, text=True, capture_output=True, env=env
        )
        assert passed.returncode == 0, passed.stderr
        assert "TUBE_SERVER_COMMAND_CONFIRMED=EXPECTED_MARKER=YES" in passed.stdout
        client_log.write_text("OTHER=NO\n", encoding="utf-8")
        failed = subprocess.run(
            ["bash", "-c", helper_script], cwd=root, text=True, capture_output=True, env=env
        )
        assert failed.returncode != 0, "missing server acknowledgement must fail"

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
        assert verify_tube_texture_client_log.verify(
            log,
            status.replace(
                "TUBE_SERVER_COMMAND_CONFIRMED=Changed the block at 10, 149, 12\n", ""
            ),
            screenshots,
        ), "missing real setblock acknowledgement must fail"
        assert verify_tube_texture_client_log.verify(
            log.replace("TC_SMOKE_TUBE_VALVE_ONLY_DESTROYED=YES", ""),
            status,
            screenshots,
        ), "breaking the wrong tube must fail the exact-block verifier"

        assert verify_tube_texture_client_log.verify(
            log.replace("TC_SMOKE_TUBE_VALVE_DESTROYED=YES", ""),
            status,
            screenshots,
        ), "missing actual valve destruction marker must fail"

    assert r"\${metric" not in generated, "shell metric expansion must not be escaped"
    assert generated.count("awk 'NR == 1 {print $1}'") == 1

    def run_shell_diff(name: str, args: str, metric: str) -> subprocess.CompletedProcess[str]:
        helper = re.search(r"(?ms)^  tube_crop_metric\(\) \{\n.*?^  \}", generated)
        pattern = r"(?ms)^  " + re.escape(name) + r"\(\) \{\n.*?^  \}"
        match = re.search(pattern, generated)
        assert helper, "missing shared cropped-image metric helper"
        assert match, f"missing shell function {name}"
        with tempfile.TemporaryDirectory(prefix="tc-tube-diff-test-") as directory:
            fake_bin = Path(directory) / "bin"
            fake_bin.mkdir()
            compare = fake_bin / "compare"
            compare.write_text(
                '#!/bin/sh\nprintf "%s\\n" "$FAKE_COMPARE_METRIC" >&2\nexit 1\n',
                encoding="utf-8",
            )
            compare.chmod(0o755)
            convert = fake_bin / "convert"
            convert.write_text(
                '#!/bin/sh\nfor last do :; done\nprintf "%s\\n" "$*" >> "$CONVERT_ARGS_FILE"\n: > "$last"\n',
                encoding="utf-8",
            )
            convert.chmod(0o755)
            convert_args = Path(directory) / "convert-args.txt"
            env = os.environ.copy()
            env["PATH"] = f"{fake_bin}:{env['PATH']}"
            env["FAKE_COMPARE_METRIC"] = metric
            env["CONVERT_ARGS_FILE"] = str(convert_args)
            source = helper.group(0) + "\n" + match.group(0) + "\n" + name + " " + args
            result = subprocess.run(
                ["bash", "-c", source],
                text=True,
                capture_output=True,
                env=env,
                check=False,
            )
            assert convert_args.is_file(), "cropped images were not generated"
            assert "-crop 420x150+300+290" in convert_args.read_text(encoding="utf-8"), (
                "pixel metric must ignore the moving sky and compare only the tube ROI"
            )
            return result

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
