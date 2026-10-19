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

## M10.6 — controls, 19.10.2026

Четыре новых server GameTests: исходные menu bounds/action IDs/owner authority/NBT и unloaded signal guard; Pickup owner/color и support signal; Fill outward signal, default-ignore, late cancellation и повторное выполнение после signal-off; selection между реальными Fill tasks по priority. Предыдущие22 scenarios сохраняются. После suspend проверяется недействительность конкретного lease и точный груз, а не немедленное обнуление глобального reserved counter: исходная TaskBoard убирает suspended rows раз в секунду.

Для нового cold-сценария используется `-PgolemRestartScenario=controls`, отдельный `build/golem-controls-restart` (online-mode=false, eula=true, port25591). Он включает все filter/entity/container assertions и сохраняет Pickup(priority−5,color16,locked,redstone) и Fill(priority5,color6,locked,ignore-redstone). Два процесса WRITE/READ запускаются командами из M10.5 с этим scenario. Старые fixtures не удаляются/не перезаписываются. Логи: `/workspace/reference/golems-controls-{core,final,verified,restart-write,restart-read}.log`. Клиентский экран компилируется, графическая проверка отдельно не подготовлена.

Первый controls READ обнаружил гонку test fixture: persisted forced flag не гарантирует завершение асинхронной загрузки entity к фиксированному tick60. Проверка регионального NBT после ошибки подтвердила точную сущность `thaumcraft:golem`/UUID в entities/r.0.0.mca chunk0,0. Пробник инициирует getChunk только в своей тестовой среде и ждёт areEntitiesLoaded + isPositionEntityTicking до600 ticks; после готовности выполняет исходные assertions один раз. Повтор использует тот же мир без его перезаписи. Production entity/seal код ради этого не менялся.

Итог M10.6: Core PASS, полный build+26/26 GameTests PASS (8.299s/44s); cold WRITE PASS (1m8s), финальный READ PASS (52s, `golems-controls-restart-read-ready.log`). Тот же мир сохранён, все entity/cargo/filter/control assertions прошли; дубликатов и старых work leases нет. После исправления только test probe повторно выполнены build и реальный READ; production code после26/26 не менялся. Итоговый JAR CRC PASS, SHA256 `4c961fa3e0bddb549659f12ac4ee62cc4d66655f5c9af925b7ace3483e1be692`. Клиент, live chunk unloading, внешние inventory mods и crash-atomic transfer не проверялись.

## M10.7 — рецепты, 19.10.2026

Core runner Java21 PASS (task/filter/recipe948 assertions, construction34808). Полный NeoForge build и **29/29 required GameTests PASS**,6.071s набора/40s полный запуск. Новые tests: actual menu preview/research/Aer/vis, ПКМ batch3, cursor63 rollback, Shift craft без replay; реальное растворение10Desiderium+10Aversio crystals и две конвертации с точным blank/water/essentia; wrong catalyst/null player/cold/rejected output rollback. Pre-existing26 scenarios также PASS. Первый неверный тест заменял Aer через slot, который правильно отклонял Ignis; исправлен fixture, добавлена проверка post-insertion components mutation.

Логи вне Git: `/workspace/reference/golems-recipes-{core,final,verified,complete}.log`. Final JAR CRC PASS/SHA256 `58ab307537568653113f35a05d9116e39282368541f785a9318dedf89e05c342`. Новых persistent форматов нет: cold restart в этом recipe-срезе не повторялся; прошлые persistence scenarios остаются исторически проверенными. Графический клиент, recipe book/Thaumonomicon отображение и полный путь открытия MINDCLOCKWORK/CONTROLSEALS/SEALCOLLECT/SEALSTORE обычной игрой не проверены. Research gates проверены с существующей knowledge attachment, новые исследования не выдаются автоматически.

## M10.8 — Bell, 19.10.2026

Java21 Core runner PASS: construction34808, task/filter/recipe/Bell954 assertions. Полный build и **33/33 required GameTests PASS** (`golems-bell-verified.log`,8.062s/47s). Новые4 tests: vanilla crafting menu оплачивает quartz4+stick1→Bell1, mirrored recipe/wrong gem/maxStack1; Bell создаёт реальное menu выбранной грани (FakePlayer GUI transport захватывается тестом), owner/refund/rejected-spawn/stale-menu/remote guards; GOLEMDIRECT+owner и освобождение реального Pickup lease другому worker без перемещения груза; walking к владельцу, запрет задач в follow, stay→новый home→pickup. Observer проверяет отсутствие teleport на заведомо свободном пути; положительный failed-path teleport case отдельно не выделен.

