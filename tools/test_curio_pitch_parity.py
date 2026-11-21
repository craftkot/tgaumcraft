#!/usr/bin/env python3
"""Deterministic controls for the BETA26/NeoForge Curio pitch comparison."""
from check_curio_pitch_parity import check_text

def log(pitch):
    return f"TC_SMOKE_CURIO_LEARN_REQUESTED_PITCH={pitch}\n"
def matched(pitch):
    return f"CURIO_LEARN_SOURCE_MATCH=PASS correlation=0.8567 pitch={pitch}\n"

for requested in (0.33334, 0.4, 0.475, 0.49999):
    assert check_text(log(requested), matched(0.5))[1] == 0.5
for bad_log, bad_match in (
    (log(0.4), matched(0.4)),
    (log(0.4), matched(0.53)),
    (log(0.7), matched(0.5)),
    ("", matched(0.5)),
    (log(0.4) * 2, matched(0.5)),
    (log(0.4), ""),
):
    try:
        check_text(bad_log, bad_match)
    except AssertionError:
        pass
    else:
        raise AssertionError("Invalid pitch evidence accepted")
print("CURIO_LEARN_PITCH_PARITY_CONTROLS=PASS 4 positive / 6 negative")
