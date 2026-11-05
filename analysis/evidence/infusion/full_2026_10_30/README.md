# Инфузия: доказательства 29–30.10.2026

Архив относится к исходному снимку `feature/infusion`
`a3392a0f171392ef9e61383592a04afbb4f66c56`: серверный runtime зафиксирован в
`9bf940f33800bd986a4bba76b4e050c4c3a21c54`, визуальный код — в
`4f5a862fa2754233e160714f80e9cd908796f4e8`, каталог — в `a3392a0`.
Проверки выполнялись до этих фиксаций на соответствующих рабочих снимках;
последовательность и границы каждого прогона указаны ниже. Архивация журналов
не является новым запуском тестов.

Среда: Minecraft 1.21.1, NeoForge 21.1.250, Gradle 9.2.1,
Eclipse Adoptium Java 21.0.12.1+1. **Полный M06 ещё не завершён.**

| Доказательство | Наблюдаемый результат |
|---|---|
| [core.log](core.log) | Полный Java21 Core runner, все перечисленные проверки OK и финальный `CIRCLECI_THAUMCRAFT_CORE_PARITY_OK`. В том числе CoreParity 10047, очередь 379, нестабильность 198, окружение 20, mitigation 4111, acquisition 1229 assertions. |
| [native43-final.log](native43-final.log) | Финальный серверный прогон: `All 43 required tests passed`, 14.50 s игрового тестирования, `BUILD SUCCESSFUL in 57s`. Включает две исправленные регрессии меню. |
| [native41-and-cold-write.log](native41-and-cold-write.log) | Предыдущий полный build с 41/41 required GameTest и отдельной WRITE JVM; `BUILD SUCCESSFUL in 2m 28s`, WRITE сохранила реально оплаченные 8/25. |
| [cold-read.log](cold-read.log) | Новая READ JVM загрузила paid=8 того же ритуала, завершила paid=25/output=1, выдержала 120 native ticks без повторной выдачи; `BUILD SUCCESSFUL in 1m 1s`. |
| [disk-partial.json](disk-partial.json), [disk-complete.json](disk-complete.json) | Независимый разбор сохранённых MCA/NBT: Format3, банки `[2,10,5]` → `[0,0,0]`, один `crystal_aer`, пустые боковые слоты с квитанциями bits 1/2 того же UUID, матрица `crafting=0` без ledger после завершения. |
| [results.json](results.json), [client-server.log](client-server.log), [client-client.log](client-client.log) | Настоящий TCP server + графический client + Xvfb: exit **0/0/0**, `verified=true`. Три реальных C2S клика, 25 jar debits, два side items, один output, 120 ticks без replay. |
| [crafting.png](crafting.png), [output.png](output.png) | Два raw-снимка Xvfb, снятые после 70/30 фактических production draw callbacks. Финальный клиентский итог: matrixDraws=143, outputDraws=118. SHA каждого кадра записан в `results.json`. |
| [source-catalog.log](source-catalog.log) | Проверка извлечения: 56 настоящих исходных рецептов, 11 display-only примеров; независимые golden cases прошли. |
| [workflow-checks.log](workflow-checks.log) | Локальные статические проверки существующего workflow и исполняемые Core-регрессии, финальный `ALL_WORKFLOW_STATIC_CHECKS_OK`. Это локальный результат, не статус удалённого CI. |
| [production-build.log](production-build.log), [jar-audit.json](jar-audit.json) | Обычный production build без init test harness: `BUILD SUCCESSFUL in 12s`; JAR 1 811 513 bytes, 508 Java21-классов (major 65), CRC OK, тестовые классы и blank templates исключены. |

SHA256 проверенного production JAR:
`9fca24c3c5abcd3348717e4629d8d023137a7b6ad8e08740121db27de81a8315`.
Сам JAR и полные миры в этот архив не включены. `SHA256SUMS` фиксирует только
файлы данного каталога, кроме самого manifest.

Финальные 43 GameTest проверяют оплаченные Crystal Farmer, динамические рецепты,
полные результаты и квитанции, реальные события нестабильности, стабилизацию,
зарядную сеть, arcane acquisition, повтор оплаты после отсутствующего ингредиента,
legacy checkpoint и отказ при изменённой идентичности рецепта. Семь исходных
Crystal Farmer проходят реальный caster, отказ без research, 25 единиц из банок,
один результат и 120 ticks без replay. Research затем явно задаётся fixture;
это не самостоятельное прохождение исследований игроком в Survival.

Cold UUID: `8e6d3e1d-3cd0-4641-b40a-4eb7cb37dd00`. WRITE делает обычный
`saveEverything(flush=true)` и штатный stop; READ работает в новой JVM и не
создаёт заново входы. Этот прогон предшествует последним исправлениям меню.
Он подтверждает обычное сохранение/перезапуск, но не hard-kill, chunk unload
или атомарность сохранения разных BlockEntity. Fixture удерживает свои чанки
загруженными. [audit_world.py](audit_world.py) читает сектор MCA напрямую;
`verified_jar_amounts` в архивных JSON — дополнительная выписка проверенных
балансов (повторный вывод скрипта содержит те же балансы внутри `jars`).

