# BETA26 theorycraft aid bytecode audit (2026-10-10)

Original reference: `Thaumcraft-1.12.2-6.1.BETA26.jar`, `javap -c -p` on all 14 concrete `ITheorycraftAid` registrations, plus `TileResearchTable.checkSurroundingAids` and `ResearchTableData.initialize`. Never upload original JAR.

## Exact aid pools

- AidBookshelf: CardBalance, CardNotation **twice**, CardStudy **three times**.
- AidBrainInAJar: CardDarkWhispers.
- AidGlyphedStone: CardGlyphs.
- AidPortal$AidPortalEnd / $AidPortalNether / $AidPortalCrimson: each CardPortal.
- AidBasicAlchemy: CardConcentrate, CardReactions, CardSynthesis.
- AidBasicArtifice: CardCalibrate, CardTinker, CardMindOverMatter.
- AidBasicInfusion: CardMeasure, CardChannel, CardInfuse.
- AidBasicAuromancy: CardFocus, CardAwareness, CardSpellbinding.
- AidBasicGolemancy: CardSculpting, CardScripting, CardSynergy.
- AidBasicEldritch: CardRealization, CardRevelation, **CardTruth**. The last class is present in BETA26 JAR but is **not** in the 33 normal TheorycraftManager card registrations; do not silently replace or add it.
- AidEnchantmentTable: CardEnchantment.
- AidBeacon: CardBeacon.

Original aid objects: block identity (bookshelf, brain jar, vanilla End/Nether portals, Thaumcraft crucible, arcane workbench, infusion matrix, wand workbench, golem builder, eldritch block), exact block-picked ItemStack identity (glyphed stone, enchantment table, beacon), or exact EntityCultistPortalLesser class (Crimson portal).

## Detection geometry

`TileResearchTable.checkSurroundingAids` loops dy=-1..1, dx=-4..4, dz=-4..4, and for EACH position tries original aid HashMap keys. Block aids use block equality. ItemStack aids compare real picked block item with reference stack, not the raw block only. After scanning blocks, it checks entities within range 5.0 for Class-based aid objects using **exact class identity**, not superclass inference. Each discovered aid is stored only once by key in a HashMap; world duplicates do not grant additional selected aid entries.

`ResearchTableData.initialize` subtracts *selected aid Set size* from inspiration (even unknown selected keys) and appends `getCards()` class names one by one from every recognized aid. Duplicate array entries survive. Source test now checks this in `LegacyTheorycraftAidCards` and `LegacyTheorycraftInspiration.start`; `LegacyTheorycraftAidScanner` implements the original geometry as a loader-neutral probe.

## Missing actual game integration

The pure Java scanner does NOT magically make unported aid blocks exist. NeoForge still needs the actual block-picker/entity probes, UI for choosing nearby detected aids, synchronization, and card draw/activation. The full Research Table remains incomplete until these pieces exist and client playtesting succeeds.
