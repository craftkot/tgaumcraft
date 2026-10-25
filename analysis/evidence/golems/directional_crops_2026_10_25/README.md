# M54 — направленные культуры и настоящий cold restart

25.10.2026 (МСК), Java21/Minecraft1.21.1/NeoForge21.1.250. Источник поведения: официальный BETA26 `SealHarvest.onTaskCompletion`, bytecode377–414 — non-IPlantable seed и исходный BlockDirectional сохраняют FACING как сторону опоры; поздний replant кликает `target.offset(support)` по обратной грани. Binary SHA256 и происхождение описаны в основном design; оригинальные binary/source/art в репозиторий не добавлялись.

`GolemHarvestRegistry.registerDirectionalSeed(crop, exactSeed, expectedPlant, supportProperty)` явно задаёт это соответствие современному блоку. Само наличие FACING не регистрирует культуру. Свойство должно принадлежать исходному состоянию; семя сравнивается с компонентами; последняя регистрация приоритетна, её закрытие восстанавливает прежнюю. Farm сохраняет прежний support ordinal и проверяет его по текущему зарегистрированному свойству перед работой и при NBT decode. Обычные семена остаются floor/DOWN, Cocoa сохраняет свой горизонтальный контракт. Используются настоящий native useOn, фиксированная оплата1 и существующее actual-stack recovery.

Восемь новых native GameTest: четыре горизонтальные стороны; placement→actual cursor transfer→exception с rollback воздуха/сохранением двух семян без лишнего XP; явная регистрация/неверное свойство/точные компоненты/LIFO; forged vertical NBT и live floor override без дебета; настоящий автономный Harvest→replant с30 успешными наблюдениями финального состояния. Fixture block и два non-BlockItem callback зарегистрированы только opt-in. Callback успеха сам не списывает семя и меняет компоненты своего фактического резерва: Farm всё равно оплачивает1, исходный остаток сохраняется.

Полный build **2m34s**, **30/30 optional callbacks**, **252/252 required**, **4/4 с настоящим Farmer’s Delight** PASS. Это разные namespace/run; их не складываем с Core assertions как один игровой тест.

Две отдельные server JVM в новом `build/golem-directional-restart-m54b`:

- WRITE **46s/exit0**: реальный Harvest ломает зрелый east-facing fixture block; в мире остаются точные named seed2, XP1, исходный UUID/owner и pending support=EAST. Этот specimen по своему реальному noLootTable не даёт плодов; фиктивный лут не создаётся.
- READ **54s/exit0**: с диска восстановлены тот же UUID/owner, seed2, XP1, AIR и east support intent/настоящий JUNGLE_LOG. Реальный автономный callback посадил age0/FACING=EAST и оплатил1; seed1/XP2/empty intent сохранялись100ticks без replay.
- [assertions.txt](assertions.txt) — реальные native/cold markers и build results.
- [disk_observations.json](disk_observations.json) — независимое read-only чтение entity/region MCA и gzip SavedData после shutdown: один исходный UUID, exact named seed1, XP2, age0/east, east jungle support, пустые руки/recovery кроме остатка1,0pending/0grounditems. Никакой правки world NBT.
- [jar.json](jar.json) — итоговый JAR SHA256 `45a6d767809701936ed4384ecbd7c3d1e93bd39bca320055ed165be9239a0413`, CRC PASS,727 Java21 classes, expected classes/unique EN-RU JSON PASS.

Первый отдельный каталог `golem-directional-restart-m54` был ошибочно обработан старым generic restart fixture и дал его GOLEM_RESTART_WRITE marker. Он **не считается directional PASS**; мир/лог сохранены. Добавлен один scenario-exclusion guard в собственный GolemRestartProbe, затем повторён настоящий directional WRITE/READ в новом каталоге. Полные логи: `/workspace/reference/golems-m54-native.log`, `golems-m54-directional-{write,read}-final.log`.

## Воспроизведение

Нужны Java21/Gradle9.2.1 и подготовленные Minecraft dependencies. Создать новый isolated server directory с eula=true и flat/offline/localhost25628/spawn-protection0 properties. Выполнить последовательно:

```bash
gradle --offline --init-script tools/golems.gradle build -x test runGolemPlantAdapters runGolemGameTest runGolemCropIntegration
gradle --offline --init-script tools/golems.gradle -PgolemRestartScenario=seeded_directional -PgolemRestartDirectory=golem-directional-restart-new -PgolemRestartPhase=write build -x test runGolemRestart
gradle --offline --init-script tools/golems.gradle -PgolemRestartScenario=seeded_directional -PgolemRestartDirectory=golem-directional-restart-new -PgolemRestartPhase=read runGolemRestart
```

Для crop integration нужен проверенный Farmer’s Delight binary в его isolated mods directory, см. прежний external crop protocol. Cold требует именно GOLEM_SEED_RESTART_WRITE/READ_ASSERTIONS_OK с `directionalSeed=true` и отсутствие ASSERTIONS_FAILED: один BUILD SUCCESS недостаточен.

Проверены собственный four-way specimen и прежние vanilla/Cocoa/FD регрессии. Произвольный foreign directional binary, six-way/vertical native callbacks и незагруженная боковая опора отдельно не проверены. Это явная современная API-проекция, не совместимость со всем старым ABI. Общий split-save crash hazard26/34 и arbitrary callback side effects остаются открыты.
