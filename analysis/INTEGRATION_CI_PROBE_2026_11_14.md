# Integration execution gate — 2026-11-14 (CI probe)

This document is an intentional **documentation-only push** to the integration branch to request the repository's existing `.github/workflows/ci.yml` push workflow. It does not modify any gameplay implementation and must not be treated as an acceptance of PR #5–#8.

## Verified workflow coverage (source inspection)

| Gate | Existing `Thaumcraft Port CI` job | Acceptance |
|---|---|---|
| Java Core parity | `Core parity test` (javac/java) | Await actual run on this exact HEAD |
| NeoForge full build | `gradle build -x test --stacktrace --no-daemon` | Await actual run on this exact HEAD |
| Dedicated server | `gradle runServer` with ready-state check | Await actual run on this exact HEAD |
| Native GameTests | **Not in existing CI workflow** | BLOCKED pending separate real Minecraft test run |
| Real client smoke | **Not in existing CI workflow** | BLOCKED pending client test evidence |
| Cold save/reload | **Not in existing CI workflow** | BLOCKED pending cold READ/WRITE evidence |
| Anti-duplication | **Not in existing CI workflow** | BLOCKED pending cross-system transactional test |

The CI workflow uses a dedicated server and does not constitute a graphical client run. The standard `gradle build -x test` is project-specific and is not proof of JUnit execution. Individual lane results cannot certify a combined integration tree.

## Source lane status and isolation

- Draft PR #5: `feature/research` — browser and research UI; client GUI acceptance outstanding.
- Draft PR #6: `feature/infusion` — independent native tests exist, combined acceptance outstanding.
- Draft PR #7: `feature/golems` — independent native tests exist, combined acceptance outstanding.
- Draft PR #8: `feature/alchemy` — mirror direction/relink and duplication acceptance outstanding.
- Known overlap: alchemy/infusion mirror entity + client smoke script; infusion/golems mod bootstrap + EN/RU locales.

## Merge policy

Do not merge any gameplay PR into this integration branch until exact-HEAD Core, build, dedicated server, native GameTests, real client, cold reload and no-duplication evidence is available. Do not merge into `main`. After this push, query actual GitHub Actions runs and attach their URLs and conclusions to a follow-up report; do not mark any gate green based solely on the workflow's existence.
