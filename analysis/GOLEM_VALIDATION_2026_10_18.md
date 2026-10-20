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


### M10.20 — native family/Press editor, итог99

Compile PASS19s. Первый full99:97 PASS/2 FAIL (`golems-worker-editor-build.log`,1m9s). Старая restoration fixture не имела MATSTUDWOOD после ввода source research gate; исправлена фикстура. Реальный BLASTPROOF test выявил, что NeoForge21.1 LivingEntity.actuallyHurt читает DamageContainer.getNewDamage вместо переданного float. Прочитан актуальный NeoForge bytecode, адаптер меняет текущий protected damage context перед super.actuallyHurt; source min(maxHP/2,damage*0.3) остаётся до armor. Это production исправление, не ослабление assertions. `golems-worker-editor-verified.log`:99/99 PASS1m9s.

После завершения ingredient preview и ghost-slot exclusion: **full `build -x test runGolemGameTest` PASS99/99,1m25s**, `/workspace/reference/golems-worker-editor-final.log`.6 новых required tests покрывают actual configured8-component/16Machina paid Press→consumed placer→one SMART-DEFT12HP entity/rank1→NBT; server knowledge/viewer/iron-vs-brass meta/busy config/recipe ghost no extraction; fire/cactus/blast actual health; six material attributes/VOID repair40ticks; HAULER64+6 cargo and ninth advanced Empty filter with SMART gate; unsupported valid config preservedNoAI. Прежние93 scenarios также PASS.

JAR CRC/content PASS SHA256 `05bb81d053d3c8a27f6ef7572ce3b861ac678e9be3fa9c64c0e68f2be02cc2ab`.27 EN/RU keys на язык и3 temporary component models включены в этот build, но client rendering не запускался. Это180 working ground configurations и source Core costs остальных, не blanket flight/combat/farming/survival progression parity. Cold selected/paid20Machina scenario/evidence отдельным следующим commit. Нет общих registry/CI/build.gradle/research data/runner изменений.


### M10.20 — construction cold WRITE/READ

Изолированный `construction`, `build/golem-construction-restart`, port25600. Existing optional init script: `-PgolemRestartScenario=construction -PgolemRestartPhase=write runGolemRestart`, затем отдельный process phase=read. **WRITE PASS1m9s, READ PASS57s**; explicit `GOLEM_CONSTRUCTION_RESTART_WRITE_ASSERTIONS_OK`/`...READ_ASSERTIONS_OK`, логи `/workspace/reference/golems-construction-restart-write.log`/`...-read.log`.

Обе машины выбраны серверными researched selectors как SMART/FINE/HAULER, golden source cost20 и actual components10. WRITE first сохраняет remaining17/buffertrue/source18 из22 после четырёх actual debit units; second уже ready-blocked/source2. READ ждёт настоящие160ticks и проверяет обе ready/exact selected props/maximum20/source2; named blocker2 и untouched second ingredient batch пережили disk. После освобождения output каждая выдаёт ровно1 configured placer, повторные ticks не списывают/не выдают ещё. Ground copies отсутствуют. Production после final99 не менялся; процессное сохранение/восстановление проверено на точной структуре новых полей. Fixture-only forced chunk0 не называется production tickets или live chunk unload.

### 2026-10-19 — M10.21 Harvest/SealTarget/native farming

Java21 full Core PASS, construction39872/task2265 (+63 farming assertions), `/workspace/reference/golems-farm-core.log`. Production compile PASS39s. Первый full build не дошёл до сервера: в новом fixture ошибочно вызван несуществующий helper `reclaim`; исправлен на настоящий entity interact с crouching owner. Это ошибка теста, не PASS.

Первый серверный прогон `/workspace/reference/golems-farm-build-2.log`:111 tests/110PASS, старый `sourcePnbtActionsPersistForAllThreeTypes...` перебирал весь расширяемый enum и потребовал pnbt от Harvest без item filter. Исправлена область этого старого basic-menu сценария на исходные PICKUP/FILL/EMPTY;9-slot logistics уже имеют собственные required tests. Все12 новых farming tests в этом прогоне PASS.

**Финальный full NeoForge21.1.250 `build -x test runGolemGameTest` PASS111/111,1m17s**, `/workspace/reference/golems-farm-verified.log`. Настоящий GameTestServer стартовал и сохранил/закрыл dimensions.12 новых scenarios: пять реальных vanilla cultures/loot/one paid seed/age0/lease exclusivity; canceled real BreakEvent preserves crop/no loot/no intent and retry; canceled actual EntityPlaceEvent refunds seed/preserves intent and retry; actual native AI navigation/harvest/replant; actual provider stock1→SealTarget request→worker→paid replant; owner menu/source toggles/inactive ghost/settings/area/pending NBT/no leases; wrong traits/changed color after reservation; multiple named actual refund variants/NBT/owner reclaim exactly once. Existing99 также PASS.

JAR CRC/content PASS SHA256 `255afd0980c1cb2c578728839964904d81a64c455c4e54d04d3989d47d92353c`. Временная wheat model и7EN/RU keys включены, но graphical client не запускался. No source artwork copied. На этом checkpoint начат отдельный farm cold WRITE/READ; его результат фиксируется отдельно. Cocoa/fruit/stacked/mod crops, actual source infusion acquisition, live unload и crash-safe external capability transaction не выдаются за проверенные.

### M10.21 — farm cold WRITE/READ

Isolated `farm`, `build/golem-farm-restart`, port25601, optional own `tools/golems.gradle`; `-PgolemRestartScenario=farm -PgolemRestartPhase=write runGolemRestart`, затем отдельный process phase=read. **WRITE PASS1m23s/READ PASS1m2s**, `/workspace/reference/golems-farm-restart-write.log`/`...-read.log`, explicit `GOLEM_FARM_RESTART_WRITE_ASSERTIONS_OK`/`...READ_ASSERTIONS_OK`.

WRITE выполняет настоящий harvest и штатный shutdown с AIR+одним pending intent, SMART/DEFT farmer UUID/carry2seeds, prep/ppro/area1 и вторым holder UUID сfull64cobble hand+actual named3seeds/4carrots recovery queue. READ доказывает эти точные данные с диска, включает native AI и ждёт actual paid replant→age0/carry1/empty intent, ещё40ticks без повторного списания; crouching-owner actual reclaim возвращает3namedseeds+4carrots, stale callback отвергнут, остаётся ровно1farmer. Forced fixture chunk0 не является production chunk ticket/live unload test. Main/other lanes unchanged; client/crash guarantees не проверялись.

### M10.22 — fruit/stacked и настоящая встроенная мотыга

Source: official BETA26 ModConfig/CropUtils/Harvest+MCP39 fields; Forge1.12.2-14.23.5.2860 item patches read-only внеGit. Сохранение source seed discovery принципиально: ItemBlock/ItemReed не IPlantable, melon/pumpkin seeds отображаются на stems, поэтому эти4 блока не порождают fake replant/requests.

**Java21 full Core PASS39872/2606**, `/workspace/reference/golems-fruit-core.log` (+341assertions). **Full NeoForge build+115/115 GameTests PASS с первого запуска1m21s**, `/workspace/reference/golems-fruit-build.log`; четыре новых real-world scenarios: fruit harvested/stems preserved/mature stems excluded/no seed debit; cane/cactus upper segments harvested/root segment refused/no pending seed request. Existing canceled placement test теперь реально превращает dirt→farmland built-in hoe, защита отменяет seed place, actualseed возвращается и повторная попытка списывает1; virtualtool не становится loot. Все прежние111 PASS. Старый M10.21 cold не повторялся: новых persistent полей/формата нет, только новые transient harvest families и reject невозможных pending entries. Client/live unload/crash guarantees не заявлены.

### M10.23 — Cocoa original attachment/soil и modern placement target

Read-only source ModConfig/CropUtils/SealHarvest: cocoa seed is brown dye→modern cocoa_beans; maturity2; original BlockDirectional.FACING, original below-is-farmland, placement pos.offset(face)/opposite face. **Java21 full Core PASS39872/2650** (+44), `/workspace/reference/golems-cocoa-core.log`.

Первый four-direction build119/119 PASS1m3s (`golems-cocoa-build.log`). После exact BlockPlaceContext guard/двух regression scenarios и backward pending defaults121/121 PASS1m36s (`golems-cocoa-verified.log`). Source review выявил, что farmland должен переживать reload как фактический snapshot даже для Cocoa над пашней, а не вычисляться по типу культуры; исправлен loader, real Cocoa-over-farmland NBT case проверяет сохранениеtrue. Добавлена actual workXP1+1 проверка.

**Финальный full NeoForge build121/121 PASS1m10s**, `/workspace/reference/golems-cocoa-final.log`.6 новых Cocoa scenarios: все4 горизонтальных facing сactual loot/paid one bean/age0; missing original log плюс второе соседнее log не сдвигает placement в чужую клетку и не списывает seed, восстановленный support позволяет same lease retry; actual original attachment/farmland NBT и отказ vertical malformed face. Existing M10.21 wheat intent без новых полей корректно обновляется вDOWN/farmlandtrue, leases отсутствуют. Все прежние115PASS; локальные fruit fixtures исключают случайное regrowth во время stem-only refusal check.

