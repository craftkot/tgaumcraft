# Настоящий клиент: Advanced Guard и летающий стрелок

20.10.2026, Minecraft1.21.1 / NeoForge21.1.250 / Java21 / LWJGL3.3.3, portable Xvfb1280×800 / Mesa llvmpipe. Повторный боевой запуск **BUILD SUCCESS5m16s**, штатные Save & Quit и закрытие клиента. PNG — прямые необработанные снимки framebuffer, без монтажа или редактирования.

- [Guard](guard.png): реальные C2S pmobtrue→false и panimalfalse→true, pplayer остаётся false; server assertion15:01:14UTC. Tooltip объясняет исходный IAnimals overlap: фильтр включает также монстров/жителей.
- [Летающий стрелок](flying_ranged.png): включённый source FLYER/RANGED против настоящего Husk на отдельном блоке. Модель голема provisional, золотой предмет в руке — сохранённый реальный cargo7.
- [Смерть цели](native_kill.png): настоящая death animation/drop. Сервер наблюдал LivingDeathEvent с directEntity=GolemDart и исходным golem owner15:01:51UTC; затем проверил неизменные cargo7 и rankXP (нет выдуманного meleeXP8).

Actual renderer callback после super.render15:01:39UTC проверил client entity/размер .2/доступность texture, `GOLEM_CLIENT_DART_RENDER_ASSERTIONS_OK`. Это выполненный игровой render path, не startup-marker. Маленький движущийся дротик не обязан попадать в выбранный статический кадр. Используется provisional vanilla arrow texture, исходная Thaumcraft artwork/анимации не портированы.

Воспроизведение: подготовленный graphical environment, `gradle --init-script tools/golems.gradle -PgolemClientScenario=combat build -x test runGolemClient`. Создать **новый отдельный тестовый мир**, не открывать сохранение обычной игры. Opt-in probe создаёт creative площадку/одну Guard, перемещает игрока в законный8block радиус и открывает обычное owner menu. Один раз нажать «…», выключить «Монстры», включить «Животные», дождаться server toggle assertion. Escape закрывает меню и запускает actual autonomous fight. Проверить три assertions: GUARD_TOGGLES, DART_RENDER, FLYING_RANGED; нет GOLEM_CLIENT_ASSERTIONS_FAILED. Штатно сохранить/выйти/закрыть клиент. Creative fixture и отдельно предоставленный FLYER не доказывают survival acquisition отсутствующего исходного levitator.

Первый расширенный запуск6m50s прошёл реальные Use/Silk и wheeled Pickup→Fill11, затем fixture попытался открыть Guard с позиции за8block радиусом: обычная menu validation отказала. Исправлена только позиция тестового игрока; production permission guards не ослаблены. Повторный combat-only запуск не тратил время на уже пройденные старые GUI. Логи обоих запусков внеGit: `/workspace/reference/golems-combat-client.log`, `/workspace/reference/golems-combat-client-verified.log`. Существующие чужие base_metal/caster texture warnings, offline authentication warnings и временный worldgen lag не скрыты.

Обычный клиент/dedicated server без opt-in properties fixture не создаёт. Это проверенный конкретный бой и меню, не full M10/2400 live-конструкций/modAPI/stress test.
