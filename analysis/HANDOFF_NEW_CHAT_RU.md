# HANDOFF_NEW_CHAT_RU — Thaumcraft 6.1.BETA26 → Minecraft 1.21.1 NeoForge

## Главное
Продолжать существующий порт, НЕ начинать заново и НЕ упрощать механику ради скорости.
Оригинальный Thaumcraft 6.1.BETA26 для Minecraft 1.12.2 — главный эталон поведения: bytecode/resources/quirks.

## Primary / backup
- PRIMARY: private GitHub repository `craftkot/tgaumcraft`, branch `main`.
- Primary working tip: **always fetch `main` live**; don't treat the checkpoint SHA below as HEAD.
- Last verified gameplay CI checkpoint: `104c1ac472052ceedb423f3e620c8f58bd3bdacc` (SUCCESS: 6,267 core parity assertions, NeoForge build, dedicated-server smoke, uploaded JAR artifact). More recent commits may still be in CI.
- Confirmed estimated overall progress: **66%** after porting exact effects/initialization for **22 of 33** registered BETA26 theory cards, all **14 original aid class registrations**, and BETA26 CardAnalyze and CardScripting bug fixes. THE FULL CARD-DRAW/SELECT GAME, actual aid objects, and item checks/rewards for some cards ARE NOT YET CONNECTED TO THE PLAYABLE GUI. No actual client playthrough verified.
- Newer commits may exist beyond that checkpoint; inspect their CI outcomes before claiming success. The GitHub HEAD can advance during user conversations.
- ChatGPT Library `/ThaumcraftPort` remains durable fallback/full reference.
- Original JAR MUST NOT be committed to GitHub.
- Original JAR SHA-256: `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.

## User workflow/style
- User often sends only `п` or `продолжай`: this means DO ACTUAL PORT WORK NOW.
- For normal continuation responses, user now allows **a short plain-Russian mini-summary plus final overall percentage**. Do real multi-part changes per `п`, not 1 trivial edit.
- Do not ask clarifying questions for routine port work.
- Prefer small coherent commits, then CI, then next block.
- Avoid long polling loops that make the chat UI time out. Start CI, check enough to diagnose failures, but do not sit in an endless status loop.
- Do not claim a build/launch is successful unless GitHub Actions really proves it.

## Automation
An hourly autonomous task exists for this port. It should keep GitHub `main` as primary code history and Library as fallback/reference.
Never lower the confirmed baseline below 66%.
Each autonomous run should either:
1. make at least one coherent gameplay/compatibility commit and bring CI green, or
2. identify a concrete reproducible non-transient blocker.
Do not spend a whole run only rereading state.

## Already implemented / verified major slices
### Loader-independent/core
- exact 37 aspects
- research catalog / stages / addenda
- research progression quirks
- player knowledge + warp persistence quirks
- scan ordered-flow core
- FIRSTSTEPS markers/progression
- exact RecipeMagicDust/Salis logic core
- Crystal Essence legacy `Aspects` NBT shape
- minimal aura runtime
- 15-slot Arcane Workbench runtime
- exact Thaumometer recipe transaction/discount quirks
- core parity test suite

### NeoForge / gameplay wiring
- player attachments + sync
- Crystal Essence, Salis Mundus, Thaumonomicon, Thaumometer items
- Arcane Workbench block, BlockEntity, menu/screen
- Salis 50-tick transform queue
- modern RecipeMagicDust/serializer
- persistent per-chunk aura SavedData bridge
- Arcane Workbench vis sync / cost preview
- exact >100% discount / negative vis-cost quirk (must not add aura back)
- original BETA26 Arcane Workbench GUI texture
- BETA26 crystal requirement swirls
- aura generation arithmetic
- biome aura modifier table incl. DRY overwrite
- Forge 1.12 vanilla biome type compatibility table
- exact vis discount arithmetic
- BETA26 aura drain/simulation semantics

### Thaumometer client path
- scan sound restored
- 20-tick aura data packet/cache
- original BETA26 aura HUD
- original 16-rune scan burst textures/effect
- idle target highlight cadence/selection:
  - every 5 client ticks while active
  - entity 1..16 trace, grow=5
  - `isThingStillScannable`
  - separate wild 16-block random block ray
  - legacy AABB face sampling/count/color geometry
- Note: idle sparkle currently uses a modern dust projection for part of FXGeneric parity; exact animated FXGeneric sprite remains a possible fidelity improvement.

### Scanning compatibility already wired
- block pick-stack resolution
- water/lava bucket fallback
- scan feedback messages
- recursive UP-side item-handler scanning
- exact 100 non-empty-slot limit + `tc.invtoolarge`
- vanilla special scans ported
- projectile scan registrations
- vanilla golem/spider/bat/fly scan registrations
- BETA26 item metadata mapping for dyes, terracotta, logs/leaves, planks/saplings, bed colors and older stone variants.
- modern common item-tag bridge for BETA26 iron, brass, thaumium and void material scans.
- vanilla and modded ore-dictionary AspectList values extracted from ConfigAspects bytecode; exact ore quantities in `LegacyOreAspectTable`.
- original BETA26 custom NBT `Aspects:[{key,amount}]` for crystal essence scanned as actual contained aspects, with duplicate merge, nonpositive cleanup and 500 cap.
- original equipment bonus aspect handling for 1.12 armor, bows, swords and tools (`LegacyEquipmentAspectBonuses`).
- original dye ore dictionary SENSES bonus projected to the 16 dye tags (`LegacyDyeAspectBonuses`).

## 2026-10-09 continuation
- Verified gameplay commit `79782b0a`: CI success for core parity, NeoForge and dedicated-server smoke, includes NBT-contained crystal essence scanning and vanilla equipment bonus aspects.
- Subsequent dye bonuses: `32785ea3`, `ca18f963`, `4cd8535c`, `b72b3301`. Verify latest CI before describing them as passed; no gameplay client smoke yet.
- Exact JAR is available in user Library `/ThaumcraftPort/original_BETA26.jar` and can be materialized for `javap -c -p` analysis. JAR must **never** be checked into GitHub.
- Bytecode-verified sources: `thaumcraft.common.config.ConfigAspects.registerItemAspects`, `ThaumcraftCraftingManager.getBonusTags`, `ItemGenericEssentiaContainer.getAspects`. Read those to ground further porting.
- Focus next on fixing latest CI if needed, enchantment aspect effects and full ScanPotion/ScanEnchantment registration parity; avoid fake parity or invented item ids.
- No evidence of a real interactive client playthrough yet; server smoke does not mean full mod content is complete.

## 2026-10-09 later continuation — ScanEnchantment and ScanPotion

Original JAR bytecode verified:
- `ConfigResearch.initScannables` registers `ScanGeneric`, then loops all enchantments (`ScanEnchantment`), then potion effect registry (`ScanPotion`), then special entity/block/item scanners.
- `ScanEnchantment.checkThing` reads an ItemStack via ScanningManager.getItemFromParms and compares against enchanted registry entries; key `"!" + Enchantment.getName()` (1.12 `enchantment.*`).
- `ScanPotion.checkThing` checks active effects on EntityLivingBase OR potion effects from the item's PotionUtils list; key `"!" + Potion.getName()` (1.12 `effect.*`).
- `LegacyEnchantmentEffectScans.java` maps **30 vanilla 1.12 enchantments + 27 vanilla 1.12 effects** to their precise historical translation-key research IDs, excluding modern-only effects/enchantments.
- `CriticalScanHandlers.java` now creates **111** loader-independent handlers (37 ScanAspect + Generic + 57 enchant/potion + 16 vanilla/material special markers).
- `ThaumometerScanDispatcher.java` now extracts modern `ENCHANTMENTS` and `STORED_ENCHANTMENTS` component holder IDs (including enchanted books), modern `POTION_CONTENTS` from potion items and active MobEffectInstance IDs from LivingEntity, then projects them into BETA26 keys.
- First NeoForge build on `0fc94aac` failed because `EnchantmentHelper.getEnchantments(ItemStack)` no longer exists. Corrected in `6e8817c69f` to use `DataComponents.ENCHANTMENTS`. **Confirmed CI SUCCESS on `6e8817c69f`: 5,566 core parity assertions, NeoForge build, dedicated server reaching Done state, built JAR artifact upload.**
- `CoreParityTest` includes all 57 key mappings, unknown-modern exclusion, multiple simultaneous enchants/effects, once-only research progression.

User future gameplay change, **explicitly defer until the main mod port is complete**:
- Original celestial scans depend on actual sun/moon/moon-phase/star conditions; user finds waiting for all moon phases excessively slow.
- Near FINAL polishing, offer/configure an alternative in which successful moon scans give a random UNCOLLECTED moon-phase research note regardless of actual moon phase; no duplicates and keep reasonable per-day scan limit. Preserve original BETA26 mode as a toggle.
- **Do not alter ScanSky behavior during present porting stages.**

## 2026-10-09 enchantment bonuses, aspect culling and player scans

Confirmed gameplay-compliant 61% checkpoint: `941d2cbd` (5,757 assertions, Gradle NeoForge build and dedicated server smoke SUCCESS).
- `LegacyEnchantmentAspectBonuses.java`: reconstructs exact BETA26 `ThaumcraftCraftingManager.getBonusTags` enchantment contributions from original JAR bytecode: 27 enchantment-specific aspect assignments; 30 vanilla enchantment historical rarities; `(short)(lvl*3)` arithmetic; uncommon/rare/very rare extra +2/+4/+6 Praecantatio, and `AspectList.merge` (MAX).
- `ThaumometerScanDispatcher.itemEnchantmentBonuses`: reads `ENCHANTMENTS` or enchanted-book `STORED_ENCHANTMENTS` modern data components, includes modded fallback, then projects BETA26 aspect bonuses. Integration parity test combines iron sword + sharpness/fire aspect and ensures six independent scan successes.
- `LegacyObjectAspectFinalizer.java`: exact `AspectHelper.cullTags(7)` weighted pruning: primal x0.9, compound nested x1.1/x1.05; ties remove earliest insertion. Then `capAspects(500)` quantity truncation. Applied to scanned vanilla/modded items and crystal-contained aspect lists. Protects against pathological never-progressing culling when all weighted scores >=32767 rather than hanging a server.
- `LegacyPlayerEntityAspectTable.java`: exact `AspectHelper.getEntityAspects(EntityPlayer)` 1.12 rule: base Humanus 4 plus THREE randomly selected aspects worth 15 each, seeded by `new Random(playerName.hashCode())` over aspect registration order. Wired to NeoForge scanning for player entities.
- Pending after verified checkpoint: generic key for unregistered 1.12 EntityPlayer is `!null` (legacy EntityList did not register player), rather than synthetic modern `!minecraft:player`. Check CI for commits `fe6361ed` and `191dde67` before claiming full green.
- **User-requested future feature, NOT yet implemented:** at the END of port, optional random *uncollected* moon phase paper after sky scan, rather than waiting actual moon phases, keeping original behavior as setting. Do not implement this before core port is done.

## 2026-10-09 BETA26 celestial scanning and original artwork
- Pure Java `LegacyCelestialScanService` reproduces `ScanSky`'s day-key prefixes (`CEL_Day_`), aligned Sun vs 8 **real moon phases**, night-directional 4 star quadrants, per-day duplicate checks, paper + scribing-tool requirements, and old-day research removal quirks. See `testLegacyCelestialScanService`.
- NeoForge `ThaumometerScanDispatcher.scanSky` now handles true unobstructed null targets in the overworld after `CELESTIALSCANNING`, returns Sun/Star/Moon notes with metadata 0..12, consumes one paper, does NOT consume or damage `scribing_tools`, and synchronizes knowledge.
- `CelestialNotesItem` preserves the original 13 metadata variants via `CUSTOM_DATA` and `CUSTOM_MODEL_DATA`. Original 15 PNG textures (13 overlays + sheet + scribing_tools) extracted from **user-provided BETA26 JAR** and copied unchanged to `assets/thaumcraft/textures/item`. They are included in GitHub, NOT the original JAR.
- 14 modern item model JSONs recreate all original layered Sun/Moon/Star drawings, plus original EN/RU strings, aqua tooltips, and `tc.celestial.fail.*` feedback. `scribing_tools` original 100 durability and glass-bottle/feather/black-dye recipe and refill are registered; original phial variant remains for future when phial item is ported.
- Gameplay commit `805257662a8c15f490eeb974618c77b8fb405784` **CI SUCCESS**: 5,823 core assertions, NeoForge build, dedicated server smoke and jar upload.
- The newer `.github/workflows/ci.yml` also validates all artwork models/languages/recipes; run `f42d138c` initially failed solely on a CI path prefix mistake; fixed at `1e2da266`; check fresh CI.
- **Important: User wants randomized uncollected moon-phase papers only AT THE END of the full port.** Original physical moon phase dependence must REMAIN active now; random variant as optional config may be developed at final polishing stage.

## 2026-10-09 theorycraft continuation — now 63% overall (core, NOT a finished table)
- Original JAR bytecode thoroughly consulted: `thaumcraft.common.lib.research.theorycraft.CardCelestial`, `thaumcraft.api.research.theorycraft.ResearchTableData`, and `thaumcraft.common.tiles.crafting.TileResearchTable.finishTheory`.
- `LegacyCelestialTheoryCard.java`: initializes from a seeded `java.util.Random` choosing distinct metadata 0..12; selects the highest TreeMap category total (lexicographic tie ordering); requires TWO separate original note item variants; activation gains 25..50 theory total, plus special effects: star note yields +0..10 ELDRITCH (twice a 0..5 random roll), 0..5 TEMPORARY warp; Sun note increments penaltyStart; Moon note increments bonusDraws. Correct card inspiration cost 1 is separate from activation.
- `LegacyResearchTableData.java`: original `ResearchTableData.addTotal` map removal at <=0; upper-clamped `addInspiration`, `isComplete`, permanent session counters; `TileResearchTable.finishTheory` category-sorted rewards with THEORY progression=32 and exact rank/penaltyStart reduction. Added `Snapshot` and restore for original scalar/total fields. Does NOT yet model all original research cards and server block entity.
- `LegacyTheorycraftInspiration.java`: BETA26 getAvailableInspiration: base 5f, +0.5 for strictly COMPLETED SPIKY, +0.1 for strictly COMPLETED HIDDEN, cap min(15, round), chosen aid SET size subtracted without lower clamp. Category availability respects strictly completed research keys and blocked categories.
- `CelestialTheorycraftInventoryBridge.java`: compile-verified server helper checks both matching note variants in player's main inventory, consumes one each, applies original theory+warp effects and one inspiration cost, syncs player data. NOT CALLED BY A REAL RESEARCH-TABLE UI YET. Do not claim the complete in-game table works.
- `LegacyResearchTableNbt.java` writes/reads original BETA26 scalar session fields and categoryTotals list to/from NeoForge NBT, awaiting integration in full table BlockEntity.
- CI SUCCESS at `c05bad33`: 5,910 assertions, NeoForge build and dedicated server startup. Later commits (`31d2664e`, `9bc706f5`, `bd57a67b`, `c3462346`) extend NBT and category gating; verify CI for latest HEAD before claiming them fully green.
- NEXT PRIORITY: full faithful in-game Research Table block + BlockEntity, inventory, menu, original GUI art and packets; then connect CelestialTheorycraftInventoryBridge to authentic card-choice actions. Do not invent placeholder mechanics. Add more original theory cards and end-to-end test.
- **Deferred user feature stays deferred:** after all original gameplay content is ported, add optional *random uncollected moon note* from scanning Moon independent of actual phase, preserving original phase mode.

## 2026-10-09 research-table in-world gameplay slice (64%)
- Original JAR direct-bytecode investigation: `thaumcraft.common.blocks.basic.BlockTable` upgrades `tableWood` when right-clicked with `IScribeTools`, sets the resulting `researchTable` block FACING to the player's horizontal facing, MOVES the WHOLE held tool stack into TileResearchTable slot 0 and empties hand; it does NOT consume ink. `ItemScribingTools` vanilla BETA26 max damage is 100.
- `TableWoodBlock`, `ScribingToolsItem.useOn` and `ResearchTableBlock` implement that original workflow. `table_wood` original `ConfigRecipes` shaped recipe uses pattern `SSS` / `W W`, tags wooden slabs and planks. 1.21 recipe `category` MUST equal `building`, not `decorations` (fixed in `01cafe90`).
- `ResearchTableBlockEntity`: original TWO inventory slots 0=IScribeTools and 1=paper, full Container persistence, `note` compound NBT for the current `LegacyResearchTableData` state. Server-only `startNewTheory` and `finishTheory` functions exist but are NOT exposed as GUI buttons before full original card-choice logic is implemented. Ink increments scribe damage, paper consumes exactly one.
- `ResearchTableMenu`: original `ContainerResearchTable` two slot positions (16,15) and (224,16); player main 3x9 on RIGHT at x=77 y=190 and hotbar as 3x3 on LEFT at x=20 y=190. Correct server-validated shift-click routing and 3 menu data slots for session/inspiration.
- `ResearchTableScreen`: original 256x256 GUI texture and synced research state; no fake choice buttons. `ThaumcraftClientEvents` registers screen; all registrations `ThaumcraftBlocks`, `ThaumcraftItems`, `ThaumcraftBlockEntities`, `ThaumcraftMenus` are wired. Original facing ResearchTable and wooden 3D models translated from Forge 1.12 JSON to vanilla 1.21; textures copied byte-identically from BETA26 JAR, including original GUI, wooden tops and sides.
- Original wooden research table art SHA256, blockstates, item models, drops (1.21 `loot_table`), recipe and translations verified in CI `.github/workflows/ci.yml`.
- **VERIFIED CI GREEN `00afbf64de3`**: 5,924 core assertions, original texture/checksum/integrity test PASS, NeoForge build PASS, dedicated server reached Done, built jars uploaded. Additional latest `44d28b5` with valid 1.21 recipe category assertion may still be pending; check.
- **NOT YET FINISHED**: ResearchTableData.savedCards/cardChoices/lastDraw original state machine, drawCards selection, adjacent aid detection, GUI buttons and full end-to-end client interaction tests. The physical two-slot table works as a gameplay framework, but real theory cards cannot yet be selected through its screen. NEXT implement this honestly without fake random card list or losing persisted choices.
- **User optional Moon random-notes remains DEFERRED until all original content is finished.** Do not introduce now.

## 2026-10-09 late theory-card runtime (65% checkpoint)
- LIVE ORIGINAL JAR bytecode audit: `ResearchTableData.serialize/deserialize/serializeCardChoice/drawCards`, `CardCelestial.serialize`, `CardPonder.activate`, `CardRethink.activate`, `ContainerResearchTable.enchantItem`, `GuiResearchTable`. Local reference JAR is in Library and/or materialized at `/mnt/data/thaumcraft_reference/original_BETA26.jar` when available; never commit JAR.
- `LegacyResearchTableData` now preserves BETA26 `player`, `savedCards` long seeds, `aidCards` class names, `categoriesBlocked` in LIST ORDER with duplicates; `Snapshot` is immutable, tested and backward-compatible with seven-field old constructor. The loader NBT writer uses exact compound-list tags `savedCards:[{card:long}]`, `aidCards:[{aidCard:string}]`, `categoriesBlocked:[{category:string}]`.
- **Critical lossless fix**: `LegacyResearchTableNbt.write(data, originalSession)` keeps unknown original `cardChoices`, `lastDraw` and their nested subclass `cardNBT` compound unchanged through save/reload; `ResearchTableBlockEntity.originalSessionNbt` retains raw copy across autosaves and resets on finish/abandon. Previously original card data could be silently discarded.
- `LegacyCelestialChoiceNbt` handles exact `thaumcraft.common.lib.research.theorycraft.CardCelestial` card choice `cardChoice,aid,select,cardNBT:{seed,md1,md2,cat}`, declines other card classes without destroying their original raw NBT.
- `LegacyTheorycraftDrawEngine`: exact vanilla drawCards semantics: requested THREE becomes TWO unless bonusDraws>0 (then consumes one); 25% <= float roll per attempt for aid card; accepted aid key removed, failed aid stays; retry 10000 times, no duplicates across aid/normal, normal key order provided unchanged from original HashMap iteration, rejects aidOnly/cost/category-blocked/uncategorized per bytecode. Works via callback so unported card classes are NOT faked. `CoreParityTest` validates rich deterministic draw/probability/duplicates/cost/bonus edge cases.
- `LegacyBasicTheoryCards`: actual original `CardPonder` (cost 2, distribute 25 over unblocked TreeMap totals, +5 BASICS, +1 bonusDraw; preserve unusual boolean return) and `CardRethink` (cost -1, initialize on sum>=10, reduce up to ten points in iteration order, +1 bonusDraw, +1..10 BASICS). Verified 1.12 bytecode and tested several edge cases.
- `ResearchTableBlockEntity.canStartNewTheory`: requires real charged IScribeTools and paper, does NOT consume either on creating the session. `ResearchTableMenu.clickMenuButton` restores IDs **1 create with empty aids**, **7 complete**, **9 abandon** and safely rejects **2/3 draw + 4/5/6 choose** until full card registry/aid UI is ready.
- `ResearchTableScreen`: original 1.12 button positions: Create (128,22), Complete (191,96), Scrap (128,168); BETA26 `gui_base.png` texture from original JAR, button region (37,66), matching 49x11 size and original RU/EN text. Clicking invokes real server menu buttons. The `Create` path currently allows an explicitly ZERO-selected-aids start; surrounding aid detection and selection UI remain for later. DO NOT CLAIM THE FULL THEORYCRAFT GAME IS FINISHED.
- **GREEN gameplay CI `4a9d9f65`**: 6,021 tests, NeoForge build successful, dedicated server reached Done, artifact uploaded. Later EN/RU strings, original GUI button SHA256 integrity CI run may be pending, check current HEAD.
- Failures encountered and FIXED: `ResearchTableMenu` missing `ServerPlayer` import (`eb9cecd3`), 1.21 `GuiGraphics.drawString` translated Component + boolean overload (`b623aae4`). Earlier `LegacyTheorycraftDrawEngine` NPE calling `List.of().contains(null)` fixed (`68e0cedd`).
- NEXT: Port genuine TheorycraftManager card registry and individual card subclass NBT/state; wire drawCards (paper consumption) and card selection (original required items/inspiration/seed) through server packets/menu, then real clickable client previews and aid scanning. Avoid stand-in cards.
- USER FINAL-POLISH REQUEST still deferred: optional random *uncollected* moon-phase sky notes. Do not implement early.

## 2026-10-10 quality/readability improvements (still 65% overall)

User explicitly approved minor bug fixes and more legible GUIs, provided the mod's mechanics/balance/content remain original. **Do not change the original font resource for the whole game**: instead, use the player's active Minecraft language font at natural 1.0 scale and a shadow/contrast panel.

- New `src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftReadableText.java`: full-size Minecraft glyphs including Russian, shadow and dark readable panels, wrapped text and full-width button labels.
- Improved `ResearchTableScreen`: 132x18 readable dark-green BETA26-themed buttons, full-size titles, wrapped instruction text, button hover tooltip (even when another language is long), original GUI art unchanged. Buttons remain the only implemented original Create/Complete/Scrap actions.
- Improved `ArcaneWorkbenchScreen`: cost/available Vis labels no longer use 0.5 scaling; shows full-size color-coded text with backplate. `ThaumometerHudRenderer`: Shift Vis/Flux numbers now full-size with shadows/backplate. No changes to aspect costs/aura math.
- `LegacyObjectAspectFinalizer`: fixed genuine original BETA26 `AspectHelper.cullTags` potential infinite loop for >7 aspects whose weighted amounts all >=32767. Replace initial 32767f sentinel with `Float.POSITIVE_INFINITY`, preserve selection for normal input. Added regression test with 8 types at 50000.
- `LegacyTheorycraftDrawEngine`: original 10,000 futile retries for an entirely empty normal-card+aid pool now fast-exit 0 attempts, unchanged empty result and unadvanced RNG. Regression in `CoreParityTest`.
- `LegacyBasicTheoryCards.ponder`: prevent futile 1000 loops and erroneous reward when ALL categories are blocked (no valid target). Normal unblocked category mechanics are unchanged.
- `ResearchTableBlockEntity.finishTheory`: now verifies player is still nearby and table is live before granting rewards, preventing remote/stale completion through future API paths.
- Detailed change log: `analysis/PORT_QUALITY_IMPROVEMENTS_RU.md`.
- Original BETA26 celestial Moon phase mechanic remains untouched until the requested *final* optional random uncollected moon-note mode. No premature randomization.
- **Always check CI for newest head**. Last known readabilty-only slice `9bea11eb5f8` GREEN NeoForge+server; bug-fix + tests and docs latest are running through Actions. Do NOT claim an interactive Minecraft GUI screenshot/playthrough has been validated by server smoke.
- **Overall completion estimate REMAINS 65%** until next substantial gameplay system is ported. Next step full Research Table card UI and aid detection, no placeholder mechanics.

## 2026-10-10 substantial theorycraft core (66% milestone)
- Source-of-truth original JAR available in this runtime at `/mnt/data/thaumcraft_reference/original_BETA26.jar` (11.3MB) for local `javap -classpath ... -c -p`. Never commit original JAR. Its original SHA256 is stated in the header.
- `LegacyTheorycraftManifest`: exactly **33** `TheorycraftCard` class names and **14** `ITheorycraftAid` names extracted in registration order from `ConfigResearch.initTheorycraft`. Provides original Java HashMap keySet iteration order for exact seeded selections. Also enumerates **22 completed core effect/initialization models and 11 not yet implemented**.
- `LegacyAdditionalTheoryCards`: exact `CardBalance` category averaging incl direct TreeMap.put, `CardNotation` weakest/strongest seeded transfer, aid-only `CardStudy` seeded category/+15..25, `CardExperimentation` all registered categories/+15..30 and BASICS +1..10. `LegacyResearchTableData` now supports direct averages and penalty counters.
- `LegacyTheorycraftMiscCards`: original `CardInspired` strongest category/+10+floor(max/2), `CardReject` seeded blocked category/+5 BASICS, `CardMeasure` +15 INFUSION/+1 draw, `CardSculpting` +20 GOLEMANCY/+1 draw (original ingredient check still must be wired), `CardAwareness` +20 AUROMANCY and 33% +1..5 ELDRITCH/+1 NORMAL warp.
- `LegacyCardAnalyze` fixes provable original bug: BETA26 `CardAnalyze.initialize` loops research categories but asks knowledge for `card.cat` (null) rather than iterated category, often failing to select any. Port checks current category; EXACT activation cost 2, consumes 16 raw OBSERVATION, +5 BASICS, +25..50 matching category.
- `LegacySpecialtyTheoryCards`: original `CardCalibrate` +15 ARTIFICE/+1 bonusDraw, `CardFocus` +15 AUROMANCY/+1, `CardGlyphs` two 10..20 theory rolls across random category/ELDRITCH and +5 TEMPORARY warp, `CardSynergy` remove 15 points in ARTIFICE/ALCHEMY/INFUSION rounds and grant +30 GOLEMANCY/+1 penalty, `CardScripting` +25 GOLEMANCY ONLY after material consumption.
- `ResearchTableBlockEntity.consumePaperAndInkFromTable` ensures **atomic** consumption of both paper and charged writing tools; original `CardScripting.activate` checked only `!= null` and ignored BOTH consume boolean results, which could grant theory without paper in a 1.21 ItemStack.EMPTY inventory.
- `LegacyAlchemyTheoryCards`: `CardConcentrate` single seeded compound-crystal +15 ALCHEMY/+1 draw/33% inspiration refund, `CardChannel` single seeded filled phial +25 INFUSION, `CardReactions` TWO distinct seeded compound crystals +25 ALCHEMY/33% inspiration refund, `CardSynthesis` seeded compound with TWO specific component crystals consumed to produce compound crystal plus +40 ALCHEMY/33% inspiration refund. Their material bridge/item output **not yet wired to GUI**; do not claim accessible gameplay.
- End-to-end core parity coverage in `CoreParityTest` for 22 cards, seeds, category ordering, item signatures, warp and inspirations. Latest VERIFIED GREEN run `104c1ac` confirms 6,267 assertions and NeoForge server smoke; newer source/doc commits may be checking.
- Missing original cards: `CardCurio`, `CardEnchantment`, `CardBeacon`, `CardMindOverMatter`, `CardTinker`, `CardInfuse`, `CardSpellbinding`, `CardDarkWhispers`, `CardPortal`, `CardRevelation`, `CardRealization`.
- Detailed card-by-card status and original item constraints: `analysis/THEORYCRAFT_CARD_PROGRESS_RU.md`.
- HIGH PRIORITY NEXT: complete 11 missing original cards and helper blocks; implement lossless card subclass NBT, server validation/material transfer, real draw/select buttons 2..6 and client card-choice UI. **Never sample only known card subset as a stand-in**: original draw distribution would differ. Keep the sky-note randomization until the final OPTIONAL quality feature requested by user.

## Recent green commits
- `df966330` — restore vanilla golem/spider/bat/fly scans
- `c5eccbfe` — restore BETA26 projectile scan registrations
- `123c465e` — port BETA26 vanilla special scan registrations
- `8fcc7db3` — restore idle scannable target highlight
- `9cfca3cc` — restore BETA26 container recursion limit

## Next priorities
Continue from current GitHub head; fresh-read it before editing.
Recommended next order:
1. Continue exact BETA26 scan registration parity and legacy item/block/entity identity+metadata mappings.
2. Expand object/entity aspect tables from original bytecode/resources only (no guesses).
3. Finish exact idle highlight FXGeneric animated sparkle if it is still not exact.
4. Add/expand runtime FIRSTSTEPS end-to-end automation and manual-playtest readiness.
5. Move to the next critical gameplay slice only after this path is coherent.
Do NOT wander into worldgen/mobs/golems/infusion/decorative systems unless required by the critical path.

## Validation
For every meaningful code commit:
- keep core parity passing
- run GitHub Actions `Thaumcraft Port CI`
- require NeoForge build success
- require dedicated-server smoke success for runtime-ready claims
- inspect failed job logs and fix API/compile errors rather than reverting progress blindly

## Persistence rules
- GitHub main = primary working history.
- Library `/ThaumcraftPort` = fallback/full reference and original JAR source.
- Do not rebuild a giant Library checkpoint after every small GitHub commit.
- Update Library on milestones/risky migrations.
- Never reduce progress because an intermediate commit is red if the last confirmed green milestone is intact.

## New-chat start instruction
On opening a new chat, say:
`Продолжай Thaumcraft по analysis/HANDOFF_NEW_CHAT_RU.md из craftkot/tgaumcraft. Сначала fresh-read main и handoff, затем сразу продолжай работу. Не начинай заново. Пиши краткую сводку на простом русском и итоговый процент.`

## 2026-10-10 next continuation — seven missing card cores (estimated 67% after CI)
- Fresh-read GitHub main from 849da731; original JAR materialized from user Library and CardEnchantment/CardBeacon/CardSpellbinding/CardDarkWhispers/CardPortal/CardRevelation/CardRealization bytecode audited with javap -c -p.
- Added `LegacyLateTheoryCards.java` with exact 1.12 inspirations (including beacon -2 and portal -1), aid-only metadata, enchant/spellbinding XP gating and math, DarkWhispers full per-category RNG and XP-derived ELDRITCH/Warp, portal/revelation/realization ordered random draws, bonus draw and penalty effects.
- XP-based functions are intentionally named After...Spent/After...Debited: pure-Java core effects only. **Server XP removal is NOT yet wired to real GUI**. Do not treat XP effects as playable.
- `LegacyTheorycraftManifest.portedCoreCards()` now tracks **29/33**, with exactly 4 remaining: CardCurio, CardMindOverMatter, CardTinker, CardInfuse. Original CARD_REGISTRATION_ORDER remains exactly 33 in unchanged order; an intermediate erroneous 36-registration commit was corrected at `e8d94bd8`.
- `CoreParityTest` now tests 7 cards and original Java RNG order, warp, XP math, empty categories, and pool counts. The manifest/test/doc files were updated through commits `9a0ed729` → `e8d94bd8`, documentation `cf2789d0`. **Check latest GitHub Actions outcomes before claiming green**. Code-only first commit `9a0ed729` CI passed; `e8d94bd8` was progressing through server smoke at last read.
- Next: finish four remaining bytecode/NBT/material-card cores, then wire original table selection/draw buttons with all cards (never substitute partial cards) and 14 real aids. Client manual verification still missing.
- User requested percent only on this turn, but do not confuse 29/33 cards with 67% complete interactive mod; full port completion estimate conservative 67% subject to green CI.

## 2026-10-10 continuation: original 33/33 theory-card core formulas (estimated 68% overall)

- Fresh-read GitHub `main` (`d6516a88`) and this handoff, checked previous green CI. Direct `javap -c -p` audit of the actual BETA26 JAR for `CardCurio`, `CardMindOverMatter`, `CardTinker`, `CardInfuse`, `ItemCurio` and `TheorycraftCard.getRequiredItemsConsumed`.
- New `LegacyMaterialTheoryCards.java` (`cbb1ccb3`): seven original curio variants and seeded player inventory slot selection (duplicate curio slots preserved); exact extra theory by variant, two separate bonus-draw rolls; seeded 18-item artifice options, seeded 31-item INFUSION option after compound-aspect choice; exact visSize square-root arithmetic and original fallback values; nonconsumed Tinker requirement vs consumed Curio/Mind/Infuse materials.
- `LegacyTheorycraftManifest.portedCoreCards()` now includes all **33/33 original class names** (`017b5511`). Source registration order remains exactly 33; card effects + important initialization only, NOT complete in-game card classes or playable Research Table.
- `CoreParityTest` enhanced with original seed/RNG draws, curio variants, normal/invalid preconditions, item signatures and all 33 manifest membership (`bc0aac28`). Documentation updated in `analysis/THEORYCRAFT_CARD_PROGRESS_RU.md` (`ebc47ac3`).
- **CI verification rule:** `cbb1ccb3` (standalone class) was green; `017b5511` intermediate failed because manifest tests still assumed 29 cards, repaired in `bc0aac28`. At time of handoff writing the latter passed core parity + NeoForge build and was in dedicated-server smoke. Verify newest commit CI before declaring full green.
- **Still missing for gameplay:** faithful original ItemStack array entries/legacy metadata, consumed-item server transactions, 14 nearby aid detections, card-choice subclass NBT UI and server packet button wiring (2-6), client manual playtest. NEVER infer the full Research Table works from 33/33 pure core formulas.
- Deferred optional random previously-uncollected Moon research note MUST remain deferred until final polish. The font readability fixes remain; no gameplay/sky changes in this continuation.
- The conservative **overall port estimate = 68%**, conditional on latest CI validation. 33/33 cards refers only to class effect/partial init port, not whole mod content.

## 2026-10-10 later continuation: all original 14 theorycraft aid pools (estimated 69%)

- Fresh-read `main` at `2669ed8e` and original handoff; verified previous fully-green CI (`2669ed8e`).
- Bytecode audit of original BETA26 JAR: **all fourteen** ITheorycraftAid.getCards arrays and `TileResearchTable.checkSurroundingAids`/`ResearchTableData.initialize`. Detailed original values and bounds in `analysis/AID_BYTECODE_AUDIT_2026_10_10.md`.
- New pure Java `LegacyTheorycraftAidCards` (`f9d95ff`): exact original 14 aid-card pools, including **Bookshelf 1 Balance + 2 Notation + 3 Study duplicates**, and surprising Eldritch aid containing an **unregistered CardTruth** class. Do not deduplicate or swap the missing class for something else.
- `LegacyTheorycraftInspiration.start` (`8a91cc7`) now fills original weighted `aidCards` on session creation, preserving list persistence, and still subtracts the selected aid Set size from inspiration. `CoreParityTest` extended (`80a35a8`) to verify all 14 keys, 6 bookshelf entries, unregistered CardTruth, order, duplicates, save snapshots and unknown aid handling.
- New `LegacyTheorycraftAidScanner` (`1dce8ed`) precisely models original detection bounds dy=-1..1 dx=-4..4 dz=-4..4, Block vs Block-pick ItemStack vs entity-Class probes, 5-block entity radius, deduplication. Tests (`ca8601c`) validate all boundaries and call counts. **It is a pure-Java probe, NOT wired to live NeoForge world yet**; missing blocks must NOT be invented.
- At last check `f9d95ff` CI SUCCESS including server smoke, `80a35a8` completed core tests/build but server smoke still in progress; `ca8601c` had passed core tests and was building. Check latest runs before claiming newest HEAD green. Audit documentation `563ca3e`.
- Next priority: build real NeoForge block/pick-stack/entity adapter for only grounded world objects, expose eligible aid list to client UI and allow genuine user selection, then finish original 33-card draw/activation/serialization and inventory transactions. The Research Table UI is NOT finished.
- Original Moon-phase sky scan remains unchanged and user random-uncollected-note option deferred to final polish; readable GUI changes remain.
- Overall estimate **69% only after core + Gradle + server CI passes**. The 14/14 aid pools are loader-independent calculations, not proof of playability.