Первый test run ошибочно ставил боковую печать на floorY0 вне области наблюдения предметов и не задавал физически стоящему голему onGround до первого AI tick; source teleport fallback при незапущенном path законен. Fixtures исправлены без изменения production, оба случая в финальном run PASS. Исходный bell recipe pattern подтверждён raw constant-pool строками: javap скрывал завершающие пробелы `S  `.

Cold Bell fixture: `-PgolemRestartScenario=bell`, отдельный `build/golem-bell-restart` (eula=true,online-mode=false,port25592), прежние fixtures не изменялись. Включает controls/filter/entity assertions предыдущих сценариев и оригинальный follow bit2. Команды WRITE/READ те же, что M10.6, с scenario=bell. Логи вне Git: `/workspace/reference/golems-bell-{core,final,verified,restart-write,restart-read}.log`. JAR CRC PASS/SHA256 `4834e642d2b0304adbde1df53574b23d19cc79c6cad1eae8d51036264dae95ba`.

Ограничения: клиент/packet transport визуально не проверены; GOLEMLOGISTICS GUI, old seal ray marcher/overlay, dyes, flying/combat owner AI и исходные sounds/art остаются открыты. Offline/dimension-switch и positive teleport edge scenarios не выдаются за отдельные PASS. Полная survival-проходимость GOLEMDIRECT не проверена; research автоматически не выдаётся. M10 целиком не закрывается.

Cold Bell итог: **WRITE assertions PASS (1m22s), READ assertions PASS (40s)**, два процесса dedicated server. Восстановлены один UUID, точные props/owner/home/XP/color/health/cargo37, обе печати/фильтры/controls и follow bit2; без дубликата груза и stale task replay. Production и fixture source после финального33/33 не менялись.

## M10.9 — исходное окрашивание, 19.10.2026

Java21 полный Core runner PASS (`golems-dyes-core.log`), construction34808 и task/filter/recipe/Bell/dye1041 assertions. Команда `gradle --offline --init-script tools/golems.gradle build -x test runGolemGameTest`: **full build и38/38 required GameTests PASS**,42s; лог `golems-dyes-build.log` вне Git. Пять новых tests проверяют реальные standard dye tags всех16 цветов, обе руки, расход1 без research, creative и прежний цвет; owner/range/spectator/quarantined design/не-краситель, прежний sneak reclaim с точным cargo refund; прекращение настоящего Pickup goal и передачу lease другому красному работнику; изменение цвета между Fill claim/completion и невозможность replay после repaint; автономный colored Pickup→path→Fill с более приоритетным неподходящим синим контейнером. Каждый tick последнего теста сохраняет сумму всех7 emerald, итоговый stack содержит прежний custom name.

Новый NBT roundtrip сохраняет оплаченный black16, FLAGS follow, точный cargo/components без нового красителя; прежний Bell cold-restart уже проверил тот же формат COLOR/color на диске с color6. Новых persisted полей нет: холодный dedicated restart в M10.9 повторно не запускался, историческое доказательство не выдаётся за новый прогон. Новые тесты не меняют registry/tag содержимое, не подменяют transfer/goal policy. Модовый custom multi-tag предмет отдельно не испытывался; порядок неоднозначных tag matches документирован в дизайне.

JAR ZIP CRC PASS / SHA256 `23909ddd1161737bc6a1ad9720521389617ec10563998aae66c5459e8adae503`. Authlib publickeys network warning не помешал offline GameTestServer; все38 required scenarios фактически завершились успешно. Клиент, цветовая лента/исходный renderer/zap, live unload и аварийная атомарность transfer не проверены. M10/общая оценка59/100 не закрываются.

## M10.10 — область Pickup, 19.10.2026

Java21 полный Core runner PASS (`golems-area-core.log`): construction34808, task/filter/recipe/Bell/dye/area1055 assertions. Полный `build -x test runGolemGameTest` с optional init script: **42/42 required GameTests и build PASS**,1m2s, первый запуск (`golems-area-build.log`). Четыре новых сценария: owner/range/stale nonce +actions90…95 +bounds1…8 +Fill rejection +NBT/default/invalid data; реальные шесть face producers/claims/pickup из одного outward блока; shrink cancellation/expand fresh assignment/remote no forced load; автономный Pickup/path/Fill после расширения через реальное меню, сохранность emerald7 каждый tick.

Клиентские controls используют свободную строку Pickup, скрыты для Fill; размеры экрана/слоты прежние. Новые DataSlots/buttons скомпилированы и server authority проверена, но графическое отображение/packet rendering/area overlay не запускались. Перечень тестов не подменяет эту клиентскую проверку.

