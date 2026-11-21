#!/usr/bin/env python3
"""Verify OpenAL Soft selected PulseAudio and connected to the intended sink."""
from __future__ import annotations

import argparse
from pathlib import Path


def check(log_text: str, sink_name: str = "tc_curio_sink") -> str:
    required = (
        '[ALSOFT] (II) Initialized backend "pulse"',
        '[ALSOFT] (II) Added "pulse" for playback',
        f'[ALSOFT] (II) Connecting to "{sink_name}"',
    )
    missing = [marker for marker in required if marker not in log_text]
    if missing:
        raise AssertionError(
            "OpenAL PulseAudio route is not proven; missing log markers: "
            + ", ".join(missing)
        )
    # Do not reject Mojang's generic device label "Null Output": when the
    # PulseAudio module-null-sink is named tc_curio_sink, OpenAL Soft logs
    # that sink's description as "Null Output" even though the active backend
    # is PulseAudio and it is connected to the requested sink.
    marker = f"CURIO_LEARN_OPENAL_PULSE_ROUTE=PASS backend=pulse sink={sink_name}"
    print(marker)
    return marker


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("sink", nargs="?", default="tc_curio_sink")
    args = parser.parse_args()
    check(args.log.read_text(encoding="utf-8", errors="replace"), args.sink)
