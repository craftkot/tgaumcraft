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

## 2026-10-10 continuation: live NeoForge aide selection (70% estimate once latest CI green)

- Fresh-read GitHub main and existing handoff, confirmed preceding source/test commits passed CI.
- 5b68962: new NeoForgeTheorycraftAidBridge connects original 9x9x3 legacy scanner to real world BlockStates. Supports bookshelf, Nether/End portal, enchanting table, beacon and the ported arcane workbench. Missing Thaumcraft blocks / crimson portal entity not replaced or faked.
- 0ade42e: ResearchTableBlockEntity caches nearby aid detection 20 ticks to avoid repeated 3400-probe scans, and ALWAYS rescans at session start to reject vanished or forged aid selections.
- 2f2611e / 7a3f982: ResearchTableMenu exposes 14 aid choices as two synchronized DataSlot bitmasks; menu buttons 10..23 toggle aids only when valid, allow deselection after disappearance, and original button 1 starts with actual selected aids. Original card draw/activation buttons 2..6 remain disabled pending safe card-class and inventory transaction wiring.
- cab31d7 / 95deb42: original 256x256 ResearchTableScreen now shows two-column selectable readable aid names with server-validated clicking, red highlights for missing selected blocks and localized tooltips. en_us/ru_ru translations in 37b8b11 / 4fe5808.
- CI prior source-only adapter + blockentity + menu changes fully green. Verify latest UI and language commits against core parity, NeoForge build, dedicated-server smoke before claiming all green.
- Still NOT fully playable: 33 original card class mathematical effects are not yet wired to GUI, original subtype NBT/material transactions not implemented, 8 of 14 possible aid objects still unported and actual client manual testing not performed.
- Do not silently enable fake CardChoice results; preserve moon-scan original phase behavior until optional final polish.

## 2026-10-10 continuation: original nested CardChoice NBT access (71% estimate after CI)

- Fresh-read GitHub main at 7b43c567 and latest handoff. Latest existing CI was fully green, including server smoke.
- Decompilation audit of the user-supplied original BETA26 JAR with javap -c -p for thaumcraft.api.research.theorycraft.ResearchTableData, ResearchTableData$CardChoice, TheorycraftCard and ContainerResearchTable. Full key layout and exact server button ordering in analysis/THEORYCRAFT_CARD_CHOICE_NBT_AUDIT_RU.md (2e307889).
- f74faa95: added LegacyTheorycraftChoiceNbt in NeoForge layer: original compound fields cardChoice (full class FQCN), aid boolean, select boolean, nested arbitrary cardNBT subclass NBT with seed; exact ListTag cardChoices order and nullable lastDraw; defensive copy and preservation of all unknown enclosing/nested tags including absence of cardNBT on legacy exceptional saves.
- 0e982b4d: ResearchTableBlockEntity now exposes pendingChoices() and lastDrawChoice() and validated server-only writes of choice rows; rejects fake/unregistered card keys; initializes original empty cardChoices list for new theories and keeps originalSessionNbt passthrough for unknown state.
- CI at last check f74faa95 complete SUCCESS core + NeoForge build + server smoke. 0e982b4d completed build/core, dedicated-server smoke still running. Check latest GitHub runs before claiming all green.
- Still NOT complete gameplay: button IDs 2/3 and 4..6 (draw/select/activate) require server-factory initialization of all 33 cards, complete ingredient lookup and atomic item/XP transactions, live card-choice sync to client and manual playtests. This turn deliberately did NOT enable fake drawing/activation.
- Font readability retained; user's optional random uncollected moon-note QoL remains deferred until final polish.
- **Overall estimate: 71% after latest all-green CI**, not the percent of player-accessible research mechanics.

## 2026-10-10 continuation: atomic original research card ingredient + XP adapters (72% estimate after CI)

