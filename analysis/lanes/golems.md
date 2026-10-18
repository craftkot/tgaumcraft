# M10 — Големы и автоматизация

Рабочая ветка: `feature/golems`. Начало: 18.10.2026. База: `main` `a4280834adc5a20b6951098af937d30ca4aeca56`.

## Границы и исходное состояние

Прочитаны актуальные main: `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, `analysis/PROGRESS_AUDIT_2026_10_16.md`. Ветка ранее отсутствовала, создана от свежей main. Другие ветки не меняются; merge не разрешён. Общую дорожную карту и CI не переписывать. M10 начат по прямому поручению пользователя; рейтинг 59/100 не повышается.

На базе: CircleCI #232 verify-connection SUCCESS; последние GitHub Actions main FAILURE (не смешивать с CircleCI). Последние содержательные исходные доказательства main: compile #206 SUCCESS; real Minecraft #209 FAILURE. Это не проверки нового M10.

Официальный BETA26 JAR скачан из CurseForge file 2629023. SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f` совпал эталону проекта. API ZIP получен по ссылке Azanor из официальной темы; содержит отсутствующие в GitHub parts API. Бинарники/извлечённые оригинальные исходники находятся вне репозитория и не публикуются. Механики исследуются по API и `javap`; неподтверждённые правила не выдумываются.

## Подход и коммиты

1. `bfced89` — журнал начала M10 и проверенная база.
2. `933c2e4` — исходная модель Task/SealArea, server TaskBoard с leases и 922 содержательными проверками (root повторил standalone запуск: PASS).

## Проверки

- Java runtime: OpenJDK 21; установлен локальный JDK 21 для javac/javap и Gradle 9.2.1 вне проекта.
- Core: ещё не запускался в этом подходе.
- NeoForge: базовая компиляция запущена; результата пока нет.
- Minecraft: новые механики ещё не проверены.

## Следующие действия

Подтверждённые каталоги/сборка и Core-тесты; серверный голем с NBT и живой задачей; архитектура A–F; полная компиляция и реальный серверный сценарий. Изменения общих точек подключения выделять и перечислять для координатора. По окончании открыть Draft PR → main, не сливать.

## Рабочие заметки после bfced89

Task/Seal Core: standalone JDK21 тесты агента прошли 922 assertions; общий runner будет подключён отдельной минимальной правкой. Написаны Entity, placer, home goal, isolated registry module, pickup seal/task goal и provisional animated renderer. Архитектура: `analysis/GOLEM_SYSTEM_DESIGN_RU.md`. Полная первая компиляция дошла до javac, обнаружила отсутствующие ещё не сохранённые construction-модели и final API `canBreatheUnderwater`; последний заменён на NeoForge fluid extension. Это рабочий неуспешный проход, не PASS.

## Промежуточные результаты первого подхода (история)

- Существующий `bash tools/run_core_parity.sh` до добавления construction/test entries: PASS, включая прежние подсистемы.
- NeoForge `compileJava processResources`: **PASS** (38 s), JDK21/Gradle9.2.1/NeoForge21.1.250.
- Настоящий GameTestServer: **все 8 required tests PASS** (18.10.2026 12:08 UTC): placer/атрибуты, движение домой, cargo conservation/components, NBT+quarantine, два работника/один pickup, cancellation, death cargo, owner reclaim. Журнал вне git: `/workspace/reference/golems-gametest.log`, строка `All 8 required tests passed`.
- Прежние asset/loot/localization guards: PASS.
- Cold restart write/read запущен отдельно, итог ещё не получен. Клиент не запускался; Xvfb/xdotool отсутствуют.

## Итог Core и серверных проверок

