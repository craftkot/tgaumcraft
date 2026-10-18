# Направление: исследования / feature/research

**Статус:** 🟡 M07 активен с 18.10.2026; рабочая ветка `feature/research`, исключительно исследования. Текущий подэтап: M07.1 аудит и M07.2 подключение реально существующих помощников к выбору оригинальных карточек.
**Основной план:** [ROADMAP_RU.md](../../ROADMAP_RU.md).
**Опора:** [THEORYCRAFT_CARD_PROGRESS_RU.md](../THEORYCRAFT_CARD_PROGRESS_RU.md), `analysis/PROGRESS_AUDIT_2026_10_16.md`.

## Уже реализовано частично, не повторять
- 37 аспектов, 148 исследований, 33 исходные карты; основы 14 помощников.
- Реальные игровые сценарии FIRSTSTEPS, создание теории, вытягивание двух карт, активация одной; оригинальный начальный GUI.
- Не доказан полный цикл всех 33 карт/148 ветвей, их расходников, наград и browser UI; это и есть M07.

## Первые задачи после запуска чата
1. 🟡 M07.1 — fresh-read текущего main, своего feature/research, `THEORYCRAFT_CARD_PROGRESS_RU.md`; построить таблицу **непроверенных** карт/действий и указать конкретные пропуски.
2. M07.2 — сделать первый отсутствующий настоящий карточный сценарий без подмены предметов.
3. M07.3 — категории и браузер Таумономикона.
4. M07.4 — end-to-end и сохранение сессий, CI доказательства.
5. PR → main и обновление карты только после подтверждения.

## Журнал
- 18.10.2026: ветка создана в подготовительном режиме. **Не помечать M07 🟡, пока пользователь не дал второму чату команду начать.**


## 18.10.2026 — подход M07.1 / первый узкий M07.2: настоящие блоки-помощники

**Аудит перед работой:** `main` и `feature/research` начинались на `a4280834adc5a20b6951098af937d30ca4aeca56`. Прочитаны `ROADMAP_RU.md`, `AGENTS.md`, `PARALLEL_WORKFLOW_RU.md`, этот lane, `THEORYCRAFT_CARD_PROGRESS_RU.md`, `PROGRESS_AUDIT_2026_10_16.md`, `AID_BYTECODE_AUDIT_2026_10_10.md`; проверены CI и исходные Java-классы. Формулы всех **33/33** исходных карт существуют в core; **игровая проверка только части карт**. Сохранены исторические **14** классов helpers и геометрия сканирования, но реальные NeoForge identity-checks реализованы далеко не для всех. Реальный клиент ранее подтверждал старт теории, draw и активацию одной карты (см. аудит); все карточки/предметы/сохранение при холодном restart ещё НЕ доказаны.

**Обнаруженный дефект:** `NeoForgeTheorycraftAidBridge.discover` не распознавал два УЖЕ перенесённых зарегистрированных блока, нужных оригинальным исследовательским помощникам: `thaumcraft:crucible` → `AidBasicAlchemy` и `thaumcraft:infusion_matrix` → `AidBasicInfusion`. Старое `Set.copyOf(scanner.scan(...))` не обязано сохранять исходный HashMap iteration order обнаруженных помощников.

