# Closure lane supplemental journal — 2026-11-23 Wisp hit latency

## Evidence from exact-head client run

- Main HEAD: `a4280834adc5a20b6951098af937d30ca4aeca56`.
- Pre-fix code/test SHA: `878ca12ad547a4001bd061262eddaa3cad135349`.
- Real-client run: [Wisp zap #45232354922](https://github.com/craftkot/tgaumcraft/actions/runs/45232354922), artifact [14848133835](https://github.com/craftkot/tgaumcraft/actions/runs/45232354922/artifacts/14848133835).
- Offline contract, 21 geometry assertions and 24 atlas assertions passed; Minecraft 1.21.1 launched.
- The client logged a same-ID Wisp `EntityHitResult` at GameTime 724 / PlayerTick 683, but the subsequent `TC_SMOKE_WISP_ATTACK_INPUT` arrived at GameTime 730 / PlayerTick 689 with `targetWisp=false`. The real hit was therefore not accepted; no damage, target-set, payload or FXBolt acceptance is claimed.
- This proves a six-tick frame-to-input gap, not its root cause. The watcher used GNU `tail -f` without an explicit fallback polling interval; if inotify is unavailable on the runner filesystem, GNU tail's default polling can be too coarse.

## Current experiment

- Set `tail --sleep-interval=0.01` on the live Wisp attack-frame stream, before the line-number cursor and follow flags.
- Updated the fake-`xdotool` subprocess regression to use the same command and added a static guard that the generated watcher retains the 10 ms fallback interval.
- No Java, NeoForge runtime, Wisp AI, damage, network, registry, Gradle or shared workflow code changed.
- This is a test-driver latency experiment, not a production Wisp behavior change. It must be validated by a new real-client run; if the six-tick gap persists, instrument X11 dispatch and client/server input timestamps rather than claiming the polling interval was causal.

## Other observed CI

- Full Port CI on the surrounding code tree passed; this test-driver change still requires exact-SHA checks.
- Thaumometer scan-sound run [#45232355498](https://github.com/craftkot/tgaumcraft/actions/runs/45232355498) failed during Gradle configuration because Maven Central returned HTTP 429 for Gson 2.10.1. This is an external rate-limit failure, not a code regression; retry only after backoff.

## Acceptance and score

- No audit points awarded. Fixed audit remains **59/100**, M16 **4/6**, M08 **5/9**.
- Next acceptance: offline contract + Bash syntax, then same-ID click-to-server-hit delta <=1 tick, accepted damage, `TARGET_SET`, matching payload IDs, three real FXBolt frames, expiry, visible pixel delta and no essentia texture errors.
- PR #4 remains OPEN/DRAFT/UNMERGED. Its current size (1756 commits, 366 files) is not safe to merge as a single unit; no merge or force-push.

## Verification after the polling-interval patch

- Driver commit: `9e351dadd3e36bca742e474058605ffdf985c38b`.
- Regression commit: `6d8222c9729add25bab3d8879c93f0e025ea6b19`.
- Supplemental journal initial commit: `189b7bf2f6b5a0f87d759b2d9e35090008b04b66`.
- Local isolated Bash reproduction using the exact GNU `tail --sleep-interval=0.01` watcher passed: stale matching frame ignored, fresh matching frame clicked exactly once, measured append-to-clicker completion **10 ms**. This verifies the shell primitive in the local container, not the full Python contract or Minecraft.
- Exact new-SHA GitHub Actions runs were not returned by the PR-only workflow lookup. GitHub combined status for `9e351da` and `6d8222c` contains only CircleCI `verify-connection=success`; that status does not execute the offline Python regression, NeoForge build or real client. Local Git checkout is blocked because the container cannot resolve github.com.
- Therefore the latency hypothesis remains unproven in Minecraft. Next required evidence is a real-client run on the exact new code SHA; compare same-ID EntityHitResult GameTime/PlayerTick against the accepted server attack input. No audit points awarded.


## 2026-11-23 — repair stale offline watcher assertions

- Fresh main documents were reread: `AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and `analysis/PROGRESS_AUDIT_2026_10_16.md`. Main HEAD remains `a4280834adc5a20b6951098af937d30ca4aeca56`; the fixed audit remains **59/100** (M16 **4/6**, M08 **5/9**).
- The current `tools/client_wisp_zap_smoke.py` emits `tail --sleep-interval=0.01 -n +... -f`, but two older assertions in `tools/test_wisp_zap_client_smoke.py` still required the pre-experiment command without `--sleep-interval=0.01`. This made the offline contract internally contradictory: the same test also required the new interval later in the file.
- Fixed exactly those two stale assertions; no production Java, renderer, workflow, Gradle or resource changes. Code/test commit: [badcf26](https://github.com/craftkot/tgaumcraft/commit/badcf26adee35e5ef4465d79962b62d9e101cdb3).
- The exact Wisp client workflow is push-triggered on this feature branch; the GitHub connector's `fetch_commit_workflow_runs` exposes PR-triggered runs only and returned no runs for the preceding push commits. Combined status on the new commit must be checked separately. Container Git access is still blocked by DNS (`github.com` cannot resolve), so the full Python contract has not been run locally. No PASS is claimed.
- Before this edit, changed-path overlap was checked against all other open PRs returned by GitHub (#1–#3 and #5–#62); none touched the Wisp driver, verifier, test, or smoke probe paths. PR #4 is the owning lane.
- Next: inspect the new commit status and, if the Actions run remains inaccessible through the connector, obtain its exact run/artifact through a supported GitHub Actions listing path before changing the watcher again. Do not infer Minecraft success from the static contract fix.


## Exact-SHA verification after stale assertion repair (2026-11-23)

- Code/test SHA: [badcf26](https://github.com/craftkot/tgaumcraft/commit/badcf26adee35e5ef4465d79962b62d9e101cdb3).
- Wisp client workflow [#45246351434](https://github.com/craftkot/tgaumcraft/actions/runs/45246351434): the **offline geometry + Python client-smoke contract passed**, and generated Bash plus `bash -n` passed. At the latest poll, the real Minecraft client launch step was still **in progress**; no Wisp hit, payload, FXBolt or screenshot acceptance is claimed yet.
- Aura/Crystal native GameTests [#45246351459](https://github.com/craftkot/tgaumcraft/actions/runs/45246351459) completed **SUCCESS**. This confirms that test lane only; it does not close M08.
- Full Port CI [#45246351646](https://github.com/craftkot/tgaumcraft/actions/runs/45246351646) was still queued. The other M16 client checks were mixed queued/in-progress at the last poll.
- Combined status on the code SHA reports CircleCI `verify-connection=success`. No Full Port or real-client success is inferred from that status.
- The corrected test assertions are therefore accepted by the exact-SHA offline contract. The 10 ms tail-polling hypothesis remains unproven until the current real Minecraft client step and verifier finish.
- No fixed-audit points awarded: **59/100**, M16 **4/6**, M08 **5/9**. PR #4 remains open/draft/unmerged.
