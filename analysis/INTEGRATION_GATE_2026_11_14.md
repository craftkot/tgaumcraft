# Integration gate — 2026-11-14

Target: `integration/verified-port-2026-11-14`. Base commit: `a4280834adc5a20b6951098af937d30ca4aeca56`.

## Baseline comparison (GitHub compare)
| Source branch | Ahead of target | Behind | Changed files |
|---|---:|---:|---:|
| feature/alchemy | 33 | 0 | 7 |
| feature/research | 39 | 0 | 35 |
| feature/infusion | 95 | 0 | 172 |
| feature/golems | 157 | 0 | 300 |
| feature/closure-near-complete | 827 | 0 | 186 |

All sources currently share the integration base; **this does not establish cross-branch compatibility**. PRs #5–#8 are drafts. Closure branch is not an approved merge candidate.

## Acceptance gates (must be checked on the COMBINED integration tree)
- [ ] Review file overlap and merge conflicts for each PR, including common block entities, registries, client UI and CI workflow.
- [ ] Java Core tests pass on exact integration HEAD.
- [ ] Full NeoForge build passes on exact integration HEAD.
- [ ] Native Minecraft GameTests pass on exact integration HEAD.
- [ ] Dedicated server starts without errors on exact integration HEAD.
- [ ] Real Minecraft client smoke passes on exact integration HEAD, including research GUI, infusion, golems, and essentia mirrors.
- [ ] Save/reload and cold READ regression passes on exact integration HEAD.
- [ ] No essentia/item duplication or negative-count regressions on exact integration HEAD.
- [ ] Record test commands, workflow URLs, commit SHA, and artifact/log evidence.
- [ ] Only then consider merging the next compatible PR; leave `main` untouched.

## 2026-11-14 execution
Compared all source branches to target using GitHub API. The GitHub commit workflow-run query for base commit returned no PR-triggered runs; this is **not** proof of passing or failing CI. No executable combined-tree test evidence was available in this run. No source PR was merged. Previous successful tests in individual lanes do not certify the combined integration tree.

## Next safe action
Obtain a runnable checkout / GitHub Actions integration workflow on the integration branch, test the narrow alchemy branch first, then research, infusion, and golems one by one. Do not interpret an empty check list as green.
