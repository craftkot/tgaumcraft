"""Separate actual Nitor render samples from its per-tick BETA26 atlas sequence."""
import re

FRAME_START = 264
FRAME_COUNT = 8
FRAME_SET = set(range(FRAME_START, FRAME_START + FRAME_COUNT))


def assert_nitor_frame_coverage(log: str) -> tuple[tuple[int, ...], tuple[int, ...]]:
    """Require a real first render plus one full eight-age cycle in that particle."""
    rendered_matches = re.findall(
        r"TC_SMOKE_NITOR_FLAME_FRAME=YES frame=(\d+)", log
    )
    rendered_frames = tuple(sorted({int(frame) for frame in rendered_matches}))
    if len(rendered_frames) != len(rendered_matches):
        raise AssertionError("Nitor rendered-frame markers contain duplicates")
    if len(rendered_frames) < 2 or 265 not in rendered_frames:
        raise AssertionError(
            f"Nitor real renderer sampled too few flame frames: {rendered_frames}"
        )
    if not set(rendered_frames).issubset(FRAME_SET):
        raise AssertionError(
            f"Nitor renderer sampled an out-of-atlas frame: {rendered_frames}"
        )

    tick_matches = re.findall(
        r"TC_SMOKE_NITOR_FLAME_TICK_FRAME=YES id=1 age=(\d+) frame=(\d+)", log
    )
    if len(tick_matches) != FRAME_COUNT:
        raise AssertionError(
            "Nitor first rendered flame did not visit all eight BETA26 frames "
            f"during client ticks: markers={len(tick_matches)}"
        )
    ages = [int(age) for age, _ in tick_matches]
    frames = [int(frame) for _, frame in tick_matches]
    if ages != list(range(ages[0], ages[0] + FRAME_COUNT)):
        raise AssertionError(f"Nitor tick-frame ages are not consecutive: {ages}")
    expected = [FRAME_START + age % FRAME_COUNT for age in ages]
    if frames != expected:
        raise AssertionError(
            f"Nitor tick-frame mapping differs from FXGeneric age modulo 8: "
            f"ages={ages} frames={frames} expected={expected}"
        )
    if set(frames) != FRAME_SET:
        raise AssertionError(
            f"Nitor tick lifecycle missed BETA26 atlas frames: {sorted(set(frames))}"
        )
    return rendered_frames, tuple(sorted(set(frames)))