JAR CRC PASS SHA256 `a1191fc6a4b9e5b09f0014da8a03a1f8114091c81c2afeca28ba524041cb6141`. Client/renderer не запускался, no art copied. Отдельный cold Cocoa scenario запущен после этого final runtime, результаты отдельно, заранее не предполагаются.

### M10.23 — Cocoa cold WRITE/READ

Отдельный scenario `cocoa`, `build/golem-cocoa-restart`, port25602; расширен собственный farm probe с прежним backward farm route. **WRITE PASS1m17s, READ PASS59s**, `/workspace/reference/golems-cocoa-restart-write.log`/`...-read.log`, explicit `GOLEM_COCOA_RESTART_WRITE_ASSERTIONS_OK`/`...READ_ASSERTIONS_OK`. Прогон завершён на compiled runtime финального121/JARa1191fc6 до сборки следующего pcycle среза.

WRITE actual Cocoa harvest сохраняет AIR/pending original EAST attachment/actual farmlandtrue под Cocoa,2real beans, same farmer UUID, source controls и второй actual multi-refund holder. READ проверяет эти поля с диска, autonomous AI размещает age0 Cocoa на прежнем EAST log за1bean; ещё40ticks без повторного debit/intent, actual refund3namedseeds+4carrots reclaimed once, один worker остаётся. Source farmland snapshot не заменён типовым crop default. Fixture-only forcedchunk и offline dedicated server не выдаются за live unload/client/crash transaction evidence.

### M10.24 — Advanced Empty source cycle

Official BETA26 SealEmpty/SealEmptyAdvanced bytecode: pcycle index4 defaultfalse; nonempty whitelist singleton at abs(filterInc%count), blacklist/allfilters fallback; cursor advances on completion, notscan, and is transient. No skip unavailable selected item invented. **Java21 full Core39872/2678 PASS**,28new source positive/negative/overflow/precondition vectors, `/workspace/reference/golems-cycle-core.log`.

**Full NeoForge build125/125 required GameTests PASS с первого запуска2m10s**, `/workspace/reference/golems-cycle-build.log`.4new native scenarios: sparse0/8 ghosts choose iron3 despite earlier visible gold4, actual first cargo→real sink→nextgold4 conserves7; unavailable selected iron waits despite actual gold supply, then actual suppliediron1 unlocks step; blacklist retains allghosts and extracts onlycoal2; owner source34/64 toggle/cycle≠psing, flag preserved by ghosts/NBT actions, roundtrip/defaultfalse/stale-instance guard/provider index4 unchanged. All prior121PASS. Production after125run unchanged; optional cold fixture is compiled separately with build/runGolemRestart, then WRITE/READ evidence recorded separately. Client not launched; pmeta/pore/pmod/full advanced seal parity not claimed.

### M10.24 — cycle cold WRITE/READ и packaged artifact

Isolated `cycle`, `build/golem-cycle-restart`, port25603, source-compatible transient cursor. **WRITE build/run PASS1m19s, READ separate process PASS1m24s**, `/workspace/reference/golems-cycle-restart-write.log`/`...-read.log`, explicit `GOLEM_CYCLE_RESTART_WRITE_ASSERTIONS_OK`/`...READ_ASSERTIONS_OK`.

WRITE actual Advanced Empty source cursor0 забирает iron3 изslot1 при gold4 вslot0; fixture объявленно добавляет новыйiron2, shutdown сохраняет source6/sink0/hands3/UUID и sparse ghosts0/8+pcycletrue. READ доказывает это с диска, переносит actual persisted iron3 вреальныйsink, затем source reset cursor0 выбирает новыйiron2, сохраняя gold4. Все9 actual items учтены4+3+2; stale completion не повторяет extraction/advance, entity одна/no world copies. Никакого production supply из fixture не переносится вruntime. Source cursor действительно не сохраняется, policy сохраняется.

Последний packaged JAR пересобран `build -x test runGolemRestart` после добавления opt-in test probe; production совпадает с125/125 run, только новый disabled-by-default probe добавлен. ZIP CRC/class content PASS, SHA256 `c22e35e639d55f2f92cc9fca6bf4708673e1e0d105c39dfd74513b54dfc8b3e3`.2EN/RU keys на язык уже включены, последним commit выделяются только labels/journal. Main fresh-fetch всё ещёa4280834; CI main#232/fruit#392 verify-connectionSUCCESS — не build evidence. Graphical client/Xvfb unavailable, live chunk unload/crash fault injection не запускались; M06 survival acquisition/source art/special parts/extended matching не закрыты.

### M10.25 — metadata/ore/mod source matching

Java21 полный Core PASS39872/2724 (+46), `/workspace/reference/golems-match-core.log`. Official BETA26 InventoryUtils/ThaumcraftInvHelper/InvFilter/OreDictionaryEntries + Forge1.12 sources/MCP39. Native production compile PASS27s (`golems-match-compile.log`). Собственная таблица197 registration facts, reuse prior project alias/tag mappings; source dumps/binaries/art outsideGit.

Первый full native133run:131PASS/2new fixture failures (`golems-match-build.log`): mutable expected first/second stacks были переданы actual container по ссылке, actual extraction законно меняла expected count. Исправлено на independent fixture copies; gameplay comparisons/guards не ослаблялись. **Повторный full NeoForge build133/133 PASS1m18s**, `/workspace/reference/golems-match-verified.log`. Все прежние125PASS.

8новых реальных сценариев: source damage/NBT независимость и proven flattened/plate metadata; asymmetric ore canonical-right NBT и отсутствие arbitrary aggregate-tag equivalence; canonical custom ore snapshots/removal и pmod precedence;2worker preexisting cache leases реально доставляют другой metadata variant/другой item сactualname/components без превращения вghost; directional fresh pleave reserve1; owner source action/menu/flags/9th ghost/old NBT defaults; native AI Stock pmod считаетgold9, actual provider STRICT даётiron1, per-tick11items conserved. Typed options сохраняются через всеghost/cycle/mode edits. Client matching panel compiled, graphical run не заявлен. Sticky legacy wildcard shared-state defect изолирован осознанно, exact limitation in design.

Matching dedicated-server WRITE/READ начат отдельным scenario; исходная policy/source133runtime фиксируются до следующего advanced slice, результат заранее не предполагается.

### M10.25 — matching cold WRITE/READ

Isolated `matching`, `build/golem-matching-restart`, port25604; generalized own cycle probe, backward cycle route retained. **WRITE1m18s/READ50s PASS**, `/workspace/reference/golems-match-restart-write.log`/`...-read.log`, explicit `GOLEM_MATCHING_RESTART_WRITE_ASSERTIONS_OK`/`...READ_ASSERTIONS_OK`. Runtime compiled from133verified source before advanced Pickup/Fill edits; these later source edits do not affect that running process.

WRITE pmeta=false/pnbt=false/pore=true/pmod=true+pcycletrue, sparse iron/gold ghosts; actual source namedcarrot4+iron3→paid namedcarrot4; fixture declares new namedcoal2 batch. Disk saves source5/sink0/cargo4. READ checks exact options/sparse ghosts/UUID andactualcarrotname, deposits persisted4 into real sink and extracts actualnamedcoal2 using reset source cursor, leavingiron3. All9items conserved3+4+2, one worker/no world copies/no stale callback replay. Ghost iron never fabricates iron from carrot/coal. Production unchanged since133PASS; packaged JAR rebuilt with opt-in probe, CRC/sourceclasses checked. Graphical client/live unload/crash not claimed.

### 19.10.2026 M10.26 — Advanced Pickup/Fill

Java21 full Core PASS construction39872/task2737 (+13), `golems-advanced-core.log`. Production compile PASS21s, `golems-advanced-compile.log`. Full build+142/142 required GameTests PASS1m7s, `golems-advanced-final.log`: all133 prior +9 advanced. First142-run140PASS/2FAIL: fixture source action91 changedY while expectingX, failed fixture leaked a seal into neighboring test; corrected93 and finally cleanup. Second141PASS/1FAIL: random ground-item launch drift in new AI fixture; centered target/drop velocity0. Final142PASS: each-tick15actualitems; ninth Pickup iron11→ninth Fill7+hands4, gold4 untouched; first duplicate limit/zero unlimited, SMART gate, pexist retry/worlddrop source exception, HAULER actualslot binding under directional blacklist, typed save/menu, actual crucible debit and item place/refund. UI axes labels corrected, graphical client not run. Cold advanced fixture compiled after142 production baseline; results recorded separately only after explicit markers.

