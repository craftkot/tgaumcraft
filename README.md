> **Справка:** ниже частично сохранено историческое описание Stage 9 и старые счётчики. **Текущий индекс 59/100**, официальный источник — ROADMAP и аудит; [индекс документов](analysis/DOCUMENT_INDEX_RU.md).
>
> **Главный актуальный план проекта:** [🧭 ROADMAP_RU.md — дорожная карта с 🟢/🟡/🔴 статусами](ROADMAP_RU.md).
> Параллельные чаты работают только в своих ветках — [feature/alchemy](../../tree/feature/alchemy), [feature/research](../../tree/feature/research), [feature/infusion](../../tree/feature/infusion); основной план и правила: [AGENTS.md](AGENTS.md) и [порядок параллельной работы](analysis/PARALLEL_WORKFLOW_RU.md).
>
# Thaumcraft 6.1.BETA26 -> Minecraft 1.21.1 NeoForge port (Stage 9)

This workspace is a compatibility-first reconstruction from the supplied original JAR.
It is **not yet a playable release**. The project deliberately separates loader-independent TC6 semantics that can
be compiled and tested under plain Java 21 from Minecraft/NeoForge integration that still requires a real Gradle build.

## Verified BETA26 baseline

- Supplied JAR SHA-256: `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`
- 1120 class files total; 1112 under `thaumcraft/`
- 1531 asset files total; 1520 under `assets/thaumcraft/`
- 148 research entries, 271 stages, 16 addenda
- 37 built-in aspects: 6 primal + 31 compound
- EN/RU localization parity: 1464/1464 keys after filling the 12 missing Russian strings

## Loader-independent compatibility core now implemented

`src/main/java/dev/ehtid/thaumcraft/port/core` contains 44 Java files covering:

- exact 37-aspect registry and AspectList behavior;
- the seven BETA26 research categories and their exact aspect formulas;
- all 148 shipped research entries generated into a runtime catalog from the original JSON;
- player research, stages, flags, knowledge and warp state;
- exact legacy PlayerKnowledge / PlayerWarp persistence semantics in a loader-neutral codec;
- exact parent prerequisite evaluation (`getParentsStripped` + strict knowledge queries);
- research progression, siblings, addenda, rewards and the legacy warp arithmetic quirks;
- exact `ThaumcraftCapabilities.knowsResearch` / `knowsResearchStrict` expression control flow, including the old OR bug;
- Primordial Pearl rules and Pech Wand result logic;
- legacy research item/meta/NBT and recipe-ID compatibility helpers;
- loader-neutral `ScanningManager` ordered multi-handler control flow;
- `ScanGeneric` research-key construction;
- `ScanGeneric` category Observation formulas and `ScanAspect` +1 Observation side effects.

Pure Java 21 compatibility tests currently pass:

`Core parity tests: OK (4552 assertions)`

This is a real `javac --release 21` compile/test of the loader-independent core, not a textual/static-only check.

## Research data

The eight BETA26 research JSON files remain byte-for-byte preserved in:

`src/main/resources/data/thaumcraft/research/`

A generated `BuiltInResearchCatalog.java` projects all **148/148** entries into runtime-compatible data while keeping the
canonical source files untouched. The projection intentionally ignores `required_knowledgeauram` in `ARCANEEAR` because
BETA26 itself does not deserialize that misspelled field.

The research files reference 198 unique recipe IDs. 137 use names that are not legal modern ResourceLocations, mainly
because of uppercase letters. There are currently zero lowercase collisions, but the compatibility map remains explicit
rather than silently mutating source JSON.

## Player-data persistence

`LegacyPlayerDataCodec` reproduces the old NBT semantics before a NeoForge `AttachmentType` adapter is written. Important
preserved behaviors include duplicate-research handling, raw Map.put knowledge loads, ignored unknown flag names,
post-load AUTOUNLOCK processing, and raw un-clamped warp-array restoration.

See `analysis/legacy_player_data_nbt.json` for the extracted old layout.

## Scanning subsystem status

Stage 9 extends the loader-neutral control-flow core of `ScanningManager` and the research-expression helper it depends on.
The original `ConfigResearch.initScannables` bytecode contains **71 explicit scan constructor sites / 71 explicit add calls**:

- ScanEntity: 34
- ScanBlock: 18
- ScanItem: 10
- ScanOreDictionary: 4
- ScanGeneric: 1
- ScanEnchantment: 1 (inside a registry loop)
- ScanPotion: 1 (inside a registry loop)
- ScanMaterial: 1
- ScanSky: 1

This is not the final runtime handler count: the enchantment/potion sites instantiate once per registry entry and
`ScanBlock` registers companion `ScanItem` handlers in its constructor. The distinction is recorded in
`analysis/scan_registration_manifest.json` rather than hidden behind a misleading single count.

Stage 9 additionally implements bytecode-verified `ScanItem` directional tag/meta matching, `ScanSky` geometry/day-key quirks,
Thaumometer entity target geometry (1..9 blocks, min 0.8 collision expansion, independent LOS), exact 100-nonempty-slot
container recursion, and a stable legacy-ordinal/sub-order registry for preserving `ScanBlock` companion ordering. A NeoForge
`ThaumometerTargeting` projection is present but is not yet claimed as full-Gradle-compiled.

Still missing from complete scanning integration: the full 71-site runtime bootstrap, modern tag replacement for OreDictionary
rules, enchantment/effect adapters, complete `ScanSky` inventory/note side effects, GenericScan aspect extraction, FX/chat,
and final activation of the Thaumometer gameplay dispatcher.

## NeoForge workspace / build limitation

The repository has a Minecraft 1.21.1 / NeoForge / Java 21 project skeleton and keeps loader code separate from the pure
core. However this environment still lacks a working Gradle wrapper binary/dependency cache and cannot complete the
NeoForge dependency download path. Therefore **no successful full `gradlew build`, client launch or dedicated-server
launch is claimed yet**.

Do not equate the 4494-passing pure-core assertions with a successfully loaded Minecraft mod.

## Machine-readable status

- `analysis/stage9_audit.txt`
- `analysis/stage9_audit.json`
- `analysis/scan_registration_manifest.json`
- `analysis/legacy_player_data_nbt.json`
- `analysis/core_parity_test_stage9.txt`

## Stage 10 critical-path update

The physically present workspace now includes a tested loader-neutral FIRSTSTEPS vertical-slice core. `CoreParityTest` passes **4667 assertions** and `analysis/stage10_audit.txt` reports **AUDIT OK**. This includes exact Salis Mundus recipe rules, legacy crystal Aspect data, bootstrap markers, critical ScanAspect/ScanGeneric behavior, minimal persistent vis, 15-slot Arcane Workbench semantics, exact Thaumometer recipe/discount quirks/craft marker, server-side FIRSTSTEPS stage gates, and an end-to-end core regression through FIRSTSTEPS completion.

The critical item/block registrations, Crystal Essence legacy payload bridge, and two delayed Salis Mundus world transforms are now present at source level as well. This still does **not** mean the vertical slice is playable in NeoForge: the Arcane Workbench block-entity/menu, live Thaumometer scanning dispatch and remaining gameplay wiring still need to be connected, and full Gradle compilation remains unavailable in this environment.