- 6 материалов, 18 частей, 19 traits; derived stats, packed roundtrip и явная валидация. Golden и exhaustive construction tests: **34 808 assertions PASS**.
- Начальная сборка: `GolemAssembly` подтверждает 3 Greatwood plank + 2 mechanism_simple + 1 mind@0; остальные конфигурации дают явный UnsupportedOperationException вместо выдуманной цены. Начисление rank XP пока не реализовано.
- Полный `bash tools/run_core_parity.sh` с новыми construction/tasks entries: **PASS**, все прежние тесты также проходят.
- Отдельные настоящие dedicated server: WRITE assertions OK и READ assertions OK после полного shutdown/restart одного мира; один UUID, owner/home/props/XP/color/health, exact 37 named iron ingots, persisted seal, без duplicate ground cargo и stale tasks.
- `build -x test` PASS, JAR создан; финальный incremental build после добавления costs: **PASS**, 11 s, JAR ZIP CRC без ошибок.
- Полный протокол: `analysis/GOLEM_VALIDATION_2026_10_18.md`. Логи сессии: `/workspace/reference/golems-{core-final,compile,gametest,restart-write,restart-read,build-final}.log` (за пределами Git).

## Общие изменения для координатора

Отдельный integration commit: одна строка `GolemModule.register(modBus)` в `ThaumcraftPort`; по 5 новых EN/RU ключей; два теста в существующем Core runner (4 строки). Основные регистры, Gradle build/properties, CI, дорожная карта и другие ветки не менялись. Opt-in `tools/golems.gradle` добавляет только два тестовых запуска M10.

## Остаток после первого подхода (история)

Не перенесены: Golem Press и survival производство; остальные assembly costs и начисление XP; Fill/Empty/Provide/Stock, capability bridge контейнеров и сельское хозяйство; bell, фильтры/конфигурация и GUI печатей; боевые/летающие/специальные части и полный исходный визуал. Runtime placer допускает только default wood; полный каталог частей пока Core.

Не проверены: клиент (нет подготовленного Xvfb/xdotool), отдельный live chunk unload/reload, аварийное отключение посреди переноса. Не считать штатный рестарт crash-atomicity. Следующий независимый игровой шаг: BETA26 Fill → NeoForge ItemHandler и настоящий Pickup→golem→chest цикл с conservation при частичном заполнении/выгрузке. Параллельно подготовить полные component costs перед Golem Press.

Готовность всего M10 не объявляется; рейтинг 59/100 не повышается. Draft PR → main обязателен, merge не выполняется.

3. `6097041` — Core catalog/stats/traits/packed design, подтверждённая начальная стоимость и construction tests. Полный Core PASS.
4. `c6fbc05` — server entity, home/pickup AI, persisted seals, placer, provisional renderer и 8 настоящих GameTests + cold restart probe (все соответствующие runtime проверки PASS).

5. `2d6e66d` — небольшой integration commit: одна точка регистрации модуля, симметричные EN/RU ключи и подключение двух настоящих Core-тестов в существующий runner.

6. `b054928` — документационный коммит: архитектура A–F, аудит базы, отдельный протокол реальных проверок и итоговые границы. `main` перед публикацией повторно проверена: по-прежнему `a4280834`; удалённой feature/golems перед первым push нет.

Тестовые итоги относятся к Java/runtime дереву `2d6e66d` (позднее меняется только документация). JAR SHA256 `ba5234d8b666aedb9626b2b2b98576345a53d0851872e1332a9de1df8caf80ff`. Подробные команды воспроизведения находятся в протоколе.

## Публикация и передача координатору