Advanced cold isolated regular dedicated server port25605/world `build/golem-advanced-restart`: **WRITE assertions PASS1m32s, READ assertions PASS2m7s**. Logs `golems-advanced-restart-{write,read}.log`, exact `GOLEM_ADVANCED_RESTART_{WRITE,READ}_ASSERTIONS_OK`. Actual dropped named iron5 picked into real NoAI worker, sink already held same named iron1; pending Fill reservation before shutdown. Second JVM verified same UUID/owner, typed Advanced Pickup area1/ninth ghost, Advanced Fill pexist/ninth limit7, exact cargo5+target1; enabling native AI autonomously delivered5, preserved6 each read tick, no replay/ground copies/extra worker through500ticks. Cold compiled baseline142 before subsequent Breaker source changes; this is normal shutdown persistence, not crash-atomicity/client/live-unload proof.

### 19.10.2026 M10.27 — Breaker / functional breaker arms

Java21 full Core39872/2758PASS (+21), `golems-breaker-core.log`. Первый compile выявил отличие API Tool.Rule (в1.21.1 minesAndDrops, неdrops); исправлен вызов actual installed NeoForge source. Первый151-run148PASS/3fixtureFAIL: два XP ожидания у non-SMART головы, nested onEachTick mutating GameTest scheduler; fixed SMART fixture/registered timer outside callback. Второй150PASS/1fixtureFAIL: supplied SMART mind не совпадал с selected ordinary Press head; selected head corrected, payment refusal был правильным. **Финальный full NeoForge build151/151 required PASS**, `golems-breaker-final.log`: all142 previous +9new. Exact obsidian500 work требует24 callbacks/253ticks; repeated50 same-tick calls не ускоряют. Actual diamond ore dropsdiamond1 без silk/fortune, actual carried namedcarrot3 untouched; source placeholder tool не награда. Owner break cancellation/noXP/noLoot/fresh retry, replacement block invalidates old work, bedrock excluded, strict legacy oak/spruce meta/noNBT, CLUMSY allowed onlyappropriate seals, real native navigation/XP, menu/NBT and actual paid researched arms3/SMART construction proved.

Runtime supports270 ground families, only basic/fine/breaker arms/walker/non-fighter addon. Full flight/combat/climb/roller не заявляются. Source Breaker acquisition — infusion1/SEALBREAK/Instrumentum10+Perditio10+Humanus10, blank+golden axe/pickaxe/shovel: generic M06 dependency remains; no invented recipe. Cold isolated Breaker write/read has separate opt-in probe/results below after actual completion. Client not run.

Breaker cold regular dedicated port25606/world `build/golem-breaker-restart`: **WRITE1m50s/READ1m27s explicit assertions PASS**, logs `golems-breaker-restart-{write,read}.log`. Actual arms3/non-SMART worker mined12 partial callbacks into obsidian500, source block/loot0 remained at shutdown. Second JVM restored UUID/owner/design/typedseal/area/whitelist/pmetafalse; old ephemeral work was not restored, no mining/loot before fresh253ticks, actual native AI mined one obsidian, no synthetic tool/extra entity/replay through540readticks. Non-SMART XP stayed0. WRITE compiled151 production baseline plus unused Core Lumber helper; READ deliberately `-x compileJava -x processResources` used those same compiled classes/resources while later Lumber sources were edited independently. No new untested Lumber gameplay is claimed by this proof; full M10/crash/client remain open.

### 19.10.2026 M10.28 — Lumber

**Java21 full Core39872/2778PASS**, `golems-lumber-core-final.log` (+20 assertions vs Breaker). **Full build158/158requiredPASS с первого запуска1m21s**, `golems-lumber-build.log`:151prior+7realLumber scenarios. Native AI conserved3real logs per tick and cut top→base with XP3, no seed/plant/carry invention; axis/bark legacy family preserved and spruce left untouched. Ordered greedy dx-/dy+/dz- selected source branch instead of global BFS maximum; repeated30same-tick callbacks did not add loot. Both SMART+BREAKER required despiteCLUMSY allowed. Real protected distant top noloot/noXP→fresh retry. Source menu area/type saves, no ghosts/replant/NBT controls. Actual Crucible39Herba rejected without water debit, actualmissing1supplied+Sensus20+researched actual Breaker1→one actual Lumber/water50; unknown research rejected. Shared block harvest adapter refactor retained all9Breaker tests. New typed cold Lumber scenario starts separately; no cold PASS yet.

Lumber cold isolated regular dedicated port25607/world `build/golem-lumber-restart`: **WRITE1m19s/READ1m12s assertions PASS**, logs `golems-lumber-restart-{write,read}.log`, explicit GOLEM_LUMBER_RESTART_{WRITE,READ}_ASSERTIONS_OK. First process paid actual top log1/XP1, origin+middle remained; saved exact SMART/BREAKER UUID/owner and typed area. Second process verified persisted actual drop1, restored worker/area and resumed nativeAI→remaining2 logs, each read tick actual drops+remainingblocks=3, final3loot/XP3/no tool/no extraentity/no replay through100readticks. WRITE compiled158 baseline; READ excluded compileJava/processResources to keep same artifact while subsequent AdvancedBreaker source was edited. First half on19.10, READ log on20.10; this is a genuine second process, not NBT-only fixture. Shared ordinary-shutdown persistence, not client/live unload/crash proof.

### 20.10.2026 M10.29 — Advanced Breaker

**Full Java21 Core39872/2793PASS**, `golems-advanced-breaker-core.log` (+15). **Full NeoForge build166/166requiredPASS с первого запуска1m23s**, `golems-advanced-breaker-build.log`:158prior+8native scenarios. Actual Silk stone needs3callbacks/22ticks (source7step); native navigation/ninthghost yields actual diamond ore1, no diamond/syntheticstick. Source whitelistALL/blacklistNONE/emptywhitelist semantics preserved; metadata-off old oak/spruce family still drops actual spruce and ignores ghostname. Non-SMART denied, CLUMSY allowed. Owner-protected silk glass canceled without XP/loot/enchantedplaceholder; fresh retry glass1. Nine-slot psilk/pmeta/area/NBT, no unrelated ore/mod/cycle toggles; actual ghost input4 preserved. Real crucible consumes Breaker1/Sensus10/Cognitio10/Instrumentum20/water50 only with both research keys, yields correct advanced item1. Cold isolated silk WRITE started, not yet claimed PASS. Original client artwork/rendering and survival availability of base Breaker remain open dependencies.

Silk cold dedicated port25608/world `build/golem-silk-restart`: **WRITE1m34s/READ59s explicit markers PASS**, logs `golems-silk-restart-{write,read}.log`. Actual SMART arms3 advanced seal psilk=true/pmeta=false/ninth diamondore ghost; first process made2actual hardness7 callbacks into diamondore30, saved unchanged ore/no loot/XP0. Second JVM restored exactUUID/owner/type/area/whitelist/psilk/ninthghost, fresh progress (no ore/loot before44ticks), nativeAI emitted exactlydiamondore1/XP1, empty hands/no enchanted tool/no extraentity/replay through100readticks. Both processes used166productionbaseline; no new Use source compiled. Normal restart persistence only, no crash guarantee.

### 20.10.2026 M10.30 — SealUse

Full Java21 Core39872/2856PASS, `golems-use-core-final.log` (+63vsM29). Initial compile обнаружил опечатку dispatcher eligible вместо complete, исправлено до игровой проверки. **176/176fullPASS с первого запуска1m24s**, `golems-use-build.log` (166prior+10Use); финальная source сверка HAULER добавила MAIN-only empty-ghost corner и callback exception recovery, **178/178fullPASS1m28s**, `golems-use-final.log` (все176prior+2). Expected fixture exception logged by event bus/caught by Use — это настоящий fault-injection scenario, не заявление об отсутствии любых ERROR строк.

Реально проверены: native paid3dirt→assigned AIR cell1+hands2, сохранение AIR-capable seal; empty-hand actuallever toggle сохраняет3namedgold; realwaterbucket→fullcauldron/actualemptybucket1; source left START destroys maturecrop with actualwheat1/hoe retained/no fabricated replant; owner/sneak cancellation retains4actualitems and sourceattemptXP1/resetflags; STRICTProvide2→source1+actualplacement1 with per-tick conservation, metadata wildcard oakghost resolves actualspruce and places spruce; SMART+DEFT gates; nine source toggles/ghost4/owner authority/nativeNBT; actual blank1+Fabrico20+Sensus10+Cognitio20+water50→Use1 with research gate; HAULER emptyghost MAIN-only noXP/noOFFdebit then configuredghost usesactualOFF/remainder1; thrown callback recoversactual4/no worldmutation/stale task rejected. UI Java compiled; graphical client unrun.

Use cold isolated dedicated port25609/world `build/golem-use-restart`: **WRITE1m32s/READ55s assertionsPASS**, `golems-use-restart-{write,read}.log`. Before disk actualProvide2spruce→source1+paidhand1, targetAIR, XP1; typedUse flags(empty=true,sneak=true,provision=true,left=false,emptyhand=false), matchingfalse/false/true/false and oakghost saved. Separate JVM restored exactUUID/SMARTFINE/actualspruce1/source1/settings, nativeAI placedactualspruce1 (nooakconversion), source1/hands0/world1 per-read-tick conserved2, XP2 and noextraentity/groundcopies/replay through100ticks. Runtime176 before final HAULER corner; READ compile/resources explicitlyexcluded while those sources refined. Current full178 test proof covers final production; no persistence schema/path changed after cold. Graphicalclient/liveunload/crash injection unrun.