- Fresh-read main `f1929569` and this handoff. Confirmed all previous CI fully green (core parity, NeoForge build, dedicated-server smoke).
- Disassembled **actual original BETA26 JAR** via `javap -c -p`: `thaumcraft.common.container.ContainerResearchTable` button 4..6 preflights `InventoryUtils.isPlayerCarryingAmount` for EACH requirement separately, then loops over consumed requirements calling `consumePlayerItem` but **discards boolean returns**, permitting duplicated-ingredient underpayment. `thaumcraft.common.lib.utils.InventoryUtils` searches **ONLY the 36 main inventory slots** (not armor/offhand). The port should reject such missing inputs atomically, while preserving original item identity and retained catalyst semantics.
- New pure Java `LegacyTheorycraftInventoryPlanner` (`092ebd09`) plans multiple consumed/retained material requirements against all main inventory slots **before mutation**. It aggregates duplicate consumed requirements, handles splits over slots, allows retained catalysts to share the pre-transaction stack, fails the whole plan if any exact type/count missing, exposes defensive-copy debit arrays. Class accepts a `BiPredicate` to allow distinct original metadata/relaxed-NBT matchers in the future.
- New server-only `NeoForgeTheorycraftItemTransaction` (`ae6a91ce`) binds that plan to the real player's 36 main inventory stacks using strict `ItemStack.isSameItemSameComponents` (safe until audited relaxed NBT exceptions are ported); `prepare` and `commit` reject invalid cost mask shapes and consume only after a complete dry run. This is **not yet called by menu buttons**, so don't claim gameplay integration.
- `CoreParityTest` (`0cb11c3e`) tests duplicate-item underpayment prevention, two-slot split, retained catalyst coexistence, null/empty inventory, mismatched aspects, custom matcher, random plan defensive copy and invalid counts.
- New `NeoForgeTheorycraftExperience` (`467b5eb7`) implements original server XP debits: Enchantment 5 only when >=5, Spellbinding min(5,current) only when positive, DarkWhispers original current levels +10 without a 10-level affordability gate; returns original dark-whisper XP snapshot for calculating theory/warp. Call **only after all card prechecks**; no effect is granted here.
- CI at documentation creation: prior head `f1929569` verified green; `0cb11c3e` had passed core parity and NeoForge compilation, dedicated-server smoke still underway, `467b5eb7` CI started. **Verify newest run green before declaring validation.**
- Next BIG work: full 33-card factory init + original ItemStack/metadata resolutions, client card-choice synchronization, atomic transaction + actual card activation and menu button IDs 2..6. DO NOT enable draw with fake/partial cards. Preserve original sky-scan until optional final QoL random-uncollected moon note. Readable font retained.
- Estimated **72% overall only contingent on latest CI pass**, not a claim playable full Thaumcraft. 

## Next continuation (2026-10-10)

Current milestone: 74% pending final CI. Fresh-read analysis/CONTINUATION_74_CARD_CHOICE_SYNC.md for details. Three original CardChoice preview IDs + flags now synchronize server to client; all 33 original card cost/category/aid-only methods are bytecode mapped. Activation and drawing buttons 2–6 remain disabled until the complete original factory and material NBT integration are safe. Never start over; verify GitHub Actions before claiming complete. Optional randomized uncollected moon notes remain deferred.

## 2026-10-10 continuation: original BETA26 card NBT and localization (75% pending CI)

Fresh-read main and all handoffs, continued from 74%. See analysis/CONTINUATION_75_CARD_NBT_AND_LOCALIZATION.md for complete commits, bytecode-verified field schema (14 of 33 subclass serializers, 22 extra fields), safe NeoForge NBT validation, and restored original Russian/English card titles (33 each). All original assets and user font changes retained. Draw/select IDs 2-6 STILL intentionally disabled until all authentic 33 initializer/item transactions; no fake partial card draws, no manual client test claimed. Original moon scan unchanged; random uncollected notes are deferred optional polish. Check latest CI before claiming 75% fully verified.

## 2026-10-10 next checkpoint (76% pending latest CI)

