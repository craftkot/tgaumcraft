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


## Real-client failure analysis and first-frame cursor repair (2026-11-23)

- Exact-SHA Wisp run [#45246351434](https://github.com/craftkot/tgaumcraft/actions/runs/45246351434) passed the offline geometry/Python contract and generated-Bash `bash -n`, then failed in the real Minecraft client step. The log shows attempt 1 timed out; attempt 2 dispatched a click from a frame at GameTime 689 / PlayerTick 650, but the actual input marker arrived at GameTime 693 / PlayerTick 654 with `hitType=MISS` (four ticks later); attempt 3 timed out. No damage, target acquisition, payload or FXBolt acceptance occurred.
- Root-cause candidate for the *first-attempt timeout* is now concrete: `wisp_wait_for_fresh_render` consumes a first completed frame, but `wisp_attack_live_wisp` then takes a new `wc -l` snapshot. A valid next `EntityHitResult` can be appended in that hand-off interval and be skipped by the snapshot. This matches the lost first attempt; it does not explain the four-tick input lag on attempt 2, which remains a separate unresolved issue.
- Driver fix [5edbebc](https://github.com/craftkot/tgaumcraft/commit/5edbebc9f6e0ee5939e1c2cbb2dbd96044046680) captures the absolute line number of the exact first completed frame and starts the first attack watcher after that line, rather than after a later tail snapshot.
- Verifier fix [6c29cf0](https://github.com/craftkot/tgaumcraft/commit/6c29cf09704d6d9cb26e68fa3ceb5a3bc38da9a5) requires the cursor marker and proves that it points to the exact completed frame in `client.log`. Regression tests [ff056fb](https://github.com/craftkot/tgaumcraft/commit/ff056fb88774f19dced17744e80da8a0c4a5e670) cover missing/mismatched cursor values and prohibit a new `wc -l` snapshot in the first attempt.
- Local isolated Bash reproduction: **PASS**. With frame 1 consumed and frame 2 appended before the attack helper starts, the old `wc -l` cursor sees no frame; the exact frame-1 line cursor reads frame 2. This is a focused shell reproduction, not the repository Python suite.
- New exact-SHA run [#45247584938](https://github.com/craftkot/tgaumcraft/actions/runs/45247584938) was queued at the last poll; Full Port CI [#45247585089](https://github.com/craftkot/tgaumcraft/actions/runs/45247585089) and Aura/Crystal GameTests [#45247585017](https://github.com/craftkot/tgaumcraft/actions/runs/45247585017) were also queued.
- Previous exact-SHA Aura/Crystal GameTests [#45246351459](https://github.com/craftkot/tgaumcraft/actions/runs/45246351459) **SUCCESS**: both read/write phases reported 29/29 required tests; logs include a two-process cold restart and Wisp damage/payload/spawn checks. This is not full M08 game acceptance.
- No fixed-audit points awarded: **59/100**, M16 **4/6**, M08 **5/9**. The four-tick click-to-input delay remains unresolved; if the next client still misses, instrument the client input event and attack event timestamps rather than changing polling intervals again.


## CI state correction (latest poll, 2026-11-23)

- Full Port CI [#45246351646](https://github.com/craftkot/tgaumcraft/actions/runs/45246351646) has now completed **SUCCESS** on code SHA `badcf26`: Core parity **10,054 assertions**, BETA26 jar parity **51 assertions**, NeoForge `build` **SUCCESS**, dedicated server reached `Done (6.527s)`, and the JAR artifact uploaded. This is before the later test-driver cursor changes; production Java/resources were unchanged by those later commits.
- Aura/Crystal GameTests [#45246351459](https://github.com/craftkot/tgaumcraft/actions/runs/45246351459) completed **SUCCESS** with 29/29 required tests in both write and read phases, plus a two-process cold restart. No M08 score is awarded: the broader world/game acceptance matrix remains incomplete.
- The follow-up Wisp client [#45247584938](https://github.com/craftkot/tgaumcraft/actions/runs/45247584938) and follow-up Full Port CI [#45247585089](https://github.com/craftkot/tgaumcraft/actions/runs/45247585089) remain queued at the latest poll due the large number of parallel PR workflows. The exact cursor fix has not yet been exercised in a new real client.
- Branch comparison against fresh main `a4280834adc5a20b6951098af937d30ca4aeca56`: 1,767 commits ahead, 0 behind; PR #4 still contains 367 files. It remains open and unmerged; no merge/force-push was performed. This size is an integration risk, not evidence of readiness.
- Fixed audit remains **59/100** (M16 **4/6**, M08 **5/9**); no category closure or point increase.