### 20.10.2026 M10.31 — Wheels

Full Java21 Core39879/2856PASS (`golems-wheels-core.log`,+7construction). First181-run180PASS/1fixtureFAIL: immediate navigation on new airborne entities returnedfalse before ground tick. Delayed start5ticks and recorded crossing-time of4actualblocks. **Final full181/181requiredPASS1m39s**, `golems-wheels-final.log`:all178prior+3realwheel scenarios. Native ground rolling speed beats walker; correctstep0.5/0.6; actual AI pickup→capabilityfill11 conserved each tick; actual researched selection+8sourcecomponents/12actualMachina→oneplacer→oneworldentity, typedentityNBT owner/props/step restored. No persistent schema changed; no extra cold run solely for the same packed leg byte. Earlier actualUse cold/nativeNBT cover existing serialization; wheel-specific client visuals untested. Previous final178JAR SHA256 f880256f514764bb6818a3c20a5df67c3aee47809d2bbbb6cbd2f265fdcaf447, verifiedCRC/classes/uniqueENRUJSON. New181artifact recorded at next checkpoint.

### 20.10.2026 M10.32 — actual graphical client

Первый полный client BUILD SUCCESS13m12s, второй после fixes **BUILD SUCCESS9m41s**; логи внеGit `/workspace/reference/golems-client-{launch,verified}.log`. Реальные fresh worlds Golem M10 GUI / Golem M10 GUI verified, LWJGL3.3.3, NeoForge21.1.250, software Mesa/portable Xvfb:97 1280×800. Second explicit markers: Use C2S08:31:20UTC, Silk C2S08:35:00UTC, actual AI transfer/conservation11 08:35:16UTC. Clean world shutdown08:35:47UTC/client08:35:51UTC. [Необработанные снимки и инструкция](evidence/golems/client_2026_10_20/README.md). First run caught own missing compass/clock textures and misleading seed-request caption; corrected paths/caption/Silk width+tooltip and second fresh run verified. Existing unrelated base_metal/caster and offline auth warnings remain documented, no blanket error-free claim.

Final artifact `build -x test` PASS after client exit; ZIP CRC/expected probe class/uniqueENRUJSON PASS. SHA256 a798699a24c75b551470aba4aae8c6674c0d026b326af050161cf1eac0999bd4. Core39879/2856 and181/181 GameTests are previous unchanged server production baseline; client-only changes do not justify repeating all expensive tests. Probe inputs are creative fixtures; no new survival/cold/crash claim.

### 20.10.2026 M10.33 — native melee

Full Java21 Core39883/2856PASS (`golems-combat-core-final.log`). Native first187/189 and second188/189 found only fixture errors (delta2, mock login payload negotiation, mistaken Machina15 expectation). Final **189/189 requiredPASS1m40s**, `golems-combat-verified.log`: all181prior+8combat. Tested actual native pursuit/hit/kill8XP/7unchanged cargo, ordinary worker no damage, self-defense, loadedowner/team/follow-defense, canceleddamage noDEFT/XP/weaponwear, sourceDEFT100 AFTER damage with actual second-hit XP-orbs and no first-lethal retroactive orbs, researched paid10components/22Machina→oneclawfighter/NBTdamage2. Single-field AT applied by real NeoForm, no shared Gradle/CI change. Current artifact CRC/AT PASS SHA256 270554ca992bcdcf5d1363d0a7aa7d59555c39a331aa85d21f136be7cb9c3529. Graphical M32 evidence precedes combat; no new combat-client/cold/crash claim.

### 20.10.2026 M10.34 — combat seals и cold guard

FullCore39883/2893PASS, `golems-guard-core-final.log`. First198run196PASS/2fixture expectations: source reserved flag удаляется next board second; default tangent area knob3→4 (physical5→7), not6. Final **201/201requiredPASS1m44s**, `golems-guard-final.log`, all189prior+12 real tests. Source lifetime10seconds/20tickGuard/200tickButcher, actual target→nativekillXP9/cargo7, commonPickup/Guardpriority, no wrong block/item routes/staleXP, basicFIGHTER/advancedSMARTFIGHTER/sourceCLUMSY, owner/home/color/follow, panimal-onlyHusk, 3flags/area/nativeNBT, disappearedtarget, piglet/tamewolf/surplus3→2, asymmetricCow/Mooshroom and actual20/20 base+upgrade crucible/research debit checked. Graphical newGuard UI not yet exercised; M32 screenshot run precedes combat.

Cold isolated port25610 `build/golem-guard-restart`: **verified WRITE1m45s/READ1m32s explicit PASS**, logs `golems-guard-restart-{write,read}-verified.log`. Actual201runtime plus opt-in fixture; READ excludes compileJava/processResources while M35 source prepared. WRITE13:41:56UTC saved exactUUID/owner/SMARTBRASSclawsWHEELED/color4/cargo7 namedgold and actualNoAIHuskHP4; sourceAdvancedGuardlocked/priority3/area4,1,3/flagsfalse,true,true and assignmentXP1. Separate JVM confirms transient target not persisted; enables AI; **actual native LivingDeathEvent13:44:41UTC confirms original victimUUID killed by exact workerUUID**; READ13:44:45UTC asserts savedXP1+freshassignment1+kill8=10, no replays through130ticks, same7namedactualgold/no groundcargo copies/oneworker. No survival acquisition claim (fixture inputs), no crash guarantee.

Earlier cold WRITE1m38sPASS/READ1m13sFAIL is preserved: fixture relied on lastHurtMob which Minecraft clears on victim death. Read-only saved region showed actualworker atvictim/cargo7 and noHusk. Replaced observation with real death event UUID assertion; failed world preserved `/workspace/reference/golem-guard-first-read-world`, no production change. Successful runs used a fresh world, not mutated prior evidence.

### 20.10.2026 M10.35 — CLIMBER native

Full Core39884/2893PASS (`golems-climber-core.log`). First207run204PASS/3FAIL: erroneous sourceXP9 assumptions (savedregionactualgroundXP10/climberXP12/noHusks) and erroneous assumption that climber bypasses sourceBlock3D reach. Sourceconfirmed, no production XP/reach guard relaxed. Final **207/207requiredPASS1m48s**, `golems-climber-final.log`. Actual wall5blockmotion/collisionbit vswalker; source seal Pickup→Fill11 everytick; packedNBT rebuild/reset; actual elevatedGuard death event/exactkill8overpre-killassignments; distantblockrejected until realnativeclimb placesitwithinreach then actualdiamond1; paid11components15Machina→oneactualclimber. Separateown12×10×12fixture prevents placing testtower outside old5-high template. Existing201tests pass, including Guard test now measuring source-reassignments correctly. Newclimbercold not yet claimedPASS.

Climber cold, isolated port25611/world `build/golem-climber-restart`: **WRITE1m42s/READ1m18s explicit PASS**, `golems-climber-restart-{write,read}.log`. Compiled207 baseline, before separate sourcefall-immunity refinement; persistent schema/navigation unchanged by that refinement. WRITE13:55:43UTC stores same actualworkerUUID/SMARTBRASSclawsleg2/owner/color4/namedgold7 and actualHuskHP4 on5blockwall, AdvancedGuard flags/area/lock, initialassignmentXP1. Separate JVM rebuilds actualWallClimberNavigation frompackedNBT; realserver collision flag observed duringwall ascent; actual originalHusk death byexact worker UUID13:57:20UTC. READ13:57:23UTC validates source pre-killassignments+8/noextraXP through130ticks, oneworker/cargo7/no goldgroundcopies. Offline savedregion confirms workerY125/onwall, rankXP11 (=saved1+2freshassignments+8), exactnamedgold7, health19. Genericcoldprobe now observes pre-killXP rather than assuming oneassignment. Normal shutdown persistence, not liveunload/crash/client proof.

M35 fall source refinement: **Core39887/2893PASS**, `golems-climber-fall-core.log`; **208/208requiredPASS2m2s**, `golems-climber-fall-final.log`. Source func_180430_e explicitly exempts CLIMBER/FLYER; new native test actualfall10 leavesclimberhealth unchanged and damagesordinarywalker. All207priorPASS. No persistent change/new cold requirement.

### 20.10.2026 M10.36 — native flight

FullCore39903/2893PASS, `golems-flight-core.log`. CompilePASS49s, `golems-flight-compile.log`. **216/216requiredPASS first fullrun1m58s**, `golems-flight-build.log`: eightactualsourceflightscenarios andall208prior. Tested actualwall/gap collisiondetour vswalker, sourcecontroller acceleration/brake+nativefallimmune, sixaxisneighbors/stonecell exclusion, packedNBTcontroller/navigation/gravity andexplicitwalkerreset, actualfloatingPickup→groundFill11conserved eachtick, floatingGuard/nativekill8 aboveactualassignmentXP, airBreaker/actualdiamond1, missingoriginallevitator nofreePress/product/debit andoneactualseparatelysuppliedtestplacer. No survival levitator acquisition/graphicalflight/fluid-mod exactparity claim. ColdFlyerWRITE explicitPASS2m8s, separateREAD pending.