Read analysis/CONTINUATION_76_DYNAMIC_NAMES_AND_MATERIALS.md first. Card previews now synchronize actual BETA26 category/aspect strings using 7+37 bounded DataSlot IDs, with all original English/Russian translations. Exact original getRequiredItems and getRequiredItemsConsumed code audit found 10 cards and 14 ingredients (9 consumed, 5 retained); immutable pure models + tests committed. The source-obfuscated CardSculpting clay ball is identified, along with 1.21 celestial note and aspect crystal ingredient stacks; unsupported phial and legacy variant materials intentionally FAIL closed. A real player inventory preflight is wired, not item debiting or activation. Preserve all current code; do not turn on menu buttons 2-6 until original 33-card full initialization + activation works. Optional uncollected random Moon note remains last-stage QoL. Check newest CI before claiming completed checkpoint.

## 2026-10-10: original 49 material options / 38 vanilla mappings (77% estimate)

Fresh-read main at 273fcb7d and continued exact BETA26 theorycraft port. See analysis/CONTINUATION_77_THEORY_OPTION_POOLS.md. Original CardMindOverMatter & CardTinker have same 18-entry static pools; original CardInfuse has 31-entry pool. All 49 original item/Block field references, infusion yellow nitor and ingot metadata 0/2 now recorded in immutable source; seeded choice indices match original. All **38** vanilla references deobfuscated using original 1.12.2 Paper2Srg Blocks.java/Items.java and mapped to modern Minecraft IDs, including old white wool. NeoForge can resolve real vanilla/Thaumometer/Salis Mundus option ItemStacks, while still rejecting every missing Thaumcraft item (do not redraw if unavailable). New conservative old ItemStack NBT decoder handles untouched Count1 Damage0 no-extra-tag saved vanilla items and ported Thaumometer/Salis; rejects all unsafe conversions instead of discarding NBT. Tests protect original pool order, mapping counts, distinct metadata and seed sequence. Latest 77% CI should be checked before claiming completion.

Still no fake draw or activated research: original 33 factory and button 2/3/4/5/6 wiring, unported Nitor/phials/curios and other TC items remain priority. Optional randomized uncollected Moon notes only at final polish. Preserve existing translations/fonts and do not start over.

## 2026-10-10 continuation: actual BETA26 phial and research materials (78% estimate)

Fresh-read main and 77% handoff, disassembled BETA26 original ItemPhial and ItemGenericEssentiaContainer. Full report in analysis/CONTINUATION_78_ORIGINAL_PHIAL.md. Registered real original ItemPhial `thaumcraft:phial`, both historical 0/1 variants, 10-essentia filled stack with original `Aspects:[{key,amount}]` NBT, optional 1..10 partial fills, BETA26 Russian/English item names, original bottle sprite and colored overlay frame with 37-aspect client tint. Used NeoForge data components, no invented object. CardChannel and CardInfuse material resolver now matches filled 10-essentia phials exactly against 36-slot player inventory. Crucible/jar fill/drain, 32-frame overlay animation, complete 33-card factory and GUI 2–6 activation remain to port; do not pretend full gameplay. User randomized uncollected moon note deferred. Check latest CI after `0e16f647` and doc commits before claiming green.

## 2026-10-11: семь оригинальных реликтов Curio (79% при успешном CI)

Свежий main читался перед началом (3f562762), предыдущие CoreParity/Gradle/server smoke зелёные. Полный отчёт и SHA исходников — analysis/CONTINUATION_79_CURIO_ITEMS.md. Из оригинального BETA26 JAR проверен через javap байткод ItemCurio и CardCurio: все 7 вариантов, награды знаний, случайные категории, временная/обычная/постоянная порча, ограничение книги Crimson Rites actualWarp NORMAL+PERMANENT >20. Реализован чистый LegacyCurioUse с семью вариантами и тестами полного порядка RNG. Зарегистрирован CurioItem, сохранение metadata 0..6 через DataComponents, серверное использование и исследование CrimsonRites с проверкой оригинальных предпосылок. Все 7 оригинальных PNG текстур занесены как Git blobs (проверены SHA-1 исходного JAR), 7 моделей, EN/RU оригинальные названия, редкость UNCOMMON. Decoder 1.12 ItemStack -> 1.21 компоненты теперь точно поддерживает Curio Damage0..6; оригинальная предметная транзакция сравнивает Curio по реальной разновидности, не внешнему model data. Добавлен NeoForgeTheorycraftCurioInitializer, который выбирает Curio из инвентаря игрока тем же оригинальным RNG и сохраняет cardNBT.stack без списания.

