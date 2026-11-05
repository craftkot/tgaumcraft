# Питание инфузии через зеркала: доказательства 05.11.2026

Исходный снимок ветки `feature/infusion`:
`45d2f8e54cd4de4ceeb8e571a473d30552960669`. Среда: Java21.0.12.1+1,
Gradle9.2.1, Minecraft1.21.1, NeoForge21.1.250. Архив содержит реальные журналы
проверок рабочего снимка, зафиксированного этим commit; копирование журналов
не является повторным запуском. Raw stdout/stderr сохранены побайтно, включая
исходные trailing spaces. [Правила переноса](../../../infusion/MIRROR_SOURCES_RU.md)
описывают связь с оригинальным BETA26.

| Доказательство | Фактический результат |
|---|---|
| [native-red.log](native-red.log) | До реализации: запущены 5 native tests, 3 required failed, 2 отрицательных сценария прошли. `BUILD FAILED in 1m 2s`. |
| [source-volume-red.log](source-volume-red.log), [source-volume-green.log](source-volume-green.log) | Отдельный настоящий Core RED по порядку направленного поиска, затем `BETA26 infusion source volume: OK (172 assertions)`. |
| [core.log](core.log) | Полный Java21 Core runner, включая новые 172 assertions; финальный `CIRCLECI_THAUMCRAFT_CORE_PARITY_OK`. |
| [native48-first.log](native48-first.log) | Первый полный GREEN: 48/48 required GameTest, 19.19 s игрового тестирования; `BUILD SUCCESSFUL in 1m 23s`. |
| [native50-and-cold-read.log](native50-and-cold-read.log) | Финальные 50/50 required GameTest, 23.63 s игрового тестирования, и успешный cold READ в отдельной серверной JVM; общий `BUILD SUCCESSFUL in 1m 30s`. |
| [cold-write.log](cold-write.log) | Предшествующая WRITE JVM: реально оплачено 8/25 через зеркала, `mirrors=true`, штатное сохранение и остановка; `BUILD SUCCESSFUL in 50s`. |
| [disk-partial.json](disk-partial.json), [disk-complete.json](disk-complete.json) | Независимый разбор MCA/NBT до и после READ: взаимные связи зеркал, банки другого чанка `[2,10,5]` → `[0,0,0]`, один результат, тот же UUID и квитанции. |
| [jar-audit.json](jar-audit.json), [source-files.json](source-files.json) | Production JAR: 511 классов Java21, 1816239 bytes, SHA256 `a20e525bf2525acf9c3de7342e69fcaf6be0e88d6a9642aee93ae4d2ddbad571`; CRC, уникальные entries и отсутствие harness проверены. Source manifest связывает код с commit45d2f8e. |
| [ci-step.log](ci-step.log) | Локально выполнены два затронутых шага CI: jar bridge verifier и оплаченные Core-проверки, включая source volume 172. Это не полный удалённый CI. |
| [production-build-failed.log](production-build-failed.log) | Обычный `build` действительно FAILED за 20s: задача `:test` не обнаружила тесты при наличии test sources. |
| [production-build-final.log](production-build-final.log) | Стандартная для этого репозитория команда `build -x test` без init harness: `BUILD SUCCESSFUL in 7s`, все пять задач up-to-date. Это повторная сборка с готовыми результатами, не clean build. |

Первый native RED содержит конкретные ошибки: оплаченный ритуал не завершился
за 700 ticks, после исчерпания местной банки зеркало не списало удалённую
единицу, направленный поиск не списал эссенцию при facing DOWN. Два отрицательных
сценария запрета mirror relay и force-load прошли уже тогда. Это реальные
игровые failures, а не ошибка обнаружения тестов.

Финальные 50 тестов включают прежние 43 и семь сценариев зеркал: направления
и допустимые банки, приоритет местного источника и разорванная связь, запрет
relay, отсутствие force-load отсутствующего партнёра, полный оплаченный ритуал,
межмерное списание без затрагивания банки по тем же координатам в другом
измерении и обновление cache при повороте партнёра. Проверяются настоящие
банки/BlockEntity и one-unit debit. Нестабильность получает только вызывающее
зеркало. Research и конструкция установки явно задаются fixture; полный
Survival-путь игрока этим не подтверждается.