Draft PR **[#3](https://github.com/craftkot/tgaumcraft/pull/3)** создан `feature/golems → main`. Не объединён. Все локальные commits сохранены обычными commits; публикация — обычный fast-forward push без force. Точный текущий HEAD смотреть в истории PR/ветки (`git rev-parse HEAD`); этот финальный служебный коммит добавляет только ссылку на PR.

Продолжение: читать main и этот журнал, не перезапускать исследование/тяжёлые проверки без новых изменений. Следующий код — Fill/container adapter и полноценный цикл в контейнер. Для Core использовать существующий runner, для игровых regression — optional `--init-script tools/golems.gradle runGolemGameTest`; cold restart команды и границы описаны в протоколе.


## M10.4 — Pickup → голем → контейнер (продолжение)

База подхода `e7333e9`. Свежая main по-прежнему `a4280834`; последние CircleCI проверки своей ветки: #366 SUCCESS. Прочитаны актуальные правила/карта и прежние findings, повторного поиска исходников не проводилось. Новые агенты не запускались.

Подтверждение: официальный BETA26 `SealFill.tickSeal/onTaskStarted/onTaskCompletion/canGolemPerformTask`, `AIGotoBlock`, `EntityThaumcraftGolem.addRankXp` из ранее проверенного JAR. Исходные defaults: empty blacklist, meta/NBT true, ore/mod/exist false; запрещён CLUMSY; block task раз в 20 ticks и successor при резервировании. Никаких новых придуманных фильтров/рецептов.

Реализовано:
- Единое SavedData и TaskBoard для PICKUP/FILL, одна печать любого типа на грань. Старый формат pickup сохраняется; тип Fill переживает NBT. Задачи и leases не сохраняются.
- `GolemFillGoal` priority4 между pickup3/home5: подход к блоку, исходный reach4, перепрокладка/детур, ограничение1000 ticks, возврат к работе после отмены.
- `GolemContainers`: NeoForge sided ItemHandler, исходный first-carried-stack, simulation перед назначением и фактический remainder при завершении; остаток возвращается в руку. Capability разрешается заново у цели; нет принудительной загрузки support/face/double-chest partner.
- Отсутствие handler на существующем блоке: исходный реальный ItemEntity около выбранной грани; отсутствие загруженного chunk не считается world-drop целью.
- Удалённая/заполненная цель и старая резервация не отнимают груз. Токен связан также с измерением и конкретной гранью печати, его нельзя перенаправить.
- Fill даёт 1 XP только после фактического переноса. SMART/server-only, `(rank+1)^2*1000`, максимум один rank за вызов, cap10; обновление характеристик не лечит бесплатно. Для default wood XP не начисляется.
- `seal_fill` зарегистрирован в собственном GolemModule/Creative, типизированный GolemSealItem проверяет владельца и тип при снятии; временная vanilla hopper иконка. Общие регистры/Gradle/CI не менялись; общие файлы — только 3 новых EN/RU ключа на язык.

Проверки этого подхода:
- Полный Core runner PASS, прежние construction34808/tasks922 и все остальные тесты.
- Full NeoForge compile/resources и `build -x test` PASS.
- Первый server GameTest: **все15 required PASS** (8 предыдущих+7 новых), 4.171s игровых сценариев; полный запуск1m10s.
- После review добавлена отрицательная проверка перенаправления валидного lease на другую грань. Финальный GameTest/build: **все 15 required PASS**, 4.837 s набора / 51 s полный запуск; JAR ZIP CRC без ошибок.
- Новый отдельный мир `build/golem-fill-restart`: WRITE и READ assertions **PASS**, два полных процесса dedicated server. Восстановлены один golem UUID, точный именованный груз37, обе печати PICKUP/FILL и владелец, 11gold в barrel; нет stale reservations и duplicate ground cargo.
- Старый тестовый мир не удалялся и не перезаписывался; новый сценарий выбирается `-PgolemRestartScenario=fill`.

Остаток: настраиваемые filters/limits/pexist/redstone/GUI; Empty/Provide/Stock; farming; Golem Press/остальные component costs; специальные части/бой/полёт и исходный визуал. Runtime placer по-прежнему только default wood. Клиент, настоящий live chunk unload/reload и crash-atomicity не проверены. 59/100 и полный статус M10 не пересчитываются.

Следующий конкретный этап: конфигурация Fill/Pickup (фильтр, лимит количества, цвет/приоритет/redstone) с проверкой server authority и обычный survival-способ получения печатей. Затем Empty/Provide по точному BETA26, без изменения чужих направлений.

Итоговый JAR M10.4: `build/libs/thaumcraft-6.1.BETA26-port.stage9.jar`, SHA256 `f0f0fd66c33bf71e48341db09087efa51e45986ea561adf5590f5329efeb059a`.

Коммит этого шага — реальная Fill/container механика и 7 новых GameTests; последующие коммиты отдельно выделяют restart fixtures и общие EN/RU ключи.

- `1117c6a` — единые typed seals, Fill AI, sided containers, actual remainder/SMART XP и 15 GameTests PASS.
- Следующий коммит — cold restart сценарий Fill и протокол доказательств; WRITE/READ PASS.

- `f8ee53f` — отдельный Fill restart fixture и документация.
- Финальный малый integration commit этого подхода — только 3 EN/RU ключа на язык и эта запись; основные регистры/CI/Gradle не меняются. Draft PR остаётся #3, merge не разрешён.

## M10.5 — текущий срез filters/limits/menu, 18.10.2026

- Продолжение от `7114949`; fresh main `a4280834` не изменился. CI исходного HEAD CircleCI #367 SUCCESS; Draft PR #3 остаётся открытым.
- Работа одним агентом в `feature/golems`, без изменения main/чужих веток/общих регистров/CI/build.gradle.
- Проверены оригинальные basic filter, whitelist stock cap, zero=unlimited, first matching hand. Обнаружен и явно сохранён BETA26 дефект ground count: непустые stacks пропускаются; строгий общий запас гарантируется только в container adapter.
- Реализованы чистые Core rules, копируемый immutable runtime filter, persisted Items/Sizes/bl, отмена устаревших tasks при редактировании, instance token для stale menu. Минимальное owner menu использует vanilla packets, ghost count1 и split integer DataSlots; новый клиентский экран — собственный временный визуал.
- Core runner PASS (`golems-filter-core.log`), NeoForge compileJava PASS Java21 (`golems-filter-compile.log`). Полный build и expanded dedicated GameTests запущены; ниже записывается фактический исход.

Первый малый коммит M10.5 выделяет `SealFilterRules` и 15 новых Core assertions в существующий `LegacyGolemTaskTest` (всего937). Общий runner не изменялся. Все Core suites PASS Java21. Runtime/menu/restart изменения находятся в следующем коммите после реальных серверных проверок.

- `138961d` — Core filter rules +15 assertions; 937 task assertions и весь runner PASS.
- Начальный expanded server suite: 21/21 PASS, полный build PASS. Дополнительно введён 22-й автономный filtered AI cycle. Он выявил исчезновение запрещённого ItemEntity при сохранности всех17 переносимых предметов; проверки не ослаблялись. Диагностика фиксирует remove stack для установления причины; окончательный исход ниже.

Диагностика 22-го теста установила точный вызов удаления: `GameTestInfo.succeed` → discard ItemEntities, затем последний `onEachTick` того же тика. Механика сохранила17, финальные assert cap12/remainder5 уже проходили. Исправлена только последовательность теста: `startSequence().thenIdle(200).thenExecute(...).thenSucceed()` завершает его после tick callbacks. Временный tracing удалён, проверки не ослаблены. Диагностические неуспешные запуски сохранены вне Git (`golems-filter-{reviewed,diagnostic,final-fixtures,removal-trace}.log`); источник подтверждён stacktrace, а не предположением о merge или потере груза.

Финальный runtime результат M10.5 (`golems-filter-verified.log`): **все22 required GameTests PASS**, 6.425s набора /45s полный `build -x test runGolemGameTest`; Java21 NeoForge21.1.250. Проверены обе политики, full AI cycle, свежий stock budget двух workers, components/HAULER/actual remainder, stale/redirected leases, server menu authority/ghost, backward-compatible NBT. JAR ZIP CRC PASS, SHA256 `c6a08efca9d5216f5c8a65394a2748f53a88d9236ec53b57f5bc21236987343b`.

Следующий runtime commit содержит фильтры, меню и 7 новых GameTests + дизайн; отдельные следующие коммиты выделяют cold-restart fixture/протокол и только7 EN/RU ключей на язык. Cold WRITE запущен в новом `build/golem-filter-restart`; результат не предполагается заранее. Общие регистры/runner/CI/Gradle не менялись.

- `d907402` — persisted filters, server menu, строгая привязка Pickup lease к UUID и грани, контейнерный Fill cap и 22/22 GameTests PASS.
- Cold filter-сценарий: **WRITE+READ assertions PASS** (1m2s/44s, логи `golems-filter-restart-write.log` и `golems-filter-restart-read.log`). Новый отдельный мир; один работник с cargo37, обе печати, barrel11gold и named ghost/modes/int70000 восстановлены без stale leases/копии груза. Этот commit выделяет только restart fixture и протокол.
- Дизайн A–F и протокол актуализированы. Следующий конкретный этап: исходные color/priority/owner-lock и redstone настройки печатей с server authority, затем survival-рецепты/исследования и Empty/Provide. Полный Golem Press/стоимости всех частей, farming, Bell, advanced flags/pexist и исходный визуал остаются открыты. Runtime placer пока только default wood.
- Графический клиент, live chunk unloading, внешние inventory mods и crash atomicity не проверены. M10 целиком не закрыт, общая оценка59/100 не изменяется.

- `279aa72` — отдельный filter restart fixture и протокол, WRITE/READ PASS.
- Последний малый integration commit M10.5: только7 новых menu EN/RU ключей на язык и эта запись; JSON валиден. Локализации уже присутствовали в прошедшем полном build/22 tests и JAR с указанным SHA. Ни один общий registry/build.gradle/CI/Core runner не менялся в этом продолжении.
- Draft PR: https://github.com/craftkot/tgaumcraft/pull/3 (main), не объединять автоматически. Коммиты этого продолжения следуют за `7114949`; итоговый HEAD — commit с menu translations, `git rev-parse feature/golems`. Отправка одним обычным fast-forward push, без rewrite/merge/force.

## M10.6 — управление печатями, 19.10.2026

- Fresh main `a4280834`, feature HEAD `2ad3e2e`, clean tree; CI #369 SUCCESS. Продолжение одним агентом в собственной ветке; общая карта/регистры/CI/runner не меняются.
- Подтверждены оригинальные bounds, action IDs и redstone support/outward semantics по официальному BETA26 javap. Код подключает сохранение/меню/producer/claim/completion; изменение control settings отменяет прежние leases. UUID owner-lock использует документированную корректировку исходного bug.
- Подготовлены4 новых GameTest: bounds/menu authority/NBT/no forced chunks; owner/color/Pickup support power; Fill outward power/default ignore/late cancellation/resume; реальный выбор по priority. Тяжёлые проверки выполняются после реализации; фактические итоги ниже.

Core runner Java21 PASS (`golems-controls-core.log`). Первый server suite нашёл2 неверных ожидания новых tests: `reservedTasks()==0` немедленно после suspend. Исходная TaskBoard сохраняет reserved flag до tickSecond cleanup; это не работающий lease. Tests исправлены на проверку конкретного target/fillTarget, сохранности груза и невозможности воскресить старый lease даже после signal-off. Core/production semantics ради счётчика не менялись. Финальный повтор включает также guard незагруженного signal-neighborhood.

Runtime M10.6: **Core PASS**, **NeoForge build +26/26 required GameTests PASS** (`golems-controls-verified.log`, 8.299s сценариев /44s полного запуска). Новый unloaded-neighborhood assertion присутствует в скомпилированном test class и выполнен в этом прогоне. JAR ZIP CRC PASS, SHA256 `3a0a897241ffef524971ac106336d2361f10b5cd2416675c48f45eb240f27435`. Этот commit содержит control service/menu/redstone и4 новых GameTests с дизайном; cold probe и6 EN/RU ключей на язык выделяются далее. WRITE запущен в новом isolated fixture; итог cold read фиксируется после реальных assertions.

- `6dd5e8f` — control menu/service/redstone +4 новых GameTests, 26/26 PASS.
- Cold WRITE assertions PASS (1m8s). Первый READ правильно не объявлен PASS: fixture проверил getEntity до завершения асинхронной entity загрузки. Offline inspection MCA подтвердил сохранённый `thaumcraft:golem` с точным UUID в chunk0,0; SavedData на месте. Probe теперь явно инициирует fixture chunk и ждёт areEntitiesLoaded/isPositionEntityTicking (deadline600 ticks); сами assertions не ретраятся. Повторный READ использует тот же сохранённый мир, без повторной записи/удаления.

Отдельный малый integration commit содержит только6 EN/RU control labels на язык и журнал. Они уже присутствовали в успешном full build и26 GameTests; JSON валиден. Графический клиент/раскладка экрана не проверены. Остаток M10: survival-рецепты печатей/Press и остальные costs, Bell/раскраска голема/area/advanced flags, Empty/Provide/Stock, farming, специальные части/бой/полёт и исходный визуал; M10 не закрывается, 59/100 не меняется. Следующий конкретный этап — подтверждённые BETA26 survival-рецепты печатей с зависимостями от research/arcane crafting.

- `a9a0b8b` —6 EN/RU labels на язык.
- Финальный cold READ **assertions PASS** (52s, `golems-controls-restart-read-ready.log`) на том же сохранённом мире после явной готовности entity chunk. Восстановлены один UUID, cargo37 с components, обе печати/фильтры, barrel11gold и все priority/color/lock/redstone. Нет stale leases/копии груза. Повторный build PASS, JAR CRC PASS/SHA256 `4c961fa3e0bddb549659f12ac4ee62cc4d66655f5c9af925b7ace3483e1be692`.
- Этот заключительный test commit выделяет isolated controls restart и readiness barrier; предыдущие fixtures не удалены. Общие registry/build.gradle/CI/Core runner не менялись. Draft PR #3 обновляется без merge, обычный push только собственной ветки.

## M10.7 — survival-рецепты печатей, 19.10.2026

Fresh main `a4280834`, feature HEAD `375fc14`, CI #370 SUCCESS; clean tree. Работа одним агентом, только feature/golems. Официальный ConfigRecipes подтверждает arcane Blank3 (CONTROLSEALS,20vis,Aer1,clay+tallow+red dye+Nitor); Pickup/Fill — crucible conversions за10Desiderium/10Aversio и blank1. Core/adapter/module остаются lane-local; необходимая минимальная интеграция в2 общих файла выделяется отдельно. Существующий Nitor tag переиспользуется без изменения.

Core runner PASS (включая tagged aspect identity bridging). Первый compile выявил только неверное имя null-player test entrypoint (`dissolve`, не двухаргументный attemptSmelt); исправлен. В server tests добавлены реальное растворение crystals, три server output/cost/resource сценария и откат при rejected ItemEntity spawn. Финальный build/GameTests запущен; фактический исход ниже. Прогресс research не выдаётся автоматически; полная survival-цепочка Press/MINDCLOCKWORK остаётся зависимостью.

Финальный M10.7: **Core runner PASS**, construction34808 / task+filter+recipe948 assertions; **full NeoForge build и29/29 required GameTests PASS** (`golems-recipes-complete.log`,6.071s/40s). Новый menu test сначала неверно пытался заменить Aer на запрещённый Ignis через setItem: существующий slot корректно отклонил замену, оставив Aer. Fixture теперь очищает slot и отдельно проверяет изменение components уже вставленного crystal. Production crystal validator ради теста не менялся. Оба crucible tests, включая реальное растворение20 crystals и отменённый spawn, PASS. JAR CRC PASS/SHA256 `58ab307537568653113f35a05d9116e39282368541f785a9318dedf89e05c342`.

Первый commit выделяет чистые Core rules, lane-local adapter/blank registration/model и Core assertions. Следующий — необходимые hooks в2 общих файлах и3 server GameTests; затем отдельно EN/RU и протокол. Общий runner/Gradle/CI/research data/регистры не меняются.

- `8f93441` — pure seal recipes, adapter/blank item/model и11 новых Core assertions; весь runner PASS.
- Этот integration commit затрагивает только ArcaneWorkbenchMenu (preview/20vis/Aer mask, оплаченный batch и commit validation) и CrucibleBlockEntity (branch, exact debit, rejected-spawn rollback), плюс3 lane-local GameTests. Зависит от8f93441; итоговое дерево прошло29/29. Для координатора: применять вместе с recipe adapter, остальные алхимические ветки/рецепты/heat/регистры не переписывались.

- `c64f491` — минимальная интеграция arcane/crucible и3 server GameTests,29/29 PASS.
- Заключительный малый commit: только1 EN/RU blank-item label на язык, протокол проверок и журнал. Локализации/модель присутствовали в прошедшем full build/JAR. Холодный restart в этом срезе не повторяется (новых форматов сохранения нет); клиент и recipe-book/Thaumonomicon display не проверены.
- Остаток: полный Press/стоимости частей/research progression, Bell/раскраска/области/advanced filters, Empty/Provide/Stock, farming и специальные части/исходный визуал. Следующий конкретный этап — исходный Golem Bell: рецепт и серверное управление, если подтверждены source dependencies. M10 целиком не закрыт,59/100 без изменений.
- Draft PR #3 →main остаётся draft; обычный push только feature/golems. Обновлённая архитектура и evidence находятся в GOLEM_SYSTEM_DESIGN_RU.md и GOLEM_VALIDATION_2026_10_18.md. Рабочее дерево после коммитов должно быть clean; merge/force/rebase не выполнялись.

## M10.8 — Golem Bell, 19.10.2026

Fresh main `a4280834`, feature HEAD `c5795ce`, CI #371 SUCCESS; clean tree. Работа одним агентом в feature/golems. Подтверждены original normal recipe (raw class strings с trailing spaces), Bell seal interactions, GOLEMDIRECT follow/stay, FLAGS bit1, source follow thresholds/speed/repath/teleport ring. Реализованы lane item/recipe/tags, owner command, follow goal и возврат печати с accepted-spawn+instance guard. Общие меню/алхимия/регистры/CI/runner не меняются.

Core runner PASS (6 дополнительных FLAGS assertions). Первый dedicated suite:31 из33 сценариев прошли. Новые fixtures уточнены: боковая печать на Y0 находилась ниже области наблюдения, перенесена на открытую грань блокаY1; физически стоящий на floor голем помечается onGround до первого AI tick, иначе допустимый исходный failed-path fallback не позволяет доказать именно walking. Production ради этих assertions не менялся. Повторный полный run запущен; фактические итоги ниже. Последующий cold Bell fixture отдельно проверит сохранение follow bit и прежних cargo/seal controls; старые миры не трогаются.

Итог runtime: **Core runner PASS** (construction34808, task/filter/recipe/Bell954 assertions), **full build и33/33 required GameTests PASS** (`golems-bell-verified.log`,8.062s/47s). Проверены paid vanilla recipe+mirror, exact-face owner menu/refund/rejected spawn, реальные reservation release и navigation follow→stay→automation. Menu test подменяет только transport FakePlayer, не настройки/валидацию; настоящий графический клиент не запускался. JAR CRC PASS/SHA256 `4834e642d2b0304adbde1df53574b23d19cc79c6cad1eae8d51036264dae95ba`. Отдельные positive teleport-fallback/owner-dimension-switch scenarios пока не добавлены; modern ground walking проверен. Bell WRITE запущен в новом isolated world; cold итог ниже.

Первый commit M10.8 содержит Bell Core/item/recipe, режим/goal, seal interaction и4 новых GameTests +дизайн. Отдельные следующие commits выделяют restart fixture/evidence и3 EN/RU keys на язык. Common registry/ArcaneWorkbenchMenu/CrucibleBlockEntity/research data/CI/runner не менялись.

- `73f8043` — Bell recipe/commands/follow/seal management +4 server GameTests,33/33 PASS.
- Cold Bell: **WRITE/READ assertions PASS** (1m22s/40s), два процесса на одном новом мире. Восстановлены один golem UUID, cargo37/components, все прежние entity/filter/controls и follow bit2, без duplicate cargo или stale reservations. Этот commit выделяет restart fixture и протокол; предыдущие миры не удалялись и не перезаписывались.
