# M12 — генерация мира и магическая флора

Рабочая ветка: `feature/worldgen-m12` (создана от `main` 2026-11-14). Это отдельное направление, не затрагивающее `feature/alchemy`, `feature/research`, `feature/infusion`, `feature/golems` и `feature/closure-near-complete`.

## Начальный checkpoint

- Прочитаны `main:AGENTS.md`, `main:ROADMAP_RU.md` и `main:analysis/PARALLEL_WORKFLOW_RU.md`.
- По основному roadmap M12 не принят: требуется генерация Silverwood/Greatwood, растений/грибов, руд, кристаллов и проверка в новых чанках.
- Этот коммит — **только организация независимой работы**, не реализация генерации и не доказательство тестов. Историческая оценка 59/100 не меняется.
- Кодовые поисковые запросы GitHub по `worldgen`, `Silverwood`, `Greatwood` не вернули результатов; отсутствие поисковой выдачи не доказывает отсутствия реализации. Нужен прямой просмотр дерева файлов и ресурсов.

## Следующая узкая задача — M12.1

1. Инвентаризировать фактические исходники и ресурсы генерации в `main` и сопоставить с оригинальным BETA26, не переписывая существующие классы.
2. Выбрать одну отсутствующую структуру/растение с оригинальными условиями размещения; реализовать только в собственных M12-файлах.
3. Добавить deterministic tests: биомы, высоты, частота, соседние чанки, отсутствие генерации в уже существующих чанках; отдельно full NeoForge build и реальный world-generation smoke.
4. Перед изменением общих регистров, Gradle или ресурсов согласовать интеграцию с владельцами других направлений.
5. Подготовить Draft PR в `main`; merge только с разрешения владельца.

## Доказательства

Пока **нет** новых Java-тестов, CI, Minecraft client/server smoke или игрового подтверждения M12.

## 14.11.2026 — M12.1, первый кодовый срез