Flyer cold isolated port25612 `build/golem-flyer-restart`: **WRITE2m8s/READ1m9s explicit PASS**, `golems-flight-restart-{write,read}.log`, both216production plusopt-in generalizedfixture. WRITE14:15:34UTC saved originalSMARTBRASSclawsFLYER/UUID/owner/color4/gold7namedcargo and actualHuskHP4 onfloatingoneblockplatform/no wall, AdvancedGuard flags/area/priority/lock, initialassignmentXP1. Separate JVM rebuilds actualFlightNavigation/FlightMoveControl/NoGravity frompackedNBT; actualverticalascent observed; originalHusk native death byexactworkerUUID14:18:37UTC; READ14:18:41UTC asserts realpre-killassignmentXP+8/noextrareplay through130ticks, oneworker/cargo7/no groundgoldcopies. No survivallevitator/clientflight/liveunload/crash claim. Current216 JARCRC/classes/uniqueENRUJSON PASS SHA256 e67b8643e6ca06bd044f5afe7b9a551dfcaa535a6e9146da84eb82cc7b434fe8.


### 20.10.2026 M10.37 — native ranged

Java21 fullCore39962 construction/2893 task assertions PASS (`golems-ranged-core.log`); native compile PASS1m (`golems-ranged-compile.log`); full build226/226 required PASS с первого полного запуска2m42s (`golems-ranged-build.log`,14:36:09UTC). Все216 прежних и10 новых: настоящие ground/flying projectile kills, clock23/45, blocked/outside16/removed/owner refusal, source launch/size/owner/pickup/damage, native projectile NBT, actual embedded non-pickup vs actual vanilla-arrow pickup, researched paid42components including32arrows+54Machina→one actual placer/active shooter, ordinary/claws no ranged. No invented ammo debit/ranged meleeXP8. Graphical renderer пока только compiled, original art/modern all-physics parity не заявлены. Cold WRITE2m35s explicit PASS, READ идёт отдельно; до marker не объявляется успешным.


M37 cold: **WRITE2m35s/READ1m24s explicit assertions PASS**, `golems-ranged-restart-{write,read}.log`, isolated `build/golem-dart-restart`, port25613. One real deterministic zero-spread test dart had already moved one natural tick at save; source automatic shots still use Gaussian spread3. Separate READ captured actual loadedFromDisk pre-tick UUID/Pos/Motion/damage/owner/pickup, briefly froze only loaded test projectile until inspection, then restored original physics. Actual owner-attributed native impact14:48:41UTC, READ14:48:46UTC; original dart removed/no replay, restored golem resumed automatic shots, same namedcargo7/rankXP0/one worker/no duplicated world gold. Both226production; opt-in fixture creates no ordinary-runtime content. No forced-crash/live chunk-unload/graphical proof implied.


### 20.10.2026 M10.38 — настоящий боевой клиент

После0abeb6a расширен прежний opt-in integrated probe, отдельный combat-only scenario. Первый full client **BUILD SUCCESS6m50s** и graceful save/exit, но smoke assertions FAIL при попытке открыть Guard за8block menu radius; реальные Use/Silk/Pickup→Fill11 до этого PASS. Исправлен только test player teleport внутрь радиуса, source permission guard не ослаблен. Второй **BUILD SUCCESS5m16s**, Java21/MC1.21.1/NeoForge21.1.250/LWJGL3.3.3/Xvfb/Mesa, actual C2S Guard flags15:01:14UTC, actual renderer callback15:01:39UTC, originalHusk native GolemDart kill15:01:51UTC/FLYING_RANGED_ASSERTIONS_OK. Source IAnimals target rules/real enabled airnav/cargo7/source no rangedXP8 preserved. Raw screenshots/reproduction: analysis/evidence/golems/client_combat_2026_10_20. No original visuals/survival levitator/modAPI parity implied. Source production after226/cold unchanged except opt-in client verification hook; no costly226 repeat without server changes. Packaged CRC/classes/AT/uniqueENRU JSON PASS SHA2562265da53f19f6e60c62390f3b2f6a51b6e4e27c406da9b22bc6ca2489e2474c7 (also contains subsequent compiled opt-in chunk probe, not yet verified/committed here).


### 20.10.2026 M10.39 — настоящая граница ticking chunks

Проверка live unload нашла production defect: после удаления forced ticket дальний чанк оставался FULL/с entity storage. Первоначальная гипотеза event-order не объяснила повторный провал. Два первых dedicated runs имеют explicit ASSERTIONS_FAILED несмотря на BUILD SUCCESS2m56s/2m16s; миры и логи сохранены внеGit. Modern vanilla ServerChunkCache.getChunkFutureMainThread(requireChunk=true) добавляет TicketType.UNKNOWN; обычные getBlockState/capability reads после одного hasChunkAt могли постоянно обновлять этот билет. Достаточно наличия FULL chunk для прежнего guard, но этого недостаточно для безопасного серверного lifecycle.

Lane-local fix: GolemSeals.active использует non-loading isPositionEntityTicking; все seal producers/target validation/capability/двойной сундук/сигнальный halo ждут реальных ticking chunks. Неподвижная граница simulation radius не обрабатывается фоновыми seal scanners, inactive seal record не принимается за разрушенную опору; leases отменяются прежним путём. Это явно названная modern lifecycle projection, не утверждение буквальной эквивалентности старого1.12 loaded-chunk API. Никакого ручного удаления сущности/билета/файлов ради PASS.

**Full NeoForge227/227requiredPASS1m39s**, `golems-chunk-fix-build.log`15:13:55UTC: все226 прежних+новый реальный FULL/UNKNOWN без entity-ticking ticket. Все16 source seal types одновременно placed в actualFULL chunk, native capability lookup отказывает без block read; через60ticks чанк действительно исчез, saved seals сохранились. Java21 Core39962/2893 unchanged/no Core rule edit. Полный dedicated carry13→native unload→sameUUID reload→Fill сейчас запущен отдельно; PASS до marker не объявляется. Клиентский M38 proof относится к production226 до этого lifecycle fix, visuals/UI не менялись.


M39 dedicated final **BUILD SUCCESS1m34s / GOLEM_CHUNK_UNLOAD_ASSERTIONS_OK15:15:51UTC**, `golems-chunk-unload-fixed.log`, production227. Actual worker picked up13 named iron; test released its forced ticket15:15:37UTC. Actual native ChunkEvent.Unload + EntityLeaveLevelEvent followed by final UNLOADED_TO_CHUNK, block getChunkNow=null/entity storage absent15:15:40UTC. Forty further ticks remained absent: no seal scanner reloaded it. Actual disk EntityJoin loadedFromDisk restored a different Java entity with same UUID/owner/design/rank/namedcargo13/AIenabled. Native redstone removal released original Fill; restored AI delivered all13 to actual barrel, conserved each tick/no duplicate worker or groundiron/no replay for100ticks after completion. No manual entity discard/removal or world-NBT editing. Fixture-only tickets are explicit. Two prior failing runs/worlds preserved; event-order observation refined, actual pinning defect fixed byfaa264c. This is worker/seal live lifecycle proof, not Press live-unload or abrupt process-crash atomicity.

Final packaged JAR CRC/expected classes/AT/uniqueENRUJSON PASS SHA256 `d74ba4e27de23b1f1ef00fdfada5a14012e48476eff44e8894b5cc17adb6c8b9`. No new Core rule changes after39962/2893; no need costly repeated227 suite after opt-in fixture/docs only. Earlier client proof is M38/226production before server lifecycle fix; native227 covers that fix, renderer/UI unchanged.


### 20.10.2026 M10.40 — client seal mirror и области

Read-only BETA26 RenderEventHandler.drawSeals/drawSealArea/renderLast, ISealDisplayer, ItemGolemBell, ItemSealPlacer, SealEntity.syncToClient и PacketSealToClient подтверждают: bell/все seal variants (включая blank) показывают печати в любой руке; source getDistanceSqToCenter≤256, alpha=1−distance²/256; color0 pulse .7+sin((ticks+partial+axis)/4,5,6)×.1 с исходным float/table quantization, color1..16 vanilla dye metadata; sneak отключает depth test. Области показываются у всех ISealConfigArea, не только выбранной курсором. Основная face-relative геометрия уже была в Core и переиспользуется.

Порт: отдельный S2C payload с UPSERT/REMOVE/CLEAR_CHUNK, immutable key/type/area/color/priority/owner/instance/lock/redstone, без задач и инвентарей. Реальный ChunkWatchEvent.Sent отправляет reset и по одной записи на seal; place/replace/remove публикуют ordered delta только tracking clients. Каждый packet<128bytes в codec tests, произвольного clipping числа печатей нет. Source dimension-wide broadcast заменён chunk subscriptions; wire format не объявляется binary-compatible со старым Forge. Снимок читает SavedData и не запрашивает World chunks, M39 guards сохранены.