**Кодовый коммит:** [`9e59ceb4d69ac9a4eb60aba3b465a3ed53f3e052`](https://github.com/craftkot/tgaumcraft/commit/9e59ceb4d69ac9a4eb60aba3b465a3ed53f3e052). Изменён **только** `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftAidBridge.java`: оба оригинальных блока привязаны к своим BETA26 helpers без fake alternatives, сканирование по-прежнему не загружает чанки; результат Set копируется в `LinkedHashSet` и возвращается read-only с сохранением итерации. Классы алхимии и инфузии не менялись.

**Тесты в коммите:** добавлен `ResearchAidDiscoveryParityTest.java` (14 registries, оригинальные aid-card pools, границы 4/1/4, duplicates, picked-stack, новая привязка двух blocks + проверка серверного chunk guard) и независимый runner `tools/run_research_aid_parity.sh`. Исходные wiring assertions и наличие регистраций проверены инспекцией исходного кода. **Полный javac/NeoForge test и клиентский сценарий в данном подходе НЕ запущены**: есть код и регрессионные тесты, но не доказательство проходящего runtime; тест можно исполнить из checkout Java 21 командой `bash tools/run_research_aid_parity.sh`.

**CI после кода:** GitHub Actions для `feature/research` завершились `failure` ([39077597045](https://github.com/craftkot/tgaumcraft/actions/runs/39077597045), [39077597102](https://github.com/craftkot/tgaumcraft/actions/runs/39077597102)); попытка чтения job logs вернула `BlobNotFound`, точная причина недоказана. CircleCI [#225](https://circleci.com/gh/craftkot/tgaumcraft/225) SUCCESS только для `verify-connection`, **не** для javac/NeoForge/gameplay. Никаких новых баллов по общему индексу: **59/100**.

**Что остаётся в M07:** доказать через настоящий research table, что оба helper появляются в UI, выбираются с реальным debit inspiration и действительно позволяют оригинальные CardConcentrate/Reactions/Synthesis, CardMeasure/Channel/Infuse с честным item/XP debit; не только блок обнаружен. Затем остальные 6 не подключённых helper identities (не подменять отсутствующие блоки), все 33 gameplay активатора с источниками и ресурсами, полное дерево Таумономикона, scan world/entities/sky, save/restart, game+CI. `M07.1` ещё не закрыт — нужны матрица 33 карт × тесты; следующий конкретный шаг M07.2: smoke-сценарий на живом research table с `CRUCIBLE` рядом и исходной `CardConcentrate` (без бесплатных предметов), после восстановления полноценного CI.

**Запрет:** никаких изменений в `main`, чужих feature branches, Gradle/CI/общем ROADMAP, алхимических/инфузионных runtime-классах. PR в `main` открыть только после end-to-end проверки M07, не сливать самостоятельно.


## 18.10.2026 — M07.2: server gameplay evidence for helper-assisted research (второй подход)

**Fresh-read:** проверены HEAD main `a4280834` и feature/research `1ac1441`, собственный журнал, ROADMAP/AGENTS/PARALLEL_WORKFLOW, source progress, audit, recent CI, существующие `ResearchTableScreen`, `ResearchTableMenu`, `ResearchTableBlockEntity`, BETA26 card factory, materials/activation. Никакие механики других feature-веток не менялись.

**Реальное состояние перед исправлением:** GUI и сервер уже содержат выбор aid, создание теории с `LegacyTheorycraftInspiration.start`, draw/activation через меню 2..6, NBT CardChoice, preflight с настоящими предметами/чернилами/XP. Полнота по 33 Java-моделям ≠ игровой тест 33 карточек; не нужно переписывать существующие обработчики. Предыдущий M07-коммит `9e59ceb` добавил обнаружение реальных crucible и infusion matrix.

**Сделано:**
1. Коммит [`393ef2c`](https://github.com/craftkot/tgaumcraft/commit/393ef2c7670f1160edab3f9de1238eb1579aebcf): в реальном `ResearchTableBlockEntity` добавлены **строго серверные** smoke events для обоих исходных helpers:
   - `M07_ALCHEMY_AID_THEORY_CREATED` / `M07_INFUSION_AID_THEORY_CREATED` — только после повторной проверки актуальных блоков, создания сессии и списания исходной единицы вдохновения за помощник;
   - `M07_*_AID_CARD_DRAWN` — только после оплаченного бумагой draw и сохранения настоящих `CardChoice` с `fromAid=true`, без совпавших по имени карт обычного пула;
   - `M07_*_AID_CARD_ACTIVATED` — только после успешных серверных item/ink/paper/XP дебетов, применения эффектов, сохранения выбранной карты и sync игроку.
   Логи включаются только существующей диагностикой `THAUMCRAFT_SMOKE=1`. В обычном клиенте нет flood.
2. Там же добавлен Java core тест `ResearchAidTheoryFlowParityTest` и подключён в **отдельный** `tools/run_research_aid_parity.sh` (общий CI/Gradle не меняли). Проверяет помощь из тигля и матрицы, базовое 5 → 4 inspiration, оригинальные 3 класса карт и исходный порядок, seed, источник aid, генерацию 2 карт, 3-ю карту только с bonus, отсутствие side effects при pure preview и наличие источников маркеров. **Тест Java закоммичен, но не исполнен в контейнере без checkout всех NeoForge/core исходников.**
3. Коммит [`61265a9`](https://github.com/craftkot/tgaumcraft/commit/61265a9b7e3a786c40c05a11dc234f37142fe163): fail-closed `tools/audit_research_m07_aid_log.py` для конкретного игрового лога, требует порядок CREATED → CARD_DRAWN → CARD_ACTIVATED для ALCHEMY или INFUSION. Скрипт не принимает пустые/переставленные/чужие маркеры. `tools/test_research_m07_aid_log.py` проверен **локально Python: 10/10 PASS, py_compile PASS**. Это доказательство теста парсера, **не** Minecraft.
4. Коммит [`ff467d8`](https://github.com/craftkot/tgaumcraft/commit/ff467d8bfc6c1273d9580aab17ee7b155ba09fb2): устранены устаревшие ложные JavaDoc о якобы недоступных кнопках 2..6 в четырёх исследовательских классах (только комментарии). Устраняет риск будущего повторного написания уже существующей механики.

**CI / ограничения:** GitHub Actions `feature/research` [#39104410496](https://github.com/craftkot/tgaumcraft/actions/runs/39104410496) и [#39104569715](https://github.com/craftkot/tgaumcraft/actions/runs/39104569715) = failure, latest verify job `steps=[]`; runner не исполнил Java/Gradle/Minecraft. CircleCI [#250](https://circleci.com/gh/craftkot/tgaumcraft/250) = SUCCESS только `verify-connection`, не gameplay. **M07 игровые helper-доказательства пока 0/2**; маркеры подготовлены, реального игрового PASS нет. Общий индекс строго 59/100, теория 9/12.

**Следующий шаг M07.2:** на полноценном checkout с Java 21 исполнить `bash tools/run_research_aid_parity.sh`, full NeoForge build, затем реальный клиентский smoke `THAUMCRAFT_SMOKE=1`: поставить подтверждённый `thaumcraft:crucible` в диапазоне 4×1×4, выбрать его в research GUI, создать теорию, получить исходную aid-карту (Concentrate/Reactions/Synthesis), активировать её с РЕАЛЬНЫМ требуемым кристаллом/чернилами/бумагой, проверить списания и NBT. Прогнать `python tools/audit_research_m07_aid_log.py <server-log> --branch ALCHEMY`. Затем повторить с `thaumcraft:infusion_matrix` и `--branch INFUSION`. **Лог-маркеры сами по себе недостаточны:** проверить скриншоты, предметы, категории и cold restart. Если карта не выпадает из-за 25% вероятности — повторить реальную игровую теорию без подмены seed или материалов.

**После помощников:** все 33 карточки × NBT/материалы/награды, остальные реально портированные помощники, complete Thaumonomicon browser/category tree, scans/sky, save/reload, затем PR в main только по результатам полной приёмки; никаких merge.


## 18.10.2026 — M07.3 первый читательский каталог Таумономикона (без права менять исследования)

**Свежий старт:** main `a4280834`, feature/research `c123e59`. Проверены свежие ROADMAP, AGENTS, PARALLEL_WORKFLOW, свой lane, THEORYCRAFT_CARD_PROGRESS_RU и PROGRESS_AUDIT. GitHub-hosted workflow не работает; последний доступный до этого подхода завершён до шагов CI. Это не свидетельство ошибок Java.

**Добавлены новые исходники только исследовательской области:**
- [`6cea3d6`](https://github.com/craftkot/tgaumcraft/commit/6cea3d6eeed36d5c47b566b353d636f06634b889): `ResearchBrowserModel.java` — read-only модель видимости узлов BETA26; `ResearchIndexScreen.java` — клиентский просмотр 7 категорий и доступных из реального 148-узлового каталога исследований с текущим серверно синхронизированным состоянием игрока; `ResearchBrowserModelTest.java` — позитивные/негативные проверки исходной логики открытия, скрытых исследований и сохранения порядка.
- [`e70d50f`](https://github.com/craftkot/tgaumcraft/commit/e70d50f8753caf1677751848f5fd21c1596a7e03): кнопка `Research` в существующей `FirstStepsBookScreen`, не меняя сетевые FIRSTSTEPS-пакеты; standalone Java 21 runner `tools/run_research_browser_parity.sh`; тестовый игрок имеет достоверный bootstrap `!gotthaumonomicon`.
- [`8746d39`](https://github.com/craftkot/tgaumcraft/commit/8746d39ee38e108a51420000c7d52edae19a1153): не показывать неизвестные скрытые/заблокированные имена даже в списке родителей видимого исследования; негативные тесты, открытие ALCHEMY исключительно после полного `UNLOCKALCHEMY`. Сетевые действия и сторонние механики не тронуты.

**Проверено реально через GitHub connector:** 148 узлов source `BuiltInResearchCatalog`, 7 категорий исходного `BuiltInResearchCategories` в регистрации BASICS/AUROMANCY/ALCHEMY/ARTIFICE/INFUSION/GOLEMANCY/ELDRITCH. Десять статических структурных проверок PASS по свежему коду: launcher, чтение реального каталога и NBT knowledge, оригинальный book atlas, исходный category gate, скрытые записи и скрытые родительские ключи, отсутствие `sendToServer` в новом экране. Это **НЕ** Java compilation и **НЕ** Minecraft gameplay.

**Ограничения:** это **read-only индекс** с исходными research keys, а не точный пространственный BETA26 research tree, страницы исследований и рецепты; оригинальные X/Y координаты узлов не представлены в текущем generated projection `ResearchEntryData`. Не выдавать его за завершённый Thaumonomicon. Полная NeoForge/клиентская проверка должна подтвердить корректность `ResearchIndexScreen`; без неё M07.3 остаётся 🟡. Новые source-view Java тесты сохранены, но `bash tools/run_research_browser_parity.sh` пока **не исполнен**.

**Следующее:** выполнить Java 21 runner и full NeoForge на рабочем checkout; реальный клиент: открытие книги → FIRSTSTEPS → Research → категории, locked ALCHEMY/скрытые родители, получение UNLOCKALCHEMY → обновление видимости и холодное сохранение. Отдельно закрыть M07.2 активаторы помощников с оплаченной картой. Затем извлечь точные BETA26 x/y, chapter content и локализацию записей из оригинального источника для полноценного дерева без фальшивой геометрии. Индекс 59/100 без изменений; `main` и чужие feature-ветки не менялись.

## 18.10.2026 — M07.3 корректная работа оконной навигации Таумономикона

**Fresh-read:** `main` = `a4280834adc5a20b6951098af937d30ca4aeca56`, research HEAD до работы = `9ef9d0237c55684c8207a6f97dae437a2c9dc14b`. Заново проверены ROADMAP, AGENTS, PARALLEL_WORKFLOW, собственный lane, картотека и общий аудит, а также последние runs CI. Другие ветки не трогались.

**Исправлен подтверждённый дефект:** `ResearchIndexScreen.init()` ранее добавлял новые кнопки категорий в `categoryButtons` при каждом повторном вызове `init()` (включая resize). После этого `updateButtons()` индексировал список кнопок длиннее семи исходных категорий => `IndexOutOfBoundsException`. Коммит [`b94f41a`](https://github.com/craftkot/tgaumcraft/commit/b94f41a4c597ffccf4ded4851ce2b97f0050d7fc) очищает список перед повторной инициализацией, сохраняет однократную регистрацию семи кнопок и безопасно обрабатывает обновления server-side knowledge.

**Реальный Java-код, не только маркеры:** добавлен loader-neutral `ResearchBrowserPagination`; интерфейс использует единое ограничение номера страницы для стрелок, рендера и мышиных кликов. Изменение доступных исследований не допускает out-of-range page или выбора скрытого элемента. Исчезнувшее после синхронизации выбранное исследование удаляется из UI. Заголовок и входная кнопка используют уже существующий локализованный ключ `item.thaumcraft.thaumonomicon`; общие `en_us/ru_ru` ресурсы не менялись.

**Доказательства:** локально исполнен реальный Java 21 source `ResearchBrowserPagination.java` и `ResearchBrowserPaginationTest.java` командой `javac --release 21 ... && java ...`: **17/17 assertions PASS**, включая пустой список, переходы страниц, неполную последнюю страницу, изменение доступного количества, ошибочные индексы, граничный `Integer.MAX_VALUE`. Непосредственно по свежему GitHub read-back проверены вызовы `categoryButtons.clear()`, `ResearchBrowserPagination.of`, `entryIndexAtRow`, использование локализации. Удалён повторно перечисленный core source из `tools/run_research_browser_parity.sh` (core `find` уже включает его). Полный `ResearchBrowserModelTest`, NeoForge compile и реальный Minecraft всё ещё не были выполнены — их нельзя обозначать PASS.

**CI:** GitHub Actions на `b94f41a`: [39106946750](https://github.com/craftkot/tgaumcraft/actions/runs/39106946750) и [39106946728](https://github.com/craftkot/tgaumcraft/actions/runs/39106946728) = failure до подтверждённой полноценной сборки. CircleCI [#275](https://circleci.com/gh/craftkot/tgaumcraft/275) = success только `verify-connection`; не Java parity, не NeoForge, не Minecraft.

**Остаётся M07:** реальный Minecraft smoke полного paid draw helper `CRUCIBLE`/`INFUSION_MATRIX` с проверкой item/ink/paper/XP, проверка нового браузера после изменения окна и синхронизации категорий, точные BETA26 координаты и оригинальные страницы research tree, 33 карты в игре, остальные помощники, scan/sky, cold restart/save, full CI. Индекс `59/100` не повышается. Никаких коммитов в `main` и merge/PR до полноценной приёмки M07.

## 18.10.2026 — M07.3: оригинальная геометрия всего дерева и тест повторной загрузки знаний

**Fresh-read:** main `a4280834adc5a20b6951098af937d30ca4aeca56`; feature/research на старте `5a4a6d359695f3e4d5bce84f5db377932e754210`. Повторно проверены `main:ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, свой lane, `THEORYCRAFT_CARD_PROGRESS_RU.md`, `PROGRESS_AUDIT_2026_10_16.md`, веточные CI.

**Найден источник, ранее не учтённый:** `analysis/research_manifest.json` (исторический source snapshot `b97afece5`) содержит **реальные координаты всех 148 BETA26 исследовательских узлов** и исходные `name`/первый `icons[]`. Не нужно изобретать произвольную геометрию. Перепроверено по актуальному `BuiltInResearchCatalog`: все 148 ключей существуют и имеют те же категории. Внутри каждой из 7 категорий все 148 координатных позиций уникальны.

**Кодовые коммиты:**
1. [`5239add`](https://github.com/craftkot/tgaumcraft/commit/5239addf7c155757b6a99c40b549174c8e378101) — новый `LegacyResearchLayout.java` с **148 точными координатами/ключами/категориями/названиями/первым icon spec** из сохранённого BETA26 manifest, без правки generated `BuiltInResearchCatalog`; `LegacyResearchLayoutTest.java` для всех исходных ключей, уникальности и категорий; `tools/test_research_layout_manifest.py` для полного автоматического сравнения всех 148 записей mapping ↔ оригинальный JSON.
2. [`6739c54`](https://github.com/craftkot/tgaumcraft/commit/6739c54c1aebeeaeabe5d9d10d6715f94bcbf1a3) — `ResearchIndexScreen` теперь **по умолчанию рисует source-positioned tree**, с видимыми parent-соединениями внутри категории, флагами known/complete, выделением/tooltip названия и прокруткой четырьмя стрелками. Можно переключаться на прежний список и обратно. Скрин использует `LegacyResearchLayout.get()` и уже существующий `ResearchBrowserModel.entries()`, поэтому не рисует скрытые и недоступные записи. Координаты точные, но **внешний вид узлов и линий временный**, исходные иконки/шейпы/анимации ещё не перенесены.
3. [`2969926`](https://github.com/craftkot/tgaumcraft/commit/296992631397869f99dd10ac29947eb553c7e503) — `ResearchBrowserPersistenceParityTest.java`: loader-neutral тест сериализации/десериализации BETA26 knowledge через тот же `LegacyPlayerDataCodec`, что применяет player attachment. Сравнивает открытые категории, visible BASICS/ALCHEMY, hidden known `BATHSALTS`, стадию `ALUMENTUM`, завершённый `UNLOCKALCHEMY`, observation и отдельного игрока. Расширен `tools/run_research_browser_parity.sh`: запускает модель, pagination, layout, persistence, затем Python manifest parity.

**Выполненные проверки:** independent fresh-read через GitHub connector после коммита: 148/148 полных Java-мэппингов равны исходному JSON по `key/category/x/y/title/icon`, исходный порядок совпадает, несовпадений ноль. Дополнительно 10/10 структурных инспекций по экрану (использование source layout, scissor-полей, только видимых родителей и категорий, tooltip, read-only, переключение tree/list) успешно проверены. **Это не javac и не запуск клиента**.

**CI на HEAD:** GitHub Actions [#39110295959](https://github.com/craftkot/tgaumcraft/actions/runs/39110295959) = failure; `verify` job содержит 0 выполненных steps. CircleCI [#293](https://circleci.com/gh/craftkot/tgaumcraft/293) success только `verify-connection`. Нельзя утверждать compile PASS, Minecraft PASS или cold restart. Сейчас полный Java 21 runner и игровой клиент остаются непроверенными; нужно выполнить `bash tools/run_research_browser_parity.sh` в настоящем checkout и клиентский сценарий.

**Остаток M07.3/4:** проверить реальный клиентский граф (панорамирование, положение оригинальных узлов, category unlock и скрытие родителей), видимость после logout/restart, фактические page contents/recipes/research icons из BETA26 исходника и локализацию (в текущих `en_us/ru_ru` нет полного набора `research.*.title`); завершить игровое подтверждение M07.2 двух helper-backed карт со списанием материалов и довести оставшиеся карточки, scan/sky и сохранения. Это не полный Таумономикон, только геометрия и базовая навигация. Общий индекс **59/100**, без пересчёта и PR. `main`/алхимия/инфузия не изменялись.
