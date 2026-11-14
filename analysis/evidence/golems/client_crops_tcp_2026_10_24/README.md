# M52 — фермерский голем в настоящем TCP-клиенте

24.10.2026, Java21/Minecraft1.21.1/NeoForge21.1.250, Xvfb1280×800/Mesa. Две отдельные JVM, dedicated server127.0.0.1:25625 и обычный Minecraft-клиент. Client assertions читают только ClientLevel/ванильные entity/equipment/block packets, явно требуют отсутствие integrated server. Это один offline Dev client на localhost, не multi-user/internet/packet-loss proof.

Финальный новый мир `build/golem-crop-server-m52b`: **server BUILD SUCCESS3m7s**, client **exit0** после штатного Minecraft.stop. Source before600ticks/after200ticks только для воспроизводимых кадров. Логи явноUTC: initialserver16:52:23, clientactualowner/noAI/seed2/AGE7 16:52:24; initialrender16:52:26. ServerAIenabled16:52:52 → clientseed1/AGE0/10stableticks иfinalrender16:52:53; serverXP2/emptyintent/wheat1/conservation/100ticknoreplay16:52:58. Сервер штатно сохранился, disconnect→clientstop16:53:09/exit0.

- [initial.png](initial.png): необработанный кадр до действия, зрелая пшеница/реальный голем.
- [final.png](final.png): необработанный кадр после actual AI harvest→paid callback→youngwheat.
- [captures.json](captures.json): реальные render markers, после каждого frame наблюдения через1s сделан raw X11 screenshot.
- [assertions.txt](assertions.txt): настоящие server/client excerpts + наблюдённый exit status.
- [disk_observations.json](disk_observations.json): независимое read-only чтение region/entities/SavedData после server shutdown — originalUUID/oneworker/XP2/oneactualcallbackseed/AGE0/oneoriginalwheat/0groundcallbackseed/0pending.
- [jar.json](jar.json): CRC/718Java21classes/expectednewclasses/model/AT/uniqueENRU JSON, SHA256 `9e0cd1bc46003e88199c1f4d9ba7a3f3faa974528061e0be3cb50f6b7cfe9855`.

Fixture Item не наследует BlockItem, настоящий no-debit Item.useOn меняет компоненты оставленного резерва; Harvest реально оплачивает1издвухсемян. Client normal packets подтверждают exact originalnamedseed1 и прежний owner/UUID. Сервер каждыйtick проверяетseed+plant=2, реальный обычный урожай1, отсутствие копий семян/лишнихworkers; проверка не подменяется кадром. Probeitems толькоopt-in. Один modelJSON ссылается на vanilla wheat_seeds, чужая bitmap не копируется. Голем визуально остаётся прежней собственной provisionalwoodmodel: original mesh/parts/animations этим не перенесены.

Первый отдельный run достиг actualgameplay/assertions/frameproof, но внешнее `xdotool windowclose` разрушило X drawable и клиент завершилсяexit1/BadDrawable. Это ошибка закрытия тестового окна, не исправление механики голема. Первыеrawlogs/PNG сохранены `/workspace/reference/golems-m52-first/`; они не выдаются за финальный штатныйrun. Добавлен штатный stop после finalFrame+serverdisconnect, затем весьсценарий повторён в новом мире. Старый мир не редактировался/не перезаписывался.

## Воспроизведение

Нужны Java21/Gradle9.2.1, подготовленный X display/GL и Minecraft assets/cache. Создать новый `build/golem-crop-server-m52b` с eula/server.properties: localhost25625, offline creative flat, spawnprotection0/view2/simulation2. Каталог не должен содержать старую fixture world. Выполнить:

```bash
gradle --offline --init-script tools/golems.gradle -PgolemClientScenario=crops -PgolemRemoteAddress=127.0.0.1:25625 -PgolemCropServerDirectory=golem-crop-server-m52b -PgolemCropHoldTicks=200 build -x test prepareGolemClientRun runGolemCropServer
```

Клиент `build/golem-crop-client` запускается отдельной JVM через подготовленные ModDevargs, как в [проверенном M42 launcher](../client_seals_tcp_2026_10_20/README.md), меняется толькоcwd наgolem-crop-client. Обе JVM получают `thaumcraft.golemCropClientProbe=true` и `thaumcraft.golemPlantAdapterProbe=true` из своегоinitrun. Собственный GolemClientProbe пропускает scenario=crops, старые seal assertions ограниченыscenario=seals.

Нужны все INITIAL/FINAL_ASSERTIONS_OK, REMOTE_CONNECTION_OBSERVED, обаFRAME_OBSERVED и serverASSERTIONS_OK, отсутствиеFAILED и clientexit0. Rawcapture DISPLAY=:97 `import -window root initial.png/final.png` поmarker через1s; не закрывать X window принудительно. Послеservershutdownклиент штатно завершится сам. Полныефинальныелоги `/workspace/reference/golems-m52-final-server.log` и`golems-m52-client.log`; launcher/capture scripts сохраненывreference дляэтойсреды. No worldNBT editing. SourceNative22/248/FD4/Core иcoldUse — отдельные M51 доказательства, не выдаютсязаgraphicaltests.
