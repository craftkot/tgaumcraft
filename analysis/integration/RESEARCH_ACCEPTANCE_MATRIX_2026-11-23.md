# Research M07 — integration acceptance matrix (candidate only)

Base: `integration/verified-port-2026-11-14` at `9d4361ef49d566065d2a8f16767fed27936476bc`.
Source: draft PR #5, `feature/research`. This document does **not** import gameplay code or constitute evidence of passing tests.

## Preparation
1. Record immutable integration-base SHA and Research HEAD SHA. Recheck both immediately before creating a combined candidate.
2. Compare the 35 Research-changed paths against the *current* integration tree and other open lanes (#6 infusion, #7 golems, #8 alchemy). Zero same-path overlap is **not** proof of Java/runtime compatibility.
3. Create a **new** candidate branch from integration; bring over Research changes without rewriting `feature/research`. Resolve conflicts only with source review and explicit tests. Never merge this plan-only branch as a gameplay candidate.

## Exact candidate SHA: required gates (all must pass)
| Gate | Execution and proof required | Status |
| --- | --- | --- |
| Java Core | Run core parity suite **and** `tools/run_research_aid_parity.sh`, `tools/run_research_browser_parity.sh`, `tools/run_research_standalone_parity.sh`; archive logs, commands, exit codes | NOT RUN |
| NeoForge | Java 21 Gradle full NeoForge build; archive produced JAR hash and logs | NOT RUN |
| Native GameTests | Real NeoForge test runtime, nonzero executed tests, assert ResearchTable BlockEntity state transitions, reject insufficient items/ink/XP | NOT RUN |
| Dedicated server | Boot dedicated server with candidate JAR, no missing registry/network errors, interact with research table | NOT RUN |
| Client | Launch actual Minecraft 1.21.1 client; open Thaumonomicon index/pages and ResearchTable screen; click through page navigation, category visibility, server-authoritative card choice | NOT RUN |
| Save/reload | Create theory, draw/activate card, save and fully stop server, cold restart same world; verify NBT, research progress, item counts and UI | NOT RUN |
| Anti-dupe | Test duplicate activation packets, rapid repeated clicks, disconnect during debit, inventory full/rejection, chunk unload/reload; verify conserved resources and no double reward | NOT RUN |

## Minimum in-world M07 scenarios
- Place a registered crucible near a research table; verify discovery of `AidBasicAlchemy` and the resulting *original* card pool without granting free materials.
- Repeat with a registered infusion matrix and `AidBasicInfusion`.
- Check selected aid changes inspiration and draw eligibility; compare before/after item, ink, XP and inspiration balances.
- Exercise locked vs unlocked research pages, pagination, category visibility and malformed NBT handling.
- Repeat activation after restart and send duplicate requests; neither items nor progress may be credited twice.

## Evidence rules
For **each** gate store: exact candidate commit SHA, UTC timestamp, command/environment, raw log or recording, GitHub Actions run/job URL (if applicable), explicit pass/fail, and reviewer. CI success from PR #5 or #61 is not transferable to a new SHA. Unit tests that inspect source strings are not a real client test. A GameTest of pure Java arithmetic is not an in-world resource-conservation test. Missing evidence = **BLOCKED**, not passed.

**Merge decision: BLOCKED** until all seven gates are demonstrated on one exact combined candidate SHA. No change to overall 59/100 without a new full audit.
