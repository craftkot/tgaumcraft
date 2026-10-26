# M59 — параллельная реализация реальных механик

26.10.2026 (МСК), только feature/golems; base431a8d5, maina4280834 неизменен. Пять агентов получили разные файлы: acquisition, AI, API, renderer, real-client proof. Root выполнил интеграцию, настоящие RED/GREEN, review, проверки и коммиты. Пользователь явно выбрал параллельное автономное выполнение.

## Что изменилось в игре

| Часть | Фактическая реализация | Source anchor BETA26 |
|---|---|---|
| Vision module | GOLEMVISION;50Vis+Aqua1; bottles2/fermentedeyes2/brassplates2/mechanism1 | ConfigRecipes.initializeArcaneRecipes offsets1863–2171 |
| Aggression module | SEALGUARD;50Vis+Ignis1; panes3/blazepowder1/brassplates2/mechanism1 | Те же offsets; constant pool подтверждает строки ` R ` / `RTR` / `PGP` |
| Task continuity | Pickup не прерывает текущее несуспендированное Fill/другое block work; после завершения новая работа продолжается | AIGoto.shouldExecute24–48 |
| Home radius | Проверяется реальная цель block task, а не anchor area seal; provider entity использует live recipient check | AIGotoBlock.findDestination71–82 |
| Combat producer | Собственные transient counters Guard/Butcher; constructor phases0..21/0..199; delay++ перед wait, redstone/inactive пауза | SealGuard ctor15–20/tick0–17; SealButcher ctor15–21/tick0–25; SealEntity.tickSealEntity9–12/89–106 |
| Worker API | Live owner/world/UUID/thread binding; snapshots/traits/capacity/hold/actualdrop/HAULER/XP/color | Published IGolemAPI22–60; EntityThaumcraftGolem hold2097–2207/drop2208–2366 javap |
| Entity provisioning | Actual debit перед native spawn callback; shared transfer exclusion; rejected actual payload/components в recovery без snapshot refund | Современная безопасная интеграция source provisioning |
| Renderer | Своя geometry для6materials/5heads/5arms/4legs/4addons; carry/walk/wheel/climb/hover/swing, actual cargo attachment | RenderThaumcraftGolem.renderParts/createPartsCache/PartModel; current synced state |

Официальный JAR SHA2569425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f. Public API archive SHA256c3baf0279273a8ad82b6b41d4d00a2ddd9015e7269f364e84b15ff997403e56f. Оригинальные бинарники/исходные ресурсы в Git не добавлены. Renderer использует vanilla textures и собственные cuboids, не оригинальные PNG/OBJ; source attachment/animation facts не означают original mesh parity. Ranged recoil не придуман: source firing event сейчас не синхронизирован.

## Настоящие проверки

[assertions.txt](assertions.txt) сохраняет фактические выдержки всех RED/intermediate/GREEN и обоих TCP процессов; имена green/final в локальных логах не подменяют их фактический результат.

- Core RED действительно остановился на missing VISION_MODULE. После реализации full Java21 runner: **40016 construction +204 module +2909 task assertions/allrunner PASS, exit0**. Общий runner изменён только двумя строками для нового исполняемого теста; прежние golden matrices механизмов/разума не удалены.
- Native baseline276:5acquisition+2AI ожидаемых failures и старый Butcher NoValuePresent. Причина старого теста разобрана: fixed205 наблюдение могло попасть после истечения task; теперь polling actual optional task, все class/target/population/start assertions и negative2adults phase сохранены.
- Combined283 PASS2m32s/exit0. Затем independent review нашёл настоящий provider→newAPI synchronous dupe. Два новых теста подтвердили actualRED285/2FAIL1m20s, прочие283PASS. ENTITY-only actualsplit/sharedscope/publishCargo исправлены; block/Seal container paths не захвачены вложенным scope. Re-review Important resolved.
- Intermediate full285/33/4FD+freshcoldWRITE PASS4m59s/exit0, отдельная coldREAD PASS55s/exit0. Это проверяет текущие неизменённые entity/seal NBT; последующий combat counter transient согласно source и не выдаётся за проверку сохранения таймеров.
- Последний sourcecounter RED286/1FAIL1m27s: фактическое elapsed200 вместо240. После фикса **финальный full NeoForge build +286/286 main +33/33 plant adapters +4/4 real Farmer’s Delight PASS3m32s/exit0**. Один producer test держит task30ticks, блокирует настоящим redstone40ticks и получает второй task через200active+40paused, XP1→2; животные живы.
- Итого17 новых normal required tests:5acquisition,9worker/API/provider,3AI. Реальные menu clicks/capabilities/native events/AI используются вместо PASS stubs. Full2400 live-конструкций этим не заявлены.

Команда финального gameplay/build прогона:

```bash
/workspace/reference/gradle-golems --offline --init-script tools/golems.gradle build -x test runGolemGameTest runGolemPlantAdapters runGolemCropIntegration
```

Cold world `build/golem-m59-nbt` создан новым, не заменяет пользовательские миры. Existing run использовал `-PgolemRestartScenario=nbt -PgolemRestartDirectory=golem-m59-nbt -PgolemRestartPhase=write|read`; WRITE выполнен с intermediatefullrun, READ отдельной JVM. Независимое read-only MCA/gzip SavedData чтение: [cold_disk_observations.json](cold_disk_observations.json), originalUUID/owner/37namediron/recoveryDiamond3/Gold11/Emerald23/3seals/0groundcopies. Не только exit0: фактические WRITE/READ assertions и отсутствие failure markers проверены.

