"""Fail-closed validation of real-client pedestal rotation markers."""

from math import isfinite


def validate_samples(samples, *, minimum_total_tick_span=10, tolerance=0.02):
    """Validate every render angle and the last adjacent tick/angle step.

    The first-to-last angular displacement is intentionally not bounded: a real
    client may spend many seconds in world setup before the screenshots are
    taken, so that displacement can legitimately exceed 120 degrees or wrap.
    The adjacent final pair proves the measured renderer step without mixing
    unrelated setup time into the screenshot interval.
    """
    if len(samples) < 2:
        raise ValueError(f"need at least two render markers, got {len(samples)}")

    normalized = []
    for index, sample in enumerate(samples):
        if len(sample) != 3:
            raise ValueError(f"sample {index} must contain tick, partialTick, angle")
        tick, partial, angle = sample
        if isinstance(tick, bool) or int(tick) != tick or tick < 0:
            raise ValueError(f"sample {index} has invalid camera tick {tick!r}")
        if not all(isfinite(float(value)) for value in (partial, angle)):
            raise ValueError(f"sample {index} contains a non-finite render value")
        if not 0.0 <= partial < 1.0:
            raise ValueError(f"sample {index} has invalid partial tick {partial}")
        if not 0.0 <= angle < 360.0:
            raise ValueError(f"sample {index} has invalid angle {angle}")

        expected = (tick + partial) % 360.0
        angle_error = abs((angle - expected + 180.0) % 360.0 - 180.0)
        if angle_error > tolerance:
            raise ValueError(f"sample {index} angle mismatch: {angle} != {expected}")
        if normalized and tick <= normalized[-1][0]:
            raise ValueError(f"camera ticks are not strictly increasing at sample {index}")
        normalized.append((int(tick), float(partial), float(angle)))

    tick_span = normalized[-1][0] - normalized[0][0]
    if tick_span < minimum_total_tick_span:
        raise ValueError(f"camera ticks advanced only {tick_span}")

    previous, latest = normalized[-2:]
    expected_delta = ((latest[0] + latest[1]) - (previous[0] + previous[1])) % 360.0
    actual_delta = (latest[2] - previous[2]) % 360.0
    if expected_delta <= 0.0:
        raise ValueError("last render marker has no measurable tick progression")
    delta_error = abs((actual_delta - expected_delta + 180.0) % 360.0 - 180.0)
    if delta_error > tolerance:
        raise ValueError(f"last render step mismatch: {actual_delta} != {expected_delta}")

    return {
        "first_tick": normalized[0][0],
        "last_tick": normalized[-1][0],
        "tick_span": tick_span,
        "last_step_degrees": actual_delta,
    }
