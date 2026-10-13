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
