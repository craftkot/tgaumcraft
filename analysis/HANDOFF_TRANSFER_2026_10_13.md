# Актуальная передача Thaumcraft — 13.10.2026

## Начало нового чата
Работать с **существующим** репозиторием https://github.com/craftkot/tgaumcraft (ветка `main`). Сначала fresh-read реального HEAD, затем последние GitHub Actions. После этого читать ЭТОТ файл, `analysis/HANDOFF_CRYSTAL_OBJ_2026_10_13.md`, актуальные разделы `analysis/HANDOFF_NEW_CHAT_RU.md`. Последний файл ~141 KB, в начале и середине есть старые проценты/SHA: актуален GitHub, а не его исторические отметки. `analysis/HANDOFF_NEXT_CHAT_RU.md` от 11 октября описывает предыдущий этап 83%.

## Проверенный статус
- HEAD до коммита данной передачи: `2c56f66dd36e6f7a6a7dda352ccf0eae9addc058`. На следующем сеансе нужно снова прочитать HEAD, поскольку коммиты продолжаются.
- Для этого кодового SHA [основная CI #38435467940](https://github.com/craftkot/tgaumcraft/actions/runs/38435467940) = **SUCCESS**: CoreParity, проверки ресурсов, NeoForge build, dedicated server, JAR upload.
- Последний реальный клиент [#38435196868](https://github.com/craftkot/tgaumcraft/actions/runs/38435196868) = SUCCESS для **предыдущего** SHA `01a367c554eedc544af7e771ac2f57036f2e9b14`; успешный run не доказывает визуальное совпадение всех моделей, следует смотреть скриншоты/логи из артефактов.
- Первые CI после фикса атомарного расхода ауры были красными (#38435196948 и #38435326476); текущая #38435467940 исправлена и зелёная.
- Оценка общей готовности **~86%** (не объективная метрика «процента кода»); не завышать до реально доступных игровых систем.

## Что переносилось
- Сохранены 148 исследований, 37 аспектов, теория и исследовательский стол с 33 картами, части GUI/сканирования/таумометра, небесные записки, EN/RU и улучшенный шрифт.
- Есть тигель/часть алхимии, оригинальные рецепты, 16 цветов нитора и Alumentum. **Алхимия ещё не полностью готова.**
- Три типа пьедесталов и колонн; руническая матрица с фактической проверкой структуры/стабильности и NBT. Алгоритмы рецептов, последовательности эссенции, поглощения предметов и finish перенесены частично. 7 исходных `CRYSTALFARMER` infusion-рецептов + NeoForge ItemStack preflight. **Настоящий инфузионный ритуал не завершён**: нет целиком подключённой EssentiaHandler сети, caster gauntlet/start, server craft cycles/output/VFX.
- Семь `BlockCrystal`: `crystal_aer/ignis/aqua/terra/ordo/perditio/vitium`, 16 размер+поколение состояний каждый, аспектный loot, реальные серверные тики роста/истощения и per-chunk saved Vis/Flux. Последний фикс `trySpendCrystalAura` требует **атомарного списания ровно 10 единиц**, а не частичного списания ради полного роста. Генерация ауры по оригинальным биомам/чанкам пока требует интеграции.
- Оригинальный `crystal_source.obj` с 8 группами / 96 треугольниками перенесён byte-identical (SHA256 `8221ea1f3001ae7abb41f2f87a007c652b77cd138b2d29d707a06253faec23c6`). `LegacyCrystalMeshSelection` реализует исходный seed-based shuffle; **не подключена полная динамическая отрисовка шести граней/ориентаций по оригинальному CrystalModel**. Подробнее `analysis/HANDOFF_CRYSTAL_OBJ_2026_10_13.md`.

## Следующие настоящие задачи
1. Открыть последний run клиентского теста, проверить кадры кристаллов, ошибки рендера/лут-таблиц, сопоставить с оригиналом, не выдавать отсутствие краша за полную parity.
2. Подключить seed-based выбор `size+1` OBJ-групп и 6 направлений крепления к NeoForge модельному пекарю. Провести новый реальный клиентский тест.
3. Связать `AuraGenerationLogic` и `LegacyBiomeAuraModifiers` с chunk/worldgen, обеспечить отсутствие бесплатного роста и корректное сохранение.
4. Настоящая инфузия: gauntlet, каталог рецептов, сеть эссенции и списание, последовательность ингредиентов, нестабильность, серверное завершение без дюпов.
5. Далее фокусы, големы, существа, измерения и остальной BETA26 контент. Фиксить реальные баги без замены оригинального мода упрощённым аналогом.

## Рекомендуемые исходники
`src/main/java/dev/ehtid/thaumcraft/port/core/LegacyCrystalMeshSelection.java`
`src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java`
`src/neoforge/java/dev/ehtid/thaumcraft/world/ThaumcraftAuraSavedData.java`
`src/main/java/dev/ehtid/thaumcraft/port/core/AuraGenerationLogic.java`
`src/neoforge/java/dev/ehtid/thaumcraft/blockentity/InfusionMatrixBlockEntity.java`
`tools/verify_crystal_assets.py`, `tools/client_smoke.sh`, `.github/workflows/ci.yml`.

## Эталон и правила
Оригинальный JAR Thaumcraft 6.1.BETA26 (Minecraft 1.12.2), SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`. Файл находится в Library `/ThaumcraftPort/original_BETA26.jar` (если доступен), **НЕ коммитить JAR в GitHub**. Сверять Java bytecode/resources.
Команда пользователя **«П» означает разрабатывать и коммитить в main**, а не только отчитываться. Цикл работы — до ~20 минут, со статусом и честным процентом. Видео реального Minecraft — только при 90%, 95%, 100%; на 100% — полная игровая проверка. Настоящие скриншоты разрешены по необходимости. После существенного коммита обновлять handoff, запускать CoreParity/NeoForge build/сервер/при необходимости клиент. Не начинать проект заново. Улучшения читаемости шрифта сохранять; опциональную выдачу случайной ещё не полученной лунной записки при сканировании неба делать ближе к концу работы.

## Продолжение — 15.10.2026: живая генерация ауры около игрока

Кодовый коммит `57263b9f4ece21a794cfa176b6ac619886d7368b`: добавлена `ThaumcraftAuraWorldEvents`, регистрируемая в NeoForge server tick. Раз в 20 тиков для чанков в радиусе 1 от каждого игрока без записи в `ThaumcraftAuraSavedData` проверяются **пять уже загруженных** чанков (центр и четыре стороны). Никакой синхронной загрузки соседних чанков из ChunkEvent.Load — избегаем deadlock. По биомам выбирается совместимая BETA26 категория через `LegacyModernBiomeCompatibility` (новые/модовые биомы получают честный fallback 0.5), затем применяется существующая `AuraGenerationLogic.generatedBase` с Gaussian и атомарно сохраняется base/vis/flux (base/base/0). Уже существующие записи **не перегенерируются**, поэтому повторный вход не дарит Vis и не стирает flux. Это первая интеграция с текущим миром, НЕ полная source-exact система BETA26 worldgen/распределения ауры; новые биомы — сопоставление, а не исходные 1.12 IDs.

CI GitHub Actions #38687097830: CoreParity, NeoForge build, dedicated server smoke и JAR upload прошли успешные шаги (проверить финальный conclusion). В workflow реального клиента нужно следить за тестом новых событий ауры; зелёный запуск без in-game assertions не доказывает аутентичное распределение Vis в мире. Следующие шаги: проверить клиент/сохранение и оригинальный worldgen seed, затем 6-direction seed-based CrystalModel и настоящий инфузионный ритуал. Оценку пока держать ~86%.

## Night cycle 16.10.2026: original jar essentia capacity/transfer core

User requested continuing work overnight until 06:00 local. Automation scheduled hourly 02:00, 03:00, 04:00, 05:00 Europe/Moscow; each run must fresh-read main/Actions before making changes. Do not claim continuous processing between runs.

Last HEAD on fresh read was `2804a4c36b57356cf15d01356b1403036423e3af`. The final real client run `38789027560` PASSED: screenshot `08c-original-scanner-obj-in-hand` visibly shows source Thaumometer geometry and `08d-original-animated-salis-mundus-in-hand` shows the purple Salis item, with `CLIENT_ORIGINAL_ITEM_MODELS_PRESENT=YES`. Main CI `38789027838` successful. This confirms images loaded in a live world, not perfect parity of transformations/color.

Next source era mechanic: from actual original `TileJarFillable` javap bytecode, capacity exactly 250, single stored aspect, filtered suction 64, unfiltered 32, suction 0 at capacity, only UP side connectable, atomic take and clearing aspect when empty. Original `doesContainerAccept` checks label but `addToContainer` itself does not; do not silently conflate these. New immutable `LegacyEssentiaJarLogic` and `EssentiaJarParityTest` preserve these source properties, reject zero/negative transfer for safety and test no-free-resource behavior. CI now compiles/runs this test. It is only the **pure logic foundation**, NOT yet an in-game jar, and NOT a completed EssentiaHandler network. For real infusion next implement jar BlockEntity persistence + geometry/interaction and connect to actual essentia drains (12 block source range) before attempting player-visible recipes/output. Never invent stored essentia or grant output in tests without real ingredient debit.

All source JARs remain outside GitHub. Completion estimate ~86% until genuine in-game crafting/parity.
