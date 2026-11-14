# M56 — неактивная боковая опора, настоящая выгрузка и возобновление ИИ

25.10.2026 (МСК). Java21/Minecraft1.21.1/NeoForge21.1.250. Один новый opt-in `GolemSideSupportProbe` и собственный optional Gradle run. Production Registry/Farm/Core не изменились относительно9a19b39; исходный guard восстановлен дословно после отдельной mutation-проверки.

## Что действительно произошло

В далёком chunk960,960 fixture создал обычный FORCED31 ticket. Культура стоит у восточного края, её настоящий JUNGLE_LOG находится в соседнем chunk961,960. Нативный ticket gradient оставил source ENTITY_TICKING31, а side FULL32 без entity ticks и без forced-флага. Проверяются реальные public ChunkHolder/ServerLevel состояния, private trackers не подменяются. FULL на этом шаге **не называется выгруженным**.

1. Настоящий Harvest сломал зрелую east-facing культуру и оставил seed2/XP1/AIR/pending EAST1. При этой phase NoAI удерживает fixture, чтобы можно было явно запросить100 настоящих leases через тот же `GolemSeals.claimBlock`, которым пользуется goal.
2. Все100 запросов отклонены при наличии реальной queued replant task. Семена/XP/intent не изменились, side остался FULL32/inactive/forced=false.
3. Fixture снял свой единственный source ticket. Наблюдались оба `ChunkEvent.Unload`, `EntityLeaveLevel`, removal `UNLOADED_TO_CHUNK`, оба `getChunkNow=null` и отсутствие worker UUID в live manager.
4. Ещё40 server ticks оба чанка действительно отсутствовали; pending intent сохранился, production не загружал их. В этой phase используются только non-loading проверки и сериализация самого SavedData, без world block reads.
5. Fixture явно загрузил оба чанка обычными tickets. Наблюдался `EntityJoinLevelEvent.loadedFromDisk`, другой Java object с тем же UUID/owner/design, NoAI/seed2/XP1 и сохранённой EAST-обязанностью.
6. Включён настоящий ИИ. Goal сам завершил native non-BlockItem useOn: EAST/age0 plant1, исходный named seed1, XP2,0pending/empty offhand/recovery. Каждую sampled resume tick считаются обе руки и recovery с проверкой identity предмета. Финальное состояние наблюдалось100 ticks без replay, работник остался один.

Это один серверный процесс с настоящей native chunk/entity выгрузкой. Полный server cold restart — отдельное прежнее доказательство M54. Отдельный полностью unloaded adjacent chunk при остающемся entity-ticking crop здесь не выдуман: vanilla gradient требует загруженных соседей.

## Проверки и отрицательный контроль

- Первый green dedicated run:1m2s/exit0, все фактические phase markers.
- Mutation run в другом новом мире: временно убрана только проверка inactive support в `GolemFarm.eligible`. Probe действительно упал в phase2 на `Inactive side support must refuse the real candidate lease`. Это **ожидаемый отрицательный результат**, не успешный gameplay run. Исходный файл восстановлен exact bytes, `git diff --exit-code` подтвердил отсутствие production diff.
- Fresh-context read-only review: Critical/Important нет. Minor про offhand/identity в each-tick sum исправлен до финального запуска; normal-exit-on-logged-failure convention явно учтена проверкой markers.
- Финальный полный run **3m3s/exit0**: **33/33 optional +252/252 required +4/4 настоящего Farmer’s Delight**, затем весь side-support lifecycle снова прошёл в третьем новом мире.
- Свежий `bash tools/run_core_parity.sh` Java21:39962construction/2909task и весь runner PASS.

Доказательства: [assertions.txt](assertions.txt), [mutation.json](mutation.json), [disk_observations.json](disk_observations.json), [jar.json](jar.json). Read-only MCA/gzip audit после shutdown независимо подтвердил один исходный UUID, named seed1/XP2/NoAI=false, crop age0/EAST, настоящий восточный log,0pending/0grounditems/0forcedchunks. World NBT не редактировался. Итоговый JAR CRC/732Java21classes/unique EN-RU JSON PASS, SHA256 `67a08e82b05741022affcc4355aa5704cfd3221bdc67cace648b8240953c01e3`.

Полные raw logs: `/workspace/reference/golems-m56-{core,green,mutant,mutation-result,final}.log`. Мир mutation сохранён отдельно, остальные миры не перезаписывались.

## Воспроизведение

Создать новый `build/golem-side-support-new` с eula=true, flat/offline/localhost25629/spawn-protection0/view2/simulation2 properties. Не использовать пользовательский игровой мир.

```bash
gradle --offline --init-script tools/golems.gradle -PgolemSideSupportDirectory=golem-side-support-new build -x test runGolemSideSupport
```

Требовать INITIAL_READY, PAUSE_ASSERTIONS_OK, NATIVE_UNLOAD_OBSERVED, RELOADED_ASSERTIONS_OK, финальный SUPPORT_ASSERTIONS_OK и отсутствие SUPPORT_ASSERTIONS_FAILED. **Один BUILD SUCCESS/exit0 недостаточен:** как существующие probe, этот логирует assertion failure и штатно останавливает сервер. Mutation-контроль оценивался по ожидаемому failure marker/конкретной причине, а не по Gradle status.

Обычный default content не расширен; gated directional specimens переиспользованы. Новый graphical client/foreign binary/internet stress не запускался. Общий split-save crash hazard26/34 не исправлен этим тестом или snapshot recovery.