ОСТАЛОСЬ: лут/источники Curio, звук оригинала LEARN, тест в живом клиенте. Главное — исследовательский стол (GUI draw 2/3, activate 4/5/6) всё ещё намеренно выключен до полной фабрики 33 оригинальных карт. Нельзя придумывать заменители. Небо/луна по оригинальным фазам, случайная неполученная записка — опциональное улучшение в конце. Перед следующим процентом проверь CI последнего коммита.

## 2026-10-11: творческая вкладка Thaumcraft и оригинальный рецепт пузырьков (80% при успешном CI)

Стартовал с fresh-read main и предыдущего `CONTINUATION_79_CURIO_ITEMS.md` (все прежние CoreParity/NeoForge build/server smoke зелёные). Детально и с SHA — analysis/CONTINUATION_80_CREATIVE_AND_PHIAL_RECIPE.md. Создана и зарегистрирована первая настоящая Thaumcraft custom creative tab: иконка оригинальный Thaumonomicon, реальные добавленные блоки/предметы, все 7 реликтов Curio, 13 небесных записок, пустой пузырёк и полные пузырьки каждого из 37 аспектов для ручного тестирования клиента. Оригинальный рецепт пустых пузырьков из BETA26 ConfigRecipes проверен по JAR/байткоду: 8 шт., узор " C "/"G G"/" G ", глина + стеклянные блоки; в 1.21 дан tag со всеми 16 цветами окрашенного стекла и обычным стеклом плюс необязательная совместимость c:glass_blocks. Возвращены **ТОЧНЫЕ** оригинальные PNG Thaumonomicon и Crystal Essence (SHA Git blob оригинала проверены) и современные модели, для кристаллов наложен аспектный цвет. Добавлены проверки оригинальных ассетов/крафта/локализаций в GitHub Actions. ПРЕЖДЕ чем повышать 80%, перепроверь latest CI для текущего HEAD и обновления. Крупные дальнейшие проблемы: original full 33-card theory draw/activation GUI 2–6, прочие блоки/оборудование/мобы/фокусы/измерения и ручной клиентский тест; не включать фейковое завершение. Полировку лунных записок "случайная ещё неполученная" сделать в конце, фазовый оригинал пока сохранить.

## 2026-10-11 continuation: полная фабрика генерации 33 оригинальных исследовательских карт (81% оценка)

Fresh-read main=c153cf49 and prior handoffs; all previous CI green. Read analysis/CONTINUATION_81_ORIGINAL_33_CARD_FACTORY.md for full bytecode and commits. Original BETA26 javap audit: 19 fieldless cards, 14 with subclass NBT; five special initialize gates among fieldless; every recognized generated card consumes original player RNG nextLong before initialize. Created LegacyTheorycraftFieldlessInit with core parity tests; created NeoForgeTheorycraftLegacyOptionNbt round-trip guarded 1.12->1.21 item mappings; NeoForgeTheorycraftCardInitializer now constructs REAL correct 33-class original CardChoice NBT using existing original per-card formulas (14 subclass fields). Missing Thaumcraft item/legacy metadata maps fail closed with NO reroll. NeoForgeTheorycraftDrawBridge uses original HashMap normal draw pool, aids and 25% aid branch, up to 10k retries and exact seeded NBT, without changing inventory. ResearchTableBlockEntity.drawOriginalTheoryCards can server-authoritatively preflight, spend ONE paper, then atomically save 2/3 original choices plus remaining weighted aids/bonusDraws; deliberately refuse empty draws. This method is NOT exposed to player menu because **33-card activation effects, ink, item/XP/warp and GUI are still incomplete**, so do not enable buttons 2-6 and waste a player's paper. Added CI safeguards checking all fourteen typed initializer cases and disabled menu. Native tests through prior 6b66e3c9 passed; check newest main and all CI incl 893bebd7 + docs before incrementing. Original moon phase scan unchanged, optional random uncollected moon notes deferred.