- Повторно прочитаны правила `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, свой журнал и список веток/открытых PR (#1–#8). `main` и чужие ветки не изменялись.
- В собственном core-пакете создан `WorldgenChunkGate`: без побочных эффектов проверяет, что попытка размещения идёт **только для нового чанка**, разрешённого измерения, доступного текущего чанка и origin внутри него; координаты отрицательных блоков переводит в чанки через `Math.floorDiv`.
- Кодовый коммит: [4184a56](https://github.com/craftkot/tgaumcraft/commit/4184a56a29d360aa7193c4c574759573964e4b40).
- Добавлен `WorldgenChunkGateTest`: все 16 комбинаций разрешающих флагов, 13 отрицательных/положительных границ чанков, проверки смежного чанка и смешанных координат; ожидается **57 assertions** (16 + 39 + 2). Тестовый коммит: [1ac13dc](https://github.com/craftkot/tgaumcraft/commit/1ac13dc1ca6cbd7a0bb0bd9332c2a5b5baa011d8).
- **Доказательства:** файлы отправлены в GitHub. Java 21 `javac`, полная NeoForge-сборка, CI и Minecraft world smoke **в этом запуске не выполнены**; тесты добавлены, но результат PASS не заявляется. Нет доказательства, что этот guard уже подключён к реальному worldgen. Частота, биомы, деревья и оригинальные правила BETA26 не реализованы этим срезом.
- **Следующий шаг:** найти реальные M12 классы и исходные BETA26 правила размещения, интегрировать gate в узкий worldgen hook без изменения чужих регистров; затем выполнить Java 21 тест и настоящий worldgen smoke. Исторический индекс **59/100** не изменять.


## 14.11.2026 — M12.1, воспроизводимый isolated regression

- Повторно прочитаны актуальные `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и собственный lane; проверены существующие ветки и открытые PR #1–#8. Чужие ветки и `main` не менялись.
- Создан `tools/test_worldgen_chunk_gate.sh` — отдельный Java 21 runner, компилирующий именно production `WorldgenChunkGate.java` и `WorldgenChunkGateTest.java` без Gradle/Minecraft. Коммит [ae60d39](https://github.com/craftkot/tgaumcraft/commit/ae60d390d880d68301e7975b0a7a03315e529b0c).
- В текущем рабочем окружении `javac 21.0.12.1 --release 21` и `java` выполнили локальную эквивалентную компиляцию/запуск скопированных по содержимому двух классов: `M12 WorldgenChunkGate: OK (57 assertions)`. **Это локальная репродукция, не запуск runner из checkout и не CI по commit SHA**.
- Пока нет игрового worldgen hook, проверки BETA26 частот/биомов, полной NeoForge-сборки, dedicated-server или клиентского world smoke. Никакой дополнительной готовности M12 не заявлено; общий индекс 59/100 неизменен.
- Следующий шаг: получить полный список исходников worldgen/ресурсов и сверить правила конкретного дерева/кристалла с оригинальным BETA26; подключить безопасный guard в настоящий placement hook и проверить Minecraft generation. Общие registries и чужие подсистемы менять только после согласования.

## 14.11.2026 — M12.1, координатный guard для адаптера

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, собственный журнал, открытые PR #1–#8 и сравнение `main...feature/worldgen-m12` (до изменений: ahead 6, behind 0).
- Production commit [abb6dd5](https://github.com/craftkot/tgaumcraft/commit/abb6dd55bb16031cc54772f8ebc95767583d0548): `WorldgenChunkGate.mayAttemptPlacementAt` вычисляет принадлежность origin текущему чанку из координат, не доверяя переданному извне флагу; без чтения/подгрузки соседних чанков.
- Regression commit [2e41b2d](https://github.com/craftkot/tgaumcraft/commit/2e41b2de646d42e00518964d1543428d9a043d9c): 169 комбинаций X/Z на отрицательных и положительных границах × 4 проверки = **676 новых assertions**. Общий standalone тест теперь ожидает **733 assertions** (57 + 676).
- В локальном Java 21 окружении компилировалась и исполнялась **отдельно вручную воспроизведённая минимальная реализация** новой функции и 676 тестовых случаев: **676 PASS**. Это не запуск точных файлов из GitHub checkout, не проверка всего runner, не CI, не NeoForge и не Minecraft worldgen smoke. Не заявлять 733 PASS без запуска репозиторного теста.
- В игровом worldgen hook новая функция пока не вызывается. BETA26 частоты/биомы/деревья ещё не проверены. Общий индекс **59/100** без изменений. Следующий шаг: прямой аудит классов M12 и оригинального BETA26; после подтверждения правил подключить конкретный placement hook без изменений чужих регистров.

## 14.11.2026 — M12.1, проверка полного контекста и границ int

- Перечитаны актуальные `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, собственный lane; просмотрены 8 открытых PR и ветки. Общие файлы и чужие ветки не изменены.
- [da07b81](https://github.com/craftkot/tgaumcraft/commit/da07b813af120fd6291ef32292d42f6697ab5f99): расширен **реальный репозиторный тест** `WorldgenChunkGateTest` для `mayAttemptPlacementAt`: 8 комбинаций флагов × 13² origin-позиций (1352 assertions), плюс 15 проверок предельных `int` координат. Вместе с прежними 733 ожидается **2100 assertions**.
- Это усиление тестового покрытия существующего core gate, **не подключение к NeoForge worldgen**. Новые проверки пока **не исполнялись**; число 2100 — расчёт ожидаемого количества, не PASS. CI, full NeoForge и игровой smoke не подтверждены.
- Следующий шаг: исполнить точный `tools/test_worldgen_chunk_gate.sh` из checkout, затем аудит оригинальных BETA26 правил и реального размещения Silverwood/Greatwood. Общий индекс **59/100** неизменен.


## 14.11.2026 — M12.1, вертикальные границы генерации

- Fresh-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и журнал ветки; проверены открытые PR #1–#8. Рабочая ветка только `feature/worldgen-m12`.
- Код [0b22fb6](https://github.com/craftkot/tgaumcraft/commit/0b22fb68975fb5dc6d2fcd5d9d5d8ec2dac686a6): отдельный loader-neutral `WorldgenHeightGate` для проверки вертикальной точки размещения и полного диапазона корней/кроны относительно dimension build limits (верхняя граница exclusive). Арифметика расширена до `long`, чтобы избежать переполнения при крайних координатах.
- Тест [d3e10bd](https://github.com/craftkot/tgaumcraft/commit/d3e10bdd0b7935fd1e75e407a14e0b19de673741): 19 assertions для границ, корней/кроны, пустых/перевёрнутых диапазонов и переполнений. Runner [79e6563](https://github.com/craftkot/tgaumcraft/commit/79e6563c34e08b07737c2f9803c9bb06be01ebf0): `bash tools/test_worldgen_height_gate.sh`.
- **Не подтверждено:** тесты не запускались из checkout (сетевой `git clone` в доступном окружении завершился `Could not resolve host: github.com`); CI, NeoForge build, реальный Minecraft worldgen и соответствие конкретной частоты/биомов BETA26 не проверены. Новые классы пока не подключены к игровому генератору.
- Следующий шаг: получить фактическое дерево worldgen и исходные BETA26 placement rules, связать guard с конкретной feature без затрагивания общих регистров, запустить оба Java runners и настоящий worldgen smoke. Общий индекс 59/100 без изменений.

## 15.11.2026 — M12.1, запрет origin за пределами высоты мира

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, текущий lane и список веток/PR #1–#8. Работа только в `feature/worldgen-m12`.
- Выявлен дефект собственного `WorldgenHeightGate.spanWithinBuildHeight`: он разрешал origin вне build limits, если относительные offsets сдвигали весь проверяемый span обратно внутрь (например, originY=400 и offsets [-100,-90] при мире [-64,320)).
- Исправление [53ad226](https://github.com/craftkot/tgaumcraft/commit/53ad226c99cd49c85998f2db63d3b533dbac15a9) требует, чтобы сам origin был допустим, независимо от границ span; скорректирован комментарий API.
- Регрессии [4b474fd](https://github.com/craftkot/tgaumcraft/commit/4b474fd05d95ffc4a8bce70ac62cc36ef04ed103): два запрещённых out-of-world origin с формально допустимыми footprints и один допустимый near-ceiling origin. Ожидаемое количество height assertions теперь **22**, не подтверждённый PASS.
- Точное исполнение файлов из GitHub checkout и полный NeoForge build в этом подходе не выполнены: окружение `git ls-remote` вернуло `Could not resolve host: github.com`. Новые CI/GameTest/worldgen smoke не подтверждены. Это исправление loader-neutral helper, **не реальная генерация деревьев**.
- Следующий приоритет: получить исходники worldgen и оригинальные BETA26 biome/frequency rules, внедрить один узкий реальный placement hook, выполнить Java 21 + NeoForge + генерацию новых чанков. Индекс 59/100 без изменения.


## 15.11.2026 — M12.1, единый preflight размещения

- Перечитаны актуальные main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md и собственный lane; просмотрены ветки и восемь открытых PR. Сравнение main...feature/worldgen-m12 перед записью журнала: ahead 21, behind 0; изменения только в собственных M12 core/test/tools/lane файлах.
- Production [c95324c](https://github.com/craftkot/tgaumcraft/commit/c95324c219bb2a2ff349751e7d7372e75c749363): WorldgenPlacementGate.mayAttempt объединяет проверки нового/доступного чанка, принадлежности origin и вертикального footprint. Не делает world reads, chunk loads или мутаций.
- Regression [076bd60](https://github.com/craftkot/tgaumcraft/commit/076bd60716d71e1a6d9d7780b312f6fd38bbe682): 314 deterministic assertions (8 flag combinations + 300 coordinate/neighbor cases + 6 vertical/extreme cases); runner [55f14fc](https://github.com/craftkot/tgaumcraft/commit/55f14fcbb4038fbc7063fe49f8596fa77929949a): bash tools/test_worldgen_placement_gate.sh (javac --release 21).
- Проверка GitHub по 55f14fc: combined statuses пуст, PR-triggered workflow runs пуст. **Тесты не запускались в этом подходе**, PASS не заявлен; не подтверждены full NeoForge compile и настоящий Minecraft worldgen. Класс пока не вызван из реального worldgen feature; оригинальные BETA26 biome/chance/terrain правила не проверены. 59/100 без изменения.
- Следующий шаг: аудит настоящих M12 NeoForge feature/placed-feature классов и BETA26 генераторов, подключение preflight к одному конкретному оригинальному placement path без правки чужих registries; запуск точного runner и Minecraft smoke новых чанков.

## 15.11.2026 — M12.1, независимая Java 21 проверка composition gate

- Повторно прочитаны `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и собственный lane. Просмотрены существующие ветки и открытые PR. Работа ограничена `feature/worldgen-m12`.
- Проверена доступность прямого Git checkout: `git ls-remote https://github.com/craftkot/tgaumcraft.git` завершился `Could not resolve host: github.com`. Поэтому точный runner из checkout **не запускался**.
- По содержимому, прочитанному напрямую из GitHub `feature/worldgen-m12`, в отдельном временном каталоге вручную воспроизведены **исполняемые тела** трёх production-классов (`WorldgenChunkGate`, `WorldgenHeightGate`, `WorldgenPlacementGate`) и `WorldgenPlacementGateTest`. Скомпилировано `javac --release 21` и запущено `java`: **M12 WorldgenPlacementGate: OK (314 assertions)**. Комментарии и форматирование не копировались, поэтому это **эквивалентный локальный прогон, не проверка точных GitHub bytes/checkout/CI**.
- Фактический NeoForge worldgen placement hook по-прежнему отсутствует в проверенной части M12; BETA26 частоты, биомы и Silverwood/Greatwood не подтверждены. Полная NeoForge-сборка, игровой worldgen smoke, CI на точном SHA не выполнялись. Историческая оценка **59/100** не меняется.
- Следующий приоритет: инвентаризация реального NeoForge worldgen и BETA26 генераторов; привязка одного source-verified дерева/растения к реальному placed-feature пути с тестом новых чанков. Изолированные guards больше не расширять без реального потребителя.


## 15.11.2026 — M12.1, прямой аудит дерева исходников и Draft PR #9

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, свой журнал и 8 существующих открытых PR. HEAD ветки перед работой: `92882e7ce5759b8d097a077aa1ef1158062c458b`; сравнение с main: ahead 23, behind 0, только 10 M12-файлов.
- Получен **полный GitHub Git tree** (`recursive=1`, `truncated=false`), а не поисковая выдача. В `src/neoforge/java/dev/ehtid/thaumcraft/world/` есть только `SalisMundusTransformQueue`, `ThaumcraftAuraSavedData`, `ThaumcraftAuraWorldEvents`; отдельного NeoForge worldgen feature/placed-feature/tree adapter на текущем HEAD нет. `CrystalClusterBlock` присутствует в `block/`, но регистрация worldgen из этого не следует.
- Поэтому существующие `Worldgen*Gate` пока **не имеют реального игрового потребителя**. Следующая кодовая задача — исходно подтверждённый генератор одного дерева с отдельной интеграцией, не очередные абстрактные guards. Исходные BETA26 вероятности/биомы/форма деревьев по этой инвентаризации **не установлены**.
- Создан **Draft PR [#9](https://github.com/craftkot/tgaumcraft/pull/9)** из `feature/worldgen-m12` в `main` с явным предупреждением, что он не готов к merge. `main` и чужие ветки не менялись.
- CI для исходного HEAD `92882e7`: combined status `ci/circleci: verify-connection` = **FAILURE** ([job](https://app.circleci.com/workflow/a1aff789-2a89-45e3-90b7-a0893156d95a/job/1199906a-e2a2-4334-9cbc-f06de5caeeda)); GitHub PR-triggered workflow runs = 0. Причина CircleCI FAILURE без job logs не установлена. Нельзя называть эту ветку CI-green.
- `git ls-remote` в локальном окружении всё ещё завершается DNS `Could not resolve host: github.com`; точные checkout runners, NeoForge build и Minecraft gameplay в этом подходе не выполнялись. Ранее подтверждённые 314 assertions относятся только к локальной репродукции, не к exact checkout.
- **Следующий шаг:** извлечь исходные BETA26 правила Silverwood/Greatwood, создать минимальный собственный NeoForge feature/placed-feature путь с guard и тестами новых чанков, согласовать изменение общих registries отдельным PR. Индекс 59/100 не менять.


## 15.11.2026 — M12.1, исходный аудит Silverwood/Greatwood

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md и свой lane; проверены HEAD `383f446` до записи, ветки и 9 открытых PR; Draft PR #9 остаётся открытым, без merge.
- Извлечены архивные Java 1.12 исходники `ThaumcraftWorldGenerator`, `WorldGenSilverwoodTrees`, `WorldGenGreatwoodTrees` из стороннего репозитория SpencerGraffunder/Thaumcraft26 (reference/java_old-1.12). Результат: документ [worldgen-m12-source-audit.md](worldgen-m12-source-audit.md), commit [773a511](https://github.com/craftkot/tgaumcraft/commit/773a511aaa790a6193c44ca57e70712364847442).
- Установлены исходные попытки 1/80 (Silverwood), 1/25 (Greatwood), смещение центра чанка +/-4, отдельные biome predicates, Silverwood высота 7..10, Greatwood 2x2 trunk, spider flag 1/8. Отмечено, что современный `rarity_filter + in_square` из другого порта не обеспечивает идентичного RNG/позиционного паритета.
- Это **анализ архивной копии**, а не проверка идентичности с оригинальным BETA26 JAR; никаких новых игровых features, тестовых PASS, full NeoForge или Minecraft smoke в этом запуске нет. CI предыдущего HEAD: `ci/circleci: verify-connection` FAILURE, причина без логов неизвестна. Общая готовность остаётся 59/100.
- Следующий шаг: реализовать первый реальный Silverwood Feature consumer в собственном namespace, затем согласовать отдельную регистрацию, проверить точный checkout/NeoForge и генерацию новых чанков. Общие регистры, main и чужие ветки не изменены.


## 15.11.2026 — M12.1, source-era tree attempt RNG slice

- Fresh-read main AGENTS/ROADMAP/PARALLEL_WORKFLOW and own lane; inspected PR #9 (Draft) and other open PR; branch compared with main before edits: ahead 26, behind 0.
- New isolated production `LegacyTreeAttempt` preserves BETA26-style independent 1/80 Silverwood and 1/25 Greatwood attempts, centered nine-wide chunk coordinates, strict biome support thresholds, legacy magical/18/28 overrides and conditional 1/8 spider roll. Caller supplies original RNG; no invented modern biome-ID mapping or shared registration.
- Commits: production `1410984711e646f82e42430610baaed6eddf39f3`, regression `11b5f952b7e4c8983eba4b5a9f453c92a9cf8640`, Java21 runner `ab42c1c5dd2ad6fc2b58707fb416a29bb2288b26`.
- Deterministic test exercises 169 assertions including RNG consumption and overflow rejection; **not executed on exact checkout**. Local `git ls-remote` still fails DNS (`Could not resolve host: github.com`); Java 21 is available. No NeoForge build or Minecraft new-chunk smoke; code is a real selection-policy component, **not** a placed-feature consumer.
- Source reference remains third-party archived Java 1.12, not byte-verified original BETA26 JAR. Do not claim exact RNG parity across modern worldgen call sites until placement integration preserves ordering.
- Next: implement actual Silverwood Feature and replacement/soil geometry in own namespace, then coordinate shared feature registration and test on new chunks. Draft PR #9 stays unmerged; 59/100 unchanged.

## 15.11.2026 — M12.1, Silverwood conservative write envelope

- Fresh-read main AGENTS/ROADMAP/PARALLEL_WORKFLOW and this lane; inspected open PRs (#9 remains Draft), branches and main...M12 comparison (before edits: ahead 30, behind 0). Only M12 branch written.
- Read the archived `WorldGenSilverwoodTrees.java` directly from SpencerGraffunder/Thaumcraft26. Its canopy loop reaches X/Z ±5 and Y `origin+height+3+nextInt(3)`; roots reach `originY-1`. The legacy preflight checks only the narrower collision volume, so an origin-only chunk guard does NOT guarantee writes remain in the current chunk.
- Production [650d106](https://github.com/craftkot/tgaumcraft/commit/650d1061970b4838bbb1dda35eaf94dd0587dc1c): `SilverwoodLegacyFootprint` conservatively bounds all possible writes against modern build-height and chunk limits using long arithmetic. This rejects edge origins rather than reading/writing unloaded neighboring chunks. This is a **deliberate safety divergence** from legacy placement, not exact worldgen parity; some valid legacy trees will be skipped.
- Regression [880c8c7](https://github.com/craftkot/tgaumcraft/commit/880c8c71af50a6c2ec86998d9ebe1d76db621920): 17 deterministic checks for valid height, roots, canopy, chunk edges, negative chunks and integer overflow. Runner [8e5ca11](https://github.com/craftkot/tgaumcraft/commit/8e5ca11b024cdbd96f8e3c660bb0ce5b7a70bd35): `bash tools/test_silverwood_legacy_footprint.sh`.
- Tests are **committed, not yet executed on exact checkout**. Last known prior SHA `07190b8` combined CI status: CircleCI verify-connection FAILURE; new exact-head CI, full NeoForge build and Minecraft new-chunk smoke unverified. No real Silverwood `Feature`, log/leaf registry or worldgen hook exists yet. Historical 59/100 unchanged.
- Next: implement the actual Silverwood placement consumer and original block-state mapping; reconcile cross-chunk canopy behavior and coordinate shared feature registration before integration. Draft PR #9 remains unmerged.

## 15.11.2026 — M12.1, Silverwood read-only soil/trunk preflight

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and this lane; checked six feature branches, open PR #1–#9 and current main...M12 diff (ahead 34 before work, behind 0). Existing PR #9 remains Draft; no merge.
- Production [ebcabb9](https://github.com/craftkot/tgaumcraft/commit/ebcabb903d4ff8ab8e3eaa140046b6900fa97238): `SilverwoodPlacementPreflight` supplies a read-only bridge from conservative chunk/height bounds to caller-provided soil and trunk replaceability predicates. It short-circuits invalid coordinates before world reads and avoids partially placing an obstructed trunk.
- Regression [af45a2f](https://github.com/craftkot/tgaumcraft/commit/af45a2f6a09c1f4d070be70786a5788d98279063): 21 assertions for soil, trunk obstruction at all 10 heights, negative chunks, boundaries, null predicates and no reads on invalid origin. Java 21 runner [f5c3175](https://github.com/craftkot/tgaumcraft/commit/f5c31750ae4ec1ae18b04c91678e31bf42c62fc3).
- **Verification:** Java tests are committed but not executed on exact checkout; local Git still cannot resolve github.com. New head `f5c3175` has no reported combined CI statuses at inspection; prior `e7881d3` CircleCI verify-connection was FAILURE. No full NeoForge compile, client/server smoke or in-game tree placement evidence. The preflight is NOT a NeoForge Feature or full BETA26 collision parity.
- Next: source-verify BETA26 soil/replacement logic and implement a real Silverwood Feature with actual Thaumcraft blockstates in own namespace; coordinate any shared registration before writing it. 59/100 unchanged.


## 15.11.2026 — M12.1, архивная геометрия Silverwood log writes

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md и собственный lane; просмотрены шесть feature-веток, открытые PR #1–#10, main...M12 перед записью (ahead 38, behind 0). Main и чужие ветки не изменялись.
- Прочитан архивный WorldGenSilverwoodTrees.java в SpencerGraffunder/Thaumcraft26; по нему добавлен реальный **block-write phase** для ствола/корней Silverwood: [8be0b5e](https://github.com/craftkot/tgaumcraft/commit/8be0b5eb8e9dea61662c76268c448038a93b161b). Сохраняются крестообразный ствол, метаданные осей 0/1/2, четыре диагональных корня, верхние ветви и 8 последовательных RNG-проверок. Это callback writer, а не зарегистрированный NeoForge Feature.
- Тест геометрии, границ и повторяемости [f8ef80f](https://github.com/craftkot/tgaumcraft/commit/f8ef80fab53dbb85f5fec2b860196d2352b59508); Java 21 runner [e3266af](https://github.com/craftkot/tgaumcraft/commit/e3266afcf8f63da4015e299b68e64270cca6c566). **Runner на точном checkout не выполнен**: git ls-remote по-прежнему сообщает DNS Could not resolve host: github.com. Нельзя утверждать PASS.
- ВАЖНО: исходник сначала делает canopy random rolls, затем log random rolls. Caller обязан использовать тот же Random после canopy для полного parity; этот срез не включает листья, Shimmerleaf, NeoForge blockstate mapping, placement registration или GameTest. Оригинальный BETA26 JAR не сравнивался побайтово.
- Предыдущий HEAD 92147db CI: CircleCI verify-connection FAILURE; GitHub Actions run #2971 FAILURE. Причина не установлена, без логов не приписывать ошибку к Java-коду. Новый HEAD CI требует повторной проверки.
- Draft PR #9 остаётся неслитым. Следующий шаг: перенести canopy writes и точный RNG порядок, затем создать в собственном namespace NeoForge Feature с реальными blockstates; изменение общего регистра согласовать отдельно. Full NeoForge и Minecraft new-chunk smoke отсутствуют. Оценка 59/100 без изменений.


## 15.11.2026 — M12.1, shared RNG phase ordering (not canopy parity)

- Fresh-read main AGENTS/ROADMAP/PARALLEL_WORKFLOW and this lane; inspected main...M12 (ahead 42, behind 0 before edits) and open PR #1–#10. Draft PR #9 remains unmerged.
- Production [50aa7c7](https://github.com/craftkot/tgaumcraft/commit/50aa7c79f67a7045acb15c7a870c04449eceba9c): `SilverwoodLegacyTreePhases` executes a supplied canopy phase **before** the existing log/root phase, with the **same Random instance** and eager argument validation. This addresses a documented RNG-order integration hazard; it does **not** implement leaf geometry or a NeoForge Feature.
- Regression [414866a](https://github.com/craftkot/tgaumcraft/commit/414866a48dc7800bee70f92f43123a39abc691fd): deterministic 7..10-height ordering, callback count, RNG continuation, invalid heights and null callbacks. Java 21 runner [75ad7ea](https://github.com/craftkot/tgaumcraft/commit/75ad7ea9ccea6aa228b5cd68e23d1e481883eb70): `bash tools/test_silverwood_legacy_tree_phases.sh`.
- **Evidence boundary:** tests committed, not executed on exact checkout; no claim of PASS. Previous HEAD `e0e9856` combined CI = CircleCI verify-connection **FAILURE**, [job](https://app.circleci.com/workflow/47966a49-cbab-4966-b2a1-13eb151a4c24/job/2b3a93b9-57eb-4102-af35-db894b3afc87). Exact-head CI and full NeoForge/Minecraft smoke unverified.
- Archived third-party Java 1.12 source is not byte-verified BETA26 JAR. Next: recover source-verified canopy write algorithm and integrate it with the phase seam; then isolated NeoForge Feature and coordination for shared registrations. Historical 59/100 unchanged; no main/other branch edits or merge.


## 15.11.2026 — M12.1, archived Silverwood canopy writes and RNG

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and this lane; compared `main...feature/worldgen-m12` (ahead 46, behind 0 before changes), checked feature branches and open PRs (#9 remains Draft).
- Re-read archived `reference/java_old-1.12/common/world/objects/WorldGenSilverwoodTrees.java` from SpencerGraffunder/Thaumcraft26. Added `SilverwoodLegacyCanopy` with source-era leaf write coordinates, layer radius/clamping, single canopy-height random roll and **one nextInt(8) per candidate before replaceability**. This preserves the important RNG stream even for nonreplaceable blocks. Source is third-party archived Java, not independently verified against the original BETA26 JAR.
- Code [80984be](https://github.com/craftkot/tgaumcraft/commit/80984be17c8910aa3702e2d1cc55ffaeee5054b4); deterministic regression [0196856](https://github.com/craftkot/tgaumcraft/commit/0196856ff9ac4003c4aa12fe85492ba2be66b38c); Java 21 runner [55f3d91](https://github.com/craftkot/tgaumcraft/commit/55f3d91db93a3da696781ae0d2b42391cbafcd0f).
- Exact checkout test **not executed**: local `git ls-remote` still fails DNS `Could not resolve host: github.com`. At code+test+runner HEAD `55f3d91`, CircleCI verify-connection **PENDING** (https://app.circleci.com/workflow/1204ce13-8a9b-4500-ae21-b7f13cad8dcb/job/7c7194e7-1f6f-4fca-925e-af4c8e4558e7); GitHub PR workflow runs returned none. No full NeoForge build, no new-chunk Minecraft smoke.
- Canopy callback is **not yet connected** to `SilverwoodLegacyTreePhases`, and no NeoForge Feature, block-state mapping or shared registration exists. Next: integrate the two existing phase callbacks with a small source-aligned core test; then implement isolated NeoForge Feature and coordinate shared registration. PR #9 remains Draft; main/other branches untouched, no merge. Audit index 59/100 unchanged.


## 15.11.2026 — M12.1, integrated Silverwood canopy/log core seam

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; checked main...M12 (ahead 50, behind 0 before writes), open PR #1–#10 and feature branches. Existing Draft PR #9 remains unmerged.
- Production [95c431c](https://github.com/craftkot/tgaumcraft/commit/95c431c634c4db84f1ae316758db4f75bccf5964): new `SilverwoodLegacyTree.place` connects existing canopy and logs through `SilverwoodLegacyTreePhases` using a single caller-provided Random, without touching shared registries. Validates all callbacks before consuming RNG or writing.
- Regression [b8bea41](https://github.com/craftkot/tgaumcraft/commit/b8bea419f55e1e565a59cc6af3d84938309046c9): 12 height/seed cases compare complete ordered leaf/log write traces and next RNG value against explicit legacy-phase invocation, plus 2 invalid-height checks (26 assertions expected). Runner [0f20d6a](https://github.com/craftkot/tgaumcraft/commit/0f20d6acda72effd44223b8598441b393ab82804): `bash tools/test_silverwood_legacy_tree.sh` with javac --release 21.
- **Not executed** on exact checkout: Java runner, full NeoForge compile, and Minecraft new-chunk smoke. This is an integrated loader-neutral block-write callback, NOT a registered NeoForge Feature, worldgen biome hook, or verified in-game tree. The test compares two implementations of the same archived interpretation, not independently verified BETA26 JAR behavior.
- CI for runner HEAD 0f20d6a: CircleCI `ci/circleci: verify-connection` **PENDING** (https://app.circleci.com/workflow/07a6584a-64b7-455c-baf2-9305b2b1c0b9/job/44bdfca0-e095-4701-9c6d-6010659a9f43); GitHub PR-triggered workflow runs empty. CI on journal commit must be rechecked.
- Next: build an isolated NeoForge Feature consumer mapping leaf/log states, then coordinate feature registration and test newly generated chunks. Preserve chunk/height safety preflight and document the conservative legacy divergence. No main/other-branch writes or merge. Historical audit 59/100 unchanged.


## 15.11.2026 — M12.1, isolated NeoForge Silverwood consumer (unregistered)

- Fresh-read main AGENTS/ROADMAP/PARALLEL_WORKFLOW and own M12 lane; checked main...M12 (ahead 54, behind 0 before writes), open PRs (#9 still Draft), other branches. Main and shared registries/Gradle were not edited.
- Code [ea7cbc6](https://github.com/craftkot/tgaumcraft/commit/ea7cbc630f97648af5a6a024a9797a6d43451afd): added `src/neoforge/java/dev/ehtid/thaumcraft/worldgen/SilverwoodLegacyFeature.java`, an **unregistered** `Feature<NoneFeatureConfiguration>` with injected log/leaf BlockStates. It checks soil, conservative same-chunk/height preflight, and replaceability before invoking existing combined canopy/log callbacks; maps legacy log metadata to pillar axes. It is a genuine Minecraft world-write consumer but is **not reachable from new-chunk worldgen** without coordinated registration.
- Source wiring guard [7fcd327](https://github.com/craftkot/tgaumcraft/commit/7fcd3275ad095df9c4bdd6074cd45a9102668a2b): `python3 tools/test_silverwood_neoforge_wiring.py` (8 static checks). No claim that this checks actual compilation or behavior.
- Explicit differences / risks: Java `Random` is seeded from modern `RandomSource.nextLong()`, so exact BETA26 RNG parity is not established; grass/dirt soil rule and `canBeReplaced` are provisional pending source/JAR comparison; conservative same-chunk rejection loses edge trees. Missing original Shimmerleaf placement, biome placement and feature registration. `Feature` code has **not** passed NeoForge compilation.
- Local `git ls-remote` still fails DNS `Could not resolve host: github.com`; Java 21 available, exact checkout unavailable. Previous exact HEAD `f51e93b` combined CI CircleCI verify-connection FAILURE (https://app.circleci.com/workflow/990af6a7-8632-4e45-8b00-f42b15eea458/job/8336a204-8ed2-4a5c-a0ca-c66e412da75b); not evidence of source compilation failure. Full NeoForge build and Minecraft new-chunk smoke unverified.
- Next: obtain exact checkout and run source guard + NeoForge compile, fix API errors, source-verify original soil/replaceability and RNG, coordinate registration with integration owners, then run actual new-chunk worldgen smoke. Draft PR #9 stays unmerged. Historical audit 59/100 unchanged.


## 15.11.2026 — M12.1, NeoForge Silverwood placement result semantics

- Re-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and own lane; checked `main...feature/worldgen-m12` (57 ahead, 0 behind before edits), other branches and open PRs. Draft PR #9 remains unmerged.
- Fixed `SilverwoodLegacyFeature.place` returning `true` unconditionally after callbacks, even when every `WorldGenLevel.setBlock` failed. Production [5a56f5f](https://github.com/craftkot/tgaumcraft/commit/5a56f5f7e8631ee70d8ec7d14e6772ed2f719a59) now counts only successful writes; feature returns true iff at least one block was placed. `setIfReplaceable` propagates `setBlock` boolean.
- Expanded source-level regression guard [c2c39ab](https://github.com/craftkot/tgaumcraft/commit/c2c39ab2d4c906b8360ae8191d09c46cad81766f) to assert placement success semantics. A read-only connector inspection confirmed the updated source contains expected guards, but the exact-checkout Python runner, Java tests and NeoForge compilation were **not executed** because local Git DNS still cannot resolve github.com.
- Exact test commit `c2c39ab` CircleCI `ci/circleci: verify-connection` was PENDING at inspection: https://app.circleci.com/workflow/33cf67b9-06c1-4533-bac8-a30609297049/job/f13fcffc-e600-4e5c-ad84-6f1991e9c96c . GitHub Actions returned no PR-triggered runs. No gameplay smoke or registration. This corrects the API return contract, **not** BETA26 parity or a working worldgen hook.
- Next: obtain exact-checkout full NeoForge compile for the feature and test in-game with injected real Silverwood blockstates; coordinate shared registration and verify biome/chunk placement without touching common registries unilaterally. Historical audit remains 59/100.


## 15.11.2026 — M12.1, repair stale NeoForge source guard

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; checked main...feature/worldgen-m12 (ahead 60, behind 0 before write), open PRs #1–#10 and branch inventory. Draft PR #9 remains unmerged.
- Found a **real false-negative in the existing source-level regression script**: `tools/test_silverwood_neoforge_wiring.py` demanded literal `if (canReplace(level.getBlockState(pos)))`, but production now correctly delegates both leaf and log writes to `setIfReplaceable`. The obsolete assertion would fail even when the intended guarded writes exist.
- Fixed only the owned M12 test script: [26ef715](https://github.com/craftkot/tgaumcraft/commit/26ef7153ec4d3e3c48dc20dffa7a6b71ece163c3) now checks both guarded leaf and log write call sites. This is a test-maintenance change, **not a new worldgen mechanic or evidence of compilation**.
- CI for 26ef715: CircleCI verify-connection PENDING at inspection (https://app.circleci.com/workflow/1dcd7a4a-a367-4a95-a5fb-b310b994dbd2/job/730184e6-acf9-41bf-a604-fc7141382aaf). Exact-checkout Python runner, full NeoForge compile, and Minecraft new-chunk smoke NOT executed: local git ls-remote still fails DNS resolution of github.com. No PASS claimed.
- Next: run the corrected script against exact checkout, then compile SilverwoodLegacyFeature with NeoForge, validate block-state injection, and coordinate shared feature/biome registration. Main, other lanes, shared registries and Gradle untouched; historical 59/100 unchanged.


## 15.11.2026 — M12.1, repair Python syntax in wiring guard

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (62 ahead, 0 behind before write), inspected open PRs and branch inventory. Draft PR #9 remains open, unmerged.
- Discovered a **literal backslash-n** embedded in tools/test_silverwood_neoforge_wiring.py after the previous test maintenance edit: `in source\\n                          and`. This is invalid Python syntax, so the source-level wiring guard could not run at all.
- Fixed the owned script by replacing that escaped text with a real line break in commit bcb392ac87fc4b64bedd849ba2146f40877bc725. No gameplay production code, shared registry, Gradle, main or other lane modified.
- The fix was confirmed by inspecting the GitHub file content before write; exact-checkout execution, Python syntax compile, NeoForge build and Minecraft new-chunk smoke remain **unverified**. Local git ls-remote continues to fail DNS resolution of github.com. No PASS claimed.
- Next: execute python3 tools/test_silverwood_neoforge_wiring.py on an exact checkout, then compile the isolated NeoForge Silverwood Feature, resolve any API issues and coordinate feature/biome registration before new-chunk gameplay tests. Historical 59/100 unchanged.


## 15.11.2026 — M12 Silverwood: canopy-first log overwrite

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (ahead 64, behind 0 before changes) and inspected open PRs including Draft #9. No shared registries, Gradle or other branches modified.
- Fixed an actual placement-path conflict: `SilverwoodLegacyTree` writes canopy before logs, while `setIfReplaceable` rejects existing leaves when their state is not replaceable. Commit `ae11ad43c84d0550437256e5dd25979301c50838` adds a dedicated log writer permitting replacement of matching Silverwood leaf blocks while still checking the result of `setBlock`.
- Regression source guard updated in `613f4504e997e701b2ca6e252703ba3adc688e91` to verify both canopy-first log path and leaf overwrite condition.
- CI status API for `613f450` returned no statuses or PR workflow runs. Tests, exact-checkout NeoForge compile, native GameTests and Minecraft new-chunk worldgen **not executed**; no PASS claimed. Current predicate matches leaf block type, not provenance: potential replacement of pre-existing same-type leaves must be addressed with per-attempt placed-leaf tracking before registration. Original BETA26 parity remains unproven.
- Next: constrain log overwrite to leaves placed by this attempt, execute source guard and NeoForge build, then coordinate Feature registration and in-game worldgen smoke. Historical index 59/100 unchanged.


## 15.11.2026 — M12 Silverwood: provenance-limited canopy overwrite

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; inspected branch inventory, open PRs and main...M12 (67 ahead, 0 behind before writes). Existing Draft PR #9 remains open; no merge.
- Production commit `0b01128999be01a9530aa02930cf45df11ee405b`: track positions of **successful** canopy leaf writes in a per-invocation `HashSet<BlockPos>`. Logs may replace otherwise non-replaceable leaves only at those tracked positions, not arbitrary pre-existing Silverwood foliage. Other replaceable blocks retain the previous rule; `setBlock` success remains required.
- Source guard commit `7c1633559d7824789d05ab5348141d5076833ac8` checks the provenance set and rejects the previous same-block-type bypass. This is a static source regression, not a behavioral GameTest.
- Previous HEAD `bf402aa` CircleCI `ci/circleci: verify-connection` FAILURE: https://app.circleci.com/workflow/56412684-2b1a-45ad-9627-f7200d740744/job/e2a100a6-e1d0-4de6-a797-a7ad2dc91521 . Exact checkout Python runner, Java Core suite, NeoForge compilation and in-game new-chunk smoke remain unverified. Failure of verify-connection is not evidence of Java compilation failure.
- Next: execute source guard and full NeoForge compilation against exact branch checkout, add an executable placement test with a fake world / GameTest that proves adjacent pre-existing foliage survives, then coordinate registration and verify new chunks. BETA26 RNG, soil and biome parity still unproven. Main, shared registries, Gradle and other branches untouched; historical index 59/100 unchanged.


## 15.11.2026 — M12 Silverwood: stale canopy-position safety

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; inspected open PRs and branch inventory. Before edits main...M12 was ahead 70, behind 0; Draft PR #9 remains unmerged.
- Production d0501aed44c964de80f1a9e16da6ff3cf314cd58: a tracked successful leaf position is no longer sufficient by itself to authorize log overwrite. The current block must still equal this feature invocation's injected leaf BlockState; otherwise the log writer rejects a stale position unless the current block is ordinarily replaceable. This narrows an intervening-write edge case without changing shared registries.
- Source regression 023ce3ab08603052a91ebe5557a97f24c5b92652: static guard requires exact leaf-state check and propagation of injected leaf state to the log writer.
- Tests are source assertions only and not executed against a checkout. Full NeoForge compile, GameTest and Minecraft new-chunk smoke remain unverified; no BETA26 RNG/biome/soil parity claimed. Remaining issue: this still is not transactional worldgen, and Feature is unregistered. Next: exact-checkout Python guard, Java/NeoForge compilation, executable GameTest with pre-existing and intervening block writes, then coordinate registration. Historical audit remains 59/100.


## 15.11.2026 — M12.1, regression wiring test repair

- Re-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, this lane, open PRs and `main...feature/worldgen-m12` (before edits: ahead 73, behind 0).
- Found two stale literal assertions in `tools/test_silverwood_neoforge_wiring.py`: the actual leaf callback uses local `pos`, and log helper now requires `leaves` as a separate argument. Corrected test in commit [`69795f5`](https://github.com/craftkot/tgaumcraft/commit/69795f5e7c2b1da4ae42597643469bdbe3b1d06f).
- Checked all 15 equivalent source substring predicates against freshly fetched exact GitHub source in connector JavaScript: **15/15 true**. This is **not** execution of the Python runner, not NeoForge compilation, not Minecraft GameTest. No gameplay implementation changes in this iteration.
- Remaining: execute exact Python runner and Java 21 runners from checkout; compile full NeoForge; coordinate feature registration and test real new chunks. Draft PR #9 remains unmerged. No shared registries, Gradle, main or other branches touched; audit index 59/100 unchanged.


## 15.11.2026 — M12 Silverwood: одноразовое разрешение на замену собственной листвы

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md и свой lane; проверены main...M12 (перед работой ahead 75, behind 0), список веток и открытых PR. Draft PR #9 остаётся без merge.
- Production commit 0c4bcd5e315be89642c87dab45d2c9d32d13ec7e: при успешной установке бревна удаляется соответствующая координата из ownLeaves. Неудачный setBlock не расходует разрешение. Повторный callback больше не может использовать старую запись provenance как право на перезапись листьев. Это локальное усиление защиты, не транзакционность и не доказательство происхождения блока при внешней замене на идентичный BlockState.
- Regression commit ed461ef85a502e919ab6553d45cbb94be5955b79: source guard проверяет порядок успешного setBlock и удаления координаты; устаревшее требование прежнего return-выражения заменено.
- Прямой git checkout по-прежнему заблокирован DNS (Could not resolve host: github.com); точный Python runner, Java 21 suite, NeoForge compilation, GameTest и Minecraft new-chunk smoke НЕ запускались. Наличие проверки в исходнике не является PASS. Shared registries, Gradle, main и чужие ветки не затронуты. Историческая оценка 59/100 не изменена.
- Следующий шаг: получить точный checkout, запустить Python guard и Java runners, проверить NeoForge compile и реальный тест сохранности соседней листвы; затем согласовать регистрацию Silverwood и biome/chunk правила с другими lanes.


## 15.11.2026 — M12 Silverwood: portable source regression runner

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (ahead 78, behind 0 before edit), inspected feature branch inventory. Existing Draft PR #9 remains the intended unmerged review vehicle.
- Test maintenance commit [6ce9b9b](https://github.com/craftkot/tgaumcraft/commit/6ce9b9b4eb2040efbfade2431b651942c5470b18): tools/test_silverwood_neoforge_wiring.py now resolves its Java source relative to the script's own directory instead of the process working directory. This makes the static source regression usable from any cwd and avoids false failures in isolated runners. No production Java code changed in this iteration.
- CI on 6ce9b9b: CircleCI verify-connection PENDING at inspection (https://app.circleci.com/workflow/494c771b-09ce-42b0-9b32-93e235b02c1e/job/a49809c0-6af0-4c6c-86c8-873a37404884); GitHub PR workflow runs empty. Local git ls-remote still fails DNS resolution of github.com. Exact checkout Python runner, NeoForge compile, GameTest and Minecraft new-chunk smoke NOT executed. No PASS claimed.
- Next: obtain exact checkout and run source guard, Java 21 suite and NeoForge compilation; implement an executable placement test for preservation of neighboring blocks, then coordinate feature/biome registration. Shared registries, Gradle, main and other branches untouched. Historical index 59/100 unchanged.


## 15.11.2026 — M12 Silverwood: canopy-only result is not a tree

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked open PRs (#5–#13), branch inventory and main...M12 (ahead 80, behind 0 before edits). Draft PR #9 remains open.
- Production `9be6e121fe8c234f397c7bb7213c30988ae75d9d`: Feature.place now returns true only if at least one log write succeeds, rather than counting canopy-only writes as a successful tree. This fixes the reported result but does NOT roll back partial foliage writes when log placement fails.
- Regression `881ccef27cbd597cfc5064b4b58ca3eb193afa0a`: static source guard now checks trunk-only success accounting; this is not an executable NeoForge test.
- No full NeoForge compilation, exact-checkout Python/Java runner, GameTest, or new-chunk Minecraft smoke was performed in this iteration. Source-level edit and test guard are committed, but gameplay behavior is unverified. BETA26 RNG, soil, biome and chance parity remain unproven; registration still requires coordination. Historical project index remains 59/100.
- Next: test failed-log/partial-canopy behavior with an executable fake-world or NeoForge GameTest; verify exact checkout runners and NeoForge build; coordinate feature registration without touching shared registries unilaterally.


## 15.11.2026 — M12 Silverwood canopy-only rollback

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and this lane; inspected other branches and open PRs #5–#13. Before edits `main...feature/worldgen-m12` was ahead 83, behind 0.
- Java change [`39c9c99`](https://github.com/craftkot/tgaumcraft/commit/39c9c99fcb1c6d9e99cfaeae6e15c35d0b6431c6): capture original states of successful leaf writes, and on zero successful log placements restore only positions still equal to injected leaves. Avoid rollback of positions whose current state changed. This is best-effort cleanup, **not transactional generation**; equal-state concurrent replacements cannot be distinguished and failed restore writes are not retried.
- Source-level regression guard [`db9caf7`](https://github.com/craftkot/tgaumcraft/commit/db9caf7130c7e31a1409b775f82185b56941342d) updated for the new leaf-write path and rollback checks. **Not executed**; it is not a NeoForge/GameTest.
- No shared registries, Gradle, main or other feature branches changed; Draft PR #9 remains pending. Full NeoForge build, exact-checkout Java tests, in-game generation and BETA26 RNG/biome parity remain **unverified**. Historical audit stays 59/100.
- Next: execute exact-HEAD Python guard and full NeoForge build; add an executable fake-world/GameTest for failed log writes, rollback and foreign-block preservation before worldgen registration.


## 16.11.2026 — M12 Silverwood: repair stale source assertions after canopy rollback

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 86, behind 0 before write), reviewed feature branches and open PRs. Existing Draft PR #9 remains unmerged.
- Inspected exact GitHub Java and Python guard blobs. Found two stale checks in tools/test_silverwood_neoforge_wiring.py: successful foliage writes were still expected through removed setIfReplaceable(), and success was still expected as return placedLogs[0] > 0 despite new rollback branch. Fixed both in commit 2334f56c760093c5bfd800b850d49ea1e956c4ce. No production Java change.
- Re-fetched both files and checked six relevant source predicates against the GitHub content (6/6 true); this is **not** execution of the Python runner. CI for code commit was ci/circleci: verify-connection PENDING at inspection: https://app.circleci.com/workflow/bf64d73f-27d8-4cec-81a3-ce97b0c329ba/job/7d276a95-29d7-4162-a0ab-9ba1bc895f83 . Exact-checkout Python/Java tests, NeoForge compilation, GameTest and real new-chunk worldgen remain unverified. BETA26 RNG/biome parity unproven; Feature still unregistered.
- Next: run exact-head Python guard and Java 21 suites, implement executable rollback/foreign-block GameTest or fake-world test, then compile NeoForge and coordinate registration. Historical index 59/100 unchanged. No changes to main, other branches, shared registries or Gradle.


## 16.11.2026 — M12 Silverwood wiring guard syntax repair

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected open PRs and branch inventory. Before write main...M12 ahead 88, behind 0. Draft PR #9 remains open; no merge.
- The exact GitHub blob of tools/test_silverwood_neoforge_wiring.py still contained a literal `\\n` between two Python boolean-expression lines (`in source\\n and ...`), which is invalid Python syntax. Replaced that escaped sequence with an actual newline in commit 0b271dfcb5c17cae8f72b7f1df66d178b5f35de8. No production worldgen behavior changed.
- Connector readback and source inspection only; Python runner execution, exact checkout Java suite, NeoForge compilation, Minecraft GameTest/new-chunk smoke are NOT verified. Source assertions remain non-behavioral. Feature is still unregistered; original BETA26 biome/RNG parity unverified. Historical audit 59/100 unchanged.
- Next: run `python3 tools/test_silverwood_neoforge_wiring.py` on exact checkout, add an executable rollback/foreign-block preservation test, compile NeoForge, then coordinate feature registration. No shared registries, Gradle, main or other branches modified.


## 16.11.2026 — M12: fix CI Java compilation blocker

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (ahead 90, behind 0 before edit), inspected 16 branch names and open PR #5–#14. Draft PR #9 remains open, not merged.
- Retrieved the actual GitHub Actions #3243 verify job log (PR merge checkout f7f08087da545dbe805d26ec946455ade86f8f01): Core parity javac failed at WorldgenHeightGate.java:21, undefined variable maxBuildY. This is a confirmed Java compilation failure, not an inferred Python failure; NeoForge and server smoke were skipped.
- Fixed the typo in own production file: originWithinBuildHeight now compares minBuildY < maxBuildYExclusive, consistent with the method parameter and exclusive upper limit. Commit addf41ddc733ef45ee837c61e16ef491b059e8cc. No shared files touched.
- Local JDK 21 is present, but exact checkout still blocked by DNS (Could not resolve host: github.com). Exact-head Java tests, NeoForge compilation and Minecraft GameTest/new-chunk generation NOT verified; await fresh CI for the new SHA. This fix only removes the confirmed compile blocker, not proof of tree generation. BETA26 RNG/biomes and feature registration remain open.
- Next: inspect CI of corrected HEAD, execute exact checkout Java/Python runners when available, then implement an executable Silverwood rollback/foreign-block test and coordinate registration. Historical audit remains 59/100.


## 16.11.2026 — M12: exact-commit CI verification

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 92, behind 0 before this journal-only commit). Inspected 16 branch names and open PR #5–#14; Draft PR #9 remains unmerged.
- GitHub Actions [#3265](https://github.com/craftkot/tgaumcraft/actions/runs/43792866032), associated with code/journal HEAD `bf093d9e8f86f1d5fb8771415f9584c83904c7f2`, completed SUCCESS. Verified `verify` job steps: Core parity test SUCCESS, NeoForge build SUCCESS, dedicated server smoke SUCCESS, JAR upload SUCCESS. This clears the earlier `WorldgenHeightGate` compile blocker on that commit.
- Re-read `SilverwoodLegacyFeature.java` and `tools/test_silverwood_neoforge_wiring.py`: the latter is explicitly source-level only. No executable Silverwood rollback GameTest or new-chunk test executed; no source-verified BETA26 RNG/biome parity. Successful general NeoForge CI does NOT prove unregistered Silverwood gameplay.
- No production changes in this iteration. Next: create an executable isolated rollback/foreign-block regression test; coordinate feature registration before testing worldgen in new chunks. Historical audit stays 59/100. Main, shared registries, Gradle and other branches untouched.


## 16.11.2026 — M12: executable isolated Silverwood canopy rollback regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md, this lane; inspected active branch inventory and open PR #9 and #10–#15. Before changes main...feature/worldgen-m12 ahead 93, behind 0. No changes to shared registries, Gradle, main or other branches; no merge.
- Added loader-neutral rollback policy in [273220d](https://github.com/craftkot/tgaumcraft/commit/273220d2561fe4af2dbc7e4e7fdcc07cede9681d), wired the unregistered Silverwood Feature to it in [14d3ad1](https://github.com/craftkot/tgaumcraft/commit/14d3ad18108e909801a12a4f1dc1db7fd2c19f6b). The policy restores original states only if the current state is still the written leaves, counts successful writes, and does not retry failures; equal-state concurrent replacement remains indistinguishable.
- Added standalone Java 21 executable regression [eb09254](https://github.com/craftkot/tgaumcraft/commit/eb09254dc8da04e37b88311e1402c188370bd77e): restores air and flower, preserves a foreign stone block, does not count failed restore, handles empty input (7 assertions). Runner [5c0f013](https://github.com/craftkot/tgaumcraft/commit/5c0f0138a0d98ae91d79ad1897118475e19cbde7): bash tools/test_silverwood_canopy_rollback.sh. Updated source guard [49bc413](https://github.com/craftkot/tgaumcraft/commit/49bc413c66868a1446664456f44293a12b391a25).
- **Not yet executed on exact checkout**: Java runner, Python source guard, full NeoForge compilation, native GameTest, or Minecraft new-chunk smoke. The new test covers the extracted policy, NOT actual WorldGenLevel callbacks or full tree generation. CI status must be checked for this new HEAD; previous successful #3265 predates these changes.
- Next: execute exact-head Java 21 runner and Python guard; inspect CI and fix any failures; then add a native GameTest for actual WorldGenLevel writes before coordinating registration and biome/chunk placement. Original BETA26 RNG and biome frequencies unverified. Historical audit index remains 59/100.

## 16.11.2026 — M12 rollback repeat/unknown-state regression

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked main...M12 (ahead 99, behind 0 before edit) and open PR #1–#16. Draft PR #9 remains unmerged.
- Commit ef93b244216b64907cebae66d3a0b3a1f8b0ddf5 extends the standalone SilverwoodCanopyRollbackTest with four assertions: a second rollback pass must not rewrite restored/foreign positions, and unknown (null) current states must not be overwritten. No production worldgen, shared registry, Gradle or other lane changed.
- Java 21 isolated reproduction of the fetched rollback policy and test scenarios passed 11/11 assertions in a temporary directory. **Not** a byte-for-byte exact-checkout execution of the repository runner: the files were manually reproduced from fetched GitHub text. The earlier seven-assertion baseline also passed in that isolated environment.
- GitHub status queried for the code commit; status details were inspected separately. Full NeoForge build, real WorldGenLevel/GameTest, new-chunk Minecraft generation and BETA26 RNG/biome parity are NOT established by this unit test. Feature registration still requires coordination. Historical project index 59/100 unchanged.
- Next: execute the exact committed shell runner in a real checkout and inspect CI; then add a native WorldGenLevel test and coordinate feature/biome registration. No merge.

## 16.11.2026 — M12 trunk preservation in rollback regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Verified branch HEAD fac76c7cf5ad98550310610168da5e0ac56f0f51 before editing; main...M12 ahead 101, behind 0. Inspected open PRs #1–#16; Draft PR #9 remains open, unmerged.
- Extended the exact repository Java 21 rollback regression in commit b3df2d8525103f0111bddfeb5f7f1ff07dd07017 with five assertions: a position changed from own canopy to a successfully written log must not be rolled back; other leaves must restore AIR and WATER; write count must exclude the log.
- No production or shared files changed. This is a unit regression for the loader-neutral policy, NOT a native WorldGenLevel GameTest. The new assertions were not executed locally in an exact checkout; await CI for code and journal commits. Previous successful workflow run for fac76c7 was not proof of this change.
- Remaining: exact-head Java runner, NeoForge compile and in-game new-chunk smoke, feature registration coordination, BETA26 RNG/biome parity. Next: inspect fresh CI, then add native WorldGenLevel integration coverage. Audit index remains 59/100; no merge.

## 16.11.2026 — M12 rollback API contract and rejected-write regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected open PR #1–#16 and feature branch inventory. Draft PR #9 remains open and unmerged.
- Commit [4517f8e](https://github.com/craftkot/tgaumcraft/commit/4517f8e70be7ed880b2505371fd7aa0b5fe16e01) adds seven standalone Java 21 regression assertions to SilverwoodCanopyRollbackTest: four null-dependency guards and three rejected-write/foreign-block preservation checks. The isolated test now has 23 assertions (previously 16). No production, registry, Gradle, main, or other branch changes.
- Direct git ls-remote still fails with DNS 'Could not resolve host: github.com'. Thus the exact repository runner, full NeoForge compilation, Minecraft GameTest and new-chunk worldgen have not been executed in this iteration; no PASS claimed. Need fresh CI for code commit and journal HEAD.
- Next: run exact checkout tools/test_silverwood_canopy_rollback.sh and source guard, inspect CI, then implement native WorldGenLevel integration tests and coordinate feature registration. BETA26 biome/RNG parity remains unverified. Audit index remains 59/100.

## 16.11.2026 — M12 rollback callback-interleaving regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 105, behind 0 before change), inspected open PR inventory and feature branches. Draft PR #9 remains open, unmerged.
- GitHub Actions #3339 (https://github.com/craftkot/tgaumcraft/actions/runs/43827600419) for previous HEAD 3b929a00ac050cc2b95c8e96fbb3a3111ae2b934 reports SUCCESS; this does not prove the new test or real tree generation. The standalone runner is not confirmed as executed by CI.
- Added four deterministic assertions in commit 6943151aa2124d100073bbed074711d3ca4e7fd6: the first rollback callback mutates a later canopy position into foreign STONE; the later position must be re-read and skipped. Expected standalone test count now 27, NOT an executed PASS.
- Direct git ls-remote still fails DNS (Could not resolve host: github.com). Exact-checkout Java runner, Python guard, new-head NeoForge build, native GameTest and new-chunk smoke not executed here. BETA26 biome/RNG parity and feature registration unresolved.
- Next: run exact tools/test_silverwood_canopy_rollback.sh and source guard, inspect new-head CI, then implement native WorldGenLevel GameTest and coordinate registration. Main, shared registries, Gradle and other branches untouched. Historical index 59/100 unchanged.

## 16.11.2026 — M12 rollback partial failure does not abort later positions

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 before editing (ahead 107, behind 0); reviewed 18 open PRs including existing Draft #9. No merge or shared-file edits.
- Verified GitHub Actions run #43851645913 for preceding HEAD 46f70e37e2c853c41a56548af11fa9a6d09de2ec completed SUCCESS. This is not evidence of exact standalone runner or in-game worldgen.
- Commit 98068122ad5dccd547312e9b198f5df5c710323f adds four deterministic standalone Java 21 assertions: a failed first canopy restoration does not abort later restoration, both callbacks run, the failed leaf stays unchanged, and the next leaf returns to air. Expected count 31; not an executed PASS.
- Exact checkout Java runner, NeoForge build for new SHA, native GameTest, new-chunk generation and BETA26 RNG/biome parity remain unverified. Silverwood feature remains unregistered; coordinate shared registration separately. Next: execute exact Java runner and Python source guard, inspect new CI, then native WorldGenLevel integration coverage. Historical index 59/100 unchanged.

## 16.11.2026 — M12: exact-head CI and Draft PR evidence refresh

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane. Compared main...feature/worldgen-m12: ahead 109, behind 0 before this journal commit; reviewed open PR inventory and feature branches. PR #9 remains Draft and unmerged.
- Confirmed branch HEAD `4be0a94ab6e7085291da7915ca2dc79829bc5508` via PR #9. GitHub Actions [#3369](https://github.com/craftkot/tgaumcraft/actions/runs/43860575834) for this exact SHA completed SUCCESS: Core parity test, NeoForge build, dedicated server smoke, and JAR upload steps all SUCCESS. CircleCI verify-connection also reports success. These checks do not prove standalone rollback runner execution or natural Silverwood generation.
- Updated Draft PR #9 description to reflect actual current Silverwood feature and rollback tests instead of its outdated gate-only scope. Explicitly marked absent registration, native WorldGenLevel GameTest, new-chunk smoke and BETA26 RNG/biome parity. No production changes in this iteration.
- Exact checkout remains unavailable: local `git ls-remote` failed DNS resolution for github.com. Standalone Java runner and Python source guard were not executed on exact checkout; 31 assertions remain expected, not independently verified PASS.
- Next: add an executable native WorldGenLevel integration test, coordinate registration outside shared registry edits, then validate natural generation on fresh chunks. Keep audit index 59/100; no merge, main, Gradle or other branch changes.


## 16.11.2026 — M12 partial-trunk rollback primitive (not wired)

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...M12 (ahead 110, behind 0 before edits), reviewed open PRs including Draft #9. Direct git checkout remains blocked by github.com DNS.
- Added SilverwoodTrunkRollback.java at commit 90a23d8617d6b5c38fb5e44802be320e54c4c6cf and standalone SilverwoodTrunkRollbackTest.java at c046b5339fd4682260bfd3352726b0ce0dad6bd1. Policy compares each position's expected axis-specific log state before best-effort restoring its original block; skips foreign changes; rejects missing originals. Six assertions written, NOT executed on exact checkout.
- This is a **loader-neutral primitive only**, not connected to SilverwoodLegacyFeature.place(). It does not yet fix partial-trunk success, nor guarantee atomicity or detect equal-state foreign writes. No registry, Gradle, main, or other branch edits.
- Next: track original/expected successful log writes in the Feature, use this primitive and canopy cleanup on partial failure, then add real WorldGenLevel GameTest and fresh-chunk validation. Confirm CI for new HEAD and exact Java 21 runner. No BETA26 RNG/biome parity claim; historical index 59/100 unchanged. No merge.

## 16.11.2026 — M12 partial Silverwood trunk failure cleanup wired

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Compared main...feature/worldgen-m12: ahead 113, behind 0 before writes; checked parallel branch inventory and open PRs. Existing Draft PR #9 remains unmerged.
- Commit [8addbdb](https://github.com/craftkot/tgaumcraft/commit/8addbdb142b12e3f6534bf2f3e1b3898d6a29bd9) connects SilverwoodTrunkRollback to the unregistered NeoForge Feature: counts requested log callbacks, tracks successful axis-specific writes and pre-canopy original states, rejects partial skeletons, restores logs before remaining leaves. This is best-effort, NOT atomic; equal-state foreign writes and rejected restoration callbacks remain limitations.
- Commit [f07b824](https://github.com/craftkot/tgaumcraft/commit/f07b82479270abdf5dd83e84586b2f40bd477bd7) updates the source-only wiring guard for the new rollback path. Connector-fetched source/guard text was checked for expected markers; this is NOT execution of the Python guard or Java tests.
- Exact checkout blocked by DNS: git ls-remote reports Could not resolve host: github.com. Java runner, Python source guard on checkout, full NeoForge build and native Minecraft GameTest not executed locally. At status query, GitHub Actions run [#3425](https://github.com/craftkot/tgaumcraft/actions/runs/43920648186) was IN PROGRESS and CircleCI verify-connection PENDING for f07b824; no PASS claim.
- Remaining: native WorldGenLevel failure-injection GameTest, real new-chunk generation and registration coordination, exact BETA26 RNG/biome parity. Next: inspect CI #3425, execute exact-head tests when checkout is possible, then implement actual WorldGenLevel regression. Historical audit 59/100 unchanged. No main, shared registry, Gradle, other-branch edits or merge.

## 16.11.2026 — M12 trunk rollback journal prevalidation

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; checked feature branch inventory, main...M12 comparison (ahead 116, behind 0 before changes), and PR search. Existing Draft PR #9 remains the integration target; no merge.
- Production [48dff96](https://github.com/craftkot/tgaumcraft/commit/48dff9620b31e3d9a2b34be65d1826cbdf2c6f50) prevalidates every successful log write against its original-state journal before any rollback setBlock call. This avoids partially restoring a tree and then throwing on a later missing original. This is a narrow fail-fast safety fix, not atomic worldgen.
- Regression [4d26fc8](https://github.com/craftkot/tgaumcraft/commit/4d26fc8f45fec444cb7cd7723292e74d46da983a) adds three checks for a missing *later* original: exception, zero world writes, first log unchanged. Nine standalone assertions now expected, not executed on exact checkout.
- Prior head 0bc77dd GitHub Actions #3427 concluded SUCCESS, but is not evidence for these new commits or the standalone runner. Exact-head NeoForge build, native WorldGenLevel GameTest, new-chunk smoke and BETA26 RNG/biome parity not verified. Feature remains unregistered. No shared registries, Gradle, main or other branch edits. Historical index 59/100 unchanged.
- Next: inspect exact-head CI, run standalone Java runner on exact checkout, then implement native WorldGenLevel failure-injection test and coordinate feature registration separately.

## 16.11.2026 — M12 trunk rollback callback-interleaving regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Checked main...feature/worldgen-m12 (ahead 119, behind 0 before test commit), feature branches and open PR inventory. Draft PR #9 remains open, without merge.
- Confirmed GitHub Actions #3437 (https://github.com/craftkot/tgaumcraft/actions/runs/43931241444) SUCCESS for previous HEAD 689e68c9e2a0f8eed91592414a2f681b564fec3a. This does NOT establish exact standalone runner execution, in-game worldgen, or the new regression's result.
- Commit ee09dc53a9266e0fceddf0eb6642df122bd03691 adds four regression assertions to SilverwoodTrunkRollbackTest: first restoration callback changes a later log into foreign STONE; the later log must be re-read and skipped, while the first original AIR is restored. Expected test total is now 13 assertions; NOT executed here.
- New commit's PR-triggered workflow query returned no runs at check time. Exact checkout Java runner, current-head NeoForge build, WorldGenLevel GameTest and new-chunk generation are unverified. Natural feature registration and exact BETA26 biome/RNG parity remain outstanding.
- Next: execute exact tools/test_silverwood_trunk_rollback.sh, inspect fresh CI, implement native WorldGenLevel failure-injection test and coordinate registration without editing shared registries. No main, Gradle or other branch edits; historical audit remains 59/100.

## 16.11.2026 — M12 trunk rollback rejected-write continuation

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; reviewed open PRs, parallel branches and main...feature/worldgen-m12 (ahead 121, behind 0 before this test commit). Draft PR #9 remains open; no merge.
- Previous exact journal HEAD 065c5c85e3834134f69ed6f9acdff826d3124015: GitHub Actions [#3447](https://github.com/craftkot/tgaumcraft/actions/runs/43941262907) completed SUCCESS; CircleCI verify-connection SUCCESS. This is not evidence of the new test or in-game generation.
- Commit [2c180f7](https://github.com/craftkot/tgaumcraft/commit/2c180f7eb0016956277dd5707531dbc6dc0d850e) adds four deterministic standalone trunk rollback assertions: a rejected first restoration leaves that log in place but does not abort a later successful restoration; only accepted writes count. Expected standalone total is 17 assertions, **not an executed PASS**.
- Exact checkout unavailable (git ls-remote fails DNS resolution of github.com). No local exact-source Java runner, new-head NeoForge compile, native WorldGenLevel GameTest or new-chunk smoke was performed. Feature remains unregistered; BETA26 RNG/biome parity unresolved.
- Next: execute exact tools/test_silverwood_trunk_rollback.sh, check CI for this new SHA, then add native WorldGenLevel failure-injection coverage and coordinate natural feature registration. No changes to main, shared registries, Gradle or other branches. Historical audit remains 59/100.

## 16.11.2026 — M12 Silverwood log-coordinate uniqueness regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked 26 parallel branches, 24 open PRs and main...feature/worldgen-m12 (ahead 123, behind 0 before edit). Draft PR #9 remains open; no merge.
- Previous HEAD 44f7c4a33ac98f17e58eddc0bb26bff97490d4eb: GitHub Actions run 43951886410 completed SUCCESS and CircleCI verify-connection SUCCESS. These checks predate the new commit.
- Commit d852b5b3dd3ae2d415378f956c5ef4013d23b1a3 adds 128 deterministic checks (4 supported heights x 32 seeds) that SilverwoodLegacyLogs never writes the same block coordinate twice. This guards the actual SilverwoodLegacyFeature completion condition (unique written positions == attempted log callbacks). Test has NOT been executed on an exact checkout.
- Direct git ls-remote still fails DNS resolution of github.com. No exact-source Java runner, NeoForge build, native WorldGenLevel GameTest or new-chunk smoke for this commit. Feature remains unregistered; BETA26 RNG/biome parity unverified.
- Next: run exact standalone Java 21 runner and check current CI, then implement native WorldGenLevel failure-injection GameTest and coordinate natural generation registration separately. No main, other branches, shared registries or Gradle touched; historical audit remains 59/100.

## 17.11.2026 — M12 rejected canopy write cannot report success

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 125, behind 0 before changes), inspected parallel branches and open PRs. Draft PR #9 remains open; no merge.
- Production commit [4aa3dc4](https://github.com/craftkot/tgaumcraft/commit/4aa3dc4215f1b09121788e40c3785b53c05dc683) records rejected `WorldGenLevel.setBlock` calls for replaceable canopy positions. Previously, if all log writes succeeded, a failed leaf write could still produce a successful Feature result with an incomplete crown. Such attempts now take the existing best-effort trunk-then-canopy rollback path and return false.
- Source guard commit [1f7a29f](https://github.com/craftkot/tgaumcraft/commit/1f7a29f6e9f42ea54ca00d73895df5e434c56a62) updates existing source-level checks to match the new leaf-write control flow and asserts that rejected leaves trigger cleanup. Connector source inspection confirms both markers; this is NOT execution of the Python guard or a native regression.
- Previous HEAD 20f40ddaba100fb383196f1fc1ab1db2b35eb556 had GitHub Actions [#3480](https://github.com/craftkot/tgaumcraft/actions/runs/43961811903) SUCCESS and CircleCI verify-connection SUCCESS. These results predate the two code commits. Exact-head CI remains to be checked.
- Local git checkout still fails DNS resolution of github.com. No exact-checkout Java runner, Python guard, NeoForge compile, native WorldGenLevel GameTest or fresh-chunk smoke executed here. Rejected rollback writes and equal-state foreign changes remain best-effort limitations. Feature remains unregistered; BETA26 biome/RNG parity unresolved.
- Next: inspect exact-head CI, run source guard and Java runners, add native failure-injection WorldGenLevel coverage, then coordinate registration without touching shared registries. Historical audit 59/100 unchanged; main, other branches and Gradle untouched.


## 17.11.2026 — M12: не размещать ствол после отказа кроны

- Перечитаны `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и этот журнал; проверены 27 видимых веток, открытые PR и сравнение с `main` (до изменений +128/−0).
- Для предыдущего HEAD `2cbbfcdc0acb927685fa881078df0825495ca357` подтверждены [GitHub Actions #3503 SUCCESS](https://github.com/craftkot/tgaumcraft/actions/runs/43981462952) и CircleCI `verify-connection` SUCCESS; это **не** доказательство Minecraft GameTest.
- [9f5f242](https://github.com/craftkot/tgaumcraft/commit/9f5f24257a81328b38a911bec026d75e123b6b4b): после отказа записи листвы `SilverwoodLegacyFeature` больше не пытается записывать брёвна, которые пришлось бы немедленно откатывать; уже записанная листва по-прежнему восстанавливается существующим rollback.
- [5be8d0b](https://github.com/craftkot/tgaumcraft/commit/5be8d0b0afd310123257c86083e90e6863da06f9): добавлена проверка этого порядка в source-level wiring guard.
- **Статус:** коммиты отправлены; новый source-level guard и Java runner в этом запуске не исполнялись, CI новых SHA ещё не подтверждён, NeoForge GameTest и генерация новых чанков не выполнялись. Код не является доказательством игрового паритета BETA26. Общие регистры, Gradle, `main` и чужие ветки не менялись; Draft PR #9 не слит.
- **Далее:** проверить CI нового HEAD; добавить исполняемую проверку отказа `setBlock` в настоящем `WorldGenLevel` и согласовать регистрацию Feature в новых чанках.


## 17.11.2026 — M12: reject empty Silverwood trunk

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; checked open PRs and parallel branches. Before edits main...feature/worldgen-m12 was ahead 131, behind 0. Draft PR #9 remains unmerged.
- Previous HEAD b13adfb3ea155cd51169aedb4e14085abc5b881c: GitHub Actions run #3517 (https://github.com/craftkot/tgaumcraft/actions/runs/43988928478) completed SUCCESS. This does not prove exact Java runners or gameplay.
- Production commit 302af3ba5f1e24baec4a3ec7d162e55b05e28041 rejects a zero-attempt trunk rather than returning successful placement when no logs were requested; failure uses existing canopy rollback. This is a defensive success-condition fix, not a new registered worldgen feature.
- Guard commit 40b484aecf0c724bf54defb0e8c82a4581dbaa53 checks the zero-log condition and updates stale leaf-write marker in source-level wiring guard. The guard was edited but NOT executed; source-level string assertions cannot prove runtime behavior.
- Exact-head CI, NeoForge compilation, native WorldGenLevel GameTest, rejected setBlock gameplay and new-chunk generation remain unverified. Feature remains unregistered; BETA26 biome/RNG parity unresolved. No shared registries, Gradle, main, other branches or merges. Historical audit 59/100 unchanged.
- Next: execute exact source-level guard and Java21 runners, inspect new-head CI; implement native WorldGenLevel failure-injection test and coordinate natural feature registration.


## 17.11.2026 — M12: exact-head CI verification and native-test readiness

- Re-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and this lane; checked feature branches, 26 open PRs, and `main...feature/worldgen-m12` (ahead 134, behind 0). Draft PR #9 remains open and unmerged.
- Confirmed the previously unverified HEAD `3674cd121c812f21a0d264ac8da6ef64032c7563` has GitHub Actions [#3538 SUCCESS](https://github.com/craftkot/tgaumcraft/actions/runs/43996952271). This reports a completed CI workflow, **not** execution of the standalone Silverwood Java tests or native GameTest.
- Inspected `SilverwoodLegacyFeature.java` and its source-level guard. It remains an isolated, unregistered Feature with canopy-first writes and best-effort rollback; a native `WorldGenLevel` failure-injection harness is still missing. No speculative GameTest or production change was committed in this pass.
- Local exact checkout remains unavailable: `git ls-remote` fails with `Could not resolve host: github.com`. No local Java 21 runner, NeoForge compile, real Minecraft GameTest or fresh-chunk smoke was executed. BETA26 RNG/biome parity and shared registration coordination remain unresolved. Historical audit remains 59/100.
- Next: build a native NeoForge GameTest fixture that calls the Feature with a controlled `WorldGenLevel` write failure, execute it in a real NeoForge test server, then coordinate placed-feature/biome registration separately. No edits to main, other branches, registries or Gradle; no merge.


## 17.11.2026 — M12: stop additional canopy writes after rejected setBlock

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, this lane, open PRs and parallel branches. Before edits `main...feature/worldgen-m12` was ahead 135, behind 0. Draft PR #9 remains open and unmerged.
- Verified previous HEAD `6b52a16c17ea1a8460ba9fcc061427e5deb79ff2`: GitHub Actions run [44004532470](https://github.com/craftkot/tgaumcraft/actions/runs/44004532470) completed SUCCESS. This is not a native GameTest.
- Production commit [e8c1810](https://github.com/craftkot/tgaumcraft/commit/e8c18107138aebbba751d9bc37c5fd830e3a331b) stops *subsequent* Silverwood leaf writes after the first rejected `WorldGenLevel.setBlock`, while the legacy canopy RNG traversal still proceeds. Previously it kept attempting foliage writes even though the final result was already doomed to rollback. Earlier successful foliage still enters the existing best-effort rollback.
- Source-level guard commit [3dca9d9](https://github.com/craftkot/tgaumcraft/commit/3dca9d9317f8c012e3c7b0ada599d6e7814df9e9) checks the early-return ordering. This is only a textual guard, **not** a native rejection-injection test.
- New-head CI, exact Java runners, full NeoForge compilation, native `WorldGenLevel` GameTest and fresh-chunk generation have **not** been executed or verified in this pass. Feature remains unregistered; BETA26 biome/RNG parity unresolved. No main, other branches, shared registries, Gradle or merges; historical audit remains 59/100.
- Next: inspect exact-head CI, run source guard and Java21 runners; implement native GameTest with injected failed `setBlock` and coordinate feature registration separately.

## 17.11.2026 — M12: canopy rejection RNG regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked visible parallel branches and open PRs. Before edit main...feature/worldgen-m12 was ahead 138, behind 0.
- Previous HEAD 755b11b23ea04062e030cd7c6c81f1ae6c2c5433: GitHub Actions [run 44018843470](https://github.com/craftkot/tgaumcraft/actions/runs/44018843470) reported completed SUCCESS. This is not evidence of the standalone test or Minecraft GameTest.
- Commit [e69eebf](https://github.com/craftkot/tgaumcraft/commit/e69eebfef7bb1798d6b694dbfb048e5d3d06244f) adds 80 pure-core callback assertions across 4 heights and 4 seeds: inject rejection at the third leaf callback, suppress later canopy/trunk writes, and verify that legacy RNG continuation matches an unrestricted traversal. This checks the callback policy used by the Feature; it does NOT exercise WorldGenLevel.setBlock or prove rollback.
- Direct git checkout remains blocked by github.com DNS; exact Java 21 runner not executed in this pass. New-head CI, NeoForge build, registered GameTest and fresh-chunk worldgen remain unverified. Silverwood is still unregistered; BETA26 biome/frequency and RNG bridge parity unresolved.
- Next: run exact test_silverwood_legacy_tree.sh and source wiring guard from a verified checkout, then implement native WorldGenLevel failure-injection GameTest without touching shared registries/Gradle. No merge; main, other branches and shared registries unchanged. Historical audit 59/100 unchanged.


## 17.11.2026 — M12: stop post-failure canopy world reads

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and own lane. Compared `main...feature/worldgen-m12`: ahead 140, behind 0 before edits; reviewed 28 open PRs including Draft #9 and parallel integration branches.
- Verified previous HEAD `c6d9349a166acbfe7e641b6c515cde1cd37de7d9`: GitHub Actions [run 44025499314](https://github.com/craftkot/tgaumcraft/actions/runs/44025499314) completed SUCCESS; CircleCI `verify-connection` success. Neither status proves standalone Silverwood runner or native GameTest.
- Production commit `d714ca77bb70dfd57e496c2427ff56aec9dded21`: canopy replacement predicate now short-circuits after the first rejected `WorldGenLevel.setBlock` write, avoiding subsequent `getBlockState` calls. The legacy canopy still evaluates `random.nextInt(8)` for every candidate, preserving the RNG traversal. This narrows post-failure world interaction; it does not make rollback atomic.
- Source guard commit `651fb1db7a9d6eb554e0d6bd2a79e3f6d7180705` adds a textual check for the short-circuit. Guard and Java tests were NOT executed. Exact checkout is still blocked by `Could not resolve host: github.com` in this environment.
- Current-head CI, NeoForge compilation, native WorldGenLevel/GameTest, new-chunk generation and BETA26 biome/RNG parity remain unverified. Silverwood Feature is unregistered. No shared registries, Gradle, main, other branches or merge touched. Historical audit stays 59/100.
- Next: check current-head CI, run exact Java runners and source guard in an accessible checkout, then implement a real WorldGenLevel failure-injection GameTest and coordinate registration separately.


## 17.11.2026 — M12: fail-fast после отказа записи ствола

- Повторно прочитаны main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md и этот lane; проверены параллельные feature-ветки, 28 открытых PR, сравнение с main (+143/−0 до изменений). Draft PR #9 остаётся открытым.
- Предыдущий HEAD bc05b1685229bcd935ba1f15e7ac1e992287beb4: GitHub Actions run 44032322023 SUCCESS, CircleCI verify-connection success. Это не доказательство запуска отдельных Java runner или GameTest.
- Production 1ccc80831bb2c5442b04b4d1d7b44fe22d71402a: первая неудачная запись брёвна теперь помечает rejectedLogWrite и прекращает последующие записи ствола; при отказе остаётся rollback ранее записанных блоков. До этого генератор продолжал писать последующие брёвна несмотря на уже неизбежный откат.
- Guard 5e4120ff14e7f4f00e8fcc37853110448e795d08: текстовые проверки нового fail-fast и обновление старого условия rollback. Guard не запускался и не является runtime доказательством.
- Для guard HEAD CircleCI был pending, GitHub Actions PR run пока отсутствовал. Exact-checkout Java, NeoForge compile, native WorldGenLevel GameTest и генерация новых чанков в этом проходе не выполнялись. Feature не зарегистрирован; BETA26 biome/RNG parity не доказан.
- Далее: дождаться CI точного HEAD, запустить source guard и Java runners из checkout, сделать нативный GameTest с инъекцией отказа setBlock; регистрацию деревьев согласовать отдельно. Main, чужие ветки, Gradle и общие регистры не изменялись; merge нет. Индекс 59/100 без пересчёта.


## 17.11.2026 — M12: negative-coordinate Silverwood log regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Compared main...feature/worldgen-m12 before edit: ahead 146, behind 0; inspected parallel feature branches and open PRs including Draft #9.
- Verified previous HEAD c7347d8cb3ff8e640f8402c0bf2a950515cd0868: GitHub Actions [#3621](https://github.com/craftkot/tgaumcraft/actions/runs/44039926577) completed SUCCESS. This is CI for the previous SHA, not the new test.
- Test commit [252f817](https://github.com/craftkot/tgaumcraft/commit/252f8175dc134a3d695ffa380ca2d325b72fa3da) adds 64 deterministic assertions to SilverwoodLegacyLogsTest (4 supported heights x 16 seeds). The same skeleton sequence, including metadata and random branches, must remain identical after coordinate normalization between (8,64,8) and (-24,64,-8). This checks negative-coordinate translation invariance only; it does not prove chunk placement, NeoForge or BETA26 RNG parity.
- The new Java test has been committed but NOT executed in an exact checkout. Exact-head CI, native WorldGenLevel GameTest, actual new-chunk generation, biome registration and client smoke remain unverified. Silverwood Feature remains unregistered; rollback is best-effort. No shared registries, Gradle, main, other branches or merge modified. Historical audit remains 59/100.
- Next: verify CI for the new SHA, execute tools/test_silverwood_legacy_logs.sh, then add an actual WorldGenLevel failure-injection GameTest without changing shared registration.


## 17.11.2026 — M12: CI подтверждение negative-coordinate regression

- Повторно прочитаны `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и этот журнал. Проверены параллельные feature-ветки, открытые PR и сравнение с `main` (до обновления: ahead 148, behind 0); Draft PR #9 остаётся открытым, без merge.
- Для HEAD `c71a0bd56443b0c03fcd5b857d18f38fb22c8eef` получен GitHub Actions [run #3649 SUCCESS](https://github.com/craftkot/tgaumcraft/actions/runs/44057596853) и CircleCI `verify-connection` SUCCESS. Это доказательства статусов CI на конкретном SHA, **не** подтверждение исполнения `tools/test_silverwood_legacy_logs.sh` или нативного GameTest.
- Прочитаны `SilverwoodLegacyFeature`, `SilverwoodTrunkRollback`, `SilverwoodCanopyRollback` и trunk rollback regression. Отказ `WorldGenLevel.setBlock` по-прежнему не воспроизведён в настоящем NeoForge GameTest; естественная регистрация Feature отсутствует. Дополнительную неподтверждённую правку игровой логики не делали.
- В этом проходе исходный Java-код и тесты не изменены, exact-checkout Java runners, NeoForge build и Minecraft fresh-chunk smoke не запускались. BETA26 RNG/biome parity и атомарность rollback не доказаны; исторический аудит 59/100 неизменен.
- Следующий безопасный шаг: реализовать и запустить нативный GameTest с контролируемым отказом `setBlock` на настоящем `WorldGenLevel`; отдельно согласовать регистрацию placed feature и biome modifiers. Main, чужие ветки, общие регистры и Gradle не изменены.


## 17.11.2026 — M12: negative-coordinate RNG continuation regression

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (+149/-0 before change); reviewed 30 open PRs, including existing Draft PR #9 and parallel alchemy/integration branches.
- Previous HEAD 8b200251c4285afbb676f1b7cf45dcdb798b8e55: GitHub Actions run 44065697042 completed SUCCESS. This does not prove standalone Java runner or native GameTest.
- Test commit c53df961baa9e249a78e75ecbeff35d80b2134ee adds 128 RNG continuation comparisons (4 supported heights x 32 seeds) for translated Silverwood log generation, verifying that nextLong() matches after placing at positive vs negative coordinates. This is a pure Java regression, not an original BETA26 equivalence proof.
- Test NOT executed on exact checkout: git ls-remote still fails DNS resolving github.com. New-head CI, NeoForge compilation, real WorldGenLevel setBlock failure injection, fresh-chunk generation and gameplay remain unverified. Feature is not registered for natural generation. No main, other branches, shared registries, Gradle or merge touched. Historical audit remains 59/100.
- Next: run tools/test_silverwood_legacy_logs.sh on exact checkout and inspect CI for new SHA; prioritize native WorldGenLevel GameTest and coordinate registry integration separately.

## 17.11.2026 — M12 Silverwood: оси горизонтальных корней

- Перед записью повторно прочитаны `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и текущий lane. Проверены существующие параллельные feature-ветки и сравнение с `main` (до коммита +151/−0). CI предыдущего SHA `57e42c8`: GitHub Actions [#3676](https://github.com/craftkot/tgaumcraft/actions/runs/44076230227) SUCCESS.
- Добавлен узкий regression в `SilverwoodLegacyLogsTest`: 4 высоты × 16 seed × 2 утверждения = **128 новых assertions**. Проверяются исходные metadata 0 (ось X), 2 (ось Z) для четырёх горизонтальных корней и metadata 1 для четырёх нижних опор на отрицательных координатах. Кодовый коммит: [ed6aa4d](https://github.com/craftkot/tgaumcraft/commit/ed6aa4d5030f78968e146754f33731ac4eca3176).
- **Доказательства:** тест записан в GitHub, но Java runner, компиляция NeoForge, GameTest и генерация новых чанков **не запускались**. CI на новом SHA пока не подтверждён. Это проверка модели логов, а не подтверждение отображения осей в Minecraft.
- **Осталось:** реальный отказ `WorldGenLevel.setBlock`, естественная регистрация Feature по согласованию, точный BETA26 RNG/биомы и игровая проверка чанков. Следующий шаг — выполнить runner на точном SHA и разработать нативный GameTest; не повышать 59/100.

## 2026-11-17 — RNG draw-count regression

- Code commit `3f31d52c2ff7ba1c36449fb861d886a303fc20b5`: added 128 assertions to `SilverwoodLegacyLogsTest` (4 heights × 32 seeds), checking that log generation consumes exactly eight `Random.nextInt(3)` calls and leaves the subsequent RNG state unchanged relative to that reference sequence.
- This is a source-level Java regression only. The new assertions have **not** been executed in this run; NeoForge compile, native GameTest, and fresh-chunk Minecraft worldgen are **not verified** for this commit.
- Remaining: run exact-SHA Java runner and CI; native `WorldGenLevel.setBlock` failure GameTest; coordinate registry ownership before natural placement; verify original BETA26 RNG/biome parity.
- No shared registry, Gradle, `main`, other branches, or merges changed. Draft PR #9 remains the integration vehicle.


## 17.11.2026 — M12: verified CI and native GameTest integration blocker

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked main...feature/worldgen-m12 (+155/-0), six feature branches and 33 open PRs including Draft #9. Changes remain restricted to this lane.
- Current pre-entry HEAD: 6c1073bab8af006fd0c14622d135d8c1a88e2604. GitHub Actions [run #3761](https://github.com/craftkot/tgaumcraft/actions/runs/44128231398) completed SUCCESS for that SHA. This is CI status only: no evidence that the standalone SilverwoodLegacyLogsTest or native GameTest ran.
- Re-read SilverwoodLegacyFeature and SilverwoodLegacyLogs/Test, plus trunk/canopy rollback tests. Feature is still an isolated, unregistered consumer. Its RandomSource-to-java.util.Random seed bridge is explicitly non-parity. Rollback remains best-effort and cannot identify foreign equal-state writes. Existing pure-core tests already cover rejection/continuation and per-position rollback; further duplicate assertions are not a substitute for native gameplay evidence.
- Exact Git checkout again failed with 'Could not resolve host: github.com'; Java 21 is available, but exact-byte runners and native NeoForge GameTest were not executed. No new production/test code was added in this pass. No worldgen fresh-chunk test, original BETA26 RNG/biome parity or natural registration verified.
- Next: add an isolated, native GameTest fixture for controlled WorldGenLevel.setBlock rejection, with explicit registration/test execution evidence; coordinate shared registration separately. Keep PR #9 Draft and unmerged. Main, other branches, shared registries and Gradle untouched. Historical audit remains 59/100.


## 17.11.2026 — M12: CI точного HEAD fe431ea, аудит покрытия

- Повторно прочитаны `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` и собственный журнал; проверены шесть feature-веток, 34 открытых PR и Draft PR #9. До записи журнала сравнение `main...feature/worldgen-m12`: ahead 156, behind 0; HEAD `fe431ea426d21ef1cc257141c5debd6da79c38bc`.
- Для `fe431ea` GitHub Actions [run #3772](https://github.com/craftkot/tgaumcraft/actions/runs/44149607270) завершился SUCCESS. Проверен список шагов: `Core parity test`, `NeoForge build`, `Dedicated server smoke test` — SUCCESS; CircleCI `verify-connection` — SUCCESS. В списке шагов **нет** отдельного `tools/test_silverwood_legacy_logs.sh`, нативного Silverwood GameTest или генерации новых чанков; общий CI-green не доказывает их выполнение.
- Перепроверен `SilverwoodLegacyLogsTest`: тесты translation/RNG/root-axis/draw-count присутствуют в исходнике, но исполнение на точном SHA не подтверждено. Это не устраняет разрыв `RandomSource`→`java.util.Random` и не доказывает BETA26 parity.
- В этом проходе нового Java/NeoForge кода нет: до подключения GameTest к реально исполняемому native runner добавление ещё одного неисполняемого теста не даст доказательств. `SilverwoodLegacyFeature` остаётся незарегистрированным для естественной генерации, rollback best-effort. Отдельный нативный `WorldGenLevel.setBlock` failure-injection GameTest и fresh-chunk smoke не выполнялись.
- Следующий шаг: согласовать точку подключения нативного GameTest (учитывая параллельные PR #26–#34, меняющие GameTest/CI), реализовать изолированный M12 тест отказа `setBlock`, выполнить его в реальном NeoForge, затем отдельно согласовать регистрацию placed feature и биомов. `main`, чужие ветки, shared registries, Gradle не изменены; merge нет. Исторический индекс 59/100 без пересчёта.


## 17.11.2026 — M12: exact-head CI audit and native GameTest coordination

- Re-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and this lane; compared `main...feature/worldgen-m12` (+157/-0 before entry), inspected six feature branches, open PRs including Draft #9 and stacked native GameTest infrastructure PRs #26–#35.
- Verified previous HEAD `ac85f67e8974717ecc59b9d5200dd9361f92cad2`: GitHub Actions [run #3789](https://github.com/craftkot/tgaumcraft/actions/runs/44161040434) completed SUCCESS. Inspected `verify` job steps: Core parity, NeoForge build and dedicated server smoke SUCCESS; **no standalone Silverwood Java runner, native Silverwood GameTest, or new-chunk worldgen test** in that job. This does not establish gameplay parity.
- Re-read `SilverwoodLegacyFeature`, `SilverwoodPlacementPreflight`, `WorldgenChunkGate` and their existing regression tests. The Feature remains unregistered; `RandomSource` to `java.util.Random` bridging is not exact BETA26 RNG parity. The existing pure-core gates do not provide a controllable real `WorldGenLevel.setBlock` failure. PRs #26–#35 are developing the native GameTest runner/fixture path in another lane; modifying shared GameTest registration or Gradle here would risk overlap.
- No new production or test Java code in this pass; no exact-checkout runner, native GameTest or fresh-chunk worldgen executed. This entry records the exact-SHA evidence and cross-lane integration dependency, not a completed M12 gameplay milestone. CI of this journal commit is pending. No main, other branches, shared registries, Gradle or merge changed; historical audit remains 59/100.
- Next: when native GameTest runner/fixture integration is available, implement an isolated Silverwood failure-injection GameTest against real `WorldGenLevel`; then coordinate biome/placed-feature registration and test fresh chunks.


## 17.11.2026 — M12: exact-head CI #3813 and GameTest dependency audit

- Fresh-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md`, and this lane. Compared `main...feature/worldgen-m12`: ahead 158, behind 0 before this entry; inspected six feature branches and open PRs including Draft #9 and parallel native GameTest infrastructure work.
- Verified previous HEAD `883db43791c0ad73949aeb101eaafa6255d7fbf9`: GitHub Actions [#3813](https://github.com/craftkot/tgaumcraft/actions/runs/44171449282) completed SUCCESS. This is a workflow status only; standalone Silverwood Java runner, native GameTest, and new-chunk generation are not established by this evidence.
- Re-inspected `SilverwoodLegacyFeature` and `SilverwoodTrunkRollback` with its regression. Current Feature remains unregistered and uses `new Random(context.random().nextLong())`, not proven BETA26 RNG equivalence. Rollback is explicitly best-effort; native `WorldGenLevel.setBlock` failure injection remains missing. PRs in the parallel lane are actively modifying native GameTest infrastructure; no shared registration or Gradle edits attempted.
- Exact checkout failed (`Could not resolve host: github.com`), so no Java runner, NeoForge GameTest or fresh-chunk smoke was executed locally. No production Java change in this iteration; this entry only records verified CI and integration blockers. New journal commit CI not yet verified. Main, other branches, shared registries, Gradle and merge untouched. Historical audit remains 59/100.
- Next: once a reliable native GameTest runner is available, add isolated M12 failure-injection test, execute it, and separately coordinate Silverwood placed-feature/biome registration before fresh-chunk tests.


## 2026-11-17 — M12 Silverwood: direct feature RNG stream

- Fresh-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and this lane; inspected six feature branches and open PRs (including Draft #9 and native GameTest work #26–#35). Before code change, `main...feature/worldgen-m12` was +159/-0; previous HEAD `3959b3ab0260d281714ee591cd45b35802fc322c`.
- Code commit [`3825951`](https://github.com/craftkot/tgaumcraft/commit/38259514a2e6639bcaba5383bc03fdaa0d99873b): replaced `new Random(context.random().nextLong())` in isolated `SilverwoodLegacyFeature` with an adapter delegating `nextInt(bound)` directly to `FeaturePlaceContext.random()`. This removes the extra reseeding draw and uses the caller's worldgen RNG stream. Unsupported `Random.next(bits)` operations fail closed. No shared registries, Gradle or biome injection changed.
- Previous HEAD GitHub Actions [#3829](https://github.com/craftkot/tgaumcraft/actions/runs/44181421528) completed SUCCESS. This is previous-SHA workflow status, **not** a compilation or native GameTest result for the adapter. New SHA CI and NeoForge compilation are pending. No Java runner, native `WorldGenLevel.setBlock` failure-injection GameTest or fresh-chunk gameplay test was executed here.
- This change is **not** proof of exact BETA26 RNG parity: Minecraft 1.21.1 `RandomSource` may use a different underlying algorithm from legacy `java.util.Random`. Verify algorithm/seed semantics and native build before claiming parity. The feature remains unregistered and rollback best-effort.
- Next: inspect exact-SHA CI/build and add a native failure-injection GameTest without modifying other lanes' shared GameTest registration; coordinate integration before natural Silverwood placement. Draft PR #9 remains unmerged. Historical audit stays 59/100.


## 2026-11-18 — M12 Silverwood: exact-SHA NeoForge CI evidence

- Fresh-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and own lane; checked six feature branches and `main...feature/worldgen-m12` (+161/-0 before this entry). Draft PR #9 remains the unmerged integration path; shared GameTest/CI work is owned by other lanes.
- For `f470fb707e5cb453c91c66ee07fab8e8bedd22ee`, GitHub Actions [#3843](https://github.com/craftkot/tgaumcraft/actions/runs/44192434659) completed SUCCESS. Inspected job 134425041985: `NeoForge build`, `Dedicated server smoke test`, and `Core parity test` all SUCCESS. CircleCI `verify-connection` also SUCCESS. Thus the preceding `FeatureRandomAdapter` change has **compiled under the NeoForge build** on that exact SHA.
- The job does **not** contain a standalone `SilverwoodLegacyLogsTest`, native Silverwood GameTest, injected `WorldGenLevel.setBlock` failure test, or fresh-chunk worldgen verification. A successful build/server smoke does not establish RNG parity, successful tree placement, or gameplay correctness.
- Direct Git checkout still fails DNS (`Could not resolve host: github.com`). No local exact-checkout Java runner or Minecraft test executed, and no new production Java code added. Avoided another unexecuted test fixture or edits to shared native GameTest registration/Gradle while parallel PRs own that infrastructure.
- Next: implement and execute a native controlled `setBlock` rejection GameTest once its registration is coordinated; then register Silverwood placement and verify new chunks and source-era biome/RNG rules. Historical 59/100 unchanged; no main/other branch/merge changes.


## 2026-11-18 — M12: isolated rejected-trunk rollback regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (+162/-0 before change), reviewed open PRs and competing GameTest infrastructure. No shared registry/Gradle changes.
- Added loader-neutral deterministic failure-injection regression `SilverwoodRollbackFailureInjectionTest` in commit [e34101b](https://github.com/craftkot/tgaumcraft/commit/e34101b57860d81ec6f2aeab504e5f2b26507917). Simulates canopy writes, one accepted trunk write, a rejected subsequent trunk write, two-phase restoration, original flower recovery, and idempotence (8 assertions).
- This regression invokes the actual core rollback helpers but does NOT invoke `SilverwoodLegacyFeature.place` or a real `WorldGenLevel.setBlock`; it is not native NeoForge GameTest evidence. Exact-checkout test execution and new-head CI are not claimed.
- Next: run exact Java 21 regression and NeoForge CI, then implement native controlled setBlock rejection with the parallel GameTest lane; coordinate feature registration and new-chunk biome/worldgen smoke. Draft PR #9 stays unmerged, historical 59/100 unchanged.


## 2026-11-18 — M12: rollback foreign-write and rejected-restore regressions

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected six feature branches, open PRs including Draft #9 and parallel GameTest #26–#35. Before change main...feature/worldgen-m12 was +164/-0.
- Commit [84632d8](https://github.com/craftkot/tgaumcraft/commit/84632d85321480622eee35c00113661bbcd98654) extends the existing loader-neutral SilverwoodRollbackFailureInjectionTest with 8 assertions: foreign trunk/canopy replacements must survive rollback, and rejected rollback writes must not be counted as successful. Expected total now 16 assertions (8 existing + 8 new), **not an executed PASS**.
- Exact checkout Java runner unavailable because local git DNS resolution fails. No native WorldGenLevel.setBlock GameTest or fresh-chunk generation executed. CI for this commit not yet verified; test only covers core rollback helpers, not NeoForge world writes.
- Next: verify exact SHA Java 21/core and NeoForge CI; coordinate native GameTest registration and run actual setBlock rejection, then register natural Silverwood placement with biome/chunk checks. Draft PR #9 remains unmerged. No main/other branches/shared registries/Gradle touched; historical 59/100 unchanged.


## 2026-11-18 — M12: exact Java 21 runner for rollback failure regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Compared main...feature/worldgen-m12 (+166/-0 before edit); reviewed open PRs and overlapping GameTest infrastructure.
- Added exact-checkout standalone Java 21 runner `tools/test_silverwood_rollback_failure_injection.sh` in [22f9003](https://github.com/craftkot/tgaumcraft/commit/22f900301aab76150534900aacc949920c410fd8). It compiles both production rollback helpers and the existing 16-assertion failure-injection regression, then invokes its main method; no shared Gradle/CI/registries changed.
- Before this change the branch's prior SHA `1055d6519145a2f09d7193258e9f2563c657122f` had CircleCI verify-connection SUCCESS. The new runner has **not** been executed on exact checkout and new-SHA CI is pending. This is not a native NeoForge GameTest or a fresh-chunk worldgen pass.
- Next: execute the exact runner and NeoForge CI; coordinate a real `WorldGenLevel.setBlock` failure-injection GameTest, then register natural Silverwood placement with biome/chunk evidence. Draft PR #9 stays unmerged, historical 59/100 unchanged.


## 2026-11-18 — M12: fail-fast corrupt trunk journal regression

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; inspected six feature branches and open PRs (#9 draft; #26–#36 overlapping GameTest/CI). Before changes main...feature/worldgen-m12 was +168/-0, HEAD 5784acd3b6fe254723d1ffb1d8442ca7e482d1f8.
- Regression commit [d4fc69a](https://github.com/craftkot/tgaumcraft/commit/d4fc69aaed56b9c2fd7cbf785f95435b9c8e604b) adds four deterministic assertions to SilverwoodRollbackFailureInjectionTest: a corrupt trunk journal with an original missing for its second write must throw before any rollback write and leave both blocks unchanged. Existing runner tools/test_silverwood_rollback_failure_injection.sh covers the same class; expected count 20 assertions (not a verified PASS).
- Previous exact SHA 5784acd: CircleCI verify-connection SUCCESS; GitHub Actions run [44260485341](https://github.com/craftkot/tgaumcraft/actions/runs/44260485341) completed SUCCESS. These are previous-head results, not proof of execution of the four new assertions.
- Direct git ls-remote still fails DNS (Could not resolve host: github.com); exact checkout runner, new-head NeoForge CI, native WorldGenLevel.setBlock GameTest and fresh-chunk worldgen have not been executed here.
- Next: run Java 21 runner on exact checkout and verify new-head CI; coordinate native GameTest registration before testing real rejected world writes, then coordinate Silverwood natural placed-feature/biome injection. No main/other branches/shared registries/Gradle/merge changes; audit 59/100 unchanged.


## 2026-11-18 — M12 exact-SHA CI verification for rollback journal regression

- Re-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and this lane; compared `main...feature/worldgen-m12` (+170/-0 before this entry) and reviewed active feature branches and open PRs, including Draft #9 and overlapping GameTest #26–#36.
- Verified exact HEAD `a7572320f485681adfd479cc862bd25597175b9f`: GitHub Actions [run #3937](https://github.com/craftkot/tgaumcraft/actions/runs/44268792534) SUCCESS. Inspected `verify` job 134679864675: Java 21 setup, `Core parity test`, `NeoForge build`, and `Dedicated server smoke test` all SUCCESS. This confirms build/server CI on the commit containing the corrupt-journal regression.
- Importantly, the workflow job does **not** list `tools/test_silverwood_rollback_failure_injection.sh` or the standalone 20-assertion test. Therefore those 20 assertions have **not** been demonstrated to execute, despite green CI. No native `WorldGenLevel.setBlock` failure GameTest or new-chunk generation was executed.
- Reviewed production `SilverwoodTrunkRollback` and `SilverwoodCanopyRollback` against the failure-injection regression; no evidence-backed new code change was justified in this pass. No source/test code added; this entry records new exact-SHA evidence and the missing test-execution gate.
- Next: execute the standalone Java 21 runner on an exact checkout, then coordinate native GameTest infrastructure before introducing `WorldGenLevel.setBlock` injection and natural biome placement. No shared Gradle/registry, main, other branch, or merge changes. Draft PR #9 remains open; historical 59/100 unchanged.

## 18.11.2026 — M12 Silverwood partial rollback rejection

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 before edit (+171/-0), inspected feature branch names and current rollback source.
- Test commit [ade5529](https://github.com/craftkot/tgaumcraft/commit/ade55294da32ad1013ebf78b0b9e2b34b2d22139) adds six deterministic assertions: one failed trunk/canopy restore does not stop later successful restores, rejected blocks remain unchanged, and successful later blocks regain original states.
- Java 21 is installed locally, but direct Git checkout failed with DNS "Could not resolve host: github.com". Exact checkout runner and new SHA CI have NOT been executed/confirmed. This remains a loader-neutral simulation, not native WorldGenLevel.setBlock GameTest or proof of new-chunk worldgen.
- Next: run tools/test_silverwood_rollback_failure_injection.sh on exact checkout (expected 26 assertions), confirm NeoForge CI, then coordinate native GameTest and real biome/chunk placement. No shared registries, Gradle, main or other lanes modified; no merge. 59/100 unchanged.


## 2026-11-18 — M12 exact-head CI evidence and test coverage gap

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (+173/-0) and reviewed open PRs including M12 Draft #9 and GameTest #26–#35. Six feature branches found; no changes outside own lane.
- Verified exact pre-entry HEAD `4898c0388cb5765db27fb0680a486885664bc1c7`: GitHub Actions [run #3954](https://github.com/craftkot/tgaumcraft/actions/runs/44287663240) completed SUCCESS, including Java 21 setup, Core parity, NeoForge build, and dedicated server smoke. CircleCI `ci/circleci: verify-connection` reported SUCCESS for the same SHA (workflow ff9107f6-2ef1-47ea-b4c5-a71f9c08555a).
- Examined actual Actions job steps: `tools/test_silverwood_rollback_failure_injection.sh` is not an explicit step. Thus the 26 failure-injection assertions have **not** been proven to run by this CI result. Local exact checkout still fails DNS (`Could not resolve host: github.com`); no Java runner execution is claimed.
- Source inspection confirms this regression calls loader-neutral rollback helpers, not `SilverwoodLegacyFeature.place` or a real `WorldGenLevel.setBlock`. No native NeoForge GameTest, BETA26 biome/RNG parity, natural placement registration, or fresh-chunk worldgen pass. Historical 59/100 unchanged.
- Next: execute the exact Java 21 runner on a valid checkout; coordinate a native controlled setBlock failure GameTest and then the Silverwood placed-feature registration without touching shared registries/Gradle independently. Draft PR #9 stays unmerged.

## 18.11.2026 — M12 Silverwood canopy rollback journal validation

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected open PRs (#9 remains present) and compared main...feature/worldgen-m12 (ahead 174, behind 0 before changes). Direct git ls-remote still fails DNS resolution.
- Production commit ce4df9b2c8610837af08c913c423eb650ee6a0a2 validates all canopy rollback journal entries (non-null position and original state) before any block restoration. This prevents a corrupt journal from restoring a valid prefix before failing on a null original.
- Regression commit 7b1a53d4c8b5edd4ef32fd2903b1edd5448bc96f adds four assertions: corrupt journal rejection, zero attempted writes, unchanged valid prefix and unchanged invalid suffix. Existing runner tools/test_silverwood_rollback_failure_injection.sh compiles the production helpers and regression test; its exact checkout execution remains **unverified** due to DNS.
- No shared registries, Gradle, main, other lanes, or merge modified. These are loader-neutral checks, NOT native NeoForge GameTests or evidence of new-chunk generation. CI for new HEAD pending; 59/100 unchanged.
- Next: confirm exact Java 21 runner and CI; native WorldGenLevel.setBlock failure injection and new-chunk Silverwood biome/placement tests still required.


## 2026-11-18 — M12: fail-closed null trunk rollback states

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (+177/-0 before edits), reviewed open PRs including Draft #9 and overlapping native GameTest infrastructure.
- Production commit [2c216b0](https://github.com/craftkot/tgaumcraft/commit/2c216b0624628a09c18356b19d13ae9dc1860331) validates non-null trunk journal positions, written states and original states before any rollback mutation; missing originals continue to raise IllegalArgumentException.
- Regression commit [994a9d3](https://github.com/craftkot/tgaumcraft/commit/994a9d33b908a8b963c443b3188155e42cdf2f9c) adds four checks for null original/written states and zero attempted writes; expected total 34 assertions, not an executed PASS.
- Java 21 available, but exact git checkout remains blocked by DNS (Could not resolve host: github.com); exact runner, native NeoForge GameTest and fresh-chunk worldgen not executed. CI for new commits not yet verified. No shared registries, Gradle, main, other branches or merge modified. Historical 59/100 unchanged.
- Next: run exact Java 21 rollback runner in checkout/CI, then coordinate native WorldGenLevel.setBlock rejection GameTest and actual Silverwood biome/chunk placement.


## 2026-11-18 — M12 exact-head CI verification; native placement still blocked

- Re-read main:AGENTS.md, main:ROADMAP_RU.md, main:analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked six feature branches, open PRs and main...feature/worldgen-m12 (+180/-0 before entry). Draft PR #9 remains unmerged.
- Verified exact previous HEAD e83ac342933f96ecb46b05f10f56019896e2c4fc: GitHub Actions run 44325647612 (Thaumcraft Port CI #4029) completed SUCCESS. Its verify job passed Java 21 setup, Core parity, NeoForge build, and Dedicated server smoke. CircleCI ci/circleci: verify-connection also SUCCESS (workflow ec1da968-f236-4f53-882e-babceee38c54).
- Inspected actual workflow steps: tools/test_silverwood_rollback_failure_injection.sh is not an explicit step; 34 rollback assertions are still NOT proven to execute. The runner and the two production rollback helpers plus regression source were re-read from this branch. No new production/test change justified from source review.
- Local git ls-remote still fails DNS (Could not resolve host: github.com), despite local Java 21 availability. No exact-checkout standalone runner, native WorldGenLevel.setBlock GameTest, or fresh-chunk Silverwood generation executed here. Existing tests are loader-neutral and do not prove native placement.
- Next: execute exact runner in a working checkout, coordinate native GameTest and feature/biome registration with owners before modifying shared registries, then verify new chunks and original BETA26 biome/RNG rules. No main/other branches/Gradle/shared registries/merge touched; audit 59/100 unchanged.

## 2026-11-18 — M12 standalone rollback runner fail-closed assertion count

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked six feature branches, open M12 Draft PR #9 and other PRs; pre-change main...feature/worldgen-m12 was ahead 181 / behind 0, base SHA a4280834adc5a20b6951098af937d30ca4aeca56.
- Commit 6a5f62f07d3797c0a350b5cf31e0aee6d1badd1a updates only tools/test_silverwood_rollback_failure_injection.sh: captures Java regression output and fails unless the exact 34-assertion success summary is emitted. This guards against a silently shortened test, skipped execution, or misleading output. The Java regression itself was not changed.
- This is runner source change, NOT an executed Java 21 PASS; no native NeoForge GameTest or fresh-chunk worldgen verification. No new CI result yet for this SHA. Natural Silverwood biome registration and real WorldGenLevel.setBlock rejection remain blocked on coordinated native wiring. Historical audit stays 59/100.
- Next: execute the standalone runner on exact SHA and inspect its output; verify NeoForge build/server CI, then coordinate native setBlock GameTest and biome/chunk registration. Draft PR #9 remains unmerged; main, other branches, shared registries, Gradle and common CI untouched.

## 2026-11-19 — M12 unavailable world-state rollback regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and own lane; compared main...feature/worldgen-m12 (+183/-0 before edits), reviewed open PRs and overlapping GameTest work.
- Commit 104450403e6720be0b648ccc1bb8376083421396 adds three loader-neutral assertions: null current-state reads (unavailable/unloaded positions) must skip both trunk and canopy rollback and must not invoke any world writes. No production behavior changed.
- Commit ae65af7431969d8e5479417326c40f54ba4d23b5 updates the exact-summary Java runner from 34 to 37 assertions. These are source-level regression additions, NOT executed PASS evidence. Exact-head Java runner, NeoForge CI and native GameTests remain unverified for these commits.
- Still required: execute tools/test_silverwood_rollback_failure_injection.sh on exact SHA, verify NeoForge build/server CI, coordinate native WorldGenLevel.setBlock GameTest and Silverwood placed-feature/biome registration, then validate new chunks. No main/other branches/shared registry/Gradle/merge changes. Draft PR #9 remains the M12 integration route; historical 59/100 unchanged.

## 2026-11-19 — M12 exact-head CI evidence after 37-assertion regression

- Fresh-read main:AGENTS.md, main:ROADMAP_RU.md, main:analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected six feature branches and open PRs, including overlapping native GameTest PRs. Compared main...feature/worldgen-m12: ahead 186, behind 0, base a4280834adc5a20b6951098af937d30ca4aeca56.
- Verified exact pre-entry HEAD 5c39b4c200187bb6a955a70e4014828d55c377c3: GitHub Actions [#4126](https://github.com/craftkot/tgaumcraft/actions/runs/44446116476) reports completed SUCCESS. This is workflow-level evidence only; standalone tools/test_silverwood_rollback_failure_injection.sh was NOT confirmed to execute, nor was native worldgen GameTest.
- Re-read exact regression (37 source assertions), runner (exact 37-assertion summary), and both production rollback helpers. No evidence-backed production defect established, so no Java/test change in this pass. Direct local Git checkout still blocked by DNS: Could not resolve host: github.com. Exact runner, real WorldGenLevel.setBlock rejection, natural Silverwood placement, new-chunk smoke and original BETA26 frequency parity remain unverified.
- Updated existing Draft PR #9 with exact SHA, successful workflow URL and outstanding gates. This journal entry is documentation-only; no gameplay milestone claimed. No main/other branch/shared registry/Gradle/merge changes. Historical audit 59/100 unchanged.
- Next: run exact Java 21 rollback script with 37 PASS assertions; coordinate native GameTest and natural Silverwood biome/placed-feature registration, then verify fresh chunks.

## 2026-11-19 — M12 null-position rollback journal regression

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 187 / behind 0 before changes), checked six feature branches and open PRs. Draft PR #9 remains the integration route.
- Commit 3540da2587483de6a18578bf3c9b22633527b941 adds a loader-neutral regression with two corrupt-journal cases (null trunk/canopy position), requiring rejection before any world read or write. Commit a7741d702a16ef4063005b34cc18804de7bcad8c adds a dedicated Java 21 fail-closed runner.
- The production helpers already validate these positions; this change locks that contract against regressions. This is not a native NeoForge GameTest. Java 21 is installed, but exact repository checkout remains blocked by DNS (Could not resolve host: github.com); neither new runner nor original 37-assertion runner has a confirmed execution PASS. CI for new HEAD is pending. Earlier a0b847b workflow 44453097244 was SUCCESS, not proof of these tests.
- No changes to main, other branches, Gradle, shared registries or common CI; no merge. Historical 59/100 unchanged.
- Next: run both Java 21 runners on exact HEAD, verify new-head CI, then coordinate native WorldGenLevel.setBlock failure injection and biome/placed-feature registration followed by fresh-chunk testing.

## 19.11.2026 — M12 Silverwood rollback: null original state regression

- Fresh-read `main:AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md` and this lane. Compared `main...feature/worldgen-m12` (before edits: ahead 190, behind 0), reviewed feature branches and open PR search.
- [75ae5ee](https://github.com/craftkot/tgaumcraft/commit/75ae5eeeb72b33dd4d72cea01462fd849239da21): two additional deterministic cases require trunk/canopy rollback to reject a null **original block state** in the second journal entry before any world read or write. This exercises existing production preflight; production code unchanged.
- [dad739d](https://github.com/craftkot/tgaumcraft/commit/dad739d2c9dd7aa5f00d205822ac0cf2e0459dda): runner expected output updated from 2 to 4 cases.
- **Not executed:** exact Java 21 runner, full NeoForge build, native GameTest and real new-chunk Silverwood worldgen. Local git checkout is blocked by DNS (`Could not resolve host: github.com`). No PASS is claimed from source inspection. CI for new HEAD must be checked separately.
- Next: execute `bash tools/test_silverwood_rollback_null_position.sh` and failure-injection runner from exact checkout; then validate native `WorldGenLevel.setBlock` failure and biome/chunk placement. Historical 59/100 unchanged; no merge.

## 2026-11-19 — M12 exact-head CI check (acb2d73)

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (ahead 193, behind 0), checked six feature branches and open PRs including M12 Draft #9.
- Verified commit acb2d73b499bd94a28aaec45a7b4f85c3a462905: GitHub Actions [run #4244](https://github.com/craftkot/tgaumcraft/actions/runs/44516306071) completed SUCCESS. Its verify job passed Java 21 setup, core parity, NeoForge build, dedicated server smoke, and artifact upload.
- Inspected the actual job steps: neither tools/test_silverwood_rollback_null_position.sh nor tools/test_silverwood_rollback_failure_injection.sh was executed as an explicit step. Thus the four null-position cases and 37 failure-injection assertions still have no demonstrated PASS. Local git ls-remote fails DNS (Could not resolve host: github.com); Java 21 is available, but an exact checkout is not.
- No native WorldGenLevel.setBlock rejection GameTest or natural new-chunk Silverwood biome generation evidence. No production/test change justified by this CI-only inspection. Next: run both exact Java runners, then coordinate native feature registration/GameTest with owners before touching shared registries or Gradle. No main/other branches/merge changes; 59/100 unchanged.

## 2026-11-19 — M12 CI verification at 24ed1ab

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Compared main...feature/worldgen-m12: ahead 194, behind 0; reviewed feature branch search and open PRs. M12 Draft PR #9 remains the integration route.
- Verified exact HEAD 24ed1abafb4b754521591aa1f9cba89a56f736b5: GitHub Actions run https://github.com/craftkot/tgaumcraft/actions/runs/44525348094 completed SUCCESS. The verify job includes Java 21 setup, Core parity, NeoForge build and dedicated server smoke, all successful.
- Inspected individual workflow steps: neither tools/test_silverwood_rollback_null_position.sh (4 cases) nor tools/test_silverwood_rollback_failure_injection.sh (37 assertions) ran explicitly. This CI success does not establish either standalone runner PASS, native WorldGenLevel.setBlock GameTest or natural Silverwood placement in fresh chunks.
- Next: execute both standalone Java 21 runners on an exact checkout; coordinate native GameTest and biome/placed-feature registration without touching shared registries or Gradle unilaterally. No main/other branches/merge changes. Audit remains 59/100.

## 2026-11-19 — M12 rollback source audit and execution blocker

- Fresh-read `main:AGENTS.md`, `main:ROADMAP_RU.md`, `main:analysis/PARALLEL_WORKFLOW_RU.md` and this lane; compared `main...feature/worldgen-m12` (ahead 195, behind 0 before entry) and reviewed open PRs, including parallel Alchemy native GameTest work.
- Re-read exact production `SilverwoodTrunkRollback` and `SilverwoodCanopyRollback`, plus both dedicated Java 21 shell runners. Both helpers validate journals before world mutation, and the runners require exact success summaries (37 assertions and 4 cases). No additional production defect was established by this source audit.
- Attempted actual `git ls-remote` in the execution environment; DNS failed with `Could not resolve host: github.com`. Therefore neither exact-head runner nor native NeoForge GameTest or new-chunk worldgen was executed in this pass. Do not treat source-level expected counts as PASS or earlier CI SUCCESS as gameplay evidence.
- Next: obtain exact checkout, execute both runners and record outputs; coordinate native `WorldGenLevel.setBlock` failure-injection GameTest and real Silverwood placed-feature/biome registration without unilateral shared registry changes. Draft PR #9 remains the integration route; no merge, other branch, shared registry, Gradle or main changes. Historical audit 59/100 unchanged.

## 2026-11-20 — M12 Silverwood preflight fail-closed regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; compared main...feature/worldgen-m12 (before edit ahead 196, behind 0), inspected other feature branches and open PRs, including Draft PR #9.
- [4bbecb8](https://github.com/craftkot/tgaumcraft/commit/4bbecb8a5aa99fbc85a9fa97aad61917970c7139) extends the actual SilverwoodPlacementPreflightTest with six assertions: invalid height, wrong chunk and build-height overflow reject before **any** soil/trunk world read; failed soil check never reads the trunk column. No production behavior or shared registration changed.
- **Not a confirmed PASS:** GitHub-connected source update succeeded, but the execution host still reports `Could not resolve host: github.com` for git checkout. Java 21 exists; the exact-head standalone preflight runner, two rollback runners, NeoForge build, native GameTest and fresh-chunk worldgen have not been executed in this pass. CI for this new HEAD must be checked; earlier green CI does not cover the new commit.
- Next: run `bash tools/test_silverwood_placement_preflight.sh`, both rollback runners on exact HEAD, verify CI; then coordinate native setBlock rejection GameTest and feature/biome registration. Do not merge Draft PR #9 before gameplay proof. No changes to main, other branches, shared registries or Gradle. Historical 59/100 unchanged.


## 20.11.2026 — M12 Silverwood preflight read-count regression

- Fresh-read main:AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked branch HEAD e8c58cb, parallel branches, open PRs and Draft PR #9. Before changes main...feature/worldgen-m12: ahead 198, behind 0.
- Test-only commit [a9cfcef](https://github.com/craftkot/tgaumcraft/commit/a9cfcef1bc8af428552e9f1002b87f6f8747e9a4): five assertions in SilverwoodPlacementPreflightTest for exactly one soil read, exactly seven trunk reads on success and early termination after the fourth trunk read when the fourth position is blocked. No production code or shared registry changes.
- GitHub Actions runs #44681711799 and #44681705942 succeeded on prior SHA e8c58cb; they do **not** validate the new test commit. Direct git checkout remains blocked by DNS (Could not resolve host: github.com), so new assertions have **no local PASS**. No native NeoForge GameTest or fresh-chunk worldgen proof. Score stays 59/100.
- Next: run tools/test_silverwood_placement_preflight.sh on exact branch checkout, inspect CI for a9cfcef, then implement native failure-injection coverage without touching shared registries; biome registration requires coordination.

## 2026-11-20 — M12 Silverwood negative-chunk and upper-limit coordinates

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected six feature branches, open PRs and Draft PR #9. Pre-edit branch HEAD 12f9cce4591320740b2dc9c3c4ef3b29b188857a; comparison main...feature/worldgen-m12 ahead 200 / behind 0.
- Test-only commit [47bfafc](https://github.com/craftkot/tgaumcraft/commit/47bfafc437e81c4aa9c2de4ebedb1f7b4da72b47) adds five deterministic assertions to the existing SilverwoodPlacementPreflightTest: exact soil/trunk read coordinates in negative chunk (-1,-1), one soil read, seven trunk reads, and upper-world-bound successful ten-block trunk read count. Production code, registries, Gradle, CI and other branches untouched.
- GitHub Actions run [#4413](https://github.com/craftkot/tgaumcraft/actions/runs/44688443496) completed SUCCESS for preceding 12f9cce; this is NOT test-only commit CI and does not prove standalone preflight runner PASS. New tests are source-committed but not executed in an exact checkout. Native NeoForge GameTest, fresh-chunk worldgen, biome registration and original BETA26 parity remain unverified.
- Next: check CI for 47bfafc and run exact Java 21 preflight/rollback runners; coordinate native setBlock failure GameTest and biome registration before worldgen smoke. Draft PR #9 remains draft, no merge; audit remains 59/100.


## 2026-11-20 — M12 exact-head CI evidence for negative-chunk regression

- Re-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane. Confirmed feature/worldgen-m12 HEAD 0585cad77b56d0448a52721c006aad9dde5e4e04 and main comparison ahead 202, behind 0; reviewed six feature branches and existing Draft PR #9.
- Exact-head GitHub Actions [run #4422](https://github.com/craftkot/tgaumcraft/actions/runs/44695998772) reports completed SUCCESS; CircleCI `ci/circleci: verify-connection` reports SUCCESS for the same SHA. This is CI status evidence, NOT standalone preflight/rollback runner PASS or gameplay proof.
- Direct git checkout attempted again: `Could not resolve host: github.com`. Exact Java 21 runners were not executed in this run. No production or test change was made; no demonstrated defect justified adding further isolated assertions.
- Still missing: exact-head standalone preflight and rollback runner outputs, native NeoForge WorldGenLevel.setBlock failure-injection GameTest, agreed placed-feature/biome registration, and actual new-chunk Silverwood/Greatwood gameplay smoke. Do not merge Draft PR #9. Historical score 59/100 unchanged; no shared registries, Gradle, main, other branches or merge touched.
- Next: obtain runnable checkout or CI runner evidence; prioritize native Silverwood Feature integration and biome/chunk verification with coordination rather than expanding abstract guard tests.

## 2026-11-20 — M12 preflight contract clarification

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked main HEAD a4280834, own pre-edit HEAD 50ccc714, six feature branches and open PRs. Before edit main...feature/worldgen-m12 ahead 203 / behind 0; Draft PR #9 remains open.
- Source audit established that SilverwoodPlacementPreflight validates the bounding envelope and scans soil plus the *central trunk column*, not the complete canopy/branch collision set. Corrected misleading Javadoc in [b1ad17f](https://github.com/craftkot/tgaumcraft/commit/b1ad17f0afa7f1cecca42e2f38e33c983a220c7b) without changing executable logic. This is documentation correctness, NOT new worldgen functionality.
- Exact checkout attempt: `git ls-remote` failed DNS (`Could not resolve host: github.com`). Standalone Java runners, native NeoForge GameTest and fresh-chunk gameplay smoke were not run. CI must be checked for the resulting documentation commits; earlier green workflow cannot prove gameplay behavior.
- Next: obtain executable checkout or remote runner, execute existing Silverwood preflight and rollback scripts, then add native failure-injection GameTest and coordinate feature/biome registration. No main, other branches, shared registries, Gradle or merge changes. Historical score 59/100 unchanged.


## 2026-11-20 — M12 Silverwood legacy-origin acceptance audit

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked main...feature/worldgen-m12 ahead 205 / behind 0 before edit, open PRs and Draft PR #9. Exact pre-edit HEAD 5f1d45fa899e5b4fabfba14aee2db8a4310f7ed9; GitHub Actions runs 44746073167 and 44746063841 completed SUCCESS for that SHA (not native gameplay evidence).
- Identified a **specific parity blocker** by comparing source-audit legacy placement with SilverwoodLegacyFootprint.withinChunk: legacy X/Z candidate offsets are 8+random(-4..4), i.e. 4..12 inclusive (9x9=81 equally likely offsets). The current fail-closed 11x11 canopy envelope accepts only local offsets 5..10 inclusive (6x6=36), thus **36/81 = 4/9 (~44.44%)** of legacy center candidates can pass chunk gating even before biome/soil/height/collision checks. This is conditional geometry, not an observed game spawn rate; legacy candidate distribution and RNG parity still require original BETA26 verification.
- This guard is deliberate protection against writes into adjacent chunks; removing it to inflate generation rates would be unsafe. Future integration must choose a coordinated cross-chunk-safe placement strategy or explicitly document the distribution change. Do not register the untested Feature as naturally spawning yet.
- No production changes: this pass established an actionable worldgen parity limitation rather than adding duplicate assertions. Exact Java runners, NeoForge native GameTest and fresh-chunk worldgen are still unverified. No changes to main, foreign branches, shared registries, Gradle or merges; 59/100 unchanged.
- Next: design a safe placement boundary for the legacy 9x9 center range with integration owners; execute exact-head Java 21 runners and native setBlock rejection tests before enabling placed-feature/biome hooks.

## 2026-11-20 — M12 signed-chunk Silverwood origin distribution regression

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; inspected six feature branches, main...feature/worldgen-m12 (ahead 206, behind 0 before edit) and prior Draft PR #9. Existing pre-edit HEAD bd8396f6739543b3fecf220445728f91e57559eb had GitHub Actions #4489 SUCCESS (run 44765313396) and CircleCI verify-connection SUCCESS; these checks do not prove worldgen gameplay.
- Test commit [06e2843](https://github.com/craftkot/tgaumcraft/commit/06e2843c6aed0fd6583fc66a09af99aaeedf5ca4) adds an exhaustive 9x9 candidate-origin matrix in each of 16 positive/negative chunk pairs (1296 coordinate assertions plus 16 acceptance-count assertions). Expected conservative acceptance is 36 of 81 candidates per chunk. This locks down the already identified geometry/parity limitation without loosening the fail-closed guard or changing production behavior.
- New regression is committed but **not confirmed executed** on exact HEAD; new CI result, standalone Java runner, native NeoForge GameTest and natural new-chunk Silverwood generation remain unverified. The 36/81 result is a geometric conditional, not an observed spawn rate. No shared registry, Gradle, main, foreign branch or merge changes.
- Next: execute exact Java 21 footprint runner and rollback runners, then coordinate a cross-chunk-safe placement policy and registered biome/placed-feature path with owners. Keep Draft PR #9 unmerged and audit 59/100 unchanged.

## 2026-11-20 — M12 Silverwood exact reachable horizontal radius

- Fresh-read main AGENTS.md, ROADMAP_RU.md, analysis/PARALLEL_WORKFLOW_RU.md and this lane; checked main...feature/worldgen-m12 (ahead 208, behind 0 before edits), branch inventory and existing Draft PR #9. Main baseline a4280834; prior branch HEAD d5639f8.
- Fixed a concrete overly conservative bound: SilverwoodLegacyCanopy scans +/-5 but its condition distanceSquared < 10 + nextInt(8) has maximum threshold 17; at horizontal offset 5, squared distance >=25, so no leaf can be written there. SilverwoodLegacyLogs reaches only +/-2. Thus maximum horizontal write radius is +/-4, without touching neighbor chunks. Production commit fc323286; footprint matrix regression bf991b55; preflight boundary regression 980312ae.
- For original candidate centers with local offsets 4..12 inclusive, fail-closed acceptance rises from 36/81 to 64/81 (offsets 4..11 in each axis), while rejecting offsets 12 and preserving no-cross-chunk writes. This is geometric eligibility, not measured spawn rate or full BETA26 parity. The remaining 17/81 need a coordinated cross-chunk policy.
- Direct git ls-remote still failed DNS (Could not resolve host: github.com); exact checkout Java runners, NeoForge compilation, native GameTest and actual fresh-chunk worldgen NOT run. CI on 980312ae initially CircleCI pending; PR-triggered GitHub Actions query returned no runs. No PASS claimed.
- Next: run tools/test_silverwood_legacy_footprint.sh and tools/test_silverwood_placement_preflight.sh on exact checkout, verify CI, then coordinate registered placement and native setBlock failure-injection GameTest. Draft PR #9 remains draft, no merge; main, other branches, shared registries and Gradle unchanged. Historical score 59/100 unchanged.
