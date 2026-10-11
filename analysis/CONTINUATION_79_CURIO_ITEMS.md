# Continuation 79 — BETA26 Curio item, effects and original visuals

Date: 2026-10-11. Repo: `craftkot/tgaumcraft` branch `main`.

## Baseline

Fresh read `main` at `3f562762`, `analysis/HANDOFF_NEW_CHAT_RU.md` and `analysis/CONTINUATION_78_ORIGINAL_PHIAL.md` before any modifications. All latest baseline CI checks fully green: pure-Java CoreParityTest, NeoForge Gradle build and dedicated-server smoke.

## Audited BETA26 behavior

Original user-provided `Thaumcraft-1.12.2-6.1.BETA26.jar` via `javap -c -p` for `thaumcraft.common.items.curios.ItemCurio` and `thaumcraft.common.lib.research.theorycraft.CardCurio`, and original resource files:

- Seven subtype names/order = arcane (0), preserved (1), ancient (2), eldritch (3), knowledge (4), twisted (5), rites (6).
- On right-click, metadata 0–5 awards a randomized **OBSERVATION 8..16** and **THEORY 10..16** in each variant's specific category (AUROMANCY, ALCHEMY, GOLEMANCY, ELDRITCH, INFUSION, ARTIFICE). Eldritch variant 3 also adds +1 NORMAL and +5 TEMPORARY warp.
- Rites 6 only succeeds when `getActualWarp` (**NORMAL+PERMANENT; TEMPORARY EXCLUDED**) is STRICTLY GREATER than 20. It attempts research completion of `CrimsonRites`, awards ELDRITCH O/T, +1 normal +5 temporary, and 50% chance +1 permanent warp. Failed use awards NOTHING and does not consume the relic.
- After any successful relic, two separately rolled category picks (in registration order), with separate random amounts, give another OBSERVATION 8..16 and THEORY 10..16; then spend 1 Curio except creative mode. The exact *order and number* of RNG calls was preserved.
- Original ItemCurio rarity UNCOMMON; translated item names and tooltip are taken from original en_us/ru_ru language resources.
- CardCurio.initialize scans only the 36-slot main player inventory, makes a one-item copy for each available Curio stack, seeded random chooses **one stack**, then saves its original 1.12 `{id,Count,Damage}` representation nested under `cardNBT.stack`; no Curio is consumed by initialization.

## New implementation and tests

- `LegacyCurioUse` (`609ed862`) implements original core knowledge/warp formula and denial rules with injected RNG; `CoreParityTest` (`22479663`) checks all variants, exact deterministic RNG order, two extra independently selected categories, warp effects, exact >20 Rites threshold and no RNG/state changes on denial.
- `CurioItem` (`de5f486f`) + registry (`61bc8ba1`, rarity `6757988e`) creates real modern `thaumcraft:curio` with all seven variant-specific CustomData/CustomModelData values, original names/tooltip and server-only player item-use effects; attempts `CrimsonRites` via the existing ported `ResearchProgressionService` subject to actual research requisites, and syncs PlayerKnowledge/Warp.
- **Exact original seven Curio sprites including Crimson Rites** were copied from the user-provided original JAR to binary Git blobs in commit `3953477e`. Their Git blob SHAs match independent local SHA-1 checks of the ORIGINAL bytes. Added 1.21 seven-variant item models (metadata 0 default plus model predicate 1..6) without inventing textures.
- Original English/Russian translations restored (`6bb43de5`, `f5a263b7`). `NeoForgeLegacyItemStackDecoder` (`676754e0`) now safely converts the *exact* original Curio `Damage=0..6` into the registered item + modern data components, so CardCurio material identity can persist. `NeoForgeTheorycraftItemTransaction` (`e9349d50`) now compares Curio materials by original item+metadata, not a cosmetic model-data difference.
- `NeoForgeTheorycraftCurioInitializer` (`f9bcc767`) selects real main inventory Curios with exact source RNG and writes genuine CardCurio `cardNBT.seed`/legacy `stack` NBT for future full draw. It is **not called by ResearchTableMenu yet**: full 33-class draw/activate runtime still incomplete.
- `.github/workflows/ci.yml` (`64d3ffef`) validates all seven exact PNG files, model predicate ordering, and English/Russian text keys.

## Work remaining / don't overclaim

Curio loot sources and original LEARN sound asset/event are **not** yet ported. Completion of the CrimsonRites research depends on the original parent/requisite state; this integration has not been manually client-playtested. Importantly, **research table card draw (buttons 2/3) and activation (4–6) are STILL disabled**, pending complete 33-card initializer/transaction integration. Current focus should prioritize making existing original cards actually selectable, not inventing substitute cards/ingredients. No fake success claim.

The user's optional random *uncollected* moon-note behavior remains deferred until final polish, preserving original phase-dependent sky scanning for now. Existing Russian/font enhancements preserved.

**Whole-mod progress estimate: 79% pending full green GitHub Actions for latest main**. Do not describe as percentage of gameplay independently verified.