Next major action: implement original 33-card *activation* dispatcher and atomic materials/XP/warp/ink behavior; then expose player-facing draw and three selectable cards, run actual client test. Do not start over, no fake reduced card pool.

## 2026-10-11 checkpoint: 33 оригинальных card.activate, безопасное предпросчитывание (82% при зелёном CI)

Сначала fresh-read main `e802aef8`, original handoff/81% report, предыдущий CI полностью зелёный. Новое: `analysis/CONTINUATION_82_ACTIVATION_EFFECTS.md` со всеми commit SHA и точными ограничениями. Через javap BETA26 проверены ContainerResearchTable/card.activate; CardScripting реально расходует 2 ед. чернил (одну внутри + одну после активации) и бумагу, это атомарно заложено. Новый pure `LegacyTheorycraftActivationPreflight` валидирует стоимость всех 33 карт, XP Enchantment/Spellbinding/DarkWhispers, настоящие ингредиенты и текущие чернила, вдохновение, уже выбранную карту; реальный NeoForge preflight использует полный original 36-slot inventory planner и сохранённый original NBT. Новый `LegacyTheorycraftActivationEffects` направляет **все 33** настоящие activate() через ранее проверенные оригинальные классы на КОПИЯХ player knowledge/warp/theory, без побочных эффектов при ошибке; полные тесты всех 33 activation paths. Серверный `NeoForgeTheorycraftActivationStager` преобразует реальный сохранённый cardNBT в staged effects, игровую RandomSource и строго проверенные item-aspect visSize; НЕЛЬЗЯ вызывать из UI hover или до актуальной preflight; stale check revalidation enforced. GitHub CI поддерживает проверку полноты 33-card dispatch и double ink; первый regex assertion ошибочно задвоил backslashes, исправлен. Последние CI проверяй на HEAD; старые preflight commits прошли полностью.

Очень важно: ИНИЦИАЛИЗАЦИЯ + PURE EFFECT STAGING не означает playable research table. Кнопки 2–6 всё ещё намеренно отключены! Нужно реализовать именно atomic SERVER COMMIT: inventory+XP+ink/paper debit, сохранить все изменения theory+player knowledge+warp, выдать/выбросить Synthesis reward, selected flag and UI sync. После этого лишь включать игроку выбор и запускать клиентские тесты. Полный оригинальный контент и магические блоки/существа также пока не готовы. Не обманывать процентом, не начинать порт заново. Лунные записки случайная ещё неполученная — опциональная полировка в конце.


## 2026-10-11: настоящая интерактивная теория с атомарным серверным commit (83% при зелёном CI)

Не начинай сначала. Свежий main в начале этого шага был `b18aff2`; отчет со всеми SHA — `analysis/CONTINUATION_83_PLAYABLE_THEORY_GUI.md`. Теперь **реально** подключены ID 2/3 (оригинальный server draw 2/3 карт с расходом 1 бумаги, третья только при bonusDraws), ID 4..6 (активация одной из трёх оригинальных карт; 33-class factory + preflight + full staged effect commit + ink/XP/ingredients + player knowledge/warp + оригинальный NBT selected + Synthesis crystal reward), а также прежние keep/finish/abandon. 1.21 GUI позволяет играть с оригинальными подсказками EN/RU и увеличенным шрифтом. Исправлены сброс всей истории исследований при использовании live set-view в PlayerKnowledgeData.loadSnapshot и неправильный индекс hover tooltip. Draw не перетирает невыбранные pending варианты. CI-условие, раньше предписывавшее держать ID2..6 выключенными, теперь заменено на реальный тест подключенных транзакций; красные CI до `36582e7` вызваны **старой проверкой**. Проверить самую новую сборку с Gradle, CoreParity и server smoke прежде чем утверждать 83%.