Независимый client-thread mirror игнорирует чужое измерение, меняет активный dimension при новой записи, очищает chunk/unload/logout. Renderer не читает ServerLevel даже в integrated mode; использует source range/fade/colors/either hand/sneak и Core bounds. Исходные bitmap/grid artwork/вращение/particles пока не портированы: вместо них явно provisional line geometry с scoped depth/blend render states, без выдачи временных текстур за original visuals. Owner menu authority не переносится клиенту.

Full Java21 Core39962/2901PASS (+8 source visual goldens), `golems-seal-visual-core.log`; first compilePASS20s. Native first231 run230PASS/1старый unseeded airBreaker timeout350; все4 новых сетевых сценария PASS. Saved actualworker position/rankXP0 подтверждают реальное застревание, не успешную добычу. Source AIGoto содержит stochastic ramble; fixture RNG теперь seeded0 и error message содержит pathDone/motion. Deadline350/XYZreach/collision/quantityXP guards и production navigation не ослаблены/не изменены. Это воспроизводимость representative test, не доказательство broad all-seed flight stress. **Повторный full231/231requiredPASS1m57s**, `golems-seal-visual-seeded.log`; первоначальный fail log сохранён. Native96type×face codec roundtrips, ordered updates/removal/chunk boundary/wrong dimension/clear, real saved16seal late-snapshot without chunk load, source marker inventory catalog. Graphical клиент запущен, до реальных receive/render/action markers PASS не заявляется.


### M40 — корректная камера world overlay

После `033722d` настоящий клиент обнаружил смещение контуров: AFTER_LEVEL получает свежий PoseStack, а GameRenderer передаёт camera rotation отдельно в modelViewMatrix. Renderer теперь применяет эту матрицу перед world-space translation. Сверено с NeoForge/GameRenderer sources; реальный повторный клиент показывает геометрию на исходных блоках, нормальный depth и sneak-through, включая колокольчик во второй руке. Первый неверный raw screenshot и лог сохранены вне Git, они не объявляются успешным визуальным доказательством. Native231/231 предшествует этому исключительно клиентскому исправлению; повтор полного сервера только ради camera transform не выполнялся. Opt-in сценарий и raw evidence фиксируются следующим отдельным коммитом.


### M40 — настоящий клиент завершён

`a20d040` исправляет камеру после функционального `033722d`. Отдельный opt-in `seals` fixture не запускается в обычном клиенте/сервере: fresh creative world, три настоящие серверные печати, реальный tracking snapshot. Client assertions читают только полученный cache, без доступа к integrated ServerLevel. Физические F/Shift/мышь проверили offhand и depth; реальные C2S клики изменили Pickup area(3,1,2)→(4,1,2), color4→5 без смены instance UUID. Shift удерживался отдельным вводом до ПКМ: слишком короткое сочетание до серверного crouch tick открывает обычное меню; никакой guard ради теста не ослаблялся. Actual bell removal создал ровно один ItemEntity печати, сохранил две остальные записи; клиент получил REMOVE и оставил две записи.

**Реальный Java21 Minecraft1.21.1/NeoForge21.1.250/Xvfb/Mesa клиент BUILD SUCCESS7m23s**, `golems-seal-client-fixed.log`: snapshot19:54:37UTC, offhand19:55:13, sneak-through19:55:28, сервер/C2S edit19:57:37 и client update19:57:38, server exact-one-drop и client removal19:58:17. Штатный Save&Quit: all dimensions saved19:58:37, клиент Stopping19:58:57UTC. Raw [screenshots и воспроизведение](../evidence/golems/client_seals_2026_10_20/README.md). Logout очистил cache до level-unload logger, отдельный graphical clear marker не заявляется; native clear tests PASS. Первый ранний клиент с неверной камерой/последующим завершением процесса не считается успешным запуском.

Native baseline231/231 и Core39962/2901 предыдущего коммита остаются актуальны: последующие production изменения только client camera transform; opt-in fixture не изменяет серверные правила. JAR CRC/classes/AT/uniqueENRU JSON PASS SHA256 `2bfa245ea329a2522b011b93b8c3a2ee3eafeb19aed2463bbce92a071d1290ff`. Main fresh19:58UTC остаётсяa4280834; own033722d CircleCI#423verify-connectionSUCCESS отдельно от native proof. Исходные bitmap/grid/rotation/particles/модели не перенесены; line geometry provisional. Следующий конкретный независимый пункт — настоящий Press live chunk unload/reload, затем более широкие mod API/stress/crash сценарии. M06/M07/M08 survival зависимости/levitator без обходного рецепта остаются открыты.


### M10.41 — реальные границы тиков пресса

После `352eab5` начат actual Press live chunk probe. Первый процесс прерван вместе с exec transport во время startup, никаких assertions не было; лог/мир сохранены. Следующий BUILD SUCCESS1m40s содержит явный ASSERTIONS_FAILED: fixture неверно требовал, чтобы paid buffer был потреблён до native unload. Сам чанк действительно выгрузился. Исправлено только наблюдение: remaining−(buffered?1:0)==4, затем точное сравнение состояния после диска. Постоянное pinning пресса этим запуском не доказано.

Отдельный native232 regression до production fix: **231PASS/1FAIL, BUILD FAILED1m28s**, `golems-press-inactive-before-fix.log`: actual FULL chunk исчез, но scanner преждевременно забыл PENDING/восстановил RESTORING records до block-ticking. Fixtures явно создают допустимые persisted phases; не выдаются за actual formation/unload paid job proof.

Исправление использует **ServerChunkCache.isPositionTicking**, non-loading block-ticking future + vanilla inBlockTickingRange. Это именно block-ticking, а не более узкое entity-ticking условие големов M39. Formation/restore piston halo, ACTIVE scan и соседние источники Machina ждут этого состояния; обычные client-side checks сохранены. TileGolemBuilder.func_73660_a по BETA26 сохраняет five-tick paid consumption; современная lifecycle projection не меняет source cost/interval/output/payment.

**Полный233/233required native GameTests PASS**, `golems-press-ticking-fixed.log`20:09:18UTC: все231 прежних плюс сохранность трёх inactive phases и положительный настоящий level32 block-ticking/не entity-ticking Press→один оплаченный placer/ровно8 Machina. Последний не позволяет случайно остановить машины в законной vanilla block-ticking полосе. Core39962/2901 неизменён, дополнительных Core rules нет. Следом actual formed pair live-unload/reload probe на этой233 production; до явного marker PASS ему не присваивается.


### M41 — actual formed Press live unload завершён

`23b496e` — production block-ticking fix и233nativePASS1m40s; этот отдельный коммит содержит только opt-in dedicated fixture/свою маршрутизацию и результат. **BUILD SUCCESS1m38s / GOLEM_PRESS_CHUNK_UNLOAD_ASSERTIONS_OK20:11:18UTC**, `golems-press-chunk-fixed.log`, isolated build/golem-press-chunk-unload/port25615, production233.

Две настоящие пятиблочные машины собраны через Salis Mundus с двумя реальными debit и50tick ожиданием. Каждая снимает ровно один batch компонентов из barrel; второй batch оставлен для контроля. Первая имеет partial paid work и реальный buffered Machina (4оплаченных, ещё4missing), вторая полностью оплатила8 и стоит с blocked output; namedgold2 blockers сохранены. Fixture отпустил свой forced ticket20:11:09UTC; native ChunkEvent.Unload/getChunkNow=null/entity storage absent20:11:10. Следующие40ticks chunk остаётся отсутствующим, старые JavaBE не прогрессируют. Reload создаёт новые BE с точным captured job/selection/output components, jar0/2, всеми5links обеих машин и нетронутыми вторыми batches. Реальные пополнения только4missing+2spare, разблокировка выхода→native ticks→ровно по одному placer/по2unspentMachina;100ticks без replay/новых debit/groundoutput. Настоящее разрушение anvil первой машины восстанавливает её source blocks и даёт ровно anvil+один paid product; другая машина сохраняет свой single product. Никакого ручного удаления чанков/билетов движка или редактирования мирового NBT.

Первый прерванный startup и failed buffer-timing assumption сохранены отдельно; before-fix native lifecycle failure тоже сохранён. Этот proof не заявляет abrupt process crash/multi-file atomicity. Следующий concrete пункт — separate-JVM TCP клиентские seals, уже подготовлен opt-in fixture; PASS до actual connection/C2S/S2C markers не заявляется. Core39962/2901 не менялся. ОбщиеROADMAP/build.gradle/CI/чужие ветки не тронуты.


### M42 — source sneak depth работает независимо от предыдущего GL state

После `8bfcbc0` первый настоящий separate-JVM TCP run получил snapshot/реальные area-color C2S/S2C/removal и штатно сохранил сервер (BUILD SUCCESS5m34s), но raw screenshot показал отсутствие through-wall outlines. Callback THROUGH_RENDER сам по себе не доказывает видимость. Minecraft RenderStateShard.DepthTestStateShard для func519/NO_DEPTH_TEST имеет пустые setup/clear; AFTER_LEVEL может наследовать enabled depth от мира. Source BETA26 требует реального disableDepthTest при sneak.

