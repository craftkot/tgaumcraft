# M40 — реальные клиентские печати, 20.10.2026

Java21, Minecraft1.21.1, NeoForge21.1.250, LWJGL3.3.3, Xvfb1280×800 и Mesa llvmpipe. Снимки получены непосредственно из окна клиента, без редактирования. Изолированный creative world `Golem M10 seals camera verified`; opt-in fixture создаёт три настоящие серверные печати, а проверки клиента читают только S2C cache.

- [areas.png](areas.png): исходная Pickup область на полу и Fill face marker после исправления camera matrix.
- [wall_normal.png](wall_normal.png): колокольчик во второй руке, непрозрачная стена закрывает контуры.
- [wall_sneak.png](wall_sneak.png): реальное приседание показывает те же области сквозь стену.
- [edit.png](edit.png): реальные мышиные C2S клики X+ и color+, область4/1/2 и color5, обновлённый жёлтый контур позади меню.
- [removed.png](removed.png): настоящая печать-предмет после Shift+ПКМ, прежняя Pickup область исчезла, остальные области сохранены.
- [assertions.txt](assertions.txt): выбранные реальные строки исходного лога, время явно UTC; это результат runtime assertions, не вручную созданный PASS. Полный лог в рабочем окружении: `/workspace/reference/golems-seal-client-fixed.log`.

Воспроизведение: Java21, `gradle --offline --init-script tools/golems.gradle -PgolemClientScenario=seals build -x test runGolemClient`. Создать новый мир, не перезаписывать предыдущий fixture. После READY проверить контуры; F переносит bell в offhand и fixture ставит стену. Удержать Shift для through-depth, отпустить и F для обычного owner menu. Нажать X+ и color+ по одному разу; дождаться SERVER_EDIT/UPDATE_ASSERTIONS_OK. Escape; дождаться BELL_REMOVE_READY, удержать Shift достаточно для серверного crouch tick и ПКМ на верхней грани Pickup. Требуются SERVER_REMOVE (ровно один drop) и CLIENT_REMOVE markers. Escape→Сохранить и выйти→Выйти из игры.

Проверка завершилась BUILD SUCCESS7m23s, все измерения сохранены19:58:37UTC, клиент завершился19:58:57UTC. Native231/231 и Core39962/2901 проверены отдельно; полный сервер не повторялся ради чисто клиентского camera fix. Первый ранний run обнаружил неверные контуры и не считается успешным. Исходные текстуры/сетка/анимации/particles не портированы; текущие линии — provisional visualization. Это representative integrated client proof; удалённый multiplayer, все2400 живых конструкций и широкая graphics/API parity этим запуском не доказаны.
