# Thaumcraft 6.1.BETA26 → NeoForge 1.21.1: текущий краткий handoff (2026-10-12)

GitHub: https://github.com/craftkot/tgaumcraft
Последний исходный HEAD при записи: `f3ef8e01c420f1936fc7c3d42ef0aeaf872f5825`. ВСЕГДА заново читать main, этот SHA не неизменный указатель.
Основная CI: https://github.com/craftkot/tgaumcraft/actions/runs/38261500915
Настоящий клиент: https://github.com/craftkot/tgaumcraft/actions/runs/38261500900
Оба результата по этому SHA требуют проверки, не утверждать успех заранее. Предыдущий `65b6a91` (пилоны) — CoreParity, Gradle, сервер и реальный клиент SUCCESS (#38260380755, #38260380407).
Общий прогресс: ~86% ОЦЕНКА, а не доля проверенного в игровом клиенте.

## Текущие механики
- Ранее: теории 33 карт, аспекты, оригинальные исследования/частичная алхимия, тигель, рецепты и нагрев, 16 цветов нитора, Alumentum как entity со взрывом.
- В этом этапе: BETA26 3 оригинальных ID пьедесталов, 3 разных InfusionPillarBlock с оригинальными ориентацией, hitbox=0.5 и collision=2.0, 3D временная модель; оригинальный TileInfusionMatrix.validLocation на реальных block classes; блок и Tile `infusion_matrix` с сохранением `active`, `crafting`, `stability`, `recipeinst` и 100-tick отключением при потере опоры.
- Ничего из этого НЕ полноценный инфузионный ритуал; три новые колоны ещё используют временные текстуры существующих пьедесталов. Исходные 64×64 изображения pillar_*.png и OBJ+MTL есть в original JAR, их надо безопасно перенести (не выдавать визуальную аппроксимацию за точный оригинал). Настоящие stoneArcane/stoneAncient/stoneEldritchTile пока отсутствуют, две единицы при разрушении колонны не выдаются — это TODO, не подменять случайным блоком.

## Следующие задачи по приоритету
1. Проверить client-run #38261500900: matrix screenshots 22–23 должны подтвердить `active:1` при 4 колоннах+центральном пьедестале и `active:0` после их нарушения. Не путать успешный запуск workflow с подтверждённым результатом.
2. Перенести оригинальные stone blocks и дроп 2 stone, оригинальные pillar mesh/textures, matrix модель/FX (не имитировать без пометки).
3. Обработку `IInteractWithCaster` и оригинальный `TileInfusionMatrix.craftingStart/craftCycle/craftingFinish`: research, unordered components matching, essentia and instability, atomic debit/finish, sync/client tests.
4. Потом оставшиеся фокусы/големы, другие крупные системы. 
Не завышать %. Отчёт после каждого существенного push + CI, держать handoff свежим. Видео строго 90/95/100.

## Follow-up: stone materials / correct pillar loot
See full handoff for SHA/results. Original BETA26 `BlockPillar.func_180663_b` emits two corresponding stoneArcane/stoneAncient/stoneEldritchTile drops. Three authentic base stone block IDs, block items, 2× loot and original material art are being ported; do not substitute a pillar item. Check latest main CI. Real client #38261500900 SUCCESS: visible active1 before break and active0 after break. Matrix still no player-accessible crafting.

## Original OBJ pillar visual upgrade
Source original BETA26 pillar.obj hash db048355f80d16b1c7cccbe53a17f139ab8e56aa903d04443d9e75d41354e007 restored. Three 64x64 pillar PNG and NeoForge builtin OBJ models for arcane/ancient/eldritch. Updated source verification and targeted real client smoke. CHECK client screenshots after latest main; no assumed working visuals on CI compilation. No 90% yet.


### Source BETA26 live stability smoke (2026-10-12)
After the original matrix invalidation screenshots 22–23, the real-client script now restores all four corner pillars as `pillar_eldritch`, sets `active=1, stability=0.0f`, waits seven seconds, queries actual `stability`, `stabilityReplenish` and `cycleTime` via integrated server `/data get block`, and takes real screenshot 25. Expected `stabilityReplenish=0.2`, `cycleTime=7`, stability increases; verify actual image/log independently, not implied by green workflow. No video before 90%.


## 2026-10-13 — запуск инфузии: проверка рецепта
Добавлен source-derived pure-Java `LegacyInfusionStartPlanner`: проверяет структуру, центральный предмет, непустые боковые пьедесталы, исследования, взаимно-однозначное соответствие ингредиентов; считает аспекты с округлением вниз и минимумом множителя 0.5 по байткоду оригинала. CoreParity расширен. Пока нет интеграции с игровым рецептурным каталогом, гаунтлетом и расходом эссенции: НЕ утверждать, что инфузия готова. Проверить Actions нового HEAD. Оценка 86%.


### 2026-10-13 isolated client stability regression
The old real-client screenshot #25 showed `cycleTime=7` but approximately `stabilityReplenish=0.1`, NOT the predicted 0.2. The probable cause is other asymmetrically placed smoke-test pedestals within the original ±8 stabilizer scan. The revised smoke now builds a clean 4×Eldritch altar at (30,151,30), 20+ blocks from all earlier test pedestals, `/forceload`s chunk, sets `active=1,stability=0`, waits ~7s, inspects NBT and checks exact cycleTime=7 and stabilityReplenish=0.2 via `/execute if data ... run say MATRIX_ELDRITCH_..._OK`. **Must examine resulting authentic screenshot 25**, green run alone only confirms no crash. This test does not claim infusion ritual implementation.


### 2026-10-13 — real-world inventory preflight glue (CI pending)
The new pure `LegacyInfusionStartPlanner` is now called from actual `InfusionMatrixBlockEntity.prepareInfusionStartFromWorld` (server-only, read-only): requires active/valid/not crafting matrix, reads actual `PedestalBlockEntity` center at Y-2 and copies only nonempty surrounding pedestal `ItemStack`s in original scan order, then performs the verified research/recipe predicate/essentia-cost preflight with the matrix's live `essentiaCostMult`. Returns a prepared snapshot but **does not consume, mutate blocks, start crafting or register fabricated recipes**. Needs actual original recipe registry + real player knowledge bridge + caster gauntlet before playable infusions. NeoForge compile is required.


### 2026-10-13 BETA26 craftCycle real essentia-drain algorithm (CoreParity pending)
Read original JAR with `javap -c -p thaumcraft.common.tiles.crafting.TileInfusionMatrix`, especially offsets 1015..1152 in `craftCycle`. New loader-independent `LegacyInfusionEssentiaCycle` reproduces original **ordered attempts**: if total essence zero, transition to ingredient stage; for each positive aspect in insertion order call the real pluggable drainer with range **12** and delay `amount > 1 ? countDelay : 0`; reduce exactly one on first success then immediately return; each failed attempt subtracts **0.25** stability; after all failures flag `checkSurroundings`. Tests verify original zero-valued AspectList entries, failure and recovery, ordering, delay, input immutability and non-fabrication. Important: drainer MUST debit a real essentia source on success; **not connected to a live NeoForge pipe/jar network yet**. Avoid counterfeit recipes, no auto-completed infusions. Retain ~86%.


### 2026-10-13 BETA26 original ingredient countdown/returnable containers (CI pending)
Read exact `TileInfusionMatrix.craftCycle` bytecode 1152..1554. Implemented `LegacyInfusionIngredientCycle` as immutable CORE planner: loop over original remaining recipe ingredient order and physical side pedestal order, skip empty items, source-FX with global `itemCount=5` when itemCount was zero; each subsequent cycle ticks down 5→4→3→2→1→0; only on 1→0 consume matching item and remove ONE recipe ingredient, replacing pedestal item by original Item.getContainerItem result (e.g. empty jar/bucket) and returning. No match yields NOT_FOUND, zero requirements READY_TO_FINISH. CoreParity covers matching/order, delay, missing pedestal and container return. **Not yet invoked by NeoForge world** because no real recipe/gauntlet or finished infusion. Next integrate it carefully into server ritual with actual source inventory and no duplicated items. Keep 86%.


### 2026-10-13 — original Infusion craftingFinish variants (CI pending)
Original JAR BETA26 `TileInfusionMatrix.craftingFinish(Object,String)` disassembled and audited: (1) for `ItemStack` output, deep-copy the actual recipe result onto the central pedestal (source also carries relative damage from the existing item under specific vanilla damage/tag predicates; **do not claim that part ported yet**); (2) for NBT output, add/replace the named tag on the existing center stack; (3) for Enchantment output, increment the existing enchantment level by precisely one, preserving others (original does NOT introduce a custom max cap). Following finish, `recipeEssentia` is cleared, `recipeInstability=0`, `crafting=false`, `recipeOutput=null`, with a sound, block notification and player crafting event when player exists. Added `LegacyInfusionFinishLogic` as isolated, testable finish gate and output mutation semantics (copy, tag replacement, enchant increment, completion reset), with new CoreParity regression checks. **No live crafting commit or events wired yet** because the original infusion recipes, essentia network and caster start are still pending. Do not allow NBT administration to mint items; finish only once validated. Maintain ~86% until real complete ritual exists.


### 2026-10-13 — server-side pedestal ingredient compare-and-swap
Added real `PedestalBlockEntity.replaceInfusionIngredientIfUnchanged(ItemStack expected, ItemStack remainder)`. It rejects client/world-missing contexts, empty/multi-count expected stacks, multi-count remainder, and stale item identity/components; compares `ItemStack.isSameItemSameComponents` + count before ONE synchronized inventory write. Subsequent attempts with stale expected material fail, rather than consume a different item or duplicate outputs. This method is infrastructure, not a playable ritual; only the future validated recipe/cost transaction should call it. NeoForge build and server testing required on this commit. Keep ~86% completion.


### 2026-10-13 — Verified source BETA26 Crystal Farmer infusion catalog
Read and disassembled original 6.1.BETA26 JAR SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`, `ConfigRecipes.initializeInfusionRecipes` offsets **403..1100** and `ConfigBlocks.init`. Added `LegacyInfusionCrystalFarmerCatalog`: exact seven original research-gated infusions (`CrystalClusterAir/Fire/Water/Earth/Order/Entropy/Flux`), source-output IDs `crystal_aer/ignis/aqua/terra/ordo/perditio/vitium`, original center Aspect-tagged Crystal Essence, two unordered side items `minecraft:wheat_seeds` (SRG `Items.field_151014_N`), `thaumcraft:salis_mundus`. Essentia required: 10 respective aspect + 10 Vitreus + 5 Vinculum. Flux variant has instability 4, others 0. Independent `CrystalFarmerParityTest` runs in GitHub main CI in addition to existing 4000+ CoreParity assertions, verifies all seven IDs, flux difference, research, no wrong inputs, multiplied cost including original truncation and immutable aspect inputs.
**IMPORTANT:** This is authentic recipe metadata/matching, NOT a completed infusion. The source BlockCrystal outputs have not yet been ported/registered, and live recipe start/essentia drainer/caster/ritual are not wired. No fake item outputs or fabricated recipes. Check post-commit NeoForge build and dedicated server. Overall estimate ~86%.


### 2026-10-13 — real NeoForge pedestal adapter for the seven Crystal Farmer recipes
Added `src/neoforge/java/dev/ehtid/thaumcraft/crafting/CrystalFarmerInfusionAdapter.java`. Its `prepare(InfusionMatrixBlockEntity, PlayerKnowledgeData)` performs genuine server-only, research-gated recipe validation against actual central and side `PedestalBlockEntity` `ItemStack`s, requires a Crystal Essence with matching `DataComponents.CUSTOM_DATA` Aspect tag (legacy `IngredientNBTTC` required NBT, not merely same Item ID), plus exactly Wheat Seeds and Salis Mundus on surrounding pedestals in any order. Returns immutable source-catalog metadata with existing `LegacyInfusionStartPlanner.Prepared<ItemStack>` cost/mapping. No fake infusions: no production `BlockCrystal` outputs yet, no essentia network, no caster start or crafting commit; preview never changes inventories or crafting flag. Verify NeoForge build and dedicated server, then source world test. Keep ~86% estimate.


### 2026-10-13 — BETA26 BlockCrystal growth state logic (fresh source parity, CI pending)
Read ORIGINAL reference JAR `thaumcraft.common.blocks.world.ore.BlockCrystal` directly with `javap -c -p`, SHA-256 original JAR verified `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`. Added `LegacyBlockCrystalGrowthRules` with BETA26 exact 16 metadata states: size=0..3, gen=1..4, `meta=size | ((gen-1)<<2)`; random update nextInt(3+gen)==0; mining gives size+1 specific-aspect crystals, no Silk Touch; aura threshold **<=10** shrinks, else requires getVis/getFlux > auraBase+10, growth `size < 5-gen + pos.toLong()%3`, growth consumes 10 real AuraHelper units, generation <4 permits spreading, prospective location within +/-1 XYZ, successful nextInt(6)==0 reduces child generation to parent's otherwise +1; source checks neighbor stone. Source initial size0 gen1; if starved at size0 breaks only when touching same BlockCrystal. Added independent `CrystalGrowthParityTest` to main CoreParity stage. **Aura implementation is still missing; no fake growth from random ticks without actual vis/flux network.** This is verified growth logic, not yet a complete BlockCrystal world port. Await CI.
