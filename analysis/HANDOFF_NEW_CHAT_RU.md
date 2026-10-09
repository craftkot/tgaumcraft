# HANDOFF_NEW_CHAT_RU — Thaumcraft 6.1.BETA26 → Minecraft 1.21.1 NeoForge

## Главное
Продолжать существующий порт, НЕ начинать заново и НЕ упрощать механику ради скорости.
Оригинальный Thaumcraft 6.1.BETA26 для Minecraft 1.12.2 — главный эталон поведения: bytecode/resources/quirks.

## Primary / backup
- PRIMARY: private GitHub repository `craftkot/tgaumcraft`, branch `main`.
- Primary working tip: **always fetch `main` live**; don't treat the checkpoint SHA below as HEAD.
- Last verified gameplay CI checkpoint at time of this handoff: `79782b0ac634f3f5942f34247c4156f9daecabfa` (SUCCESS: core parity + NeoForge build + dedicated-server smoke).
- Confirmed overall progress: **59%** (do not inflate until another substantial playable slice is verified).
- Newer commits may exist beyond that checkpoint; inspect their CI outcomes before claiming success.
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
Never lower the confirmed baseline below 59%.
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
