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
