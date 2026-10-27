# Оплаченная инфузия: проверка 27.10.2026

Java 21.0.12, Gradle 9.2.1, Minecraft 1.21.1, NeoForge 21.1.250.
Рабочая ветка `feature/infusion`, начальный HEAD `2e334a967e7740067bbb18e65b4d2e81aead2283`.
Пользователь разрешил отдельную команду инфузии; `main` и чужая история не менялись.

## Свежие результаты

| Проверка | Результат |
|---|---|
| `bash tools/run_core_parity.sh`, Java 21 | полный runner, финальный marker, exit 0 |
| Full NeoForge build + native GameTest | 10/10 required, build 3m35s, exit 0 |
| 7 исходных Crystal Farmer | реальный caster, отказ без research, 25 единиц из настоящих банок, один output |
| Legacy formatless checkpoint | реально оплаченные 8 единиц, без Format/RecipeFingerprint, завершение без replay |
| Missing/altered recipe fingerprint | оба checkpoint отклонены, 30 native ticks без новых расходов/выдачи |
| Обычная остановка, новая JVM | WRITE paid8 → READ paid25/output1, 120 native ticks без replay, exit 0 |
| Независимый raw MCA audit | три банки пусты, один crystal_aer, side receipts bits1/2, матрица без ledger |

Каждый положительный GameTest наблюдает реальные native server ticks, фактические
jar balances и сохранённые BlockEntity NBT, а не только smoke-строки.
Research специально задан как fixture после проверки отказа без него.
Это не доказательство самостоятельной Survival-прогрессии игрока.

Окончательный cold UUID: `e4af369f-99cb-4f3d-8958-fb2741d13799`.
`cold-disk-audit.json` получен независимым разбором секторов MCA/NBT после остановки READ.
`source-sha256.json` связывает runtime и harness с проверенным снимком.
Сжатые `.log.gz` содержат настоящие полные stdout/stderr прогонов.

## Точные команды и воспроизведение

Установить Java 21/Gradle 9.2.1. В этом executor использовался wrapper
`/workspace/reference/gradle-golems` с общим dependency cache и настроенным прокси.

```bash
JAVA_HOME=/path/to/jdk21 PATH=/path/to/jdk21/bin:$PATH bash tools/run_core_parity.sh
THAUMCRAFT_SMOKE=1 gradle --offline --init-script tools/infusion.gradle \
  build -x test runInfusionGameTest runInfusionRestart -PinfusionRestartPhase=write
THAUMCRAFT_SMOKE=1 gradle --offline --init-script tools/infusion.gradle \
  runInfusionRestart -PinfusionRestartPhase=read
```

GameTest и WRITE в первом Gradle invocation запускаются отдельными JVM в разных
game directories; READ запускается только после завершения обоих. Для более
последовательного локального запуска допустимы отдельные invocation каждого task.
Перед WRITE нужен новый `build/infusion-restart` с `eula=true` и localhost
server.properties; старый мир сохранять отдельно. В проверке: port25578,
offline-mode, view/simulation-distance3. Manifest внутри мира запрещает повторную
инициализацию уже оплаченного WRITE. READ не создаёт входы и не чинит состояние.

Для четырёх golem recipes нужен общий source snapshot двух веток, без git merge.
Тесты имеют отдельный namespace и task `runGolemInfusionGameTest` из того же init script.
Итог этого source overlay:4/4 required PASS, full build1m25s, exit0. Harvest/Butcher/Break стоят по30, MindBiothaumic75; unrelated Aer7 сохраняется, каждый результат единственный после120 native ticks. Приложены полный combined log и source manifest. Это не git merge.

## Ревью и незакрытые границы

Ревью нашло Important: same-ID изменение рецепта могло использовать прежнюю оплату.
Исправление связывает checkpoint с каноническим SHA-256 полного recipe definition;
focused Core RED→GREEN и два native отрицательных сценария проверены.
После исправления новых Critical/Important в проверенных путях не найдено.

Не заявлены: графический клиент этого изменения, полная Survival-цепочка,
chunk unload/hard-kill, безубыточная атомарность нескольких BlockEntity,
полные instability events, весь исходный каталог, component-bearing outputs
и crafting remainders. Generic edge с одними base-cost1 при multiplier0.5
остаётся unsupported; текущие семь Crystal Farmer и четыре golem descriptor его не используют.
