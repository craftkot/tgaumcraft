# M46 — измеренная матрица полёта

Java21/Minecraft1.21.1/NeoForge21.1.250, отдельный настоящий GameTestServer. `runGolemFlightStress` включает только namespace `thaumcraft_golem_stress`; обычный `runGolemGameTest` по-прежнему проверяет245основных сценариев. Дополнительный NBT template — копия собственного синтетического12×10×12 template проекта, без оригинальных ресурсов BETA26.

32проверки: seeds0..15 × открытый путь/непрозрачная стена высотой4 блока. Каждый enabled flyer сам назначает owner-locked Breaker task, строит настоящий3D path, сохраняет collisions/NoGravity, добирается до плавающей руды и реально разрушает её: ровноdiamond1/XP1. ИИ/контроллер/навигация не менялись; меняются только fixture RNG и геометрия. У каждой копии отдельный owner, чужие задания недоступны.

**32/32required PASS / BUILD SUCCESS45s**, `golems-flight-obstacle-matrix.log`,21:20:20UTC. Открытый путь57–76 игровых тиков, стена74–354. Seed9со стеной реально завершился на354-м тике, после pending-observation350. Бюджет1100 выбран как отдельное наблюдение восстановления, обычный representative350tick test не ослаблялся. В GameTestServer время тиков ускорено; это игровое число тиков, не wall-clock latency обычного сервера. Прежний unseeded airBreaker timeout из M40 не объявляется задним числом успешным или полностью объяснённым этим другим fixture.

- [timings.json](timings.json): точные результаты всех32пар, автоматически извлечены из фактических completion logs.
- [assertions.txt](assertions.txt): оригинальные pending/completion/required-pass/build строки, UTC.
- Первый отдельный16seed open-only run тоже16/16PASS47s; лог сохранён `/workspace/reference/golems-flight-direct-16.log`.

Воспроизведение: Java21, `gradle --offline --init-script tools/golems.gradle build -x test runGolemFlightStress`. Нормальный245suite запускается отдельно. Эта матрица расширяет representative coverage, но не доказывает все seeds/геометрии/модовые fluids/collision shapes/активные чужие чанки и не означает2400живых конструкций.