Lane-local renderer fix добавляет явный scoped DepthTestStateShard: сохраняет реальный GL_DEPTH_TEST flag, отключает его и восстанавливает прежний flag после batch; depth function не меняется. Opt-in проверка читает реальный GL flag после disable. Новый отдельный TCP клиент20:21:09UTC сообщил EXPLICIT_DEPTH_DISABLED previous=true; screenshot теперь показывает cyan Pickup/другие areas сквозь ту же непрозрачную стену, обычный режим скрывает их. Первый неверный screenshot сохранён внеGit. Новое server/client binary compiled; полный233native не повторялся ради чисто клиентского исправления, Core39962/2901 unchanged. Remote fixture/raw evidence/окончательное закрытие проверок фиксируются следующим отдельным коммитом.


### M10.42 — separate-JVM TCP клиент завершён

`fd38183` — explicit sneaking depth fix после первого TCP visual failure. Этот отдельный test commit добавляет только opt-in remote server run/QuickPlay address, обобщённый fixture и клиентское утверждение настоящего InetSocketAddress при отсутствии integrated server. Обычная игра этот fixture не активирует. Server завершает сценарий через200ticks после фактического завершения действий; окно клиента закрывается штатно через GUI.

**Dedicated server BUILD SUCCESS3m24s**, `golems-seal-remote-depth-server.log`; **отдельная native client JVM exit0**, `golems-seal-remote-depth-client.log`, Java21/MC1.21.1/NeoForge21.1.250/Xvfb/Mesa. Настоящий TCP login127.0.0.1:25616 в20:20:44UTC, client asserts no integrated server + InetSocketAddress/actual3seal snapshot20:20:47. Offhand realF20:21:01, реальное source sneak и GL_DEPTH_TEST=false при previous=true20:21:09; raw screenshot показывает все контуры сквозь opaque wall, ordinary screenshot скрывает их. Owner menu actual мышь X+/color+→serverarea4/1/2,color5→clientcache с прежним instance UUID20:21:32. Удержанный Shift/ПКМ→realoneItemEntity/двеоставшиесяпечати→actualREMOVE client20:22:04. Все измерения сохранены20:22:24, клиент Stopping20:24:06UTC и exit0 (tool observed). [Raw screenshots/real excerpts/copyable reproduction](../evidence/golems/client_seals_tcp_2026_10_20/README.md).

Два Gradle JavaExec одного checkout могут ждать project lock; client запущен напрямую по подготовленным ModDev args/legacyclasspath+nativeMinecraft artifact/MOD_CLASSES, без dump среды/credentials. Первые boolean systemProperty overload/missing MC classpath failures были до gameplay, сохранены отдельно; первый полноценный TCP run network PASS, но его неверный through-wall screenshot не считается graphic PASS. Последний verified run исправляет эту проблему. Vanilla offline chat/public-key/base missing-texture warnings не скрываются и не объявлены исправленными M10.

Final JAR CRC/expected classes/access transformer/unique ENRU JSON PASS SHA256 `402bd8f1eae21491f8922f95826213b67045631a81a136f3ba030b2efcef0ac3`. Server baseline233/233required PASS1m40s; Core39962construction/2901task PASS unchanged. Полный233 не повторялся после чисто client-depth fix и opt-in test additions. Single TCP client proof не заменяет multi-user/packet-loss/stress/crash-injection. Source original artwork/particles/модели/анимации, широкие modded APIs и survival M06/M07/M08/levitator остаются открыты; общий59/100 не пересчитан. Следующий конкретный пункт — контролируемая crash-injection inventory transaction с проверкой восстановления и сохранения суммы; сначала аудит native save order, без обещания atomicity до измерения. Main fresh20:24UTC неизменёнa4280834, own8bfcbc0 CI#428verify-connectionSUCCESS (не полныйNeoForgeCI). Толькоfeature/golems, main/другиеветки/общиеGradleCI не изменялись, Draft#3 остаётсяDraft.


### M43 — controlled JVM crash, native save-order audit

Продолжение987309f: freshmaina4280834/maininstructions/ownlane, CI#430verify-connectionSUCCESS. Оригинальные SealFill/SealEmpty/InventoryUtils используют обычные equipment и container callbacks без отдельного persistent transaction journal. Современный ServerLevel.save сначалаsaveLevelData, затемServerChunkCache.save, затемPersistentEntitySectionManager.saveAll/autoSave; blocks и entities находятся в разных region stores. Нельзя объявить multi-file atomicity только по server-thread transfer.

Добавлен строго opt-in GolemCrashProbe/optional golemCrash run, отдельные build/golem-crash-{unsaved,saved,split}, localhost25617–25619. WRITE создаёт actualfinite namediron13→native extraction→golem hands и vanillaItemEntitygold17; полный checkpoint пишет обоих. Native autonomous Fill и ванильный HopperBlockEntity.addItem реально доставляют их. Runtime.halt(86) обходит shutdown hooks; WRITE ожидаемо BUILD FAILED/exit86, это не compile failure и не успешное graceful сохранение. READ — другая JVM, реальные NBT/UUID/owner/components/per-tick quantity/no replay.

Первый UNSAVED: WRITE checkpoint20:48:28UTC, actualFill/hopper→halt8620:48:30 (59s); READ **GOLEM_CRASH_UNSAVED_READ_ASSERTIONS_OK20:49:37UTC/BUILD SUCCESS51s**. Диск сохранил checkpoint cargo13/target0/item17/hopper0; восстановленная AI повторно доставила только исходные13, vanilla control17, no duplicate worker/100ticks no replay. Полностью saved и split prefix ещё выполняются, результат до markers не заявляется. Split — диагностический native block-save prefix при старом entity store; если восстановится26/34, это явный HAZARD, не recovery PASS. Классы production пока не менялись, Core39962/2901/native233 прежний baseline. Источники/бинарники/миры внеGit, никакого редактирования мировогоNBT. Следом source CropUtils.clickableCrops/SealHarvest actualcallback adapter, не изобретённый автосбор новых vanilla культур по умолчанию.


### M43 — вся crash matrix измерена

`55e99a5` — opt-in actualcrashprobe, Source/native inventory rules production233 не менялись. SAVED WRITE49s/checkpoint20:51:37UTC→nativeFill13/hopper17→fullsave→halt8620:51:39; READ53s **SAVED_READ_ASSERTIONS_OK20:53:03**, exact13/17, no replay100ticks. SPLIT WRITE42s/checkpoint20:54:39→block-only nativeflush→halt8620:54:41; READ46s **SPLIT_SAVE_HAZARD_OBSERVED20:56:44**: target13+oldhands13=26, vanillaHopper17+oldItem17=34. Это диагностическое воспроизведение разрыва vanilla block/entity stores, **не recovery PASS**. Никакого исправления/дедупликации по равенству item/components не реализовано: такие stacks могут быть легальными, а внешние container/player callbacks не покрываются lane-local journal. Требуется общий протокол durable commit/координация persistence, не новая выдуманная gameplay-механика.

[Доказательства/таблица/команды](../evidence/golems/crash_2026_10_20/README.md), реальные6 JVM excerpts и независимое read-only region+entities JSON: unsaved/saved после resumedelivery13/17/одинworker; split26/34/одинworker. Старые миры/логи сохранены внеGit. Последующие dirty Farm/click source не компилировались в этой233baseline matrix (read/split -xcompileJava/-xprocessResources), не смешивать с текущим239 candidate build. Далее подтверждённый source clickable crop hook/owner-protected native block use/common actual cursor recovery; Core39962/2909PASS (+8exactsource face facts), full239native сейчас выполняется, PASS до конца не заявляется.


### M44 — source clickable crops, настоящий public hook

Послеb0a57a4 CropUtils.addClickableCrop/isGrownCrop и SealHarvest.onTaskCompletion подтверждают exact metadata enrollment, wildcard32767, empty fake hand, EnumFacing.getDirectionFromEntity и hitXYZ0, игнорирование boolean callback/XP1 и отсутствие replant в clickable branch. Делегированный vanilla EnumFacing проверен read-only official MC1.12.2fa.a(et,vp): strict2XZ/eyeY thresholds/float block centers. Core GolemFarmRules.clickFace +8goldens, **fullJava21Core39962/2909PASS**, `golems-clickable-core.log`.

Public GolemHarvestRegistry.registerClickable(BlockState...) и explicit Block wildcard — современная exact-state projection, не binary1.12 ABI. Никаких default новых культур; duplicate registrations/scoped close/defensive input работают независимо. Настоящий Harvest scan/sharedboard/native owner FakePlayer.useItemOn вызывает зарегистрированный block callback; повторная completion проверяет актуальный registry. В отличие от старого direct activation современный путь dispatches настоящую NeoForge protection event. Реальноеcargo не передаётся в fake empty hand; busy guard блокирует повторный вход. SourceXP1 за attempted click сохранён при PASS/cancel/exception; no ghost seed/replant intent.

