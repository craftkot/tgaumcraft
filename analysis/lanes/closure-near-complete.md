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

**Actual validation:** local Java 21 `bash tools/test_readable_text_layout.sh` — **PASS, 1989 assertions**. [GitHub Actions #41255716435](https://github.com/craftkot/tgaumcraft/actions/runs/41255716435) — SUCCESS on `9703379` (core, full NeoForge build/JAR, dedicated-server smoke). Real Minecraft GUI/client smoke was **not** run.

**Audit:** fixed rubric stays **59/100** and M16 stays **4/6**. This is one localized GUI fix, not complete visuals/audio/localization or full in-game acceptance.

## 01.11.2026 — wrapped multiline overflow, styled final-line ellipsis

**Real defect:** `ThaumcraftReadableText.wrapped` drew the entire last visible line and then overpainted `"..."` at its right edge. The result obscured actual glyphs; when `maxWidth < font.width("...")`, the marker started *left* of the allotted rectangle. `maxLines <= 0` and `maxWidth <= 0` had no early exit.

**Implementation:**
- `ReadableTextLayout.longestPrefixWithSuffix` finds the longest prefix that fits *together* with a marker using the rendered pixel width. Descending scan supports style-dependent/non-monotonic advances and returns -1 when even the suffix cannot fit.
- `ThaumcraftReadableText.wrapped` now stops on nonpositive bounds, replaces only the last visible line when more lines remain, and uses a shortened suffix (3/2/1 dots or empty). It builds a `FormattedCharSequence` retaining the original per-code-point `Style`, so bold/colored text is measured and rendered consistently; supplementary Unicode is not split.
- `ReadableTextLayoutTest` adds seven independent regression assertions for prefix fitting, impossible widths, negative glyph counts and non-monotonic styled advances.

**Actual validation:** Java 21 standalone test **PASS, 1996 assertions**. Additional temporary synthetic Minecraft API stubs compiled the actual client class with `javac --release 21` and exercised `wrapped` for **21 widths (0..20)** with styled glyphs and supplementary Unicode: PASS. This is a **stub test**, not a real NeoForge build, Minecraft screenshot or game-world validation.

**Commits:** `8932022743ab03ad4c623589656fb57416c09fe1` (core), `ae35f64eec53591a9df02599b557794836599f33` (client), `31688d971d70f2e8867457f40bc10b5609e4bdd4` (tests). GitHub PR [#4](https://github.com/craftkot/tgaumcraft/pull/4) remains Draft; no merge. CircleCI [#530](https://circleci.com/gh/craftkot/tgaumcraft/530) was PENDING at initial inspection. Full NeoForge/real client checks for this change not yet confirmed.

**Audit:** unchanged **59/100**, M16 **4/6**. One overflow path fixed, but no real GUI proof or full visual/audio/resource parity.

**Next:** inspect CI on the exact latest branch HEAD, fix any real NeoForge compile failure, then obtain real Minecraft client screenshots of narrow and multiline RU/EN text; audit remaining untranslated/placeholder M16 assets without modifying other workstreams.