Cold UUID: `337bc494-31dd-4651-81e5-874b9da6d4a6`. WRITE сохранила Format3,
`Paid=8`, неоплаченные центральный/боковые входы и реальные остатки удалённых
банок. READ в новой JVM сначала проверила `paid=8` и восстановленную взаимную
связь без повторного связывания, затем завершила `paid=25 output=1 replayTicks=120
mirrors=true`. Матрица после завершения не содержит ledger, боковые слоты пусты,
их receipts имеют bits 1/2 того же UUID. Центральный `crystal_aer` единственный.
[audit_world.py](audit_world.py) непосредственно разбирает сектора MCA обоих
чанков; JSON содержит проверенные remote jar balances и состояния двух зеркал.

Последний журнал объединяет `runInfusionGameTest` и `runInfusionMirrorRestart`
одного Gradle invocation. Это **отдельные JVM и разные game directories**;
по временам в логе их работа пересекается. READ не следует после завершения
всех 50 тестов. WRITE была закончена раньше, её мир использует только READ.
Обычный `saveEverything(flush=true)` и штатный server stop не доказывают
hard-kill crash-атомарность jar→matrix→pedestal. Fixture сама загружает нужные
чанки; отдельный negative native test подтверждает, что production bridge
не загружает отсутствующий чанк партнёра.

Неудачный обычный `build` оставлен в архиве. Его `:test` не выполнил тесты и
не считается PASS. Успешный `build -x test` тоже не является результатом
JUnit-тестирования: standalone Core runner и настоящие native GameTest
выполнены отдельно и имеют собственные журналы выше. Настройки обнаружения
JUnit в рамках этой проверки не исправлялись.

Нового графического/TCP прогона механики зеркал нет. Предыдущие клиентские
кадры находятся в [архиве 30.10](../full_2026_10_30/README.md) и не подтверждают
эту новую механику. Полный M06 остаётся открытым: 35 исходных рецептов зависят
от внешнего контента, четыре golem recipes относятся к отдельному модулю,
полная research-прогрессия не пройдена, аварийная атомарность нескольких
BlockEntity не доказана.

Команды воспроизведения ниже выполняются из корня репозитория с Java21 и
Gradle9.2.1; `--offline` требует заполненного dependency cache. В executor
использовался `/workspace/reference/gradle-golems`. Это команды для повторения,
а не полная shell-история архивных запусков. Production-сборку выполнять без
test init script; затем отдельно подключать harness для native сценариев.

```bash
bash tools/run_core_parity.sh
gradle --offline --no-daemon build -x test
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionGameTest
```

Для focused проверки семи mirror tests:

```bash
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionGameTest \
  -PinfusionGameTestNamespaces=thaumcraft_infusion_mirror
```

Для cold проверки подготовить новый `build/infusion-mirror-restart` с
`eula=true`, localhost `server.properties`, `online-mode=false`,
`level-type=minecraft:flat`, `sync-chunk-writes=true` и view/simulation-distance 3.
Существующий оплаченный мир сохранить отдельно. WRITE запрещает повторную
инициализацию своего мира. Дождаться её завершения, снять partial audit,
затем запустить READ; до READ не удалять и не менять сохранённый мир.

```bash
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionMirrorRestart -PinfusionRestartPhase=write
python3 analysis/evidence/infusion/mirrors_2026_11_05/audit_world.py \
  build/infusion-mirror-restart/world partial > /tmp/infusion-mirror-partial.json
THAUMCRAFT_SMOKE=1 gradle --offline --no-daemon \
  --init-script tools/infusion.gradle runInfusionGameTest runInfusionMirrorRestart \
  -PinfusionRestartPhase=read
python3 analysis/evidence/infusion/mirrors_2026_11_05/audit_world.py \
  build/infusion-mirror-restart/world complete > /tmp/infusion-mirror-complete.json
```

`SHA256SUMS` охватывает только файлы этого каталога, кроме самого manifest.
Проверка сохранённых байтов без запуска игры:

```bash
cd analysis/evidence/infusion/mirrors_2026_11_05
sha256sum --check SHA256SUMS
```

После добавления JAR audit, source hash manifest или других evidence-файлов
пересоздать SHA256SUMS из этого же каталога и снова проверить:

```bash
python3 - <<'PY'
from pathlib import Path
import hashlib
root = Path('.')
files = sorted(p for p in root.rglob('*') if p.is_file() and p != root / 'SHA256SUMS')
(root / 'SHA256SUMS').write_text(''.join(
    f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.as_posix()}\n' for p in files))
PY
sha256sum --check SHA256SUMS
```
