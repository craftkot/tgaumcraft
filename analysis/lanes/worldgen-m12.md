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