JAR ZIP CRC PASS/SHA256 `514c79f4cc68162bd6e32dc0116ff90725caacde31e2a1286f3ac264f8df3998`. Isolated cold scenario `-PgolemRestartScenario=area` в `build/golem-area-restart` сохраняет asymmetric area2,3,4 и все прежние Bell+controls/filter/entity/cargo assertions; фактический cold итог добавляется после двух процессов. Старые test worlds не переиспользуются и не удаляются.

Фактический cold итог: **WRITE assertions PASS (54s), READ assertions PASS (45s)**. Логи вне Git: `golems-area-restart-write.log`, `golems-area-restart-read.log`; оба содержат соответствующий GOLEM_RESTART_*_ASSERTIONS_OK и BUILD SUCCESSFUL. Чтение выполнено вторым dedicated server процессом после штатного save/shutdown. Проверены asymmetric2,3,4 и все6 координат восстановленной AABB, прежние один entity UUID/owner/props/home/XP/color/health/cargo37/components, обе печати/filters/int cap70000/controls, barrel11gold и follow FLAGS; без копий груза или старых reservations. Production/fixture source после42/42 не менялся; полная сборка повторно не запускалась без причины.

## M10.11 — Empty, 19.10.2026

Java21 полный Core runner PASS (`golems-empty-core.log`), construction34808 / task/filter/recipe/Bell/dye/area/Empty1065 assertions. Полный `build -x test runGolemGameTest`: **51/51 required tests и build PASS**,50s (`golems-empty-verified.log`). Девять новых сценариев проверяют реальный Vacuos/crucible/research/rollback, placement через ItemHandler и exact typed Bell refund, общие menu controls без area/Fill cap, vanilla furnace exposure/extraction и strict components, два cached snapshots/один stock, actual partial и unexpected type retention, recovery NBT/возврат в руки после Fill, rejected reclaim retry, смерть с обоими stacks, controls/color/follow/container removal/stale face, общую priority очередь Fill/Empty и двух автономных workers с source70→destination70. Последний сценарий проверяет сумму source+руки+recovery+destination каждый tick, точные components и отсутствие ground duplicate.

Первый server run (`golems-empty-build.log`) выявил2 ошибочных ожидания новых fixtures: моментальное создание нового Fill task после configure (producer делает это на следующем interval20) и запрет top extraction raw-input (vanilla AbstractFurnaceBlockEntity это разрешает; bottom fuel extraction, кроме buckets, запрещён). Первый failed priority test оставлял unlocked Fill priority5 рядом с последующими fixtures, вызвав2 дополнительных старых Fill failures. Тест ждёт очередного producer tick, проверяет корректную vanilla sided exposure и изолирует новые задачи owner-lock. Production между этими runs не менялся; повтор включает все прежние tests,51/51 PASS. Проверки не ослаблялись под неверный контракт печи.

JAR ZIP CRC PASS/SHA256 `72ce794162fa18d54834d571c9f74692c1e5c3c9a4e5c7a029ef2e18cc5c58d8`. EN/RU JSON валиден, по3 новых ключа на язык присутствуют в прошедшем build/JAR. Core/recipe/typed enum и runtime adapter фиксируются атомарно: новый EMPTY не должен промежуточно использовать прежний ternary Fill product. Общие ArcaneWorkbenchMenu/CrucibleBlockEntity/registries/Gradle/CI/Core runner/research data не меняются; локализации выделены отдельно.

Новый opt-in scenario=empty использует отдельный `build/golem-empty-restart`/port25594. Он наследует area/Bell/filter/control/entity assertions и добавляет Empty type/owner/settings/source23emerald и actual recovery3diamond рядом с нормальным cargo37iron. Итог WRITE/READ фиксируется после реальных двух процессов. Старые worlds не изменяются. Клиент/исходная модель/advanced props, live chunk unload и аварийная атомарность не проверены; default basic Empty не объявляется полным advanced Empty/M10.

Cold Empty: **WRITE assertions PASS (1m16s), READ assertions PASS**, два настоящих dedicated server процесса на одном новом мире. Логи `golems-empty-restart-write.log` и `golems-empty-restart-read.log` содержат соответствующие GOLEM_RESTART_*_ASSERTIONS_OK, READ завершён с exit0. Помимо прежнего набора восстановлены third seal EMPTY/owner/priority1/color6/locked/redstonefalse, source23emerald и transferRecovery3diamond рядом с точным cargo37iron/components. Ни entity, ни руки, ни recovery не получили дубликата; reservations не воспроизводятся. Production и test source после51/51 не менялись. Отмена death drops внешним модом отдельно не проверена; проверены обычная смерть и rejected-spawn при owner reclaim.

## M10.12 — pleave, 19.10.2026

