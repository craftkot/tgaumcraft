#!/usr/bin/env python3
"""Offline controls for the shared page/scan PCM capture-health gate."""
from array import array
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import subprocess
import sys
import tempfile
from check_page_audio_pcm import check


def pcm(path: Path, seconds: int, amplitude: int):
    # Alternating signed samples have deterministic nonzero RMS and peak.
    samples = array("h", [amplitude, -amplitude]) * (48000 * seconds)
    path.write_bytes(samples.tobytes())


def rejected(baseline: Path, event: Path, expected: str):
    try:
        with redirect_stdout(StringIO()):
            check(baseline, event)
    except (AssertionError, ValueError) as exc:
        assert expected in str(exc), (expected, str(exc))
    else:
        raise AssertionError(f"Expected rejection containing {expected!r}")


def validate_scan_sound_driver():
    generated = subprocess.run(
        [sys.executable, "tools/client_scan_sound_smoke.py"],
        check=True, capture_output=True, text=True,
    ).stdout
    driver_start = generated.index("  focus_scan_window() {")
    driver_end = generated.index("  # Prove the command path", driver_start)
    driver = generated[driver_start:driver_end]
    required_driver = (
        "focus_scan_window() {",
        "xdotool windowactivate --sync",
        "xdotool windowfocus --sync",
        "xdotool getwindowfocus",
        'if [[ "$raw_command" != /* ]]',
        'local command_body="${raw_command#/}"',
        "xdotool key --clearmodifiers slash",
        "80a-scan-chat-open.png",
        "80b-scan-chat-text-before-enter.png",
        "80c-scan-chat-submit-result.png",
    )
    missing_driver = [fragment for fragment in required_driver if fragment not in driver]
    assert not missing_driver, f"generated scan sound command driver is missing: {missing_driver}"
    assert "xdotool key --clearmodifiers t" not in driver
    assert driver.index("xdotool key --clearmodifiers slash") < driver.index("80a-scan-chat-open.png")
    assert driver.index("80a-scan-chat-open.png") < driver.index('xdotool type --clearmodifiers --delay 24 "$command_body"')
    assert driver.index('xdotool type --clearmodifiers --delay 24 "$command_body"') < driver.index("80b-scan-chat-text-before-enter.png")
    assert driver.index("80b-scan-chat-text-before-enter.png") < driver.index("xdotool key --clearmodifiers Return")
    assert driver.index("xdotool key --clearmodifiers Return") < driver.index("80c-scan-chat-submit-result.png")

    marker_start = generated.index("  wait_for_scan_marker() {")
    marker_end = generated.index("  # Prove the command path", marker_start)
    marker_gate = generated[marker_start:marker_end]
    assert 'grep -Fq "$marker" run/logs/latest.log' in marker_gate
    assert "client-smoke/client.log" not in marker_gate

    required_calls = (
        "send_scan_chat '/say TC_SCAN_SOUND_COMMAND_CHANNEL_READY'",
        "wait_for_scan_marker 'TC_SCAN_SOUND_COMMAND_CHANNEL_READY'",
        "send_scan_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'",
        "send_scan_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say SCAN_SOUND_ITEM_READY'",
        "wait_for_scan_marker 'SCAN_SOUND_ITEM_READY'",
        "90a-scan-sound-item-ready.png",
    )
    missing_calls = [fragment for fragment in required_calls if fragment not in generated]
    assert not missing_calls, f"scan sound command sequence is incomplete: {missing_calls}"
    assert generated.index(required_calls[0]) < generated.index(required_calls[1]) < generated.index(required_calls[2])
    assert generated.index(required_calls[2]) < generated.index(required_calls[3]) < generated.index(required_calls[4]) < generated.index(required_calls[5])
    print("SCAN_SOUND_CLIENT_COMMAND_DRIVER=PASS (slash entry, screenshots, authoritative server log)")


def main():
    validate_scan_sound_driver()
    with tempfile.TemporaryDirectory() as tmp:
        baseline, event = Path(tmp) / "baseline.pcm", Path(tmp) / "event.pcm"
        pcm(baseline, 2, 8000)
        pcm(event, 5, 1200)
        # A louder baseline (ambient Minecraft sounds) is not an invalid
        # capture: original-OGG waveform matching decides event identity.
        with redirect_stdout(StringIO()) as output:
            check(baseline, event)
        assert "PAGE_PCM_CAPTURE=PASS" in output.getvalue()
        pcm(event, 5, 0)
        rejected(baseline, event, "effectively silent")
        pcm(event, 1, 1200)
        rejected(baseline, event, "incomplete")
        event.write_bytes(b"\x01")
        rejected(baseline, event, "incomplete signed-16 sample")
    print("PCM capture-health controls: PASS (4 cases)")


if __name__ == "__main__":
    main()