Actual menu cursor и inventory возвращаются через общий GolemInteractionInventory; прежний Use переиспользует тот же проверенный порядок вместо копии. Это закрывает Farm cursor/закрытие открытого callback menu, включая исключения; actual payload/components возвращаются в existing persisted recovery queue.

**Полный239/239required native PASS1m23s**, `golems-clickable-native.log`20:58:38UTC: все233прежних+6новых. Registryexact/wildcard/duplicatehandles; actual Berry right-click age3→1/realfruit2..4/cargo13/XP1/no stale; actual owner event/MAINempty/NORTH/corner0/protection cancel; reentrant callback throws after real cursorDiamond2/mainEmerald3→оба actualrecovery/oldiron13/no doublecall; unregisteredstalelease refuses/noXP; actual AI click→Pickup→barrelFill/100tick no replay. Sweet berry — явно enrolled test specimen отсутствовавший в1.12, не добавленная автоматически source culture/не доказательство конкретного foreignmod binary. Дальше dedicated clickable WRITE/READ. Original visuals/source standard-stacked extension/seed API иcrashatomicity пока не закрыты.


### M44 — actual clickable cold restart

`09c198e` — source clickable registry/Core2909/native239PASS. Этот отдельный cold commit: opt-in build/golem-clickable-restart/port25620 и свои run routes. **WRITE52s/READ45s explicitPASS**, `golems-clickable-restart-{write,read}.log`: actual AI clickableharvest→Pickup→cargo2, cropage1, powered Fill stopped, no free seeds. WRITE21:01:50UTC сохраняет originalUUID/owner/rank/cargo2/3seals. Новый READ re-enrolls exact crop adapter приstartup (runtime registry не выдаётся за worldNBT), native persistedNoAI/rank/cargo/crop/control state реально восстановлены. Actual redstone removal→original worker fills2→каждыйtick total2/100tickno replay/oneworker, READ21:03:13UTC. Блок randomticks=0 только fixture для воспроизводимости зрелости, не изменение обычной игры. Persisted expected2 — только наблюдение количества реального native fruit в testmetadata, не создаёт предметов. READ -xcompileJava/-xprocessResources сохраняет239 compiled baseline при последующих dirtyM45 registry/seed sources. Никаких claimfullcropAPI/foreignmod binaries; standard/stacked/seed extensions далее в работе.


### M45 — standard/stacked enrollment и оплаченная seed API

После2d5b9dc read-only CropUtils.addStandardCrop/addStackedCrop/isGrownCrop и ThaumcraftApi.registerSeed/getSeed подтверждены: standard/clickable mature-state registrations, stacked same-block-below, clickable completion precedence и seed map lookup. Public modern GolemHarvestRegistry теперь предоставляет standard/stacked/clickable exact-state/explicitwildcard scopes и registerSeed(block,stack). Сопоставление семян требует реального BlockItem именно этого crop block; произвольные non-BlockItem/cross-block seed callbacks старого IPlantable не объявляются реализованными (IPlantable удалён вNeoForge1.21.1). Современный native useOn/canSurvive/placement events выполняет проверку почвы. Source list strings/oldmetadata ABI не копируются, зрелые BlockStates регистрируются явно, default новых культур нет.

Registered standard crop действительно ломается защищённым destroyBlock; stacked сохраняет нижний root. Если seed mapping отсутствует, replant не создаётся. Если присутствует, создаётся pending intent с canonicalblockID/точным seed+components, support/soil flags. Actual seed переносится из рук до callback, списывается только реальный расход1, leftovers идут existingrecovery. Config изменения проверяются заново на completion; unknown/forged/malformed seed NBT не превращается в произвольную посадку. Runtime registration template/input/returned stack копируются; closing override восстанавливает прежний mapping. Старые vanilla intent enum names без нового seed field читаются как подтверждённые vanilla seeds, направленияCocoa/soil defaults сохранены.

**Первый full245/245requiredPASS1m33s**21:08:50UTC, `golems-crop-seed-native.log`; затем добавлена meaningful legacy fixture без seed/support/farmland fields и strict wrong-type seed guard: **full245/245requiredPASS1m18s**21:11:25UTC, `golems-crop-seed-legacy-native.log`. Все239 прежних +6nativeAPI scenarios: seed defensive/override restoration/rejectedunsupportedkind; actual standardbreak/no ghostseed; real upperBamboo segment/root; namedseed2→pendingNBTexact1→realBlockItem placement/youngcrop/remainingseed1; forgedDiamondseed/removedmappingrefuse; source clickablepriority/no seeddebit. Berry/Bamboo — явно зарегистрированные native adapter specimens, не автоматически добавленное sourcecontent/не чужие mod binaries. Core39962/2909 не менялся послеM44. Этот functionalcommit включает свой GolemSeals registry-provider pass-through только для item component serialization; общий saved-data framework/registries/GradleCI/main чужие ветки не менялись. Следом separate-JVM generic-seed intent WRITE/READ на245baseline.


### M45 — actual generic seed intent cold restart

`8d79f4d` — native registry/seed persistence/245GameTests. Этот coldcommit толькоopt-in seeded route/probe, build/golem-seed-restart/port25621. **WRITE51s21:13:23UTC/READ46s21:14:28UTC explicit assertionsPASS**, `golems-seed-restart-{write,read}.log`,245compiledproduction. Реальный protected standardharvest создал3ordinaryfruit иdurable replantintent, сохранил originalUUID/owner/XP1/NoAItrue и2namedcomponent-bearing seeds; sourcegroundfruit иpaidseeds различаются поcomponents. Startup READ регистрирует тот же adapter, actualSavedData nativeNBT восстановил canonicalcropID/exactseed1/support/soil, старое actualcargo2. AI enabled→nativeBlockItem placement/youngage0/оставшийсяnamedseed1, каждыйtick seedinventory+actualplant=2; initial3ordinaryfruit сохранены, namedgroundcopies0, pendingcleared/oneworker/no replay100ticks. Test observation field хранит только количество реального oldgroundfruit, не создаёт предметов. Randomticks0 толькоfixture. No new gameplay fields beyond already245seedintent schema. Следом optional multi-seed flight experiment для прежнего unseeded airBreaker timeout; до native результатов broadstressPASS не заявляется.


### M46 — 16seeds × open/wall, настоящая навигация

После60d1a27 добавлен отдельный optional runGolemFlightStress/namespace thaumcraft_golem_stress и копия собственного синтетического template. Source production navigation не менялась, normal245namespace не затронут. Первый16open-onlyPASS47s, затем **32/32requiredPASS45s**21:20:20UTC: seeds0..15 × open/4-high opaque wall, owner-locked actual Breaker/native3Dpath/collision/oreharvest/diamond1/XP1. Open57–76game ticks, wall74–354; seed9wall pending350→реальный finish354. Это расширяет evidence восстановления и показывает, почему representative350deadline не универсален. Старый M40unseededfail сохранён и не объявляется успешным задним числом; все seeds/maps/modfluids этим не покрыты.

[Автоматически извлечённые32timings/реальные excerpts/воспроизведение](../evidence/golems/flight_matrix_2026_10_20/README.md). [Crop native/cold evidence](../evidence/golems/crop_adapters_2026_10_20/README.md). Final JAR CRC/expectedclasses/AT/uniqueENRU JSON PASS SHA256 `bb27374dce911e755cadb72a1bd18985eb3c1d94b2e70d39d41f05c16603ccc5`. Основнойfull245Native245/2451m18s иCore39962/2909PASS остаются productionbaseline; optional32 — отдельный run, не притворяется одним277suite. Графическийclient M42 предшествует server-only cropAPI; свежий crop graphical run не выполнялся. ЧастичныйnativeSave HAZARD26/34 остаётся открытым общим persistence вопросом; Sourcevisuals/foreignplantcallbacks/modbinarymatrix/M06-M08survival dependencies не закрыты. Main fresh21:21UTC a4280834unchanged. Следующий конкретный независимый этап — non-BlockItem seed/cross-block plant adapter по современным callbacks и двухсторонняя mod-integration matrix; atomicity требует отдельного общего durable protocol.


### M47 — cross-block cold restart

`1ede286` — production cross-block native seed support и248GameTests. Этот commit добавляет отдельный `seeded_cross` route, build/golem-seed-cross-restart/localhost25622 и переиспользует opt-in seed probe. **WRITE55s21:34:13UTC/READ46s21:35:23UTC explicitPASS**: actual carrot harvest создал5ordinarycarrots, сохранил originalUUID/owner/XP1/NoAItrue/2namedwheatseeds и pending ожидаемый wheat. Новая JVM загружает actualintent/seedcomponents, запускает AI, nativeBlockItem сажаетWHEATAGE0/remainingnamedseed1, everytick seedinventory+plant=2, ordinarycarrots5 сохранены, namedground0, pendingclear/oneworker/no replay100ticks. Actual excerpts добавлены в crop evidence. Общий полный native baseline248/248, Core39962/2909, отдельно32flight остаются последними проверенными. Далее настоящая внешняя Modrinth MIT Farmer's Delight1.3.4 binary matrix, пока без claimPASS. JAR/оригинальные источники/миры внеGit, общие build/CI/main untouched.