Java21 полный Core runner PASS (`golems-leave-core.log`), construction34808/task1075 assertions. Full `build -x test runGolemGameTest`: **54/54 required GameTests и build PASS с первого запуска**,1m15s (`golems-leave-build.log`). Три новых scenarios: source actions35/65/owner/range/typed guards/stale instance, DataSlot/NBT/missingflag=false и сохранность остальных controls/filter; два reserved snapshots/stock7→6+1, невозможность взять последний по stale lease и выключение→новая task→последний1; полный автономный two-worker multi-slot цикл source70→destination69+source1, сохранность всех70 каждый tick и точные components последнего предмета. Все прежние Empty/Pickup/Fill/Bell/area scenarios также PASS.

JAR ZIP CRC PASS/SHA256 `73d2798d6061654371fd559865424d75c7febdeb3f08bcf27a9654f717db83c6`. Новый optional `scenario=leave` использует отдельный `build/golem-leave-restart`/port25595, не меняет старые worlds. Проверяет stored pleave плюс весь Empty+recovery/area/Bell/filter/control/entity набор. WRITE/READ итог фиксируется после двух реальных процессов. Новые2 EN/RU labels уже присутствовали в успешном build; клиентский renderer/menu layout отдельно не запускался. Остальные advanced props не объявляются готовыми.

Фактический cold pleave: **WRITE assertions PASS (1m5s), READ assertions PASS (51s)**, оба BUILD SUCCESSFUL; логи `golems-leave-restart-write.log` и `golems-leave-restart-read.log`. Два реальных dedicated server процесса сохранили/прочли один и тот же новый мир: pleave=true, Empty type/controls/source23emerald, recovery3diamond, cargo37iron/components, прежние Pick/Fill/filter/control/area/FLAGS и один UUID. No ground duplicate/stale reservations. Production/test source после54/54 не менялся; полный build без причины не повторялся.

## M10.13 — pnbt, 19.10.2026

Java21 полный Core runner PASS (`golems-nbt-core.log`): construction34808, task/filter/recipe1091 assertions. Полный `build -x test runGolemGameTest`: **60/60 required GameTests и build PASS с первого запуска**,1m23s (`golems-nbt-build.log`). Шесть новых сценариев: pnbt31/61 для всех3 types, owner/range/nonce/default strict/ghost clear-copy/mode-cap preservation/NBT; разные name/enchantments при одинаковом damage и отказ другому item/damage; Fill cap12 с разными именами и двумя reserved callbacks (9A+3B, остатки7B/10C); отмена Empty и Pickup leases при смене pnbt без ghost substitution; полный двух-worker маршрут разных повреждённых инструментов A/B/C с зачарованиями, loose cap2 и pleave1. Каждый tick сохраняет все3 actual tools в source/hands/recovery/destination; итог — A и enchanted B доставлены, exact C остался, ground copies отсутствуют.

JAR ZIP CRC PASS/SHA256 `09896b6d033f80be7d71149694ab3d4f883e5182b49e1dfe6f05080fe856f13e`. По2 EN/RU keys на язык валидны и присутствовали в прошедшем build/JAR; новый menu layout только скомпилирован, графический клиент не запускался. Физическое stack merging остаётся strict; relaxed filter не стирает actual data. Source metadata wildcard/pmeta=false/useOre/useMod/relaxedNBT не объявляются готовыми.

Новый isolated `-PgolemRestartScenario=nbt` в `build/golem-nbt-restart`/port25596 наследует все прежние assertions. Добавляет false pnbt с named ghost и отличающимся plain source23emerald, проверяя именно применение loose policy после диска вместе с pleave/recovery/cargo/controls. WRITE/READ итог фиксируется после фактического завершения обоих процессов; прежние worlds не удаляются и не переиспользуются.

Фактический cold pnbt: **WRITE assertions PASS (1m18s), READ assertions PASS (52s)**, оба BUILD SUCCESSFUL. Логи `golems-nbt-restart-write.log` / `golems-nbt-restart-read.log`. Второй dedicated процесс восстановил pnbt=false/whitelist/named ghost, который действительно принимает plain source23emerald, сохранив разные components ghost/source; также pleave/EMPTY/controls/recovery3diamond/cargo37iron/один UUID/прежние Pickup+Fill+area+FLAGS. Нет ground copies или stale reservations. Production и все compiled test sources после60/60 не менялись; сборка повторно не запускалась без причины. Клиент и crash/unload остаются отдельными непроведёнными проверками.

## M10.14 — paid Golem Press, 19.10.2026

Java21 полный `bash tools/run_core_parity.sh` PASS (`golems-press-core.log`): construction34856/task1091 assertions, все прежние Core suites. `compileJava` PASS. Первый полный build+66/66 required GameTests PASS (1m36s, `golems-press-build.log`). После улучшения payment adapter (vanilla authoritative containers, Core cost mapping) и дополнительного partial-supply assertion итоговый **`build -x test runGolemGameTest` PASS,66/66 required,1m8s** (`golems-press-verified.log`). Оба запуска используют реальный dedicated GameTest server, а не печать собственного PASS.

