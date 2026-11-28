#!/usr/bin/env python3
"""Verify that Minecraft has an active PulseAudio stream routed to the test sink."""
from __future__ import annotations

import argparse
from pathlib import Path


def sink_id_for_name(sinks: str, sink_name: str) -> str:
    matches = []
    for line in sinks.splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[1] == sink_name:
            matches.append(fields[0])
    if len(matches) != 1:
        raise AssertionError(
            f"Expected exactly one PulseAudio sink named {sink_name!r}; found {len(matches)}"
        )
    return matches[0]


def check(sinks: str, sink_inputs: str, sink_name: str = "tc_curio_sink") -> str:
    sink_id = sink_id_for_name(sinks, sink_name)
    active = []
    for line in sink_inputs.splitlines():
        fields = line.split()
        # pactl list short sink-inputs: index, sink-index, driver, ...
        if len(fields) >= 2 and fields[1] == sink_id:
            active.append(fields[0])
    if not active:
        raise AssertionError(
            f"No active PulseAudio sink-input is routed to {sink_name} (id={sink_id})"
        )
    marker = (
        f"CURIO_LEARN_PULSE_SINK_INPUT=PASS sink={sink_name} "
        f"sink_id={sink_id} inputs={','.join(active)}"
    )
    print(marker)
    return marker


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sinks", type=Path)
    parser.add_argument("sink_inputs", type=Path)
    args = parser.parse_args()
    check(args.sinks.read_text(encoding="utf-8"),
          args.sink_inputs.read_text(encoding="utf-8"))
