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

## 01.11.2026 — centeredLabel: retain styled visual glyphs

**Defect:** `ThaumcraftReadableText.centeredLabel` converted a rich `Component` to `message.getString()` before truncation and rendering. This discards formatting (including bold/color) and the resolved visual ordering for bidirectional text; width could be computed for different glyph advances than the source Component. The old routine's code-point-safe truncation was not enough for styled text.

**Implementation:** `centeredLabel` now consumes `Component.getVisualOrderText()` and measures/renders the same `FormattedCharSequence`. The existing styled ellipsis routine is reused for narrow labels; full labels retain all original styles and visual order. Empty/zero-width labels do not draw. No unrelated screen/registries or other teams' code was changed.

**Durable regression:** new `tools/test_readable_text_client.py` compiles the **actual** `ThaumcraftReadableText.java` and `ReadableTextLayout.java` with minimal test-only Minecraft API stubs under Java 21. Local result **18 assertions PASS**: bold Cyrillic truncation and centering, unchanged bold full label, visual-order permutation, supplementary emoji, zero/one/two-pixel boundaries. Stub font metrics are synthetic: this verifies our code's data flow and bounds, **not** NeoForge ABI, a Minecraft client, actual glyph widths or screenshots.

**Code commit:** `a08deca494cb350e6069a7d1d5c3b3f142f732f9`. Test commit: `07ef01cca7685b093bdd90a9dc8aee7c5bc70ed7`. [PR #4](https://github.com/craftkot/tgaumcraft/pull/4) remains Draft. GitHub Actions #41264118817 / #41264115339 started on test HEAD; final results must be inspected before claiming full compile/server success. CircleCI #533 was pending at last check. Historical rating **59/100**, M16 **4/6**, unchanged.

**Next:** verify exact HEAD GitHub Actions full NeoForge/JAR/server and investigate any failures; obtain a real Minecraft-client RU/EN visual smoke (bold, mixed-style, narrow labels, bidi). Then audit missing original visual/audio resources and choose the next independent M16 gap; do not claim complete asset parity from this fix.

**CI follow-up on code commit `a08deca`:** [GitHub Actions #41264022328](https://github.com/craftkot/tgaumcraft/actions/runs/41264022328) **SUCCESS**: Core parity, full NeoForge build, dedicated-server smoke, JAR upload. This verifies the actual client class compiles in the NeoForge build, but does not run the graphical Minecraft client. The subsequent test-only commit `07ef01c` had CI still in progress at this observation. No extra readiness points.


## 01.11.2026 — M16: Unicode grapheme-safe ellipsis (accent/ZWJ/flag)

**Observed code defect:** Previous `ReadableTextLayout.ellipsize` truncated at Unicode code-point boundaries, and `ThaumcraftReadableText.lastLineWithEllipsis` truncated at individual `FormattedCharSequence` glyph boundaries. Both could leave a base Cyrillic letter without its combining accent, a partial joined emoji, or one regional indicator of a flag. This is a genuine layout bug even though it did not split UTF-16 surrogate pairs.

**Implementation:**
- `ReadableTextLayout.java`: Java 21 `BreakIterator.getCharacterInstance(Locale.ROOT)` determines extended grapheme-cluster boundaries; descending pixel-width check selects the longest fitting *complete* prefix, without assuming monotonic kerning.
- `ThaumcraftReadableText.java`: reconstruct visual code points from styled glyphs, map grapheme UTF-16 boundaries back to styled glyph-prefix counts, and only truncate at those boundaries. Preserve the original `Style` of retained glyphs.
- `ReadableTextLayoutTest.java`: regression for Cyrillic combining accent, woman-scientist ZWJ emoji, Russian flag and non-monotonic synthetic width.
- `tools/test_readable_text_client.py`: stub-based tests for styled combining accents and joined emoji, with both narrow and fitting widths.

**Commits:** core `c807a30`, client `226cd63`, core regression `1070c5a`, client regression `258f643`, corrected synthetic ZWJ width `8c155c4`. Local Java 21 reproduction of the grapheme algorithm and seven focused regression cases: **7/7 PASS**. This is a locally reconstructed focused harness, **not** a run of the repository's full test script. GitHub Actions full NeoForge build/JAR/server on latest code was still **IN PROGRESS** when this entry was written; inspect exact run status before declaring success. Real Minecraft GUI/client has **not** been launched for this change.

**Safety/audit:** changes only in this lane's two layout classes and two dedicated tests. `main`, other feature branches, CI and registries untouched. PR #4 stays Draft. Fixed readiness rubric **59/100**, M16 **4/6** (no +1 for isolated layout corrections).

**Next:** inspect latest GitHub Actions, run `tools/test_readable_text_layout.sh` and `tools/test_readable_text_client.py` in a suitable checkout, then obtain actual Minecraft GUI screenshots to verify font shaping and the visual bounds. Continue missing visual/audio parity after the GUI smoke.