Шесть новых сценариев: отсутствие последнего компонента не списывает предыдущие; top исключён; adjacent суммируется по нескольким контейнерам, но partial adjacent+partial player запрещено; repeated busy start/viewer/action/distance/spectator/stale-instance/full/foreign output guards. Реальные server ticks снимают8 Machina из existing jar10→2 и выдают1 exact placer; capability output-only отдаёт1 предмет, настоящий useOn создаёт ровно1 живого16HP голема с AI. Exact Machina conservation проверяется на каждом тике. Дополнительные проверки: block-at-completion сохраняет ready и не списывает эссенцию повторно; смена выхода позволяет выдать уже оплаченный продукт; wrong aspect stalls; one-unit buffer/zero/top rejection; job/buffer NBT roundtrip и malformed rejection; удаление блока выдаёт ровно реально хранящийся output без второй незавершённой копии.

Новый isolated cold fixture `-PgolemRestartScenario=press`/`build/golem-press-restart`, port25597: два пресса (один mid-craft с buffered paid unit, другой ready blocked), банки и untouched second ingredient batches. WRITE/READ результат фиксируется ниже после реальных запусков; прежние worlds не меняются. Проверка графического клиента/нового UI не проводилась. Survival-formation/recipes/полный editor/art остаются открыты; compile и server tests не означают закрытие этих пунктов.

Фактический cold Press: **WRITE assertions PASS (1m14s), READ assertions PASS (52s)**, оба dedicated процесса BUILD SUCCESSFUL, explicit `GOLEM_PRESS_RESTART_WRITE_ASSERTIONS_OK` / `GOLEM_PRESS_RESTART_READ_ASSERTIONS_OK` (`golems-press-restart-write.log`, `golems-press-restart-read.log`). Первый процесс оплатил оба пресса реальными adjacent ingredients; сохранил running remaining5/buffertrue/source6 и ready remaining0/source2, каждый с blocked named output2gold и нетронутой второй партией3/2/1. После диска running-пресс сам доработал буфер+4unit до ready/source2; второй не потребил лишней эссенции. После освобождения выходов каждый выдал ровно1 exact props/xp golem; ещё10ticks не повторили output/debit. Named output components, оба input batches и отсутствие ground copies проверены. Это проверка нормального cold shutdown/resume, не аварийной атомарности chunks/player data.

JAR ZIP CRC PASS; SHA256 `52869d977e4fa386da51f9667331ade07d5e7545dee3bbad75c07b15f1865be1`, `build/libs/thaumcraft-6.1.BETA26-port.stage9.jar`. Production/full-test source после итоговых66/66 не менялся. Клиент, live chunk unload, crash rollback, mod inventory adapters и исходная survival-formation не проверены/не реализованы; полный M10 и59/100 неизменны.

## M10.15 — исходные arcane-компоненты, 19.10.2026

Java21 полный Core runner PASS (`golems-components-core.log`): construction35004/task1091 assertions, все прежние suites;148 новых pattern/cost/stage/discount assertions. Первый full build+71 server tests (`golems-components-build.log`) выявил70 PASS/1 FAIL: после normal craft новый preview не обновлялся немедленно, instant shift-click видел пустой virtual slot. Actual payment первой операции был верным. Исправлено добавлением refreshResult после успешного component commit по существующему menu flow.

Итоговый **NeoForge full `build -x test runGolemGameTest` PASS,71/71 required tests,1m8s** (`golems-components-verified.log`).5 новых сценариев: mechanism BASEARTIFICE/10Vis/Ignis+Aqua/правильные plate metadata/normal+shift; mind только MINDCLOCKWORK@2/25Vis/Ignis+Ordo/именно comparator, mutated crystal components; stale preview при смене payable recipe/viewer/full inventory; finite discount/range/spectator/extras и floor7Vis при25% без скидки на crystals; полный оплаченный цикл brass8+iron6+sticks3+panes3+comparator1+greatwood3 →3 mechanisms →mind+2mechanisms →Press8Machina→1 exact golem product. Учтены55Vis/Ignis4/Aqua3/Ordo1, сырьевые stacks не копируются между стадиями; после оплаты пресса player inventory действительно пуст. Все66 прежних native scenarios, включая Press, PASS.

JAR CRC PASS/SHA256 `174fa9579b45d322a59cec132d70b883a3f68b7654efe7fc35e93e45aab3b1a5`. Новых persisted fields нет: используются существующие15-slot workbench/knowledge/aura; новый cold server прогон по этой причине не повторялся. Последний Press WRITE/READ остаётся M10.14. Графический клиент, исследовательское прохождение из пустого мира, source formation и live chunk unload не проверялись; M10/59/100 не закрываются. Общий menu hook выделен отдельно, без изменения BE/registries/build.gradle/CI/research data/runner.

