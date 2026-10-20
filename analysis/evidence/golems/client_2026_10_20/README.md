# Настоящий Minecraft-клиент — 20.10.2026

Minecraft1.21.1, NeoForge21.1.250, Java21, LWJGL3.3.3; portable Xvfb/Mesa, окно1280×800. Второй полный запуск после исправлений GUI/models: BUILD SUCCESS9m41s, штатное сохранение и выход. PNG сохранены непосредственно из X framebuffer без редактирования. Время ниже UTC.

- [Use](use.png): actual C2S emptyHandfalse→true, server assertion08:31:20; исправленная подпись запроса предметов.
- [Advanced Breaker](silk.png): actual C2S psilkfalse→true, server assertion08:35:00; помещающаяся подпись и vanilla localized tooltip.
- [Работник](worker.png): после actual wheeled Pickup→Fill11; server conservation assertion08:35:16. Исходный wheel mesh пока не портирован, показан provisional renderer.

Воспроизведение: Java21/Gradle9.2.1 и подготовленный Minecraft graphical environment; `gradle --init-script tools/golems.gradle runGolemClient`. В своём `build/golem-client` создать **новый отдельный тестовый мир**. Probe меняет только opt-in integrated world: создаёт creative fixture/площадку/11реальныхпредметов. Не открывать существующий игровой мир. В Use дважды нажать «…», включить «Пустая рука», Escape. В следующем меню Advanced Breaker включить Silk, Escape. Проверить три `GOLEM_CLIENT_*_ASSERTIONS_OK` и отсутствие `GOLEM_CLIENT_ASSERTIONS_FAILED`, штатно сохранить/выйти/закрыть клиент. Обычные client/dedicated server без property не создают fixture.

Логи текущей среды внеGit: `/workspace/reference/golems-client-verified.log`; первый запуск `/workspace/reference/golems-client-launch.log`. Existing warnings других подсистем base_metal/caster и offline auth не скрыты. Эти снимки не подтверждают весь M10, остальные GUI, survival progression, live unload или crash recovery.
