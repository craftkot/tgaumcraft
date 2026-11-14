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


def main():
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