## M10.16 — каменный стол и финальная проверка компонентов, 19.10.2026

Первый table run: full build+73/73 required server tests PASS (2m, `golems-stone-table-build.log`). После отдельного малого fix client prediction (строгая authority остаётся на ServerPlayer, локальный client следует обычному menu prediction) итоговое точное дерево: **full `build -x test runGolemGameTest` PASS,73/73 required,1m10s** (`golems-components-table-final.log`). Два новых table tests используют настоящий CraftingMenu:3 smooth stone slabs+2 stone, отказ modern rough slab/cobble/andesite, оба вертикальных3×2 offsets, normal+shift дают2 tables за6 slabs+4 stone без остатков. Другой сценарий применяет реальный BlockItem:2 initial items→1 placed+1 held, top-only side support/full collision/nonopaque/no BE, harvest без required-tool tier и pickaxe speed tag; после world removal ровно1 drop+1 held. Все71 прежних native cases PASS, включая component stale preview и полную оплачиваемую цепочку до голема.

Java21 Core35004/1091 и все suites PASS в M10.15; Core source после этого не менялся, runner без причины повторно не запускался. JAR ZIP CRC/content PASS (recipe/loot/mining tag реально включены), финальный SHA256 `856c58b4c242d089cbe8d5b2f13a8092ba7f123fd039dbdee05767ddf6c3450a`. Дополнительный cold reboot не запускался: новый стол — ordinary blockstate без BE/новых NBT fields, component crafts используют прежний workbench inventory/aura/knowledge; последний Press WRITE/READ относится к M10.14 и соответствующему SHA, а не новому клиентскому доказательству. Графический клиент/UI/prediction/временная stone модель не проверялись в игре; native/server и compile не подменяют эту проверку. Formation/полная survival progression/аварийная атомарность остаются открыты, M10/59/100 не закрываются.

## M10.17 — formation, linked teardown и recovery, 19.10.2026

Java21 полный Core runner PASS (`golems-formation-core.log`): construction35048/task1091 assertions,44 новых blueprint/rotation/delay/invalid-input checks и все прежние suites. Первый compile:3 ошибки одного неверного package PistonBaseBlock, исправлено на block.piston. Первый full80:79 PASS/1 FAIL — positive layout переиспользовал поршень после sticky→extended negative fixture; native PistonBaseBlock.onPlace/checkIfExtend при смене block type и EXTENDED=true ставит deferred retraction event. Positive layout перенесён в независимую исходную структуру; full80/80 PASS (1m24s, `golems-formation-verified.log`). После loaded halo2/recovery pause и отдельного8-го сценария full81/81 PASS (1m20s, `golems-formation-final.log`).

Итоговое точное runtime дерево после reservation guard для simple cauldron dispatch: **полный NeoForge build +81/81 required GameTests PASS (1m16s, `golems-formation-guarded.log`)**. Новый guard протестирован повторной активацией и по controller, и по reserved cauldron с уже известным UNLOCKALCHEMY@1: dust не списывается повторно.8 новых сценариев:4 rotations/50tick delay/пять actual target states; MINDCLOCKWORK без @2/wrong sticky-extended piston/lava rejection/water+damaged mapping/facing; altered snapshot all-or-none abort; реальный canceled EntityMultiPlaceEvent/точный rollback всех5; все5 путей разборки дают по1 broken constituent и ровно7 actual output items каждый, остальные blocks восстановлены; SavedData roundtrip+rebuild links+реальный proxy menu+range; обычный simple trigger precedence; persisted RESTORING fixture запрещает расход8 Machina, затем periodic restore. Последний сценарий инъецирует сохранённую recovery phase и не называется реальным live chunk unload. Ранее существующий component chain теперь выполняет настоящую dust formation, затем оплачивает actual crafted2 mechanisms+mind+wood и8 Machina, выдаёт1 golem. Все73 прежних Native scenarios PASS.

JAR ZIP CRC/content PASS, SHA256 `58c445df72d3b946896f1691882d2227e11033e1a7581801bbaf215763173261`. Новый isolated cold formation scenario: build/golem-formation-restart, port25598, два отдельных dedicated процесса, fixture-only force ticket. WRITE/READ assertions будут зафиксированы ниже после завершения; successful Gradle exit без explicit assertion marker не считается PASS. Общая Salis правка —3 additions и fallback вместо старого PASS, без переписывания других механизмов; свой optional init script и старый probe только маршрутизируют новый scenario. Клиент/original Press renderer, live unload/crash consistency и полная research progression из пустого survival мира остаются непроверенными, M10/59/100 не закрываются.