ВАЖНО: это первая wiring-реализация, полноценный ручной живой клиент НЕ тестировался. Оригинальные нехватающие предметы и динамический visSize пока fail closed, никакие дешёвые подмены не включать. Текущая теория и оригинал должны сохраняться. Дальше ручные тесты, tooltip стоимости карт, прочие большие системы (тигель/инфузия/големы/фокусы/мобы/измерения), недостающие блоки помощников и опциональная случайная неполученная лунная записка в самом конце.


## 2026-10-11: server-synced exact theory-card costs and requirements

Fresh main before this slice was `59b468ca5b56aca5419ed6c1c8e5f871f284285a`; Actions #453 was SUCCESS. Original BETA26 `GuiResearchTable` bytecode was checked again: each card visually exposes `getInspirationCost()`, real `getRequiredItems()` stacks and `getRequiredItemsConsumed()` flags. The current 1.21 GUI previously showed only a generic “requires original materials” hint.

Commit `5d85db4d6d2106c65874edfe0503b8dd71715be4` restores that contract without trusting the client:
- `ResearchTableMenu` derives card cost metadata from the authoritative saved choice: raw inspiration cost (including negative/gain cards), exact Scripting 2-ink + 1-paper requirement, XP-mode for Enchantment/Spellbinding/DarkWhispers, and live preflight failure reason.
- Already-audited material requirements are converted on the SERVER through `NeoForgeTheorycraftMaterialResolver`. At most two exact ItemStacks per original card are synchronized through six hidden vanilla menu slots. The original cardNBT itself never leaves the server.
- consumed-vs-kept flags are synchronized separately; unresolved legacy Saved ItemStack requirements stay fail-closed and display as unresolved rather than inventing a substitute.
- hidden preview slots are `mayPickup=false`, `mayPlace=false`, and `quickMoveStack` rejects every index >= the real 38 table/player slots, preventing a ghost-item duplication path.
- EN/RU card hover text now shows inspiration, ink, optional paper, special XP semantics, exact required item names/counts, whether the item is consumed or retained, and why activation is currently blocked.
- Actions run `38148914429` / #454: SUCCESS through CoreParity, NeoForge build, dedicated-server smoke and uploaded `thaumcraft-port-jars` artifact.

This is still NOT a manual rendered-client playthrough. Overall estimate remains rounded to **83%** because the change completes theory-table usability/fidelity but does not close the large missing alchemy/infusion/foci/golem/entity/dimension systems.


## 2026-10-11 — BlockEntity theory validation hardening
Code commit `4fd818a01ff7d07706f7c8218d17acfbfa005f5b`: three-card draws now explicitly require bonusDraws > 0 at the BlockEntity boundary (not just ResearchTableMenu), before RNG usage and paper debit. Keeping a selected card now refuses legacy/corrupt choices where more than one CardChoice is selected, rather than silently preserving the first and deleting others. These are server-only rejection paths; original NBT is left intact on denial. This small hardening is not enough to raise the rounded overall 83%. Inspect latest CI before treating the change as verified.


## 2026-10-11 continuation — save-boundary CardChoice integrity
Previous GitHub Actions run #455 (38153717690) for commit 4fd818a completed SUCCESS. New hardening in `ResearchTableBlockEntity.storeCardChoices`: reject more than three pending cards, any null/structurally-invalid CardChoice, or more than one selected row BEFORE rewriting originalSessionNbt. Empty card offers remain legal. Earlier keep-path safeguard still applies to malformed old saves. No new gameplay category added; retain rounded 83% pending larger playable-content implementation. Check CI for this new commit.


## 2026-10-11 continuation — corrupt legacy card list validation
CI #456 (run 38153911426) for previous commit succeeded. A malformed NBT cardChoices list may decode to a shorter set because LegacyTheorycraftChoiceNbt.readChoices intentionally skips damaged rows. New BlockEntity.hasStructurallyValidPendingChoices rejects wrong tag type, non-compound elements, >3 rows, truncated decoder output, malformed subclass tags and multiple selected rows; NeoForgeTheorycraftActivationPreflight now fails closed before reporting a card as payable. drawOriginalTheoryCards also refuses to overwrite a saved cardChoices tag of an unexpected NBT type. No valid old-world card data is rewritten on rejection. Re-run CI; no rendered client test claimed. Retain ~83%.


