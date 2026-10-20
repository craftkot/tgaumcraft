# M48 — реальный внешний crop mod

Официальный **Farmer’s Delight 1.21.1-1.3.4**, MIT, [Modrinth XTVZDOol](https://modrinth.com/mod/farmers-delight/version/XTVZDOol). Download проверен SHA512 из API; точные URL/hash/source SHA — [artifact.json](artifact.json). Исходники официальной ветки1.21 на указанном SHA декларируют1.3.4; CropBlock/seed IDs/TomatoBlock.useWithoutItem прочитаны до теста. JAR и исходники находятся внеGit, ресурсы мода не перепубликуются.

**4/4 required native GameTests PASS49s**, 21:38:36UTC; отдельный namespace/run, не часть основной248suite. [Реальные excerpts](assertions.txt), raw `/workspace/reference/golems-external-crops-native.log`.

- Реальные cabbages/loot table: cabbage1, named cabbageSeeds2 до посадки, actual pending item components/NBT decode, nativeBlockItem посадка young cabbage/remainingnamedseed1/XP2.
- Реальный onion с включённой AI: shared task claim→protected break→paid native replant, per-tick namedseed+plant=2, обычный урожай отдельно,40tick no replay.
- Реальный tomato empty-hand callback: native useWithoutItem создаёт tomato1..2, age3→0, real ironcargo13 не меняется, XP1/no replant.
- Настоящий NeoForge protection BreakEvent с owner fake-player: отмена оставляет ripe cabbage/seed2/no fruit/no intent/noXP.

Это opt-in test adapter, а не автоматическое добавление Farmer’s Delight crops в обычный мир. Прямой зависимости Java/общего Gradle от внешних классов нет; lookup требует действительный foreign registry entry, отсутствие JAR проваливает тест. Standard/clickable/seed scopes регистрируются только в fixtures и закрываются после исполнения. Никаких surrogate vanilla blocks, synthetic drops или PASS при отсутствии мода.

Воспроизведение: положить проверенный официальный JAR в `build/golem-crop-integration/mods/`, затем Java21/Gradle9.2.1 `gradle --offline --init-script tools/golems.gradle build -x test runGolemCropIntegration`. В обычный `runGolemGameTest` этот mod и namespace не добавляются. Собственный synthetic template скопирован из golem_test порта, оригинальная графика не используется. Raw first compile выявил protected getAgeProperty; исправлено на публичный native getStateForAge, затем четыре настоящих теста выполнены.

Пока проверены одна версия/мод/четыре сценария; произвольные non-BlockItem callbacks, все моды/все custom soils/сетевая графика и crash atomicity этим не закрыты. Следом отдельная foreign item/intent cold JVM-пара.
