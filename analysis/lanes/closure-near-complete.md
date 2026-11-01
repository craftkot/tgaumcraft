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

**CI follow-up:** [GitHub Actions #41268171341](https://github.com/craftkot/tgaumcraft/actions/runs/41268171341) **SUCCESS** on `1070c5a`: Core parity, NeoForge build/JAR, dedicated-server smoke and JAR upload. The later `258f643` client stub test and `8c155c4` corrected test fixture were not part of that exact run; their workflows were still in progress. Separate local Java 21 reconstruction of the styled-grapheme prefix mapping passed **6/6** targeted cases. This is algorithm-level evidence, not a run of the actual Minecraft client.


## 01.11.2026 — account for Minecraft's one-pixel font shadow (M16)

**Rendering defect:** both `centeredLabel` and `wrapped` used `font.width(...)` as the entire drawable rectangle, yet called `GuiGraphics.drawString(..., true)`. Minecraft's shadow pass adds one pixel at the right and bottom. A glyph run whose measured width exactly equaled the panel width therefore still drew one pixel beyond its right boundary. The previous regression checked glyph widths, not the shadow footprint.

**Actual source fix:** `src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftReadableText.java` reserves one pixel before truncating centered labels and before splitting/wrapping text. Widths 0–1 skip drawing. Original font size, colors, glyph order, style and gameplay logic are unchanged.

**Durable regression:** `tools/test_readable_text_client.py` now records whether the real helper requested a shadow pass and checks `drawnWidth + 1` against the available width. Boundary tests cover a bold emoji label at exactly 16/17 pixels, one-dot fallback at 2/3 pixels, and a multiline `wrapped` label at 12 pixels. Added a narrowly scoped `.github/workflows/closure-readable-text.yml` to run the existing pure Java pixel/grapheme test and the real GUI-helper compilation against minimal Minecraft API stubs. This workflow is NOT a real Minecraft client and does not replace full NeoForge CI.

**Commits:** `7267ac5` actual client fix; `452f26d` and `843baf6` regression and threshold adjustments; `71e7baa` independent test workflow. A standalone Java 21 synthetic boundary reproduction passed; **do not report the repository stub suite or latest NeoForge CI as passed until the new GitHub Actions runs finish**.

**Safety/audit:** work is confined to this lane's existing GUI helper, dedicated test and new independent workflow. No other feature branch or `main` was modified. Historical audit stays **59/100**, M16 **4/6**. No real Minecraft GUI screenshot or BETA26-wide visual/audio parity proof yet.

**Next:** inspect the exact-head regression workflow and full NeoForge CI, fix any actual failure; then add real client GUI screenshot evidence for narrow RU/EN labels, and move to missing original models/FX/audio after verified GUI acceptance.


**Verification follow-up:** [M16 isolated regression Actions #41271605217](https://github.com/craftkot/tgaumcraft/actions/runs/41271605217) completed **SUCCESS** on `71e7baa`. Actual logs: `Readable text pixel bounds: OK (2003 assertions)` and `Client styled GUI layout stubs: OK (38 assertions)`. Full NeoForge/JAR/server Actions [#41271605240](https://github.com/craftkot/tgaumcraft/actions/runs/41271605240) remained **IN PROGRESS** at inspection. No real-client screenshot; 59/100 and M16 4/6 remain unchanged.


## 01.11.2026 — M16: unsplittable glyph in a narrow wrapped label

**Code defect:** `ThaumcraftReadableText.wrapped` trusted every visible `Font.split` line to fit `maxWidth - 1`, except the final line when more lines were hidden. A single indivisible glyph (e.g. emoji/CJK) can be wider than the split width. With `maxLines=1` and only one returned line, it was drawn unbounded, including the shadow, outside the panel.

**Fix:** `src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftReadableText.java` now passes every ordinary visible line through the existing grapheme-safe, styled, pixel-bounded `fitStyledLine` helper. For the last visible line with additional hidden lines, the existing explicit ellipsis path is unchanged. No changes to global registries, world logic, CI settings or other teams' files.

**Regression:** `tools/test_readable_text_client.py` now exercises the real GUI helper against synthetic Minecraft API stubs with one unsplittable 6px glyph inside a 5px shadow-inclusive box (expected two dots), and a 7px box where the full glyph fits. This test is not a real Minecraft client. Commits: code `21acb388d05b3849ebeb8f7989187052c54e2dda`, test `581920cd4de7ee1c674499707b831928f6b24b72`.

**CI baseline:** the immediately preceding HEAD `4764057` had [M16 regression #41271654773](https://github.com/craftkot/tgaumcraft/actions/runs/41271654773) **SUCCESS** and [full NeoForge/JAR/dedicated server #41271654740](https://github.com/craftkot/tgaumcraft/actions/runs/41271654740) **SUCCESS**. New exact-head runs on `581920c` started: [M16 regression #41276053884](https://github.com/craftkot/tgaumcraft/actions/runs/41276053884) queued and [full CI #41276053888](https://github.com/craftkot/tgaumcraft/actions/runs/41276053888) in progress at observation. Do not claim either new run passed before checking it.

**Audit:** 59/100, M16 4/6 unchanged. The fix prevents a specific overflow but does not prove graphical RU/EN rendering, original sound/particle/asset parity or end-to-end gameplay. PR #4 stays Draft; no merge. Next: inspect both exact-head CI results; obtain actual client screenshot evidence and identify a missing original visual/audio asset without overlapping the feature teams.

**Exact-head regression result:** [Actions #41276053884](https://github.com/craftkot/tgaumcraft/actions/runs/41276053884) **SUCCESS** on `581920c`; job logs report `Readable text pixel bounds: OK (2003 assertions)` and `Client styled GUI layout stubs: OK (44 assertions)`. Full NeoForge/JAR/server [#41276053888](https://github.com/craftkot/tgaumcraft/actions/runs/41276053888) was still in progress at this check. This is not real graphical client proof.

**Full CI follow-up:** [Actions #41276053888](https://github.com/craftkot/tgaumcraft/actions/runs/41276053888) completed **SUCCESS** on exact code+test commit `581920c` (NeoForge build/JAR and dedicated-server smoke workflow). This is still not a graphical Minecraft client. Readiness remains **59/100**, M16 **4/6**.


## 01.11.2026 — M16: source-faithful jar brace and label item art

**Defect:** jar brace used vanilla iron-ingot art and labels used vanilla paper, despite dedicated source BETA26 sprites. The original JAR SHA-256 is \`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f\`.

**Change:** imported exact 16x16 BETA26 PNGs \`jar_brace\`, \`label\`, \`label_overlay\` into modern \`textures/item\`. Restored original jar-brace layer and blank-label layer. Added \`label_filled.json\` with original base+overlay layers; \`label.json\` selects it through \`custom_model_data:1\`, which existing \`JarLabelItem.filled\` already sets. No gameplay classes changed.

**Checks:** local \`python3 tools/verify_label_brace_source_art.py\` passed on staged files; verifies source PNG SHA-256/dimensions, model mappings and Java model-data contract. This is NOT a real client render. Aspect-dependent overlay tint is not implemented; do not call filled-label parity complete. Full NeoForge/JAR/server and graphical screenshots on this HEAD remain to be verified.

**Safety:** no changed-file overlap with open PR #1/#3 or current feature/alchemy comparison; no other branch or main modified; PR #4 remains Draft. Fixed audit **59/100**, M16 **4/6** unchanged.

**Next:** check exact-head CI and obtain in-client screenshot of brace, blank label and filled label; then implement correct aspect overlay tint if needed.

## 01.11.2026 — M16: BETA26 filled label overlay tint (source change)

**Defect:** Original filled label overlay PNG/model were restored, but no client ItemColor handler colored layer1 from the saved one-aspect label NBT. All 37 filled labels therefore rendered the same neutral overlay, even though the original Aspect colors already exist in the source-derived `AspectRegistry`.

**Change:** Added isolated client MOD-bus `JarLabelColorEvents` subscriber (no shared registry edits) registering ItemColor for `ThaumcraftItems.JAR_LABEL`. It calls the existing validated `JarLabelItem.selectedAspect(stack)` and new loader-neutral `LegacyLabelTint.colorFor`: tint index 1 gets `0xFF000000 | AspectData.color()`; index 0 (paper base), unrelated layers, blank/invalid NBT stay white. The original 16x16 PNGs and the two-layer item model remain byte-identical to the previous commit.

**Durable regression:** `tools/test_label_tint.sh` compiles the REAL `AspectRegistry`, `AspectData`, new helper and `LegacyLabelTintTest` under Java 21. Covers all 37 source colors, base/other layers, blank and invalid aspects. `verify_label_brace_source_art.py` also checks event wiring. The existing isolated M16 Actions workflow now runs both checks. Full NeoForge and real-client validation must be inspected on the exact new HEAD before claiming success.

**Safety/audit:** No overlap with current M06/M10/alchemy/research changed files for these paths; no merge, no main changes. Historic rubric stays **59/100**, M16 **4/6**. Real in-game GUI screenshots of all filled-label states and client restart still missing.

**Next:** inspect Actions on the exact HEAD and correct any compile or client failure; obtain a real Minecraft-client screenshot showing distinct Aer/Ignis filled labels alongside blank label and jar brace, then continue other original FX/sound gaps.

**Exact-code CI follow-up (ceb85ee):** [M16 Actions #41283338604](https://github.com/craftkot/tgaumcraft/actions/runs/41283338604) completed **SUCCESS**: original art/model/event-wiring verifier PASS, real AspectRegistry Java21 overlay test **118 assertions PASS**, readable-text core **2003 assertions PASS**, GUI stub **44 assertions PASS**. [Full Actions #41283338612](https://github.com/craftkot/tgaumcraft/actions/runs/41283338612) completed **SUCCESS** on the same SHA: NeoForge build, JAR upload and dedicated-server smoke all passed. **Neither run launches the graphical client**, so M16 stays **4/6**, fixed readiness **59/100**. Next: actual in-client screenshot proving blank vs Aer/Ignis overlay tint, then other source FX/audio parity.


## 01.11.2026 — M16: independent REAL Minecraft client regression for label tint

**Previously missing acceptance evidence:** isolated Java/asset tests (118 assertions), NeoForge/JAR and dedicated server were green on `ceb85ee`, but no running graphical Minecraft client had rendered the original jar brace, blank label or filled Aer/Ignis labels. Neither mocked GUI tests nor generated screenshots prove actual model selection, ItemColor registration and in-game appearance.

**Implemented:** `tools/client_label_smoke.py` generates a focused shell script by reusing the existing, independently validated `tools/client_smoke.sh` Xvfb/real-world boot, level.dat command-permission gate and chat input helper, then stops BEFORE unrelated alchemy/infusion tests. It creates actual Minecraft 1.21.1 ItemStacks through `/item replace`, captures four individual same-slot screenshots plus a real inventory screen, and compares identical hotbar icon crops for brace vs blank, blank vs each filled, and Aer vs Ignis. All comparisons must be nonzero; missing label models and render-thread crashes fail the test. `.github/workflows/closure-label-client.yml` installs Java21/Gradle9.2.1/Xvfb/Mesa, validates generated shell syntax, runs `gradle runClient` and uploads real PNGs/logs even on failure. This is a real graphical-client check, not a screenshot generated from asset files.

**Commits:** `113572c374d78816096f25754dd3282a0621263e` (client probe generator), `989ce37d273817a4a0d046b9a615ba72a83d719f` (independent workflow). [Real client Actions #41287870087](https://github.com/craftkot/tgaumcraft/actions/runs/41287870087) was **IN PROGRESS** at first inspection; a second PR-triggered run #41287866182 also started. **Do not claim real GUI parity or successful screenshot before exact-head run completes and artifacts are inspected.** The old 59/100 audit and M16 4/6 are unchanged. No edits to main, other feature lanes, shared client_smoke.sh, global registries or Gradle configuration.

**Next:** inspect client run's first failing step and real screenshot artifact, correct any item-component syntax/coordinate/model problems, then verify actual Aer vs Ignis color difference. After graphical confirmation, audit the next missing BETA26 sound/FX resource. Keep PR #4 Draft and do not merge without user approval.


## 01.11.2026 — M16: настоящий клиентский арт подтверждён, усилен регрессионный критерий

**Полученные доказательства:** [real Minecraft Actions #41287870087](https://github.com/craftkot/tgaumcraft/actions/runs/41287870087) **SUCCESS** на кодовом SHA `989ce37`. Лог реального Xvfb/Mesa/NeoForge 1.21.1 клиента подтвердил создание интегрированного creative/peaceful мира с разрешёнными командами, четыре фактических hotbar-скриншота, один общий creative-inventory скриншот и `LABEL_REAL_CLIENT_VISUAL_SMOKE=PASS`. На старом тесте сравнения 32×32 были ненулевые AE: brace/blank 372, blank/Aer 303, blank/Ignis 303, Aer/Ignis 68. Реальные PNG из GitHub artifact [#13074984471](https://github.com/craftkot/tgaumcraft/actions/runs/41287870087/artifacts/13074984471) **скачаны и визуально просмотрены**: в inventory видны разные предметы, у Aer зеленоватая заливка, у Ignis оранжево-красная. Это именно скриншоты Minecraft, не макеты.

**Найденный дефект теста:** старый crop с центром `Y+HEIGHT-22` захватывал только нижнюю часть иконки вместе с границей hotbar. Поэтому простой AE>0 мог пройти из-за фонового отличия, не проверяя основной цвет аспекта. Исправлено в [`a81f726`](https://github.com/craftkot/tgaumcraft/commit/a81f726c29192e5bf8bf1a14c940e66498f35bfd): центр crop сдвинут на 20 px вверх, проверены границы реального скриншота, для центральных 16×16 добавлено независимое сравнение `mean.r-mean.g`. Ignis должен иметь существенно более красную заливку, чем Aer; иначе клиентский тест падает. Это не вмешивается в игровую механику и не требует изменений чужих веток.

**Локальная повторная проверка на реальных PNG из #41287870087:** новый полный 32×32 crop содержит всю иконку; для центральных 16×16 Aer `R-G=0.0435968`, Ignis `R-G=0.311857`, проверка порога **PASS**. Это проверка нового алгоритма на сохранённых реальных кадрах, **не** новый полный клиентский запуск на `a81f726`. Новый точный CI необходимо проверить отдельно. Артефакты остаются доступны до 15.11.2026.

**Аудит:** фиксированный индекс **59/100**, M16 **4/6**. Подтверждён именно узкий in-game сценарий оригинальных brace/blank/Aer/Ignis иконок; не подтверждены все 37 вариантов, все GUI масштабы, другие модели/звуки/частицы и полная игровая прогрессия. PR #4 остаётся Draft; никаких merge в main.

**Следующее:** проверить точный клиентский CI на `a81f726`; при падении исправить конкретную ошибку. Затем взять отдельный оригинальный BETA26 звук или FX, которого не хватает в игре, с независимым подтверждением и без пересечений с M06/M07/M10/M04.

**Follow-up к тестовому коммиту:** [`a2989f2`](https://github.com/craftkot/tgaumcraft/commit/a2989f275138de007b41c6e343fe9016b24cc0bd) добавляет завершающий newline в `identify -format '%w %h\\n'`: без него Bash `read` под `set -e` мог вернуть EOF/exit 1 даже при полученных размерах. Локально на фактическом PNG 1280×800 проверены `read`, crop-in-bounds и цветовой порог (PASS). На этом SHA [GitHub Actions #41291923168](https://github.com/craftkot/tgaumcraft/actions/runs/41291923168) был QUEUED, [M16 GUI regression #41291923218](https://github.com/craftkot/tgaumcraft/actions/runs/41291923218) IN PROGRESS; новый графический прогон **не подтверждён**. Отдельный `closure-label-client` workflow должен быть проверен после запуска.


## 01.11.2026 — M16: collapsed panel geometry regression

**Observed code defect:** the existing `ThaumcraftReadableText.panel` always called its interior `GuiGraphics.fill` even for a 0px, 1px or 2px panel. At width=1 it submitted `(x+1, y+1) -> (x, y)`, an inverted rectangle; at width=0 even the border was zero-area. Such draw commands are not safe clipping and may paint outside narrow UI bounds. This is a modern GUI-helper bug, not a claim that an original BETA26 gameplay feature was missing.

**Fix:** in the existing M16-owned helper, skip non-positive panel sizes and only draw the inner rectangle when both dimensions exceed 2. Normal panels (3px and larger) keep the exact original two fills and colors. Dedicated Java client-stub regression now records each real helper `fill` call, asserts no inverted/empty rectangles, and checks the 3x3 interior coordinates. No shared registry, sound/FX, Gradle, other lane, or `main` edits.

**Verification status at commit creation:** structural review complete; GitHub Actions must verify the exact commit. The prior branch HEAD `056995d` had real label client #41291945725 SUCCESS, M16 regression #41291945686 SUCCESS, full NeoForge/JAR/server #41291945670 SUCCESS. Those are *baseline* results, not tests of this patch. The prior label in-game evidence covers brace/blank/Aer/Ignis only. **Audit remains 59/100, M16 4/6**; no parity credit for guarding a collapsed rectangle.

**Next:** inspect exact-head M16 Java regression and full NeoForge CI; if green, resume missing original BETA26 FX/audio work with independent asset provenance and real client evidence. PR #4 stays Draft; no merge.

**Exact-code verification follow-up (commit [68e81c9](https://github.com/craftkot/tgaumcraft/commit/68e81c9bb3aa0adca2f5f31cb654b3f1f175514c)):** [M16 regression #41296376309](https://github.com/craftkot/tgaumcraft/actions/runs/41296376309) **SUCCESS** with 2003 readable-layout assertions, **54** real-class GUI-stub assertions (10 more than the previous 44), and 118 label-tint assertions. [Full NeoForge/JAR/server #41296372165](https://github.com/craftkot/tgaumcraft/actions/runs/41296372165) **SUCCESS**, including Core parity, NeoForge build, dedicated-server smoke and JAR upload. A separate exact-code graphical [client run #41296376246](https://github.com/craftkot/tgaumcraft/actions/runs/41296376246) remained **IN PROGRESS** when checked; it exercises label artwork, not collapsed panel geometry. The standalone Java 21 geometry reproduction also passed, but is not the real client. No score change: **59/100, M16 4/6**.


## 01.11.2026 — M16: restore BETA26 entity-height scan rune lifetime

**Source comparison (pinned original):** `TheDarkTower314/Thaumcraft-6-Source-Code@954022bb`, `ItemThaumometer.drawFX` calls `FXDispatcher.blockRunes` with duration units `(int)(target.height * 15)` for entity targets and `15` for block targets. `FXBlockRunes` sets `particleMaxAge = 3 * m`. Its dispatcher adds 0.5 to each incoming position, so the existing modern entity/block anchor coordinates are already correct; **do not offset them again**.

**Defect:** `ScanRuneParticle` hard-coded `lifetime=45` for both target types. The modern entity scan therefore expired too early for tall targets and too late for small ones, unlike BETA26. This is a real visual timing mismatch, not a missing sound file.

**Implementation:** `ThaumometerRuneFx` passes source-derived entity duration units to `ClientLevel.addParticle` (15 for blocks). `ScanRuneParticle.Provider` decodes the otherwise-unused Y-speed slot as duration units, forces original green color channel to zero, and applies `3*m` ticks. `LegacyScanRuneDuration` keeps the exact integer truncation for normal positive entity heights, with guards for zero/invalid/oversized values. No global particle/sound registry, original textures, other feature branches or `main` changed.

**Changes:** `src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumometerRuneFx.java`, `src/neoforge/java/dev/ehtid/thaumcraft/client/ScanRuneParticle.java`, new `src/main/java/dev/ehtid/thaumcraft/port/core/LegacyScanRuneDuration.java`, `src/test/java/dev/ehtid/thaumcraft/port/core/LegacyScanRuneDurationTest.java`, `tools/test_scan_rune_duration.sh`, dedicated `.github/workflows/closure-readable-text.yml` and this lane journal.

**Commits:** core `c0e2116`, test `ea2629f`, harness `4674257`, actual FX `a1d8e5b`, particle `79a9d5f`, isolated CI `e5d0009`. Standalone Java 21 regression **13/13 PASS locally** (`bash tools/test_scan_rune_duration.sh`). This validates timing math only. **Exact-head NeoForge compile, dedicated server and actual Minecraft-client rune timing are not yet confirmed**; inspect the workflows triggered by the code changes. The earlier real label-client #41296376246 is SUCCESS on the *previous* `68e81c9` and does not test the new scan-rune behavior.

**Audit:** fixed baseline remains **59/100**, M16 **4/6**. One scan particle lifetime bug fixed in code; no M16-wide audio/FX acceptance or point increase. PR #4 stays Draft and must not be merged without owner approval.

**Next:** check exact-head isolated M16 and full NeoForge/JAR/server CI. If compilation fails, repair it first. Then add a real Minecraft-client scan-particle lifetime probe (block=45 ticks, player-height target=81 ticks) and inspect actual frames/logs; continue cataloguing original BETA26 audio/FX only after verifying this regression.


**CI follow-up (exact code SHA `e5d0009`):** [M16 isolated regression #41300784421](https://github.com/craftkot/tgaumcraft/actions/runs/41300784421) **SUCCESS**. Its actual job log reports 2003 text-core assertions, 54 GUI-helper stub assertions, 118 label-tint assertions and **13 source-derived scan-rune duration assertions**. [Full NeoForge/JAR/server #41300784470](https://github.com/craftkot/tgaumcraft/actions/runs/41300784470) was **IN PROGRESS** (NeoForge build step) at last check; [real label client #41300784472](https://github.com/craftkot/tgaumcraft/actions/runs/41300784472) was **IN PROGRESS** and does not test scan-rune lifetime.

**Sound inventory (scope warning):** pinned original `SoundsTC.registerSounds` declares **65** named events, current port `ThaumcraftSounds` declares only **1** (`scan`). This is an observed registry gap of **64**, not a claim that every one is needed by currently implemented gameplay or that 64 new OGGs may be copied without checking provenance/licensing. Keep M16 at 4/6.


**Full NeoForge follow-up:** [Actions #41300784470](https://github.com/craftkot/tgaumcraft/actions/runs/41300784470) **SUCCESS** on code/workflow SHA `e5d0009f`. Job steps explicitly passed Core parity, **NeoForge build**, **dedicated-server smoke** and JAR upload. This proves compilation and server startup, **not** the new entity-height particle duration in a rendered Minecraft client. Label-only client #41300784472 was still running at this observation.


## 01.11.2026 — M16: world-space scan rune orientation (BETA26 source parity)

**Verified original:** pinned `TheDarkTower314/Thaumcraft-6-Source-Code@954022bb`, `FXBlockRunes.renderParticle` applies `glRotatef(rotation, 0,1,0)` and then `glRotatef(90, 0,0,1)`. `rotation` is a random multiple of 90 degrees. The old NeoForge `ScanRuneParticle` instead stored the random turn in `roll`, which rotates a camera-facing billboard in the view plane. Looking around therefore changed the apparent rune plane relative to the world: **not** the BETA26 geometry.

**Fix:** `ScanRuneParticle.getFacingCameraMode()` now supplies the original world-space Y-quarter-turn followed by a Z 90-degree tip via the 1.21.1 `SingleQuadParticle.FacingCameraMode` quaternion. Billboard `roll` is zero; alpha, sprite, duration and gravity are unchanged. No common registries, research/alchemy/infusion/golem files, or `main` modified.

**Code:** [4420341](https://github.com/craftkot/tgaumcraft/commit/4420341ff08b4c2c8ee01fa561dd0dd784a2eafb). **Regression:** [2c61096](https://github.com/craftkot/tgaumcraft/commit/2c61096de475db75b58bfbc50bb6e076e455db70) adds `tools/test_scan_rune_orientation.py`, which compiles the **actual** particle class and core duration helper with Java 21 API stubs, instantiates 128 particles, checks four world-Y quarter turns, exact 90-degree Z tip, no billboard roll, fixed orientation across camera/partial ticks and 45-tick block duration. This is a client-class regression using synthetic API stubs, **not a real rendered screenshot**. [a95ec6e](https://github.com/craftkot/tgaumcraft/commit/a95ec6e96f0da3963e1e713d64a76481f823a2da) wires the test into isolated M16 CI.

**Validation at journal write:** [M16 workflow #41304894278](https://github.com/craftkot/tgaumcraft/actions/runs/41304894278) QUEUED and [full NeoForge #41304894262](https://github.com/craftkot/tgaumcraft/actions/runs/41304894262) QUEUED on test/workflow HEAD `a95ec6e`. The earlier M16 GUI #41304868320 was SUCCESS on code-only `4420341`, but did not include the new orientation regression. **Do not claim exact-head PASS until logs are inspected.** Existing label-client smoke is unrelated to rune world orientation.

**Audit:** fixed historical baseline **59/100**, M16 **4/6**. No +1 without real in-game particle visual acceptance and broader M16 audio/FX coverage. PR #4 remains Draft; no merge.

**Next:** inspect exact-head M16 and NeoForge jobs; repair any compile/test failure first. Then build a genuine in-world scan-rune screenshot/timing probe to distinguish camera-facing billboards from original world-space runes. Continue original sound events after this visual check.

**CI follow-up:** [M16 regression #41304894278](https://github.com/craftkot/tgaumcraft/actions/runs/41304894278) **SUCCESS** on exact code+test+workflow SHA `a95ec6e`: actual Java 21 job log shows **897 ScanRuneOrientationTest assertions PASS** against the real particle source compiled with synthetic API stubs; previous tests 2003 GUI-layout core, 54 GUI stubs, 118 label tints, 13 rune duration also PASS. [Full NeoForge/JAR/server #41304894262](https://github.com/craftkot/tgaumcraft/actions/runs/41304894262) was **IN PROGRESS at NeoForge build** at this observation; no full compile or game-render PASS claim until it finishes. The existing label client does not validate rune orientation. Historical index stays 59/100 (M16 4/6).


## 01.11.2026 — M16: restore BETA26 scan rune face-space placement

**Verified original:** `TheDarkTower314/Thaumcraft-6-Source-Code@954022bb`, `FXBlockRunes.renderParticle` executes world-Y quarter turn, then local-Z 90-degree rotation, **then** `glTranslated(ofx, ofy, -0.51)`. `ofx` ranges [0,0.2), `ofy` ranges [-0.3,0.3). `FXDispatcher.blockRunes` adds 0.5 to the anchor; `ItemThaumometer.drawFX` passes block coordinates and entity coordinates already compensated by -0.5. The earlier port incorrectly added `ofx` to **world X** and `ofy` to **world Y**, and completely omitted the original -0.51 local-Z displacement. Consequently the rune quads were anchored inside/near the scanned block instead of its four original faces.

**Fix:** existing M16-owned `ScanRuneParticle` transforms the original local displacement using the same four discrete world-Y rotations as its quad: N (dx=-ofy,dz=-.51), W (dx=-.51,dz=ofy), S (dx=ofy,dz=.51), E (dx=.51,dz=-ofy); world-Y offset is `ofx`. Initial previous-position coordinates are synchronized after placement to avoid a first-frame interpolation jump. Sprite, color, original 0.03 gravity, 45/entity-derived lifetime and orientation remain unchanged. No shared registry, other feature lane, `main` or Gradle touched.

**Code:** [b9a8df2](https://github.com/craftkot/tgaumcraft/commit/b9a8df2513346606978c020c4327d851cc6356e6). **Regression:** [4862724](https://github.com/craftkot/tgaumcraft/commit/4862724e901a9a4a66a34402a2c430d0a56e510a) extends the existing Java-21 test of the **actual particle source compiled with Minecraft API stubs**: all four face planes at exactly 0.51 offset, tangent jitter range, vertical local offset, and stable previous position, for 128 particles. Standalone Python mathematical cross-check against the Y*Z rotation formula: 2000/2000 placements PASS. This cross-check is not a NeoForge compile or rendered client test.

**CI at initial journal write:** [exact code+test M16 regression #41309819796](https://github.com/craftkot/tgaumcraft/actions/runs/41309819796) and [full NeoForge/JAR/server #41309819818](https://github.com/craftkot/tgaumcraft/actions/runs/41309819818) were IN PROGRESS. Do not claim their success before checking exact SHA `4862724`. Last prior head `24402e2` passed [M16 regression #41304962669](https://github.com/craftkot/tgaumcraft/actions/runs/41304962669), [full CI #41304962690](https://github.com/craftkot/tgaumcraft/actions/runs/41304962690) and [real label-client #41304962753](https://github.com/craftkot/tgaumcraft/actions/runs/41304962753); none tests new rune placement.

**Audit:** historical 59/100, M16 4/6; no point credited without real scan-rune in-game evidence and broader visual/audio parity. PR #4 remains Draft; no merge. **Next:** inspect exact-head CI and fix any failure, then add a real Minecraft-client scan-rune camera/face/timing smoke rather than treating label screenshots or stub tests as rendered-rune proof. After that, continue missing original sound events with actual gameplay wiring.


**Exact-code test follow-up:** [M16 #41309819796](https://github.com/craftkot/tgaumcraft/actions/runs/41309819796) **SUCCESS** at code+test SHA `4862724`. Actual job log: **1409 ScanRuneOrientationTest assertions PASS**, now including face-space offsets and first-frame coordinates; 2003 text-core, 54 GUI-stub, 118 tint and 13 duration assertions also PASS. [Full NeoForge #41309819818](https://github.com/craftkot/tgaumcraft/actions/runs/41309819818) was still **IN PROGRESS** at the `NeoForge build` step when checked. No rendered scan-rune smoke; score unchanged.


**Full-build follow-up:** [Thaumcraft Port CI #41309819818](https://github.com/craftkot/tgaumcraft/actions/runs/41309819818) **SUCCESS** on exact code+test SHA `4862724`: full NeoForge build, dedicated-server smoke and JAR upload completed successfully. This proves compilation and server startup, **not** scan-rune rendering in a real Minecraft client. M16 4/6 and total historical 59/100 remain unchanged.


## 01.11.2026 — M16: real-client Thaumometer scan-rune acceptance harness

**Why this iteration:** earlier rune geometry/lifetime assertions compiled the actual Java class with synthetic Minecraft API stubs, but did **not** prove that the particles were created, rendered and expired in a real 1.21.1 client. The previous label screenshots do not cover the rune effect. Original comparison remains pinned to `TheDarkTower314/Thaumcraft-6-Source-Code@954022bb`, `FXBlockRunes` / `FXDispatcher.blockRunes`.

**Changed code, isolated to M16:**
- `src/neoforge/java/dev/ehtid/thaumcraft/client/ScanRuneParticle.java`: opt-in `THAUMCRAFT_SMOKE=1` diagnostics from the **real** particle constructor, its render-orientation callback and the tick that expires it. Silent during normal gameplay; no registry, physics, rotation, color or duration changes. Code commit [dbfbe6f](https://github.com/craftkot/tgaumcraft/commit/dbfbe6fcb9b5341595a094ffda2d9c5b5b411c97).
- `tools/client_scan_rune_smoke.py`: reuses the proven integrated-world/creative/commands bootstrap without editing shared `tools/client_smoke.sh`. Places a real gold-block target in front of the player, equips the Thaumometer, right-clicks in the live client, captures before/during/after PNGs, validates actual constructor/render/expiry log markers, 45-tick lifetime and all sampled face-space coordinates, and checks framebuffer pixel changes. Commit [52df1fa](https://github.com/craftkot/tgaumcraft/commit/52df1fab67a18e9ad61bcf1328640aea1a103523).
- `.github/workflows/closure-scan-rune-client.yml`: independent real Minecraft/Xvfb/Mesa workflow, artifacts even on failure. Commit [47f67a8](https://github.com/craftkot/tgaumcraft/commit/47f67a8fb3ccb2a7dca30559498fd2528bbc77e4).

**Validation at journal write:** real-client [Actions #41314620065](https://github.com/craftkot/tgaumcraft/actions/runs/41314620065), full NeoForge/JAR/server [#41314620052](https://github.com/craftkot/tgaumcraft/actions/runs/41314620052) and isolated M16 [#41314620063](https://github.com/craftkot/tgaumcraft/actions/runs/41314620063) were **IN PROGRESS** on exact code+workflow SHA `47f67a8`. No successful real-client rune render/timing claim until the run finishes and its log/screenshots are examined. Earlier exact-head `49c5b8b` [full CI #41310024723](https://github.com/craftkot/tgaumcraft/actions/runs/41310024723) SUCCESS is a **previous revision**, not proof for this new probe.

**Safety and audit:** no modifications to `main`, foreign feature branches or their PRs; PR #4 stays Draft. Fixed historical index **59/100**, M16 **4/6**. New instrumentation/test is not by itself completion of the category or a gameplay repair.

**Next action:** inspect #41314620065 step logs and screenshots; if the new real-client smoke fails, identify whether it is client input/world setup, particle rendering or the acceptance probe and repair that specific blocker before starting unrelated sounds/FX. If it passes, record exact real-world evidence and move to the next small BETA26 visual/audio mismatch.


## 01.11.2026 — M16: actual client found and fixed scan-rune velocity drift

**Real failure evidence, not a synthetic test:** [live client #41314614960](https://github.com/craftkot/tgaumcraft/actions/runs/41314614960) (SHA `47f67a8`) created, rendered and expired 40 rune particles with correct initial four-face coordinates and 45-tick lifetimes. **Its actual screenshot `71-rune-during-use.png` shows runes visibly scattered far into the sky instead of remaining at the scanned gold block.** Original pinned `FXBlockRunes` explicitly resets `motionX=motionY=motionZ=0` after construction. The NeoForge `Particle` velocity constructor adds randomized motion even for zero input speed, which the earlier port had not reset. This was a **real gameplay/visual bug** missed by stub-only orientation tests.

**Gameplay fix:** [b5ce2db](https://github.com/craftkot/tgaumcraft/commit/b5ce2dbed08a0c88d4ffceb95ec642659b96351c) explicitly resets `xd/yd/zd` to zero after `super(...,0,0,0)` in `ScanRuneParticle`. Original gravity remains 0.03, so the rune may fall naturally but no longer receives unintended random horizontal motion. The opt-in actual-client expiry marker now reports horizontal drift. [Regression 1d0dc29](https://github.com/craftkot/tgaumcraft/commit/1d0dc29e8923d245c3791d8461d9409b873ff73d) makes the synthetic vanilla constructor deliberately inject nonzero velocity and asserts the **real class** zeroes it and does not drift after ticking. [M16 regression #41315190510](https://github.com/craftkot/tgaumcraft/actions/runs/41315190510) **SUCCESS**, log: **1665 scan-rune assertions**.

**Client smoke blocker and repair:** [#41314620065](https://github.com/craftkot/tgaumcraft/actions/runs/41314620065) **FAILURE**: in that random fresh world, origin chunk was not loaded; vanilla chat said `That position is not loaded` for `/fill` and `/setblock`, leaving an empty-water screenshot and 0 particles. This is a test-fixture/world-preparation failure, not a gameplay defect. [93b1567](https://github.com/craftkot/tgaumcraft/commit/93b1567f2cc04dd39cd1dd3b63507d154b8717d5) now `/forceload`s origin and requires real vanilla block and item predicates before scanning. [#41315090717](https://github.com/craftkot/tgaumcraft/actions/runs/41315090717) **SUCCESS** for that world setup. [88d774b](https://github.com/craftkot/tgaumcraft/commit/88d774bc97dc5c579a642b0e9686ee585504fa06) additionally requires zero horizontal drift from the real particle expiry log.

**Exact latest code+test proof:** [full NeoForge/JAR/dedicated server #41315207341](https://github.com/craftkot/tgaumcraft/actions/runs/41315207341) **SUCCESS** on `88d774bc`; [real Minecraft/Xvfb client #41315207365](https://github.com/craftkot/tgaumcraft/actions/runs/41315207365) **SUCCESS** on the same SHA. Its logs verify actual integrated creative world, real gold-block target, real Thaumometer, **40 created / 40 render callbacks / 40 expired at 45 ticks**, all four world faces, **horizontal drift 0.0**, and **1437 changed framebuffer pixels** in the target region. Original PNGs/logs: [client evidence artifact](https://github.com/craftkot/tgaumcraft/actions/runs/41315207365/artifacts/13087635668). Manually inspected the live screenshot: the previous sky-scattered glyphs are gone; the visible runes stay close to the gold block edges. This does **not** prove every rune is front-facing or full BETA26 FX/audio parity.

**Modified files this iteration:** `src/neoforge/java/dev/ehtid/thaumcraft/client/ScanRuneParticle.java`, `tools/test_scan_rune_orientation.py`, new `tools/client_scan_rune_smoke.py`, new `.github/workflows/closure-scan-rune-client.yml`, and this lane journal. No shared registries, Gradle, global languages, `main` or foreign lanes changed. PR #4 stays Draft and must not be merged without user approval.

**Audit:** historical **59/100**, M16 **4/6** (unchanged). A real rune bug is fixed and client-proven, but most original sound/particle catalog and other M16 visual assets remain absent. **Next:** compare front-face rune visibility/occlusion against BETA26 and implement the next small original sound/FX event with actual gameplay wiring, avoiding the research/alchemy/infusion/golem branches.


## 01.11.2026 — M16: two-sided real-client scan-rune visibility regression

**Remaining acceptance gap:** prior [real-client #41315207365](https://github.com/craftkot/tgaumcraft/actions/runs/41315207365) proved 40 rune creations, rendering callbacks, 45-tick expiry, zero horizontal drift and a changed framebuffer from one camera position. It did **not** establish that the same world-space rune effect is visible from the opposite face of the scanned block. This iteration expands the real-client acceptance test; it is **not** a claim of new gameplay parity or a Java gameplay fix.

**Change:** [df1552d](https://github.com/craftkot/tgaumcraft/commit/df1552dc797694740a5309671b5199cecb8cf5ee) updates only `tools/client_scan_rune_smoke.py`. The existing first-view checks remain. The script now extends the test platform, teleports the real player to the opposite side of the same gold block, verifies the server-accepted position, triggers another actual Thaumometer scan, requires new particle constructor markers, captures before/during/after real Minecraft screenshots and measures the second view's **own** framebuffer difference. Comparing frames across different camera positions is explicitly forbidden because it would yield a false positive from the scene change. No changes to Java game logic, shared registry, Gradle, main or foreign feature lanes.

**CI status at journal write:** [rune client #41320127265](https://github.com/craftkot/tgaumcraft/actions/runs/41320127265) and [full NeoForge #41320127281](https://github.com/craftkot/tgaumcraft/actions/runs/41320127281) were **IN PROGRESS** on code/test SHA `df1552d`. The previous exact-head [#41315651629](https://github.com/craftkot/tgaumcraft/actions/runs/41315651629) was SUCCESS on `4d2ca63`, but did not include the new opposite-view check. Do **not** claim two-view success without inspecting the new run logs and screenshots. If the opposite-view probe fails, distinguish a blocked teleport/target, absent particles and actual invisible rendering before modifying game code.

**Audit:** historical fixed rubric **59/100**, M16 **4/6**, no additional points for test instrumentation. PR #4 remains Draft; no merge. **Next:** inspect the exact-head opposite-view run and artifacts, fix any actual failure, then resume one missing BETA26 audio/FX event with its owning gameplay path only after checking cross-PR overlap.


## 01.11.2026 — M16: original BETA26 Thaumonomicon opening-page sound

**Fresh branch/PR check:** main HEAD `a4280834`; own branch `8486a18` before this iteration. Open PR #1 (infusion), #2 (coordination), #3 (golems) do not modify any of the seven paths below. The previously pending exact-head two-sided rune client [#41320164821](https://github.com/craftkot/tgaumcraft/actions/runs/41320164821) is **SUCCESS** on `8486a18`: actual integrated Minecraft created/rendered/expired 80 runes, 40 on the opposite-side repeat, with own-framebuffer differences **1278** and **296** pixels, all 4 faces and zero horizontal drift. Full NeoForge [#41320164759](https://github.com/craftkot/tgaumcraft/actions/runs/41320164759) **SUCCESS** on the same SHA. This validates two-sided effect presence, not every glyph's precise visibility/occlusion.

**Concrete missing gameplay event:** pinned BETA26 `ItemThaumonomicon.onItemRightClick` plays `SoundsTC.page` locally at player coordinates with `PLAYERS`, volume 1.0, pitch 1.0, then opens the book. The current NeoForge `ThaumonomiconItem.use` opened the FIRSTSTEPS screen silently. BETA26 `sounds.json` defines `page` as a random selection of original `page1` and `page2` OGG clips (`category: master`). Verified against `TheDarkTower314/Thaumcraft-6-Source-Code@954022bb`, not invented vanilla sounds.

**Fix:** `ThaumonomiconItem.java` now plays the registered `ThaumcraftSounds.PAGE` exactly once on client use, before opening the screen; no server double-play or research mutations. Added `PAGE` event to `ThaumcraftSounds.java`, restored the `page` event in `sounds.json`, and imported two byte-identical original source OGGs: `page1.ogg` Git blob `a0c4f5b01b58e5720e79978637c20f425b79f2c5`, `page2.ogg` Git blob `2db59662cb448028f88c54d8df245be03d00b09e`. Original blob SHAs were compared to new destination Git blobs and **match**.

**Files:** `src/neoforge/java/dev/ehtid/thaumcraft/item/ThaumonomiconItem.java`, `src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftSounds.java`, `src/main/resources/assets/thaumcraft/sounds.json`, `src/main/resources/assets/thaumcraft/sounds/page1.ogg`, `src/main/resources/assets/thaumcraft/sounds/page2.ogg`, `tools/test_thaumonomicon_page_sound.py`, `.github/workflows/closure-book-page-sound.yml`.

**Commits:** [5ef964f](https://github.com/craftkot/tgaumcraft/commit/5ef964f373d25f0516ea4b672ab9657963294deb) functional code, resources and regression; [2d7dd59](https://github.com/craftkot/tgaumcraft/commit/2d7dd5907f47e8e9c12a3d292539319a0a591246) corrects the test's Git-blob NUL separator. [M16 page-sound CI #41324501589](https://github.com/craftkot/tgaumcraft/actions/runs/41324501589) **SUCCESS** on `2d7dd59`: actual `ThaumonomiconItem` compiled under Java 21 with client/server stubs, **11 assertions PASS**, original OGG Git SHA-1 and JSON/registry mapping **PASS**. [Full NeoForge/JAR/server #41324501600](https://github.com/craftkot/tgaumcraft/actions/runs/41324501600) and [real-client general smoke #41324482821](https://github.com/craftkot/tgaumcraft/actions/runs/41324482821) were **IN PROGRESS** when recorded. The general client smoke is on code SHA `5ef964f`; it does not specifically assert audible playback. Do not report audible-client acceptance from Java stubs alone.

**Audit:** historical fixed index **59/100**, M16 **4/6**, unchanged. One original book-opening audio event is implemented, but remaining sound catalog, other particles and all-client audio acceptance are incomplete. PR #4 stays Draft, no merge. **Next:** inspect exact code CI; if green, add a real Minecraft-client sound-event probe on Thaumonomicon right-click and compare client sound log/audio behavior, then choose the next independent BETA26 audio/FX gap without touching other lanes.

**Exact-code build follow-up:** [full NeoForge/JAR/server #41324501600](https://github.com/craftkot/tgaumcraft/actions/runs/41324501600) completed **SUCCESS** on `2d7dd59`: core parity, source-art/gate checks, real NeoForge build (`BUILD SUCCESSFUL in 1m 8s`), server reached `Done (11.319s)!`, and JAR artifact upload. [M16 page-sound #41324501589](https://github.com/craftkot/tgaumcraft/actions/runs/41324501589) **SUCCESS** on the same SHA (11 real-item Java-stub assertions plus two original OGG Git-blob checks). General real-client smoke [#41324482821](https://github.com/craftkot/tgaumcraft/actions/runs/41324482821) remained **IN PROGRESS** at this check; even if green, it has no explicit audible page-event assertion. Score remains **59/100**, M16 **4/6**.


### 01.11.2026 — page sound real-client acceptance probe
- Fresh main `a428083`, closure head `29dc72c`; checked open PR #1–4, no overlap with #1–3 for this change. Existing generic client run [#41324482821](https://github.com/craftkot/tgaumcraft/actions/runs/41324482821) **FAILURE** at `PHIAL_JAR_WITHDRAWN` in an unrelated late alchemy scenario; it cannot validate page sound. Previous [page regression #41324501589](https://github.com/craftkot/tgaumcraft/actions/runs/41324501589) and [NeoForge #41324501600](https://github.com/craftkot/tgaumcraft/actions/runs/41324501600) **SUCCESS** on `2d7dd59`.
- Added opt-in item-use markers for the actual client `playLocalSound` dispatch, the real client sound manager's loaded `thaumcraft:page` event and book GUI opening. The new isolated Xvfb/Mesa client smoke tests actual right-click, item presence, three markers, and framebuffer difference without running the unrelated full alchemy chain. Java-stub regression covers client/server separation and missing sound resource (GUI still opens; no false resolved marker).
- **Important evidence boundary:** `ALSOFT_DRIVERS=null` means the CI runner cannot establish audible/PCM output. Event dispatch + resource resolution + GUI is NOT proof that a human hears the OGG. Await exact-head CI before calling even this smoke PASS.
- Fixed audit remains **59/100**, M16 **4/6**; PR #4 Draft, no merge. Next: inspect isolated client CI, fix failures first; then confirm sound via real audio device/capture before claiming audible parity, or select next independent BETA26 FX scenario.

**Verified follow-up for code commit [df67a4b](https://github.com/craftkot/tgaumcraft/commit/df67a4b9397dd5e3bd3eefeb7a10a322d74f4988):**
- [Java 21 page regression #41330048322](https://github.com/craftkot/tgaumcraft/actions/runs/41330048322) **SUCCESS**, 21 assertions, original OGG hashes and JSON registry PASS.
- [Full NeoForge/JAR/dedicated server #41330048285](https://github.com/craftkot/tgaumcraft/actions/runs/41330048285) **SUCCESS** on the exact code SHA.
- [Isolated real Minecraft 1.21.1 client #41330048310](https://github.com/craftkot/tgaumcraft/actions/runs/41330048310) **SUCCESS**: item predicate PASS; local sound dispatch marker count=1; client sound-manager `thaumcraft:page` definition resolved count=1; GUI open marker count=1; actual framebuffer difference 83,200 pixels. [Screenshot/log artifact](https://github.com/craftkot/tgaumcraft/actions/runs/41330048310/artifacts/13092664176).
- The unrelated general client [#41324482821](https://github.com/craftkot/tgaumcraft/actions/runs/41324482821) **FAILURE** at `PHIAL_JAR_WITHDRAWN`, not at Thaumonomicon. The isolated page smoke bypasses that scenario without changing other lanes.
- This confirms actual in-game item interaction, loaded sound event and rendering; it **does not** verify audible output because the runner uses the null OpenAL backend. M16 **4/6**, overall **59/100** unchanged. Next: capture PCM on a real audio backend or proceed to next independent missing BETA26 sound/FX with proper event wiring.


## 01.11.2026 — M16: real PCM output probe for Thaumonomicon page

**Verified starting point:** main `a4280834`, closure branch `5cbc60c`. Previous exact-head page GUI and sound-manager probe [#41330391603](https://github.com/craftkot/tgaumcraft/actions/runs/41330391603), full NeoForge [#41330391534](https://github.com/craftkot/tgaumcraft/actions/runs/41330391534), and rune/label workflows all completed **SUCCESS** on `5cbc60c`. However `ALSOFT_DRIVERS=null` had prevented any PCM evidence. PR #1–3 changed paths were checked; none overlaps the three paths below.

**Engineering iteration:** [commit 653cd5a](https://github.com/craftkot/tgaumcraft/commit/653cd5a2edc9deb65d3e3322856596d2e8ec75bd) changes the *isolated* page-sound real-client smoke to use OpenAL Soft's PulseAudio backend with a virtual `module-null-sink`, instead of the null OpenAL backend. It records the sink's actual signed-16 PCM monitor before and after a real right-click on the Thaumonomicon. New `tools/check_page_audio_pcm.py` rejects incomplete captures, silence and a signal not sufficiently above the preceding baseline; the original GUI/sound-manager event checks remain. This does not alter any Java gameplay, audio assets, global client smoke, Gradle, shared registries or other lanes.

**Changed paths:** `tools/client_book_page_sound_smoke.py`, `.github/workflows/closure-book-page-client.yml`, new `tools/check_page_audio_pcm.py`. A local synthetic PCM positive and silent negative control both passed. This is a *checker unit test*, not evidence of Minecraft PCM. CI for [real PCM client #41335445653](https://github.com/craftkot/tgaumcraft/actions/runs/41335445653) and [full NeoForge #41335445641](https://github.com/craftkot/tgaumcraft/actions/runs/41335445641) started on code SHA `653cd5a`; **their final results are not yet claimed**. Additional pull-request-triggered runs may be present.

**Evidence boundary:** a non-silent PulseAudio monitor and loaded `thaumcraft:page` event will demonstrate an audio playback path, but do not alone prove the waveform is one of the original OGGs, nor human-perceived parity. If the runner cannot initialize PulseAudio or capture PCM, first fix that specific environment blocker in the next iteration. If PCM is captured but silent, investigate actual client sound playback and resource routing before adding another event.

**Audit:** fixed historical **59/100**, M16 **4/6**, unchanged. PR #4 remains Draft, no merge. **Next:** inspect exact-head page PCM workflow logs and artifacts, distinguish backend failure from silent playback, and either fix the blocker or validate a genuine audio signal; only then continue with another independent original BETA26 audio/FX event.
