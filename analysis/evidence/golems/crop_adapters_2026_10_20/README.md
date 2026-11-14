# M44–M45 — настоящий crop adapter и сохранение семян

Java21/Core39962construction/2909taskPASS. Source CropUtils/SealHarvest/ThaumcraftApi исследованы по официальному BETA26JAR; exact click face дополнительно сверена с official MC1.12.2 EnumFacing. Native registry — modern state-based API, без default новых культур и без1.12binaryABI.

Полный239/239native1m23s для clickable callback/cursor recovery; полный245/245native1m33s для standard/stacked/seed mapping; финальный245/245native1m18s дополнительно проверяет старый pendingNBT без seed/support/farmland и wrong-type seed guard. Эти12новых сценариев выполняют реальные block use/break/placement/events/AI/callbacks, не только копирование моделей.

Два отдельных dedicated WRITE/READ proof:

- Clickable52s/45s: настоящий click→fruit2→Pickup→disk; новый процесс регистрирует тот же adapter и восстанавливает originalUUID/owner/cargo2/rank/cropage1/3seals. Redstone release→nativeFill2, каждыйtick total2,100tick без replay/одинworker.
- Seeded51s/46s: actual standardbreak→3ordinaryfruit и durable pending intent, но2named seeds остаются в руках. Новый процесс восстанавливает canonicalcropID/exactseedcomponents/support/soil, seed2/rank1/NoAItrue. EnabledAI выполняет nativeBlockItem placement, расходует1namedseed, оставляет1; seedinventory+actualplant=2 каждыйtick, ordinaryfruit3 не меняются,100tick no replay/oneworker/pendingcleared.

[Оригинальные excerpts](assertions.txt), полный лог `/workspace/reference/golems-{clickable,seed}-restart-{write,read}.log`. Cold fixtures используют собственные observation поля в entity persistentData только для ранее измеренного числа настоящего fruit; они не производят предметы. Randomticks0 только для воспроизводимого состояния растений.

Воспроизведение native: `gradle --offline --init-script tools/golems.gradle build -x test runGolemGameTest`. Cold: свежие отдельные `build/golem-clickable-restart`/`build/golem-seed-restart`, eula/server.properties с localhost25620/25621; `-PgolemRestartScenario=clickable` или `seeded`, сначала `-PgolemRestartPhase=write build -x test runGolemRestart`, затем `-PgolemRestartPhase=read runGolemRestart`. Требуются явные WRITE/READ_ASSERTIONS_OK, BUILD SUCCESS сам по себе недостаточен. Старые fixture worlds не перезаписывать.

Sweet berries/Bamboo — явно зарегистрированные native adapter specimens, отсутствовавшие в1.12, а не новый default Thaumcraftcontent. Custom non-BlockItem/cross-block planting callbacks, конкретные foreign mod binaries и полноценная external API parity не проверены. Свежий graphical client после server-only M44/M45 не запускался; последние реальные integrated/TCP graphical proofs — M40/M42.


M47 расширяет seed mapping на другой Native BlockItem plant: исходная культура и ожидаемый результат сохраняются раздельно. Full **248/248 native PASS1m26s**; три новых сценария проверяют actual carrot→wheat paid placement, pumpkin→stem enrollment и поддельный/отсутствующий результат. Legacy same-block intent остаётся читаемым, cross-block без нового `plant` отвергается.

Отдельный **seeded_cross WRITE55s/READ46s PASS** (21:34:13/21:35:23UTC): настоящий carrot break оставил5ordinarycarrots/2namedwheatseeds/XP1/один originalUUID и pending `plant=minecraft:wheat`. Новая JVM восстановила intent/owner/NoAI/cargo; native AI посадила wheatAGE0, ровно1namedseed остался, ordinarycarrots5 не изменились, каждыйtick seed+plant=2,100tick no replay/одинworker. Это устраняет прежнее same-block ограничение M45; non-BlockItem callbacks и foreign binaries этим не проверены.

Воспроизведение cross-block: отдельный свежий `build/golem-seed-cross-restart`, localhost25622/eula, `-PgolemRestartScenario=seeded_cross`, фазы write/read как выше. Логи `golems-seed-cross-restart-{write,read}.log` внеGit. Тестовый мир не редактировался и старые worlds не перезаписывались.
