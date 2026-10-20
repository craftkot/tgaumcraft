# M43 — фактическое аварийное сохранение, 20.10.2026

Java21/Minecraft1.21.1/NeoForge21.1.250, отдельные dedicated JVM, disposable flat worlds `build/golem-crash-{unsaved,saved,split}`. Обычные запуски не включают пробу. WRITE заканчивается **Runtime.halt(86)** без shutdown hooks: ожидаемый Gradle BUILD FAILED/exit86 означает намеренную аварию после реальных assertions. Ошибок компиляции в этих запусках нет. READ — другая JVM с нативной загрузкой region NBT.

Исходный checkpoint содержит одного настоящего голема с13 namediron, пустой целевой barrel и vanilla ItemEntity с17namedgold над пустой воронкой. Груз13 получен реальным GolemContainers.extractInto из source barrel. `ServerLevel.save(null,true,false)` сохраняет этот checkpoint. Затем настоящая AI выполняет Fill13, а `HopperBlockEntity.addItem` забирает реальный контрольный ItemEntity17. Воронка/голем — два самостоятельных entity→block пути.

| Авария | READ: что восстановилось | Результат |
|---|---|---|
| `unsaved`: после доставки без нового save | Последний полный checkpoint: груз13/target0, item17/hopper0. Восстановленная AI вновь доставила только исходные13; контроль17 собран один раз | Recovery PASS,100ticks без replay |
| `saved`: после нового полного native save | target13/пустые руки, hopper17/исходный item отсутствует | Recovery PASS,100ticks без replay |
| `split`: после native block-save prefix, до entity save | target13 **и старый груз13**; hopper17 **и старый item17** | **HAZARD26/34, не recovery PASS** |

`split` вызывает настоящий `ServerChunkCache.save(true)`, затем halt86. Это воспроизводимый префикс `ServerLevel.save`, который сначала пишет level SavedData, затем block chunks, затем entity manager. В этой пробе level metadata неизменны; entity write преднамеренно ещё не вызван. Это контролируемая граница сохранения, а не утверждение о частоте такого окна при обычных случайных падениях или сбое питания. Искусственного редактирования мирового NBT/копирования предметов нет. Разрыв проявляется и на ванильном контроле, поэтому ограничение шире переноса големов.

- [assertions.txt](assertions.txt) — выбранные оригинальные строки всех6 JVM-запусков, UTC. WRITE59/49/42s, READ51/53/46s. Полные логи `/workspace/reference/golems-crash-<case>-<phase>.log`.
- [disk_observations.json](disk_observations.json) — независимое read-only чтение реальных `region/r.0.0.mca` и `entities/r.0.0.mca` после READ. Нативный READ штатно сохраняет наблюдённый split world, поэтому обе копии действительно присутствуют на диске. Данные не исправлялись.

Воспроизведение: Java21 и существующий Gradle/cache; свежий отдельный case directory с eula/properties, локальный порт25617/25618/25619, flat, simulation/view2. Запустить `gradle --offline --init-script tools/golems.gradle -PgolemCrashCase=<case> -PgolemCrashPhase=write build -x test runGolemCrash`, потребовать CHECKPOINT+ARMED и именноexit86. Затем `-PgolemCrashPhase=read runGolemCrash`; для unsaved/saved требуются READ_ASSERTIONS_OK, для split только явный HAZARD26/34. Старый fixture не перезаписывается: повтор WRITE требует нового disposable world. Baseline233 production; последующие исходные изменения farm/click API не компилировались в READ/split с `-x compileJava -x processResources`.

Механизм не исправлен эвристическим удалением одинаковых предметов: одинаковые legal stacks не доказывают replay. Гарантия атомарности требует общего протокола сохранения/журнала всех участников (block entities, entities, player/mod inventories и callbacks), проверки порядка durable commits и внешних вмешательств. Это отдельная интеграционная работа, не существующее source BETA26 правило и не заявленный результат M43. Обычные graceful restart/live-unload гарантии предыдущих тестов остаются действительными; abrupt multi-file atomicity не объявляется.
