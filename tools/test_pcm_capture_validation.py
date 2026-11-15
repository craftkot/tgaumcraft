#!/usr/bin/env python3
"""Offline controls for the shared page/scan PCM capture-health gate."""
from array import array
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
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
    source = Path("tools/client_scan_sound_smoke.py").read_text(encoding="utf-8")
    required = (
        "focus_scan_window() {",
        "xdotool windowactivate --sync",
        "xdotool windowfocus --sync",
        "xdotool getwindowfocus",
        "send_scan_chat() {",
        "send_scan_chat '/say TC_SCAN_SOUND_COMMAND_CHANNEL_READY'",
        "send_scan_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'",
        "send_scan_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say SCAN_SOUND_ITEM_READY'",
        "wait_for_scan_marker 'SCAN_SOUND_ITEM_READY'",
        "90a-scan-sound-item-ready.png",
    )
    missing = [fragment for fragment in required if fragment not in source]
    assert not missing, f"scan sound command driver is missing: {missing}"
    assert "send_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'" not in source
    assert source.index("send_scan_chat '/say TC_SCAN_SOUND_COMMAND_CHANNEL_READY'") < source.index(
        "send_scan_chat '/item replace entity @p hotbar.0 with thaumcraft:thaumometer 1'"
    ) < source.index("send_scan_chat '/execute if items entity @p hotbar.0 thaumcraft:thaumometer run say SCAN_SOUND_ITEM_READY'")
    print("SCAN_SOUND_CLIENT_COMMAND_DRIVER=PASS (focus, command acknowledgement, item predicate)")


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
