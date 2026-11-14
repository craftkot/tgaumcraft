#!/usr/bin/env python3
"""Positive/negative contract tests for the real Curio PulseAudio capture route."""
from verify_curio_pulse_sink import check

sinks = "0 tc_curio_sink module-null-sink.c s16le 2ch 48000Hz IDLE\n"
positive = "41 0 protocol-native.c s16le 2ch 48000Hz\n"
marker = check(sinks, positive)
assert "sink_id=0" in marker and "inputs=41" in marker

for bad_sinks, bad_inputs in [
    (sinks, "42 1 protocol-native.c s16le 2ch 48000Hz\n"),
    ("1 unrelated module-null-sink.c s16le 2ch 48000Hz IDLE\n", positive),
    (sinks, ""),
]:
    try:
        check(bad_sinks, bad_inputs)
    except AssertionError:
        pass
    else:
        raise AssertionError("Invalid PulseAudio routing was accepted")

print("CURIO_LEARN_PULSE_SINK_CONTRACT=PASS (1 positive, 3 negative)")
