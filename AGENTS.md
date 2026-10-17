# Repository agent instructions — Thaumcraft 6 NeoForge port

**MANDATORY SINGLE SOURCE OF TRUTH:** `ROADMAP_RU.md` at the repo root.
This is an in-progress port of the EXISTING Thaumcraft 6.1.BETA26 to Minecraft 1.21.1 NeoForge. Do NOT create a fresh project, rewrite architecture from scratch, or invent independent feature priorities.

## Startup steps for every new agent / ChatGPT chat

1. Read current `main` HEAD, then `ROADMAP_RU.md` (authoritative task/status list), then `analysis/HANDOFF_2026_10_17_ALCHEMY_GAMEPLAY_VERIFIED_RU.md`, `analysis/PROGRESS_AUDIT_2026_10_16.md` and current CircleCI statuses.
2. Find the **one 🟡 active numbered stage** and its first unfinished substep. As of 18.10.2026 this is **M04.1**, investigate failed real-game job #209. **Always use the live roadmap in case this has changed.**
3. User input `П` / `п` = continue writing real code from that specific task now. Do NOT ask to choose the next mechanic, start over or spend the whole turn polling CI.
4. Commit small real improvements to `main`, validate with appropriate Core/NeoForge/client checks, update `ROADMAP_RU.md` with evidence and next substep after work. If the substep is not complete, it remains 🟡.
5. Only promote a completed top-level stage to 🟢 and the next to 🟡 after meeting all gameplay acceptance criteria; never call compilation "Minecraft verified". One top-level 🟡 stage, strictly ordered M00–M17.
6. Historical handoff documents and previous numerical estimates are **not competing roadmaps**. The last audited 59/100 rating is an independent weighted readiness metric, not the proportion of green rows.
7. No force-loaded chunks, free essentia, fabricated items or tests that claim success based merely on typed chat commands. Use true server-world state and source-backed BETA26 parity.
8. If user explicitly reprioritizes stages, update the roadmap first and log why. Otherwise keep current milestone.

The root `README.md` links to the roadmap. Persist instructions in GitHub so future chats can reliably continue by reading this file.