Фактический formation cold: **WRITE assertions PASS (1m28s), READ assertions PASS (56s)**, оба dedicated процесса BUILD SUCCESSFUL, explicit `GOLEM_FORMATION_RESTART_WRITE_ASSERTIONS_OK`/`GOLEM_FORMATION_RESTART_READ_ASSERTIONS_OK` (`golems-formation-restart-write.log`, `golems-formation-restart-read.log`). Первый процесс реально заплатил Salis1, дождался50ticks, получил связанный5-part controller, заплатил actual wood3/mechanisms2/mind1, сохранил job remaining8/Machina8/blocked named gold2. Второй процесс восстановил все5 memberships из SavedData и actual blockstates; без owner присутствия job дошла до ready, банка8→0. Named blocker извлечён без потери components, освобождённый output выдал ровно1 actual golem item. Разрушение persisted anvil placeholder вернуло controller в UP piston и остальные несломанные составляющие; ровно2 ground entities — anvil1 и actual golem1, без дополнительных refunds/duplicates. Production и compiled Native tests после финальных81/81 не менялись; repeated cold run/client/live unload/crash consistency не объявляются проверенными. Архивная SHA256 JAR выше остаётся финальной для этого среза.


## M10.18 — Core lifecycle заявок снабжения, 19.10.2026

База `fe57baa`, fresh main `a4280834`, CI #385 verify-connection SUCCESS (только проверка подключения, не full NeoForge). Прочитан официальный BETA26 bytecode ProvisionRequest/GolemHelper/Task/SealProvide/SealStock/hasRoomFor; файлы исследования только `/workspace/reference/javap`.

Команда `JAVA_HOME=/workspace/toolchain/jdk-21.0.12.1+1 PATH=/workspace/toolchain/jdk-21.0.12.1+1/bin:$PATH bash tools/run_core_parity.sh` — **PASS**, construction35048/task2182. Лог `/workspace/reference/golems-provision-core.log`.1091 новых Core assertions проверяют три получателя/side/count/components/UI identity, dedup без refresh, initial/link/unlink deadlines, timeout equality, stale callbacks, terminal/closed/foreign-thread requests, bound1000 без eviction, исходные task data/priority/lifespan, Stock budget и provider radius/psing/pleave. Общий runner не менялся, фиктивные маркеры не добавлялись.

Core request lifecycle не означает готовые Provide/Stock items, UI, recipes или actual golem transport. Серверный API и фактический native прогон выделяются следующим commit, результаты ниже. Запросы ephemeral; новых NBT fields в этом срезе нет.


### Серверный API M10.18 и итоговое дерево

`/workspace/reference/gradle-golems --offline --init-script tools/golems.gradle build -x test runGolemGameTest` — **BUILD SUCCESSFUL in1m10s, All84 required tests passed** с первого запуска, `/workspace/reference/golems-provision-build.log`. Запущен настоящий NeoForge21.1.250 GameTestServer под Java21;3 новых required scenarios:

1. Actual barrel stock5 named iron +40 plain iron: desired12 даёт7; request template/input/output defensive copies, duplicate reuse. Затем заняты все слоты/63named iron→room1 даёт1;64→no request, desired0→no request. Ни один вызов не меняет реальные stacks.
2. Actual furnace UP input3raw iron→demand7; DOWN не принимает raw iron→no request. Содержимое остаётся прежним. Remote chunk25M остаётся незагруженным до/после producer.
3. Native world board: caller stack mutation не меняет saved request identity, UI discriminator разделяет requests, pickup→entity delivery data1/UUID сохраняют взаимные ссылки; поздний pickup unlink не сбрасывает delivery, invalidation/sweep отменяет связанную работу. Это API/task lifecycle integration, не маршрут голема с фактическим переносом.

Все прежние81 tests прошли, включая Salis formation/rollback/teardown/component chain/navigation/ItemHandlers/menu/persisted-state fixtures. JAR CRC/content PASS: `build/libs/thaumcraft-6.1.BETA26-port.stage9.jar`, SHA256 `3e3b91696b76ef02bcfd6b91d95a34094be791b198bcfefd02a19bf6d746f4d4`.

Новый regular-server cold WRITE/READ и Minecraft client не запускались; новых NBT fields/сохраняемых заявок нет. Исходный ephemeral lifecycle не доказывает восстановление будущего Provide transport. Старые cold PASS не переименованы в M10.18. Live chunk unload/crash atomicity/full progression/Provide-Stock gameplay остаются непроверенными. Новых общих integration files в этом срезе нет, Core runner неизменен. M10 и59/100 не закрываются.


## M10.19 — Core recipes и mixed task selection, 19.10.2026

