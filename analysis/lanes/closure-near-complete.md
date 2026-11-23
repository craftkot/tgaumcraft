# Closure near-complete lane

- Repository: `craftkot/tgaumcraft`
- Owned branch: `feature/closure-near-complete`
- Pull request: [#4](https://github.com/craftkot/tgaumcraft/pull/4), OPEN / DRAFT / UNMERGED; do not merge without explicit owner approval.
- Fresh main HEAD checked: `a4280834adc5a20b6951098af937d30ca4aeca56`.
- Current branch HEAD before this iteration: `8004f5c3cf3769195bc1ef5cdbabd0db0f341c93`.
- Active narrow task: M16 real-client Wisp zap acceptance. M04 remains owned by `feature/alchemy`; do not edit its paths. M06/M07 remain parallel lanes; do not edit their paths.
- Main roadmap marks M02/M03 complete and keeps the fixed audit at **59/100**. Historical category values are not automatically increased by code, Core parity, or a single successful client marker.

## Current iteration — post-hit Wisp camera capture

- Exact run [#45247584938](https://github.com/craftkot/tgaumcraft/actions/runs/45247584938) on `ff056fb`: offline contract and Bash syntax PASS; the real first Survival hit, accepted damage and target acquisition PASS. The run failed after combat because the Wisp moved after the first camera re-aim; the post-hit camera sample had `aimDot=0.976984` (<0.99), so capture timed out and the visual verifier was skipped.
- Fix: bounded three-attempt re-aim/capture wrapper; retain strict `aimDot >= 0.99`, same-entity ID/tick checks and pixel-based screenshot verification. This changes only the smoke driver/test, not production Wisp behavior.
- Added subprocess coverage for success on retry 2 and fail-closed after 3 attempts. Details and raw evidence are in [the supplemental Wisp journal](closure-near-complete-wisp-2026-11-23.md).

## Fixed audit

- **59/100** unchanged.
- M16 **4/6**; remaining: complete Wisp visual FX evidence (three real frames, expiry, pixel delta), plus broader asset/UI/audio parity and final full-category audit.
- M08 **5/9**; aura/world acceptance remains incomplete. The 29/29 scoped GameTests do not close M08.

## Next

1. Run `python tools/test_wisp_zap_client_smoke.py` and generated `bash -n` on the exact new SHA.
2. Run the exact-SHA Wisp real Minecraft client; require same-ID hit, accepted server damage, target set, matching payload IDs, three actual FXBolt frames, expiry, pixel delta above baseline noise, and no essentia texture errors.
3. If three re-aim attempts still fail, instrument the server command response and camera yaw/pitch rather than weakening the 0.99 or pixel thresholds.
4. Keep PR #4 unmerged; its broad 367-file diff and 1768-commit ancestry require later small integration candidates and path-by-path reconciliation.
