# Handoff 2026-10-10: theory-card selection

Actual GitHub main after the prior 72% handoff:

- `9ee183e9` adds bytecode-verified `LegacyTheorycraftChoiceFlow`: marked-card activation costs, original keep ordering, previous `lastDraw` seed retention, aid/bonus draw bookkeeping.
- `af4b813` adds focused core parity tests, passed in GitHub CI.
- `3473c9d`: server-side `ResearchTableBlockEntity.keepSelectedTheoryCard` safely retains existing selected card as `lastDraw`, preserves arbitrary subclass NBT, and carries old `lastDraw` seed to `savedCards`; malformed legacy NBT is rejected without deleting it.
- `4374805`: existing session uses original menu button 1 for Keep; only absent sessions start a theory.
- `ba587baa`: synchronized selected-card DataSlot; `7fd282f`, `f0d349b`, `c76d57d`, `673de65`, `f9790f7` draw a readable Keep button with click and hover tooltip. `e0dca1e`, `2751ab3` supply English/Russian labels.
- The original BETA26 ContainerResearchTable NEVER incremented `placedCards` on successful activation. The port previously did; fixed in `0d600de`, related core tests repaired in `08afb16d`.
- Original BETA26 bytecode was examined with javap against user's `original_BETA26.jar` for button 1 and buttons 4..6.
- No complete card-factory+inventory/materials mapping yet: DO NOT enable menu buttons 2/3 or 4..6 with fake card implementations. Manual NeoForge **client playtesting has not happened**.
- Optional Moon uncollected note randomization remains deferred. Original GUI and font improvements retained.
- Conservative overall estimate **73% conditional on latest GitHub Actions success**; check latest main and CI before starting next turn.

Next: exact BETA26 33-card factory initializers, faithful subclass NBT, real item requirements and server validation, three-choice client sync and real draw+activation.
