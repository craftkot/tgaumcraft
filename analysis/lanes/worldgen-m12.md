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
