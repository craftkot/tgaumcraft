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
