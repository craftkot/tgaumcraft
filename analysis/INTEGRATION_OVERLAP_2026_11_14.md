# Cross-lane integration overlap audit — 2026-11-14

Target: `integration/verified-port-2026-11-14`. Source comparisons were taken against `main` through GitHub compare API. This is a **path overlap audit**, not a semantic conflict or test pass.

| Pair | Common changed paths |
|---|---:|
| alchemy / research | 0 |
| alchemy / infusion | 2 |
| alchemy / golems | 0 |
| research / infusion | 0 |
| research / golems | 0 |
| infusion / golems | 3 |

## Exact overlapping paths

**Alchemy / Infusion**
- `src/neoforge/java/dev/ehtid/thaumcraft/blockentity/EssentiaMirrorBlockEntity.java`
- `tools/client_smoke.sh`

**Infusion / Golems**
- `src/main/resources/assets/thaumcraft/lang/en_us.json`
- `src/main/resources/assets/thaumcraft/lang/ru_ru.json`
- `src/neoforge/java/dev/ehtid/thaumcraft/ThaumcraftPort.java`

## Findings and execution gates

1. Alchemy and research change disjoint paths relative to `main`; this does **not** establish behavioral compatibility, because they can share APIs and runtime assumptions.
2. Do not resolve alchemy/infusion mirror overlap with an automatic “ours” or “theirs” choice. Inspect transfer direction, transactional debit, chunk-loaded checks, mirror teardown and client smoke semantics together.
3. Do not discard either infusion or golems registrations in `ThaumcraftPort.java`; preserve both initialization paths. Merge EN/RU localization keys semantically and reject duplicate or missing keys.
4. Target currently has an integration-gate documentation commit beyond the common source base, so compare status is `diverged` (behind target: 1) even though source code is not merged.
5. Existing `.github/workflows/ci.yml` already triggers on push and pull_request. An empty PR-only workflow-run lookup is **not** a CI pass. Check actual Actions runs and artifacts.
6. Before merging each candidate, require Core Java parity, NeoForge build, native GameTests, dedicated server, real client smoke, cold save/reload, and no item/essentia duplication on the **exact combined commit**. Preserve evidence URLs and SHA.
7. No source branch or `main` was changed by this audit; no gameplay PR was merged.

Suggested review order: research or alchemy (narrow path set), then infusion with explicit mirror reconciliation, then golems with registration/localization reconciliation. Do not treat file disjointness as sufficient for merge.