Клиентские кадры сняты 30.10 в 11:13 UTC после исправления atlas sprite
`caster_basic`, до исправлений серверного меню. Renderer, client events,
probe и atlas не менялись между `4f5a862` и `a3392a0`. После исправлений меню
прошёл отдельный финальный серверный прогон 43/43; новый графический прогон
этих исправлений не заявляется. Успешные client assertions проверяют canonical
sprite и реальные production draws. [client-before-atlas-fix.log](client-before-atlas-fix.log)
сохранён как предыдущий диагностический прогон. Собственный восьмичастный
renderer использует исходные формулы анимации; снимки подтверждают синхронизацию
и отрисовку, но не идентичность оригинальному artwork.

Две ошибки меню подтверждены настоящим RED в [workbench-red.log](workbench-red.log):
`partiallyFullInventoryRetainsEveryPaidShiftClickOutput` терял второй оплаченный
inlay при единственном свободном месте, а
`staleNormalPickupCannotGrantUnpaidOutputAfterSharedBenchRecipeChanges` выдавал
устаревший неоплаченный preview после смены рецепта общего верстака. Журнал
содержит обе assertion failures, `2 required tests failed` и `BUILD FAILED`.
Финальный `native43-final.log` содержит batch `infusion_workbench_regression`
из двух тестов и успешный итог всех 43. Исправления заново проверяют идентичность
preview перед выдачей и сохраняют оплаченный остаток частичного shift-click
выбросом в мир.

[workbench-discovery-failure.log](workbench-discovery-failure.log) — более ранняя
ошибка фильтра namespace: `No test functions were given!`. Несмотря на строку
`BUILD SUCCESSFUL`, тесты там не выполнялись. **Это ни игровой RED, ни PASS.**
После исправления discovery отдельный запуск дал настоящий RED выше.

Весь source catalog описан в [CATALOG_SOURCE_RU.md](../../../infusion/CATALOG_SOURCE_RU.md):
56 реальных регистраций (47 обычных, 8 enchantments, 1 RunicArmor), 11 исключённых
display examples, 128 legacy field mappings и пять acquisition definitions.
В этой ветке доступны семь встроенных Crystal Farmer и десять ресурсных рецептов
(восемь enchantments, RunicArmor, MirrorEssentia). Ещё четыре golem recipes
относятся к отдельному модулю; их предыдущая совместная проверка находится в
[архиве 27.10](../generic_2026_10_27/README.md) и не входит в эти 43 теста.
Оставшиеся **35 рецептов требуют настоящего внешнего контента** согласно
`beta26_dependencies.json`. Полная Survival research-прогрессия, эффекты всех
зачарованных инструментов/runic shield/Baubles и завершённая ecology модель
также не доказаны этим архивом. Общая crash-атомарность jar→matrix→pedestal
остаётся открытой; возможна потеря материалов при разновременном сохранении.

Команды ниже воспроизводят проверки на указанном source snapshot; это рабочие
команды, а не полная shell-история каждого архивного запуска. Запускать из корня
репозитория с Java21/Gradle9.2.1. `--offline` требует уже заполненного dependency
cache; в executor использовался `/workspace/reference/gradle-golems`. Для
исходного каталога нужны оригинальный JAR и javap-текст вне Git по путям,
указанным в `tools/extract_or_verify_infusion_catalog.py`.

```bash
bash tools/run_core_parity.sh
python3 tools/extract_or_verify_infusion_catalog.py \
  --javap /workspace/toolchain/jdk-21.0.12.1+1/bin/javap
```

Production-сборку следует делать в свежем рабочем каталоге без test init script.
Здесь `clean` гарантирует отсутствие результатов прошлой компиляции harness;
архивный 12-секундный build использовал готовые результаты и не был clean build.

```bash
gradle --offline --no-daemon clean build
sha256sum build/libs/thaumcraft-6.1.BETA26-port.stage9.jar
```

Затем native suite и, отдельно при необходимости, два menu regression tests:

```bash
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionGameTest
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionGameTest \
  -PinfusionGameTestNamespaces=thaumcraft_infusion_regression
```

Перед cold WRITE нужен новый `build/infusion-restart` с `eula=true` и локальным
`server.properties` (offline-mode, flat world, `sync-chunk-writes=true`,
view/simulation-distance 3). Существующий мир сохранить отдельно; не перезапускать
WRITE поверх оплаченного мира. Выполнять последовательно, дожидаясь остановки
каждой JVM; partial audit обязательно снять до READ:

```bash
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionRestart -PinfusionRestartPhase=write
python3 analysis/evidence/infusion/full_2026_10_30/audit_world.py \
  build/infusion-restart/world partial > /tmp/infusion-disk-partial.json
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionRestart -PinfusionRestartPhase=read
python3 analysis/evidence/infusion/full_2026_10_30/audit_world.py \
  build/infusion-restart/world complete > /tmp/infusion-disk-complete.json
```

Для графического TCP прогона [run_client.py](run_client.py) использует реальные
ModDev run arguments, Xvfb/GLX, software GL и ImageMagick `import`. В нём явно
заданы executor-пути ROOT/JAVA/PORTABLE, которые нужно адаптировать при переносе.
Каждый повтор требует нового suffix без существующего мира; `results.json`
фиксирует коды завершения всех трёх процессов и SHA сырых кадров.

```bash
gradle --offline --no-daemon --init-script tools/infusion.gradle \
  classes prepareInfusionClientRun prepareInfusionClientServerRun
python3 analysis/evidence/infusion/full_2026_10_30/run_client.py \
  --suffix full20261030-reproduce --port 25632 \
  --output /tmp/infusion-client-reproduce
```

Проверка целостности архива без игровых запусков:

```bash
cd analysis/evidence/infusion/full_2026_10_30
sha256sum --check SHA256SUMS
```
