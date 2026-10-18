# M10: проверки 18.10.2026

Ветка `feature/golems`, база `a4280834adc5a20b6951098af937d30ca4aeca56`.
Окончательные SHA и результаты — в [журнале](lanes/golems.md).

## Среда и происхождение

- JDK Eclipse Temurin 21.0.12.1+1, Gradle 9.2.1, NeoForge 21.1.250, Minecraft 1.21.1.
- Официальный BETA26 JAR, SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`; совпадает с эталоном проекта.
- Исследование: API автора, API ZIP из его официальной темы, `javap -c -p` официального JAR. Исходные бинарники и извлечённые оригинальные файлы в Git не добавлялись.
- Пользовательские ветки и `main` не менялись; общие изменения перечислены в журнале.

## Воспроизведение

Нужны Java **21** с `javac`, Gradle **9.2.1**, доступ к Maven/Minecraft зависимостям и штатная настройка доверия CA/прокси среды. В облачной сессии инструменты установлены вне репозитория, общая конфигурация Gradle и CI не менялась.

```bash
bash tools/run_core_parity.sh
gradle --init-script tools/golems.gradle build -x test
gradle --init-script tools/golems.gradle runGolemGameTest
```

`tools/golems.gradle` — необязательный init script: добавляет две M10-конфигурации к существующему ModDev. Не использовать имя `test` как доказательство выполнения standalone Core-тестов: их запускает именно `tools/run_core_parity.sh`.

Для холодного перезапуска используется **отдельный тестовый мир** `build/golem-restart`. Перед первым запуском каталог мира должен отсутствовать. Создать `build/golem-restart/eula.txt` с `eula=true`, а `server.properties` настроить для локального теста: `server-ip=127.0.0.1`, свободный `server-port`, `online-mode=false`, `spawn-protection=0`. Миру GameTest соответствует `build/golem-gametest`; при использовании его настроек также отключить spawn protection.

```bash
gradle --init-script tools/golems.gradle -PgolemRestartPhase=write runGolemRestart
gradle --init-script tools/golems.gradle -PgolemRestartPhase=read runGolemRestart
```

Probe включён только явным system property из этого run. `write` создаёт настоящую сущность и печать, кладёт именованный груз, проверяет входные условия и штатно останавливает сервер. `read` запускает **новый JVM-процесс с тем же миром**, ждёт загрузки chunk/entities и проверяет UUID/конструкцию/владельца/дом/XP/цвет/здоровье/точные item components, одну сущность и отсутствие копии груза на земле. Задачи и reservations намеренно не переживают рестарт.

Положительные строки: `GOLEM_RESTART_WRITE_ASSERTIONS_OK` и **отдельно** `GOLEM_RESTART_READ_ASSERTIONS_OK`. Один лишь exit code Gradle не заменяет обе проверки; при `GOLEM_RESTART_ASSERTIONS_FAILED` результат отрицательный.

## Что проверяют игровые тесты

В `GolemGameTests` находятся восемь серверных сценариев с настоящими `ServerLevel`, навигацией, `ItemEntity`, `ItemStack`, FakePlayer-взаимодействиями и NBT:

1. Survival placer: один spawn и один расход, исходные 16 HP/2 armor/.3 movement attribute, владелец и дом; отказ при препятствии и некорректных props без расхода.
2. Возврат домой с реальным изменением позиции и препятствием на пути.
3. Переносимые stacks: частичное слияние, отдельные components, max stack size и независимость ссылок/remainder.
4. NBT round trip дважды: идентичность, свойства, flags/цвет/XP/здоровье и обе руки; ephemeral working не восстанавливается. Повреждённые props сохраняются в карантине без потери груза.
5. Два работника, одна печать, один реальный stack из 17 diamonds: движение, единственный сборщик, сумма ground+hands неизменна.
6. Резервирование и удаление цели/печати: устаревшее назначение не создаёт предметы.
7. Реальная смерть с грузом: точные количество, имя и повреждение инструмента; один путь сброса, повторный урон не повторяет перевод.
8. Владелец забирает голема в placer: один placer с props/XP, один груз, повторное взаимодействие с удалённой сущностью не создаёт копию.

## Результаты

Таблица отражает фактически завершённые запуски, а не наличие тестовых классов.

| Проверка | Факт |
|---|---|
| Существующий Core runner до добавления construction | PASS |
| Task/SealArea standalone, 922 assertions | PASS |
| `compileJava processResources` с новыми runtime-классами | PASS, 38 s |
| Полный Core runner с обоими новыми тестами | **PASS**, construction 34 808 и tasks 922 assertions, все прежние тесты сохранены |
| Восемь server GameTests | **PASS**, все 8 required, 12:08 UTC, игровой набор 2.372 s; полный запуск 1 min 40 s |
| JAR packaging | **PASS**, финальный `build -x test` после costs: 11 s; ZIP CRC проверен |
| Два запуска dedicated server, write/read | **PASS**, оба сервера достигли Done; WRITE и READ assertions OK, штатные остановки и сохранение |
| Клиент/картинки/анимации | Не запускались: графическая среда не подготовлена |
| Отдельный живой unload/reload chunk в активном мире | Не запускался |
| Аварийное отключение в середине cross-entity transfer | Не проверялось |

## Практические границы

Runtime placer принимает пока **только стандартную деревянную конструкцию**, хотя Core хранит весь исходный каталог. Остальные части не рекламируются как работающие големы с реализованным полётом/боем. Нет survival-изготовления в Golem Press, Fill/Empty/Provide/Stock и полного маршрута в контейнер; фильтры/настройки/GUI печати и bell пока отсутствуют. Pickup использует исходный empty-blacklist default, область 5×1×5 над блоком для UP. Предметы доступны в Creative Tab и командами для разработки; бесплатный survival-рецепт не добавлен.

Модель, текстура и item icons временные, самописные/ванильные. Клиентская регистрация скомпилирована, но визуальный результат не проверен. Нормальный рестарт не доказывает атомарность нескольких entity saves при обрыве питания. Полный M10 и рейтинг 59/100 не меняются по этим узким проверкам.

Стоимость подтверждённой базовой конструкции реализована в `GolemAssembly`: 3 Greatwood planks + 2 Simple Arcane Mechanism + 1 Clockwork Mind (`mind@0`). Это расчёт ингредиентов в Core, не выдача результата/рецепт и не готовый пресс. Остальные стоимости и начисление rank XP оставлены явными пробелами.

Финальный JAR: `build/libs/thaumcraft-6.1.BETA26-port.stage9.jar`, SHA256 `ba5234d8b666aedb9626b2b2b98576345a53d0851872e1332a9de1df8caf80ff`. Java/runtime дерево для этих результатов соответствует `2d6e66d` плюс последующие изменения документации. Добавление Core стоимости базовой сборки не меняло уже прошедшие восемь игровых сценариев; после него повторены полный Core и incremental full build.


## Дополнение M10.4 — Fill

Новые правила сверены с `SealFill`, `AIGotoBlock` и `EntityThaumcraftGolem.addRankXp` того же официального JAR. Повторный полный Core runner прошёл; NeoForge compile/build прошли. Набор расширен до15 GameTests (8прежних+7новых):

1. Actual ground item → живое движение голема → настоящий chest, с conservation на каждом tick и сохранением custom name.
2. Два работающих голема и только5 свободных мест: destination+hands сохраняют79 diamonds, ровно5 перенесены.
3. Handler обещает принять10 на simulate, реально принимает2: у голема остаются8; разные item components не сливаются.
4. Реальная furnace capability: raw iron не входит снизу, входит сверху; coal входит сбоку.
5. Container заполнен после выбора задания, seal удалён или lease перенаправлен на другую грань: груз остаётся прежним.
6. Исходный fallback без container создаёт один ItemEntity; повторный callback не повторяет предмет/XP; Fill1XP повышает SMART999→rank1/XP0 без бесплатного лечения.
7. Typed seal NBT и zero restored reservations; producer/adapter не загружают удалённый chunk.

Первый запуск: `All 15 required tests passed`, 4.171s внутри игрового набора, 1m10s полный Gradle запуск. После добавления строгой привязки lease к грани проведён отдельный финальный запуск, **PASS: все 15 required, 4.837 s / полный запуск 51 s**.

Холодный перезапуск нового Fill-сценария: **WRITE+READ PASS**. Проверены одна сущность,37 именованных iron ingots, сохранение PICKUP и FILL, owner/props/home/XP/color/health,11gold в barrel, отсутствие старых leases и копии груза. Свежий fixture — `build/golem-fill-restart`; старый `build/golem-restart` не трогается. EULA/server.properties создаются так же, как в предыдущем сценарии.

```bash
gradle --init-script tools/golems.gradle build -x test runGolemGameTest
gradle --init-script tools/golems.gradle -PgolemRestartScenario=fill -PgolemRestartPhase=write runGolemRestart
gradle --init-script tools/golems.gradle -PgolemRestartScenario=fill -PgolemRestartPhase=read runGolemRestart
```

Логи сессии: `/workspace/reference/golems-fill-{core,compile,gametest,final,restart-write,restart-read}.log`. Подготовленные зависимости позволяют `--offline`; в чистой среде они сначала должны быть загружены штатным Gradle способом. Полная внешняя compatibility с чужими inventory mods, клиент, live chunk unloading и аварийный power loss не проверялись.

JAR M10.4 SHA256 `f0f0fd66c33bf71e48341db09087efa51e45986ea561adf5590f5329efeb059a`; ZIP CRC PASS. Последующие изменения касаются документации.

## Продолжение M10.5 — filters/limits/menu

Источники и подтверждённый дефект BETA26 ground count описаны в design M10.5. Предыдущие сценарии сохранены. Новые server assertions:

1. Pickup whitelist по item/components, копия ghost count1; смена на blacklist отменяет lease; размер не ограничивает Pickup. Изменение components на земле повторно проверяется; подмена target UUID при валидном lease запрещена.
2. Два заранее зарезервированных Fill, стартовый запас5, cap12, у каждого5: в цели12, остаток3; plain diamonds с другими components не учитываются в cap.
3. HAULER выбирает подходящую offhand, не затрагивая mainhand stone; handler реально принимает2 вместо6 — withheld+remainder сохранены; пустой whitelist запрещает перенос, size0 разрешает без лимита.
4. Реальный серверный Menu: ghost никогда не расходует/не выдаёт предмет; THROW/SWAP/CLONE/drag/quickmove безопасны. Owner/distance/instance, некорректные action IDs, shift-copy, limit70000 через два DataSlot, saturation int max, stale delete/recreate.
5. Registry-aware NBT сохраняет item components, bl, full int size и ghost count1; старые строки без настроек сохраняют прежние defaults; leases не восстанавливаются.
6. Исходный world-drop с дефектным ground count сохраняет каждый предмет и исходный per-drop cap; не выдаётся за строгий общий лимит на земле.
7. Полностью автономный AI выполняет filtered Pickup → navigation → Fill до cap12 из17, оставляет5 у голема и5 неподходящих diamonds на земле; conservation проверяется на каждом tick, по завершении нет зацикленного задания.

Команды (Java21; в этой среде cached Gradle позволяет `--offline`):

```bash
bash tools/run_core_parity.sh
gradle --offline --init-script tools/golems.gradle build -x test runGolemGameTest
gradle --offline --init-script tools/golems.gradle -PgolemRestartScenario=filter -PgolemRestartPhase=write runGolemRestart
gradle --offline --init-script tools/golems.gradle -PgolemRestartScenario=filter -PgolemRestartPhase=read runGolemRestart
```

Новый restart-сценарий использует отдельный `build/golem-filter-restart` (eula=true, online-mode=false, port25590, view/simulation2). Старые миры не удаляются и не перезаписываются. Проверяет предыдущие инварианты сущности/груза/barrel и дополнительно named ghost count1, blacklist Pickup, whitelist Fill и int cap70000 после двух процессов dedicated server.

Логи сессии вне Git: `/workspace/reference/golems-filter-{core,compile,final,reviewed,restart-write,restart-read}.log`. Меню тестируется на сервере; GUI Minecraft-клиент и его раскладка остаются непроверенными. Внешние inventory mods, live chunk unload и crash-atomic transfer по-прежнему не проверены. Реальные итоги финальных запусков записываются ниже и в lane-журнале.

При расширении с21 до22 тестов обнаружен дефект test harness usage: прямой `h.succeed()` в `runAfterDelay` удалял ItemEntity до последнего `onEachTick` в том же тике. Трассировка подтвердила `GameTestInfo.succeed` как источник discard; все17 переносимых предметов и final cap12/remainder5 уже сохранялись. Завершение переведено в GameTestSequence после tick callbacks, временный tracing удалён. Диагностические ошибки не объявлялись PASS.

Итог M10.5: **Core runner PASS**, construction34 808 /task+filter937 assertions; **build +22/22 required GameTests PASS**, 6.425s сценариев /45s полного запуска (`golems-filter-verified.log`). **Cold filter WRITE/READ assertions PASS**, два процесса dedicated server (1m2s /44s); сохранены один UUID/owner/props/home/XP/color/health/точный cargo37, обе печати, barrel11gold, ghost item/components count1, blacklist/whitelist и size70000. Нет старых leases и дубликата груза.

Итоговый JAR ZIP CRC PASS; SHA256 `c6a08efca9d5216f5c8a65394a2748f53a88d9236ec53b57f5bc21236987343b`. Эта проверка относится к реализованному срезу; графический клиент, live chunk unload/reload, внешние inventory mods и аварийный обрыв посреди transfer не запускались.
