# M53 — два настоящих TCP-клиента и изоляция владельцев

Игровой запуск 24.10.2026 (МСК), доказательства оформлены 25.10.2026. Java21, Minecraft1.21.1, NeoForge21.1.250, отдельный dedicated server и две независимые JVM Minecraft на Xvfb/Mesa 1280×800. Offline localhost, имена M53Alice/M53Bob, разные настоящие серверные UUID. Клиентские assertions читают только ClientLevel, публичный cache печатей и обычные menu DataSlots, требуют TCP и отсутствие integrated server.

Сервер фактически получил по одному чужому и собственному EntityInteract от обоих игроков. Чужая окраска оставила оба цвета0 и оба dye stack6; собственная применила source color15 и списала ровно один краситель. Реальные чужие клики колокольчиком по печати не открыли меню. Затем оба владельца одновременно открыли свои меню и отправили button81: обе priority0→1, разные серверные объекты меню при одинаковом числовом containerId1. Исходные owner/instance сохранились в публичной синхронизации.

Две locked-печати имели пересекающиеся области и разные точные фильтры. Для каждого груза чужой работник изначально находился ближе. Настоящий автономный ИИ передал13 named iron только работнику Alice и17 named gold только работнику Bob. Сервер проверял сумму ground+hands+recovery каждый tick, запрет чужого груза, ровно два исходных worker UUID и100 ticks без повторной выплаты. Оба клиента увидели точный финальный груз, owner metadata и реально отрисованные кадры с двумя големами.

- [assertions.txt](assertions.txt) — фактические фазовые server/client markers и штатное сохранение/остановка.
- [M53Alice.png](M53Alice.png), [M53Bob.png](M53Bob.png) — необработанные кадры; [captures.json](captures.json) связывает их с реальными AFTER_LEVEL markers.
- [disk_observations.json](disk_observations.json) — независимое read-only чтение сохранённых entity MCA и SavedData: два исходных UUID/владельца, цвета15, точные грузы13/17, priority1, пустые offhand/recovery и отсутствие ground items.
- [jar.json](jar.json) — JAR, на котором выполнена проверка M53: CRC,723 Java21 classes, SHA256.

Сервер записал `All dimensions are saved`, оба клиента вызвали штатный Minecraft.stop. **Коды завершения исходных exec-сессий не сохранились после паузы окружения; exit0 здесь не заявлен.** Игровые assertions и raw кадры сохранены. Это не internet/packet-loss/load-stress/cold two-client restart proof. Визуал големов остаётся собственной временной моделью.

Важное отличие от BETA26: исходный ItemGolemBell.onItemUseFirst и SealBaseContainer не обеспечивали такую серверную owner-проверку редактирования/снятия. Она является современным усилением защиты. Исходный доступ чужих големов к **unlocked** печатям сохранён; тест касается locked-настроек.

## Воспроизведение

Создать новый server directory (старый fixture не перезаписывать): eula=true, server.properties localhost25627/offline/max-players2/spawn-protection0/flat/view-distance2/simulation-distance2. Подготовить два X display и Java21/Gradle/assets:

```bash
gradle --offline --init-script tools/golems.gradle -PgolemClientScenario=owners -PgolemRemoteAddress=127.0.0.1:25627 -PgolemOwnerServerDirectory=golem-owner-server-new build -x test prepareGolemClientRun runGolemOwnerServer
```

После `Done` сервера, в другом процессе с нужными native graphics libraries:

```bash
python3 tools/run_golem_owner_clients.py --java /path/to/java21/bin/java --display-a :97 --display-b :98 --output-directory /path/to/owner-logs
```

Требовать все server phase assertions, два CLIENT_FINAL_ASSERTIONS_OK и два FINAL_FRAME_OBSERVED, отсутствие ASSERTIONS_FAILED. Launcher выводит коды обоих процессов; для нового запуска сохранить и этот stdout. Клиенты сами закроются после финального кадра и отключения сервером. Полные исходные логи этой проверки находятся в `/workspace/reference/golems-m53-server.log` и `golems-m53-clients/`; первый server launch использовал подготовленные ModDev server args и явный owner property вместо запуска дополнительной Gradle JVM.