## Реальный TCP Minecraft client

[results.json](results.json), [sourcehashes/proof inventory](proof-inventory.json), [independent disk](disk_observations.json), [точный использованный launcher](run_client.py).

- Dedicatedserver, forgeclientdev и Xvfb завершились **0/0/0**. Настоящий TCP127.0.0.1:25630, нет integrated server.
- Actual client recipe sync получил3 custom ingredient predicates и различил9 native ingot metadata комбинаций. Через handleInventoryMouseClick ушли3 настоящих C2S output takes:3brass/3thaumium/3void ingots оплачены, получены3+3+3 typedplates; сервер независимо подтвердил расход.
- RenderLivingEvent.Post реально наблюдал все6materials/5heads/5arms/4legs/4addons, соответствующие received packed design, visible variant children и textures/tint. Это representative selections, не2400 одновременно проверенных моделей.
- Тот же original wheeled worker поднял и доставил11fundedfixtureiron. Каждотиковая conservation на сервере, actual client moving position/raisedcarryarms/changedwheelrotation, затем обычный barrel menu подтвердил11.
- Read-only диск: семь исходных UUID/packeddesigns/owner; галерея отдельно сохраняет заданные fixture hand stacks1..6; actual worker hands/recovery пусты, grounditems0, barrel11, playerplates9 и ingots0. Начальное ошибочное ожидание пустых рук всей галереи исправлено по fixture: декоративный груз галереи не приписывается рабочей доставке.
- [gallery.png](gallery.png), [moving-cargo.png](moving-cargo.png), [deposited.png](deposited.png) — неизменённые raw1280×800 frames, SHA256 в results/proof inventory. Barrel частично закрывает void body; head и actual draw assertions видимы/проверены. Не выполнялся повтор ради косметической композиции кадра.

Подготовка argfiles выполнена через existing `prepareGolemClientRun prepareGolemCropServerRun -PgolemRemoteAddress=127.0.0.1:25630 -PgolemClientScenario=parts -PgolemCropServerDirectory=golem-parts-server-m59`; затем:

```bash
python3 analysis/evidence/golems/parallel_completion_2026_10_26/run_client.py --suffix m59-new --output /workspace/reference/golems-m59-parts-client-new
```

Launcher сохранён exact bytes реально использованного запуска и привязан к текущим cloud paths/JDK/Xvfb; для другой машины нужны её tools paths и existing ModDev argfiles. Он требует новый world directory, не перезаписывает существующий world. Нормальная игра не включает probe: property `thaumcraft.golemPartsClientProbe` по умолчанию false. Старые probes выключены явными JVM overrides. Rawlogs UTC, пользовательская дата МСК.

Renderer/probe7 source SHA256 совпадают с actual TCP snapshot; последующие server provider/counter fixes не меняли их и не выдаются за новый graphicsrun. Client scenario покрывает wheel/Pickup/Fill; combat/climbing/flight в этом новом графическом запуске не проверены. Галерея/funded inputs/research знания в отдельных arcane fixtures — явные предусловия, не полный survival walkthrough.

## Применение worker API

На owning server thread: `GolemWorker.bind(level, ownerUuid, workerUuid)`. Связь не force-loadит чанки; выгрузка/смерть/смена owner/level/replacement делает старую связь недействительной. getCarrying выдаёт копии, getProperties immutable construction view. holdItem использует действующую runtime convention: argument data неизменны, caller должен заменить свой storage возвращённым remainder и отказаться от переданного logical ownership; нельзя оставить и оригинал, и remainder как два источника. dropItem отделяет фактическую первую подходящую стопку, не спавнит мир и уплотняет HAULER slots. Mutation разделяет shared transfer scope с обычной логистикой.

Это trusted server-addon cooperation API, не authentication и не весь Minecraft1.12 ABI. Custom ISeal/task registration ещё не реализована; owner-bound safety policy и современные capacity/snapshot semantics явно отличаются от некоторых aliasing/oversized quirks старой EntityThaumcraftGolem.

## Границы готовности

Read-only remote snapshots: researcha33245c имеет read-only browser без generic C2S progression; alchemy633e088 не имеет Greatwood log/tree/levitator; infusion2e334a9 остаётся verified CrystalFarmer без biothaumic mind/Harvest/Butcher/Breaker infusion recipes. MindBiothaumic source: instability4/Cognitio50+Machina25/clockworkcenter+brain+complexmechanism; бесплатная подмена не добавлена.

Полный survival требует M07/M12/M06/M08 интеграции. Original art/full custom-seal API/широкий internet stress и inherited M43 split block/entity save hazard26iron/34gold не исправлены и не названы PASS. Индекс59/100 не повышался. Только feature/golems; common Gradle/CI/ROADMAP/main/чужие ветки не менялись. Единственный общий Core runner delta2строки документирован. PR#3 остаётся Draft, без merge/rebase/force/delete.

Итоговый JAR/CRC/Java21/EN-RU/hash — [jar.json](jar.json). Source code commits и публикационный HEAD — собственный [журнал](../../../lanes/golems.md).