## 2026-10-11: reject invalid preserved theory offers before draw
After commit bb45789, direct server draw also uses hasStructurallyValidPendingChoices for any existing cardChoices NBT. This closes a remaining inconsistency: a malformed existing list whose compound parser returned no choices could previously be overwritten by a new draw. Now empty valid lists remain drawable, but invalid legacy saves are preserved unchanged; no paper or RNG is used. Check latest GitHub Actions before claiming success. Still rounded 83%.


## 2026-10-11 continuation — strict keep/lastDraw validation
Fresh-read main 48b605b; GitHub Actions #458 passed. The BETA26 keep-selected action now calls the same full hasStructurallyValidPendingChoices invariant as server activation rather than accepting a malformed subset. It also refuses an existing lastDraw of the wrong NBT type or with an invalid subclass/seed before appending its seed to savedCards. No saved NBT is mutated on failure. Check new Actions before claiming compilation. The change is a defensive research progress integrity fix, not evidence of a playable full-client test; rounded estimate remains 83%.


## 2026-10-11 — readable multi-line theory card costs
GitHub Actions #459 for 2bc4ad2: SUCCESS. The card tooltip was one concatenated line including item names and special XP, overflowing the screen particularly in Russian. Updated ResearchTableScreen to use vanilla GuiGraphics.renderComponentTooltip with separate lines for card name, inspiration, ink, optional paper, special XP, each actual synced ingredient, and server preflight status. All menu cost data remains authoritative, no cardNBT is exposed, no activation behavior modified. Check CI before calling it build verified; full client GUI screenshot/playthrough still outstanding. Project estimate stays rounded 83%.


## 2026-10-11 — prevent phantom research cards in client menu
After CI #460 success at 988f22a, ResearchTableMenu now checks the complete BlockEntity.hasStructurallyValidPendingChoices invariant before exposing synchronized card IDs, names, selected flags, cost/XP/material metadata, or ghost ItemStack requirements. A malformed old-world cardChoices list previously decoded to a subset of enticing visible cards even though authoritative activation preflight rejected the entire list. Now no phantom selectable cards are shown, no client-supplied NBT or rewards, and the raw historical save remains intact for future migration. Build and server smoke of THIS commit must be checked separately. Maintain estimated 83%; no claim of manual GUI test.


## 2026-10-11 — blocked false draw button on corrupt saved card lists
At code head e510c0d, CI #461 is SUCCESS (NeoForge build plus dedicated-server smoke). Prior menu blocked invalid cards from preview, but thereby hasPendingCardChoices() became false; the client displayed an enabled Draw action that server drawOriginalTheoryCards correctly refused. A single server DataSlot corruptTheoryChoices now distinguishes an existing malformed cardChoices tag from a truly empty valid list, and ResearchTableScreen.canDraw suppresses the false button. BlockEntity exposes only the boolean raw-tag presence (never sends historic cardNBT). User can still deliberately abandon an incomplete corrupt session; no silent loss or mutation. Verify new CI. Still rounded 83% pending major gameplay milestones.


## 2026-10-11 follow-up — validate original lastDraw before future draws
At previous head f21c141, Actions #462 was still in progress at fresh-read. Added hasValidLastDrawHistory() to ResearchTableBlockEntity: existing lastDraw must be original CompoundTag and a structurally valid registered card; absent lastDraw is legal. Both drawOriginalTheoryCards and keepSelectedTheoryCard reject corrupt existing lastDraw before consuming paper/RNG or replacing saved history. Preserve the raw NBT for deliberate recovery. Verify Actions run for new commit; overall ~83% pending full client testing and large gameplay systems.


