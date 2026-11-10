#!/usr/bin/env python3
"""Regression tests for OpenAL Pulse backend attribution."""
from verify_curio_openal_backend import check

# This reproduces the real 2026-11-10 client log: "Null Output" is the
# PulseAudio null-sink description, not the selected OpenAL backend.
real_route = """
[ALSOFT] (II) Supported backends: pipewire, pulse, alsa, oss, jack, port, null, wave
[ALSOFT] (II) Initialized backend "pulse"
[ALSOFT] (II) Added "pulse" for playback
[ALSOFT] (II) Added "pulse" for capture
[ALSOFT] (II) Connecting to "tc_curio_sink"
[ALSOFT] (II) Created device 0x1234, "Null Output"
[Render thread/INFO] OpenAL initialized on device Null Output
"""
marker = check(real_route)
assert marker.endswith("sink=tc_curio_sink")

for bad_log, bad_sink in [
    ('[Render thread/INFO] OpenAL initialized on device Null Output', 'tc_curio_sink'),
    ('[ALSOFT] (II) Initialized backend "null"\n[ALSOFT] (II) Connecting to "tc_curio_sink"', 'tc_curio_sink'),
    (real_route.replace('Connecting to "tc_curio_sink"', 'Connecting to "other_sink"'), 'tc_curio_sink'),
    (real_route.replace('Added "pulse" for playback', 'Added "pulse" for capture'), 'tc_curio_sink'),
]:
    try:
        check(bad_log, bad_sink)
    except AssertionError:
        pass
    else:
        raise AssertionError("Invalid OpenAL backend log was accepted")

print("CURIO_OPENAL_BACKEND_CONTRACT=PASS (1 real-style positive, 4 negative)")