Fresh main a4280834, base399a306, CI #386 verify-connection SUCCESS. Java21 полный `bash tools/run_core_parity.sh` **PASS**, construction35048/task2202,20 новых assertions: точные catalysts/оба aspects/двойной research requisites/пограничные отрицательные оплаты и общий priority/distance порядок block+entity. Лог `/workspace/reference/golems-logistics-core-final.log`; runner не менялся. Full native/cold относятся к следующим commits этого среза, не подменяются Core результатом. Исследованы ConfigRecipes/SealEmptyAdvanced и предварительно SealHarvest из официального BETA26 JAR, dumps только внеGit.


### M10.19 — полный native маршрут, итог93

Core35048/2202 PASS. Первый `golems-logistics-build.log`:89/90 PASS, conservation fixture ошибочно помещала130 в64+66; Container.setItem clamp урезал второй stack. Фикстура исправлена на64+64+2. `golems-logistics-final.log`:92/92 PASS,1m16s. После добавления onItemUseFirst и фактического dispatch/refund test `golems-logistics-guarded.log`:93/93 PASS,1m15s. Review дополнительно сохранил pending raw API task при canceled drop; последний **`golems-logistics-verified.log`: full build+93/93 PASS,1m10s** с реальной проверкой retry того же lease без повторного source debit.

9 новых required native scenarios: автономные2workers source130→Stock70+source60/per-tick conservation; fresh recipient9/10 после paid pickup10→insert1/held9/stale callbacks; source2/psing/pleave→pickup1/retain1; replacement Stock отменяет old instance→resumed delivery5 из held10 без source debit;9-slot menu/cursor37→ghost1/owner guard/per-slot11/NBT/typed persistence/no task NBT; entity UUID delivery/canceled spawn retry/ровно1actual drop; actual upgraded Empty→Provide и Fill→Stock с обоими аспектами/research/rollback; recipient owner/color/provider whitelist перед списанием; onItemUseFirst actual typed placement/Bell nine-slot menu/exact provider refund. Все прежние84 scenarios тоже PASS.

JAR CRC/content PASS, SHA256 `10f3df5a13c3fcc96285d92ecec24caf980fb3dea8a1e33e9da72dc030549ae1`. EN/RU11 additions на язык уже включены в этот build; native Java после него не меняется. Клиентский rendering пока не проверен. Исходные модели временные. Доказательства cold WRITE/READ ниже отдельным commit; live chunk unload/аварийное сохранение/full research progression не подменяются этим набором. Seal-recipient provisioning (Harvest), advanced filter modes и runtime SMART/DEFT parts ещё не закрыты; весь M10/59/100 остаётся открытым.


### M10.19 — cold actual paid cargo resume

Изолированный `logistics`, `build/golem-logistics-restart`, port25599. Commands через существующий optional tools/golems.gradle: `-PgolemRestartScenario=logistics -PgolemRestartPhase=write runGolemRestart`, затем новый процесс с phase=read. **WRITE PASS1m13s, READ PASS1m6s**, explicit `GOLEM_LOGISTICS_RESTART_WRITE_ASSERTIONS_OK` и `...READ_ASSERTIONS_OK`. Логи `/workspace/reference/golems-logistics-restart-write.log`/`...-read.log`.

WRITE использует настоящие provider/Stock SavedData, реальные8 named iron в source и actual pickup через lease; перед disk shutdown source0/recipient0/hands8, один fixed UUID/owner, ninth filter desired12. Task/request NBT не сериализуется. READ на новом процессе проверяет сохранённые block inventories/UUID/owner/filter8/name/count12 и actual hands8, включает AI и ожидает автономную доставку8; source остаётся0, после40ticks unwritten deficit4 не материализуется, ровно один golem/no ItemEntity copies. Fixture force-loads только собственный chunk0; production ticket logic не добавлена. Это cold process restart, не live chunk unload/crash test. Последний raw API rejected-drop retry guard введён после cold; Stock path и persisted fields он не меняет, final93 включает весь итоговый runtime.


## M10.20 — Core component catalog, 19.10.2026

Java21 полный `bash tools/run_core_parity.sh`: **PASS**, construction39872/task2202, лог `/workspace/reference/golems-construction-catalog-core.log`.4824 новых assertions проверяют golden source counts всех2400 combinations, уникальные merged keys, конкретные source SRG mappings/metadata, runtime180-family gate, research sets и exact fire/cactus/blast math. Все прежние Core suites прошли, runner неизменен. Cost data получены из официального BETA26 GolemProperties/JAR и официального MCP stable39-1.12; результаты исследования только внеGit. Это Core parity, не blanket доказательство всех native parts. Дальше native worker/editor/cold evidence отдельными commits.
