# Closure-near-complete lane — 01.11.2026

## Scope and branch safety

- Branch: `feature/closure-near-complete`, created from `main` at `a4280834adc5a20b6951098af937d30ca4aeca56`.
- Workstream: **M16, GUI readability** (historical audit category “models, sounds, particles, RU localization, interface polish” 4/6).
- No changes to `main`, `feature/alchemy`, `feature/research`, `feature/infusion`, `feature/golems` or `chore/coordination`. Existing copies of `ThaumcraftReadableText.java` in those feature branches matched `main` blob `95c8f75306a74ec03b40e437cf62addf3c444118` before work.

## 01.11.2026 — narrow RU/EN centered labels

**Real defect:** `ThaumcraftReadableText.centeredLabel` appended the three-dot suffix unconditionally after `font.plainSubstrByWidth`. For a button narrower than the suffix itself, the empty prefix still produced `"..."`, rendering outside the allotted rectangle. The code had no early return for zero/negative available width. This is a real clipping/layout error, not a claim about missing translation data.

**Implementation:**
- `src/main/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayout.java`: deterministic code-point-aware binary search using the **active font's pixel width**; fallback from three to two to one dot, or empty string if even one dot cannot fit; avoids splitting supplementary Unicode surrogate pairs. Does not claim full grapheme-cluster segmentation.
- `src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftReadableText.java`: use the shared layout helper for centered labels; skip draw when no pixels available.
- `src/test/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayoutTest.java` and `tools/test_readable_text_layout.sh`: independent Java 21 regression for Cyrillic, ASCII, supplementary emoji and widths -1..150.

**Actual validation:** local Java 21 `bash tools/test_readable_text_layout.sh` — **PASS, 1989 assertions**. This tests the width algorithm with a deterministic synthetic font; it is **not** a NeoForge compilation, real Minecraft client or screenshot verification. Full CI status must be checked after opening the PR.

**Audit:** fixed rubric stays **59/100** and M16 stays **4/6**. This is one localized GUI fix, not complete visuals/audio/localization or full in-game acceptance.

**Next:** inspect actual NeoForge GUI rendering on narrow translated buttons and the separate `wrapped` overflow path; add an end-to-end client screenshot/measurement before awarding any M16 score. Avoid modifying research/infusion/alchemy-owned GUI paths without coordination.
