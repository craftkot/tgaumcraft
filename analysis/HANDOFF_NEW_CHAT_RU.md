# HANDOFF_NEW_CHAT_RU — Thaumcraft 6.1.BETA26 → Minecraft 1.21.1 NeoForge

## Главное
Продолжать существующий порт, НЕ начинать заново и НЕ упрощать механику ради скорости.
Оригинальный Thaumcraft 6.1.BETA26 для Minecraft 1.12.2 — главный эталон поведения: bytecode/resources/quirks.

## Primary / backup
- PRIMARY: private GitHub repository `craftkot/tgaumcraft`, branch `main`.
- Primary working tip: **always fetch `main` live**; don't treat the checkpoint SHA below as HEAD.
- Last verified gameplay CI checkpoint at time of this handoff: `805257662a8c15f490eeb974618c77b8fb405784` (SUCCESS: 5,823 core assertions, NeoForge build, dedicated-server smoke, original celestial assets and scribing recipes).
- Confirmed estimated overall progress: **62%** after original BETA26 ScanSky mechanics, thirteen original celestial note variants and their original textures, and glass-bottle scribing tool/refill crafting recipes. Keep this as the next baseline until further substantial work.
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
Never lower the confirmed baseline below 62%.
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