## 2026-10-11 — Four original ItemTCBase material items (pending CI)
After the successful #463 build of e8fcb7b, audited the supplied original BETA26 JAR: ConfigItems registers vis_resonator, amber, quicksilver and fabric as unvariant ItemTCBase, all with original 16x16 item art and en/ru names. New registry items carry their ORIGINAL IDs, not invented item behavior. Added strict source 1.12 ItemStack decoding and the exact historical-field seeded option mappings used by CardMindOverMatter, CardTinker and CardInfuse. Their original texture PNGs and modern item/generated model JSONs are included; do not re-roll a candidate if its original item is unported. This unlocks genuine cards when the player has their requirement but does NOT implement their crafting/alchemical acquisition recipes or full overarching alchemy/foci systems. Check new CI, then client art/model preview; remain at approximate 83% until larger functionality is playable.

### Creative-mode accessibility for the four original items
Registered VIS_RESONATOR, AMBER, QUICKSILVER and FABRIC in ThaumcraftCreativeTabs, alongside other original first-step objects. This is creative-test availability only; it is NOT a substitute for BETA26 world loot, ore processing, alchemy or vis crafting, and should not be treated as survival progression being complete. Source original vis resonator recipe (ConfigRecipes): shapeless arcane recipe UNLOCKAUROMANCY@2, 50 vis, aer 1 + aqua 1 crystals, ore entries plateIron and gemQuartz; do NOT fake it as a vanilla shapeless recipe without the research/aura/plate prerequisites.


## 2026-10-11 — Actual BETA26 vis resonator ARCANE recipe, not vanilla substitute
Fresh main: `2dbd68a6d76f4f0b373d617e6a060f829040744c`; GitHub Actions #465 later confirmed SUCCESS.
Inspected actual original `ORIGINAL/Thaumcraft-1.12.2-6.1.BETA26.jar` using `javap -c -p thaumcraft.common.config.ConfigRecipes`: at registration `thaumcraft:vis_resonator` the constructor is `ShapelessArcaneRecipe(defaultGroup, "UNLOCKAUROMANCY@2", 50, AspectList(AIR=1,WATER=1), ItemsTC.visResonator, new Object[]{"plateIron","gemQuartz"})`. Confirmed source `ShapelessArcaneRecipe.matches()` checks crystal slots separately and uses Forge `ShapelessOreRecipe` for a truly shapeless 3x3 ingredient grid.

Implement `VisResonatorRecipeLogic` in loader-neutral core with exact research/vis/crystal constants and a two-slot shapeless multiset matcher, including overlapping ore tags, with regression cases in `CoreParityTest`. In `ArcaneWorkbenchBlockEntity`, map old ore dictionary `plateIron` to NeoForge common `c:plates/iron` and legacy `forge:plates/iron` *only*; DO NOT silently use a vanilla iron ingot. Map `gemQuartz` to vanilla quartz or `c:gems/quartz`/`forge:gems/quartz`. Validate research, one Aer crystal at slot 9 and Aqua crystal at slot 11, aura after discount, then consume exactly the two grid items, two crystals and 50 vis at zero discount. Other crystals remain untouched. The recipe is server-authoritative and full changes occur only after preflight.

`ArcaneWorkbenchMenu` now previews and crafts either the existing Thaumometer or the Vis Resonator, including click and shift-click server paths; no result item granted on failed validation. Sync a crystal-requirement bitmask so `ArcaneWorkbenchScreen` highlights exactly two required aspects for the resonator, all six for the thaumometer. Research and cost data remain server-derived. This is an actual recipe bridge, NOT a JSON vanilla crafting substitute.

**Limitations to preserve:** The original ore dictionary might recognize further aliases not covered by 1.21 common tags; in a vanilla-only pack no iron plate is registered, so survival crafting depends on porting an authentic plateIron source/recipe or installing a tag-compatible mod. Do not replace plateIron with iron_ingot to fake progress. Only this original recipe has been added: full general arcane recipe dispatcher, visor discount equipment, remaining acquisition and thaumaturgy systems, and manual client gameplay tests are still outstanding. Verify CI and report any compile failures before raising progress estimate.
