# Направление: инфузия / feature/infusion

**Статус:** 🟡 M06 — обобщённый оплаченный runtime; 7 Crystal Farmer и cold JVM проверены, полный M06/Survival ещё не завершён.
**Основной план:** [ROADMAP_RU.md](../../ROADMAP_RU.md).
**Общий аудит:** [PROGRESS_AUDIT_2026_10_16.md](../PROGRESS_AUDIT_2026_10_16.md) — M06 5/11, проект 59/100, без пересчёта.

## Проверено в исходном коде

- Настоящая активация матрицы Basic Caster на собранной структуре.
- Оригинальные семь Crystal Farmer: центр — аспектный кристалл, боковые пьедесталы — пшеничные семена и Salis Mundus; исходная стоимость при множителе 1.0 — **25** эссенции (10 конкретного аспекта, 10 Vitreus, 5 Vinculum).
- Серверный адаптер списывает по одной единице из реально находящихся в мире банок. Пьедесталы удаляют настоящие ингредиенты только после countdown. Результат помещается на центральный пьедестал через однократную reservation/checkpoint.
- Свежие серверные доказательства 27.10.2026 перечислены ниже; полная Survival-прогрессия остаётся отдельной непроверенной границей. Атомарность сохранения между несколькими BlockEntity пока не гарантирована; авария между сохранениями может остановить ритуал или потерять материалы.

## 18.10.2026 — первый подход M06.1

Исходные HEAD: main и feature/infusion `a4280834adc5a20b6951098af937d30ca4aeca56`.

Коммиты в feature/infusion:
1. `7e34df9` — `LegacyInfusionRitualLedger.restoreVerifiedCrystalFarmer` сверяет ключи и точные количества первоначальной эссенции с оригинальным каталожным рецептом и множителем; вычислительно согласованные, но неверные цены отвергаются.
2. `9e24a8a` — NeoForge матрица сохраняет `RecipeCostMult` именно на старте. При загрузке требует полный ledger, фазу завершения, masks/countdown и настоящую цену; несогласованное или неполное состояние fail-closed. Старые checkpoints без поля множителя пробуют `costMult` (но также обязаны пройти проверку реальной цены).
3. `bca4890` — Core-регрессии для всех 7 рецептов при x0.5/x1/x1.35; ожидаемые успешные и отказные случаи (пока не выполнены в CI).
4. `72b49e8` — серверный pure-core прогресс запрещает недостижимое сохранённое состояние «Salis уже съеден, семена нет».
5. `d9ac3af` — два отрицательных теста недостижимой стадии ингредиентов.

Draft PR #1: https://github.com/craftkot/tgaumcraft/pull/1 — **не сливать без отдельного согласия**.

CI: базовый GitHub Actions на исходном коммите #748 был failure; на первом патче #756 failure (шаги проверки отсутствуют в доступном результате). CircleCI commit status `ci/circleci: verify-connection` сообщает SUCCESS (последняя ссылка на момент проверки [#226](https://circleci.com/gh/craftkot/tgaumcraft/226)), но **это не CoreParity, не полная NeoForge сборка и не игровой тест**. Внешний CircleCI job-log здесь недоступен, поэтому результаты новых тестов **не подтверждены**.

## Оставшиеся шаги

1. 🟡 **M06.1** — после локальных Core/full-build/paid-server/cold PASS27.10 пройти настоящую Survival-цепочку получения исследования и графическую клиентскую приёмку; fixture research не заменяет её.
2. **M06.1** — подтвердить неконсистентные checkpoint-сценарии с фактическим world save/unload/reload. Устранить потери материалов в окне нескольких BlockEntity; консервативный STALLED безопаснее дюпа, но ещё не соответствует полному критерию.
3. **M06.2** — нестабильность, аварии и побочные эффекты исходного TileInfusionMatrix.
4. **M06.3** — остальные оригинальные рецепты, эффекты/звук/анимации.
5. **M06.4** — настоящая игра Survival, сохранение, перезагрузка, multiplayer.
6. **PR в main** — на проверку; merge только после явного разрешения владельца.

## 18.10.2026 — второй подход M06.1 (продолжение по «П»)

Fresh HEAD: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=41eb1c7528606489f0017c4c40b6f9e44df01bea` на старте подхода. Перечитаны roadmap/AGENTS/parallel workflow/audit/этот журнал.

Коммиты (только `feature/infusion`):
- `b4c8c29`: матрица проверяет `level.hasChunkAt` прежде чем читать unloaded world blocks при `validLocation`, `nearbyPedestals` и расчёте стабильности. Нет намеренного force-load.
- `98601b0`: opt-in серверные `TC_SMOKE_INFUSION_CRYSTAL_*` маркеры исключительно после успешного исследования/старта, реальной полной оплаты, фактического удаления обоих side items и завершённой замены центра. Это **инструмент** для будущего клиентского теста, не доказательство прогона.
- `b84fa94`, `f5f03ad`, `eb35870`: проверка `consumedMask` против настоящего текущего содержимого педесталов на каждом тике, в том числе после загрузки: сохранённый флаг оплаты при восстановленном ингредиенте запрещает выдачу результата. Добавлены положительные/отрицательные core-регрессии.
- `4232bce`: позиции именно двух сопоставленных изначально боковых пьедесталов берутся из подтверждённого `RecipeMatcher`, фиксируются в NBT и после перезагрузки проверяются на геометрию/загрузку чанков/соответствие ингредиентов. Продвижение ритуала больше не ищет замену ингредиентам на другом пьедестале, что закрывает обход с незагруженным исходным чанком. При отсутствии координат у старого **незавершённого** ритуала он будет остановлен fail-closed; до миграции/восстановления без риска дюпа ещё не дошли.
- `209360e`: 7× проверка индексов соответствия `RecipeMatcher` для обратного порядка реальных боковых ингредиентов (`[1,0]`).

Проверки:
- Конфигурация `.circleci/config.yml`: стандартная задача `verify-connection` **действительно запускает `bash tools/run_core_parity.sh`**, в т.ч. `InfusionRitualLedgerParityTest`, `CrystalFarmerIngredientProgressTest` и `CrystalFarmerParityTest`. Ранее ошибочное утверждение «только проверка связи» исправлено.
- CircleCI `[#236](https://circleci.com/gh/craftkot/tgaumcraft/236)`, `[#238](https://circleci.com/gh/craftkot/tgaumcraft/238)`, `[#245](https://circleci.com/gh/craftkot/tgaumcraft/245)`, `[#248](https://circleci.com/gh/craftkot/tgaumcraft/248)`, **`[#249](https://circleci.com/gh/craftkot/tgaumcraft/249)` SUCCESS** по GitHub commit statuses. Core compile/tests подтверждены в этом job; полная **NeoForge 1.21.1 компиляция, dedicated server и реальный Minecraft client/Survival не запускались** в рамках текущего подхода.
- GitHub Actions verify на исходных коммитах завершался failure (в репозитории отмечен недоступный GitHub billing), не считать этим опровержение CircleCI.

Оставшиеся существенные риски: невозможно гарантировать атомарность `jar debit -> matrix ledger` и `pedestal removal -> matrix mask` между разными BlockEntity при аварийном сохранении; устранён конкретный сценарий бесплатного результата из восстановившегося pedestal, но возможна остановка и потеря уже оплаченных материалов. Нужен реальный сериализованный **world save -> unload/reload -> server restart** и отдельная стратегия safe recovery, не маскировать как полную защиту от дюпов. Реальный BETA26 ритуал не доказан в Survival.

**Статус:** M06.1 остаётся 🟡, 5/11 инфузия, 59/100 порт. [Draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1) не сливать без разрешения.

## 18.10.2026 — третий подход M06.1 (по «П»)

Fresh heads: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=fd10ad938abb5685c3da9939f6fdbba45ae887ac` на старте. Перечитаны `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, аудит, журнал, реальные классы матрицы, педестала, банка-моста и чекпоинта.

**Исправлен случай аварийного завершения: результат уже записан, матрица ещё в RESERVED.**
- `208b8ea`: pure-core чекпоинт `acknowledgeAlreadyPublished` подтверждает **только уже существующий предмет**, не повторяет выработку.
- `bd293ab`: core-регрессии на безопасное подтверждение и обязательный отказ при неоплате/нехватке ингредиентов/отсутствии результата.
- `c78cd2f`: сервер на STALLED сравнивает исходные две физические позиции пьедесталов, пустые слоты после оплаты, стоимость, состав рецепта и **настоящий результат на центральном пьедестале**. Только тогда очищает зависший ledger; иначе оставляет STALLED. Новый маркер `TC_SMOKE_INFUSION_CRYSTAL_SAVED_OUTPUT_ACKNOWLEDGED` означает признание результата, **не повторную выдачу**.
- `e94e179`, `a32ebcd`: у `RESERVED`/`STALLED` недопустимы неоплаченная эссенция и оставшиеся ингредиенты; добавлены отрицательные core-тесты.
- `e688a62`: live матрица не продолжает списывать ресурсы в неразрешённой фазе завершения и отвергает противоречивый NBT. Обычный `WAITING` сохраняет исходный алгоритм.

**Проверки:** CircleCI [#258](https://circleci.com/gh/craftkot/tgaumcraft/258), [#260](https://circleci.com/gh/craftkot/tgaumcraft/260), [#263](https://circleci.com/gh/craftkot/tgaumcraft/263), **[#264](https://circleci.com/gh/craftkot/tgaumcraft/264) SUCCESS** (статусы GitHub `ci/circleci: verify-connection`). Конфигурация job действительно запускает `tools/run_core_parity.sh` (чистый Java21 и все infusions Core-тесты). **Не полная NeoForge-сборка**: `run_compile` по умолчанию `false` и не была активирована в этом подходе. **Нет доказанного in-game Survival и save/restart**. GitHub Actions run failures не заменяют эти проверки.

**Сохранившийся риск:** в разных чанках сохранение банка→матрица→боковые пьедесталы не является одной атомарной транзакцией. Когда результат после аварии отсутствует, STALLED **не вправе его выпускать повторно**: возможна потеря предметов, нужен безопасный recovery-процесс или специализированный журнал транзакций. Старые незавершённые ledgers без координат исходных пьедесталов отвергаются. Приёмка M06.1 не достигнута.

**Текущий статус:** M06.1 🟡, раздел M06 5/11, проект 59/100, [draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1). Не выполнять merge без явного разрешения.

## 18.10.2026 — четвёртый подход M06.1 (по «П»)

Повторная синхронизация: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, на старте `feature/infusion=38695058713ad247ecc824c359469b748aaaf398`. Fresh-read main ROADMAP, AGENTS, PARALLEL_WORKFLOW, аудит, собственный журнал, свежие GitHub Actions и живые исходники матрицы, рецепта, планировщика.

**Изменения (только своя NeoForge матрица):**
1. `cef5f22` — при реальном исследованном `Crystal Farmer` старте сохранена полная копия исходного центрального `ItemStack` со всеми data components. Пока ритуал активен, матрица сравнивает сам `ItemStack` через `ItemStack.isSameItemSameComponents`, а не только общий предмет/аспект; состояние центра сохраняется через используемый реальным пьедесталом `ContainerHelper` в `PortInfusionLedger.OriginalCenterInput`. Reload без полного snapshots останавливает потенциально испорченный/старый незавершённый ритуал fail-closed; не генерирует бесплатных предметов.
2. `689f445` — на повторной `loadAdditional` сбрасывается исходный центральный снимок до чтения нового payload (предотвращение остаточного состояния на повторном load).
3. `825d404` — при restore проверяется, что сохранённое `recipeinst` равно исходной нестабильности зарегистрированного рецепта: Crystal Flux 4, остальные шесть 0. Подделка/порча NBT не отключает исходную нестабильность.

**Контроль:** CircleCI [#270 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/270), **[#278 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/278)** (core + asset smoke в `verify-connection`; чистая Java21 Core parity). В отличие от Core тестов, этот подход изменял **NeoForge runtime класс**, поэтому **полная NeoForge компиляция остаётся необходимой и НЕ доказана**. Отдельная CircleCI pipeline опция `run_compile=true` не активирована: API-триггер этого параметра в доступных инструментах отсутствует; общие `.circleci/config.yml`, Gradle и CI запрещено править в чужих ветках без координации. GitHub Actions run #816 FAILURE, лог 404 BlobNotFound, не даёт доказательств настоящей компиляции.

Непроработанное: safe resume без утраты материалов при разновременном сохранении jar/matrix/pedestals; full 1.21.1 NeoForge compile, server, survival ritual, physical world-save/chunk-unload/restart. Эти требования не называются выполненными. Продолжается 🟡 M06.1, PR #1 draft; общий индекс 59/100, M06 5/11.

## 18.10.2026 — пятый подход M06.1 (по «П»)

Fresh reads: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=5bba99daed3d0f51c913e4acbba90ed5cc8f33c8` на старте; изучены `ROADMAP_RU.md`, `AGENTS.md`, `PARALLEL_WORKFLOW_RU.md`, аудит, этот журнал, Core ingredient/essentia cycle, NeoForge matrix, GitHub Actions и CircleCI статусы. Ветка `main` не изменялась.

Изменения только в своём M06 коде:
- `8c85f1a`, `8378d07` — реальное начало ритуала сохраняет не только координаты двух пьедесталов, но и **полные исходные one-item ItemStack семян и Salis Mundus**, с data components. Снимки сохраняются в `PortInfusionLedger.OriginalSideInputs` через тот же `ContainerHelper`, что у пьедестала; `realInputsRemain` требует точного совпадения **до** списания каждого из двух ингредиентов. После списания требует пустой закреплённый педестал и оплаченный соответствующий `consumedMask`, не принимает другой экземпляр. Старые/испорченные незаконченные сохранения без новых полей останавливаются fail-closed.
- `3ed0083` — Core-тест `CrystalFarmerFinishCheckpointTest` расширен **21 сценарием** (`7` исходных рецептов × `0.5`, `1.0`, `1.35`): реальная каталожная цена, последовательность всех unit debits в immutable ledger, сохранение/восстановление оплаченной книги, исходная пятицикловая оплата двух side items, сохранённая RESERVED→STALLED и только подтверждение **уже существующего** результата без второй выдачи. Сценарии тестируют чистую Java-модель; не имитируют реальный Minecraft save.

CircleCI `verify-connection` действительно выполняет `bash tools/run_core_parity.sh` для всех M06 pure core suites: [#282 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/282), **[#289 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/289)**, по GitHub combined status. **Полный NeoForge 1.21.1 compile, server smoke, игровой Survival ритуал, unload/reload и reboot НЕ выполнены**: новый NeoForge код статически просмотрен, но compiled/runtime гарантий нет. GitHub Actions отказывает на verify до доступных логов, CircleCI `run_compile` по умолчанию выключен.

Приёмка M06.1 не завершена: ещё требуется подлинное end-to-end игровое доказательство jar balance, side & center inventory, а также crash-consistent recovery между BlockEntity; fail-closed рестарты могут терять ресурсы. Статусы: M06.1 🟡, инфузия 5/11, весь проект 59/100, [черновой PR #1](https://github.com/craftkot/tgaumcraft/pull/1) **НЕ сливать без разрешения владельца**.

## 18.10.2026 — шестой подход M06.1 (по «П»)

Fresh HEAD перед работой: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=90cdf69ff0e426a2883fcbad4615bbc2fcf3e034`. Прочитаны `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, собственный lane-journal, аудит, живые NeoForge classes и Core tests, последние Actions и CircleCI statuses.

**M06.1 — компенсация уже списанного бокового предмета после неатомарного save, без повторной оплаты:**
- `958c5ed`, `b8cab64` — Core прогресс принимает сохранённую операцию списания только если фактический исходный пьедестал **пуст** и есть подлинный receipt; запрещает неподходящий bit, сломанный countdown, преждевременное/повторное подтверждение. 
- `cd0e48d` — реальный `PedestalBlockEntity` теперь меняет слот на `ItemStack.EMPTY` и сохраняет `PortInfusionRemovalRitual` + `PortInfusionRemovalBit` **одним BlockEntity state update**; все внешние вставки/изъятия очищают receipt. Рестарт восстанавливает только валидные записи, если слот действительно пуст.
- `fdc0d9a` — матрица создаёт и сохраняет `RitualId` при настоящем caster/start; после перезагрузки, при `countdown=1` и подтверждённой ранее оплате всей эссенции, сопоставляет исходные физические позиции, реальный центральный кристалл, receipt c тем же UUID, ожидаемый bit и состояние **второго** пьедестала. Только тогда сдвигает immutable consumed-mask, **не списывая/не создавая предмет**. Новые серверные smoke-маркеры `INFUSION_CRYSTAL_SEEDS_DEBIT_RECOVERED` / `INFUSION_CRYSTAL_SALIS_DEBIT_RECOVERED`.
- `7fc27d4` — исправлен обнаруженный устаревающий cache `originalSeedInput` / `originalSalisInput` при многократной `loadAdditional`; обе стороны теперь очищаются перед чтением нового NBT.
- `cb9374d`, `91367aa`, `a1a4cf8`, `2703c55`, `9ef774c` — receipt вынесен в тестируемый Core record `LegacyCrystalFarmerPedestalReceipt`, реальный пьедестал использует его проверку. Core tests: разные UUID, item bits, пустой/непустой слот, недопустимые записи, одноразовость компенсации семян и Salis. Исправлена несовместимость типа исключения в негативном тесте: `#313 FAILURE` из-за `NullPointerException` вместо ожидаемого `IllegalArgumentException`; после фикса `#316 SUCCESS` и `#317 SUCCESS`.

**Проверка:** [CircleCI #317 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/317), `verify-connection` выполняет `bash tools/run_core_parity.sh` (все чистые Java21 тесты). NeoForge runtime **не компилировался** в полноценной pipeline (`run_compile=false`), game world-save/restart **не проводился**. Никакого утверждения «без дюпов и потерь доказано в мире» пока нет. GitHub Actions verification падает из-за отсутствующих/недоступных runner logs.

**Ограничение новой компенсации:** только узкое окно «пьедестал с receipt сохранён, а матрица осталась на `countdown=1`». Слишком старый матричный снимок, обратный порядок сохранений, восстановление банка-источника и отказ посреди `RESERVED` без уже существующего результата остаются fail-closed и могут вести к потере ресурсов; меж-BlockEntity атомарных транзакций всё ещё нет. Старый незавершённый ledger без новых `RitualId`/receipt полей не продолжается автоматически.

M06.1 остаётся 🟡, инфузия **5/11**, контрольный индекс **59/100**, [draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1) **не сливать без разрешения**. Следующий шаг: реальная полная NeoForge компиляция (через отдельный on-demand compile CI), world-save/unload/restart на одной и нескольких границах чанков, проверки 7 физических платных ритуалов и ненулевых jar-balance delta; не переходить в M06.2 преждевременно.

## 18.10.2026 — седьмой подход M06.1: настоящий NeoForge CI и точная проверка оплаченных пьедесталов

Fresh-read: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=5fd6ca562e8a1b2aaa476f3777d6ac182dcbe4e1` на старте. Прочитаны `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, аудит и этот журнал. Сверены actual CircleCI config, GitHub Actions, реальная матрица, receipts и Core item-progress тест.

**Подтверждена ПОЛНАЯ NeoForge проверка (впервые после изменений M06.1):**
- `59a3ae8` — временный `run_compile: default: true` **только в** `feature/infusion`, CircleCI принял [`neoforge-compile #323`](https://circleci.com/gh/craftkot/tgaumcraft/323). **GitHub commit status = SUCCESS**. В этом отдельном job `gradle build -x test`, JAR unpack integrity и настоящий localhost `runServer` с проверкой Minecraft серверного `Done (` должны завершиться до SUCCESS; не путать с Core job `verify-connection #322`.
- `8b5a9b3` — СРАЗУ после подтверждения запуска #323 исходный `.circleci/config.yml` восстановлен с `run_compile: false`; у PR **нет** diff по файлу CI.
- `ed54793` — повторная полноценная NeoForge проверка новой версии после исправления recovery/receipt, **[`neoforge-compile #337 SUCCESS`](https://circleci.com/gh/craftkot/tgaumcraft/337)** (GitHub commit status); `75dbe99` — возврат `run_compile: false` после старта; настройка не меняет main.
- `95ce9f6`, `ed573a7`, `5588e2b` — подтверждение подлинного side-пьедестал debit receipt теперь исправляет все шесть вариантов устаревшего `ItemCountdown=0..5`, а не только 1. Более старый matrix-save не должен выбрасывать уже оплаченный side input. Источник истины — receipt со строгим matching UUID, битом, EMPTY физическим пьедесталом, подлинно оплаченной эссенцией и корректным вторым слотом.
- `f027aa2`, `2d67717`, `3ff7968` — поле `ConsumedItems` теперь недостаточно для получения результата: **каждый** уже потраченный исходный боковой item обязан иметь `PortInfusionRemovalRitual` с UUID текущего ритуала и верным bit в реальном `PedestalBlockEntity`. Core-тесты отвергают missing/wrong/extra receipt, даже если mask и empty slots математически совпадают. То же условие проверяется при реальном `STALLED` acknowledgement ранее выданного результата.

**Проверки:** [Core #334 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/334) после изменений прогресса/получения receipts; [Core #336 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/336); [Core #338 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/338). **Отдельная NeoForge compile + JAR + выделенный server #337 SUCCESS** подтверждена на программном коммите `ed54793` после всех исправлений recovery/receipt; у последующих коммитов только журнал/PR, не игровая логика. Не заявлять как end-to-end игровой тест: dedicated server startup не доказывает оплаченный Crystal Farmer Survival цикл.

**Остаточные ограничения:** физический jar debit и matrix-ledger в разных BlockEntities сохраняются неатомарно; та же проблема при финальном `RESERVED` и центральном output. Сохраняется риск потери уже оплаченных ресурсов при непокрытом аварийном порядке записи. Нужны реальная клиентская инфузия, save/unload/reload/server restart и сравнение количества ресурса до/после. M06.1 остаётся 🟡, M06 5/11, весь проект 59/100. [Draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1) **не сливать** без разрешения.

## 18.10.2026 — восьмой подход M06.1: двойной recovery подтверждённых side-cost

Fresh HEAD: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=2625ed6d9c3d583a30dc8b8f596495cc9726f075` на старте. Повторно прочитаны главные roadmap/AGENTS/parallel workflow/audit, lane-journal, последние workflow checks и настоящие Core/NeoForge классы. Продолжена только `feature/infusion`; никакой записи в `main`.

**Исправлен конкретный оставшийся crash-loss сценарий:** оба реальных боковых пьедестала успели отдельно сохранить `EMPTY + PortInfusionRemovalRitual/Bit` того же UUID, а матрица после reload восстановила `ConsumedItems=0` из старой точки. Раньше recovery требовал, чтобы Salis всё ещё лежал на пьедестале, поэтому два действительно оплаченных side items не восстанавливались, ритуал мог застыть.

- `e13ffda` — Core `LegacyCrystalFarmerIngredientProgress.afterSavedWorldReceipts(seedReceipt,salisReceipt,originalSalisStillPresent)`: подтверждает один **или сразу оба** реально списанных original inputs строго в исходном BETA26 порядке; rejects bare-empty, wrong/contradictory receipts, extra Salis/отсутствующий original Salis, replay. Ничего не создаёт и не потребляет.
- `b122f92` — чистые Java-регрессии на 6 возможных сохранённых countdown=0..5: оба receipts, только seeds, mask уже paid-seeds, stale/missing/extraneous receipt, repeat, mismatched original Salis slot.
- `5d70893` — серверная матрица проверяет **два физических исходных педестала**, их авторитетные `hasInfusionRemovalReceipt(ritualUUID,bit)` и оригинальный Salis `ItemStack`, затем в одной операции корректирует `ConsumedItems` 0→1 или 0→3 или 1→3. Ровно те же smoke-маркеры `INFUSION_CRYSTAL_SEEDS_DEBIT_RECOVERED` и `...SALIS_DEBIT_RECOVERED`. Никаких фейковых debit/reward.
- `ff50a39` — на одну pipeline временно включен `run_compile: true` только в своей feature-ветке, запущены [`Core #352`](https://circleci.com/gh/craftkot/tgaumcraft/352) и [`neoforge-compile #353`](https://circleci.com/gh/craftkot/tgaumcraft/353); `068c324` — немедленно возвращён дефолт `false`, PR не содержит конечного CI config diff.

**Проверки:** `Core #346 SUCCESS` по GitHub status для расширенного теста, **[Core #352 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/352)** для pipeline с полной сборкой. **[neoforge-compile #353 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/353)** подтверждена GitHub status: стандартная конфигурация job выполняет full Gradle compile, package JAR, unzip integrity и фактический localhost dedicated runServer startup; независимо от результата отдельный реальный **Survival player ritual + client + cold world restart** не проводился. Отсутствует меж-BlockEntity атомарность `jar debit / matrix ledger` и центрального output при RESERVED без saved output. **Нельзя считать M06.1 принятым или гарантировать отсутствие потерь**. 5/11 инфузия, глобально 59/100.

Статус: **M06.1 🟡**, [draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1) не сливать без явного разрешения владельца.

## 21.10.2026 — девятый подход M06.1: физическая проверка jar unit debit и точная NBT-сигнатура Crystal Farmer

Начальный fresh-read: `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=b99f4be9765c9b3d2f04379392500b27fdf37173`; перечитаны главные `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, исходный аудит `59/100`, этот журнал и свежие GitHub Actions/CircleCI. Изменения только в `feature/infusion`.

**Физическое, не синтетическое списание эссенции из jars:**
- `6e6c5b3` — новый pure-core `LegacyInfusionJarDebitEvidence.exactlyOne` требует: успешный ответ реальной банки, исходно соответствующий аспект, фактическое уменьшение `amount` **ровно на 1**, исходный фильтр без изменений; при последней единице в банке аспект исчезает, как в `LegacyEssentiaJarLogic`. Это однотиковый доказательный снимок, **НЕ** кросс-чанковая транзакция.
- `936c459` — Core регрессии всех семи исходных аспектов, `amount` 1/2/10/250, с фильтром и без; reject unchanged amount, fake success, foreign aspect, changed filter.
- `9b8e2f0` — настоящий NeoForge `InfusionJarSourceBridge` снимает `jar.snapshot()` непосредственно до и после `takeForInfusion(aspect,1)`. Только подтверждённое физическое движение возвращает `true` в matrix ledger; smoke `TC_SMOKE_INFUSION_CRYSTAL_JAR_ONE_UNIT_DEBIT_PROVEN` на каждую единицу. На противоречии fail-closed `TC_SMOKE_INFUSION_CRYSTAL_JAR_DEBIT_INCONSISTENT` и никаких разрешений на получение результата.

**Оригинальный Crystal Farmer input, а не поддельный ключ первого аспекта:**
- `4824fda`, `ca4b46e` — проверка исходного `makeCrystal(aspect)` в чистом Core: **ровно один** raw NBT аспект и amount=1. Добавлены негативные тесты на 2 аспекта, amount 0/2, чужой/неизвестный aspect и пустые payloads для всех семи рецептов.
- `0277847`, `8ecf2a5`, `70599b5` — `CrystalEssenceItem.isOriginalInfusionCrystal` принимает только настоящий `CRYSTAL_ESSENCE`, где `CUSTOM_DATA` имеет именно `Aspects:[{key,amount:1}]`, без лишних записей. Использован в `CrystalFarmerInfusionAdapter` при matching и в `InfusionMatrixBlockEntity` при start, world tick, finish и NBT restore. Обычное чтение аспектов для других систем не изменено.
- `70e669e` — удалены повторные сбросы `originalCentralInput/Seed/Salis` и устаревшие комментарии об отсутствии output.

**CI:** [CircleCI Core #447 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/447), [#450 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/450), [#455 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/455). Для отдельной полной NeoForge Java + JAR + dedicated server pipeline временно включён `run_compile=true` только в feature-ветке (`ae5d8db`), **[#456 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/456)** — подтверждено GitHub status для job `ci/circleci: neoforge-compile`: полный Gradle `build -x test`, целостность собранного JAR и реальный запуск локального dedicated Minecraft сервера до ready. `01a74a7` немедленно вернул дефолт `run_compile=false`; итоговая ветка не содержит изменения `.circleci/config.yml` против `main`.

**Границы доказанного:** несмотря на положительные Core-результаты, отдельный настоящий *player-paid* Crystal Farmer ритуал с записью снимков jars/pedestals, save/unload/cold-restart и сравнение итогового output **не проводился**. Ошибка разновременного сохранения jar debit vs matrix ledger всё ещё теоретически может терять ресурсы. До полного игрового теста M06.1 🟡, инфузия 5/11, проект 59/100, [draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1) не сливать.

## 21.10.2026 — десятый подход M06.1: подтверждение физической публикации output

**Fresh-read:** `main=a4280834adc5a20b6951098af937d30ca4aeca56`, `feature/infusion=ebfea24191cdada3721037fc25a0e6e1219abb94` при старте; перечитаны main `ROADMAP_RU.md`, `AGENTS.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, актуальные lane/audit, Github Actions и CircleCI. Все изменения только в `feature/infusion`.

**Новый аварийный барьер в реальном центральном BlockEntity:**
- `0cbae7a` — pure Core `LegacyCrystalFarmerOutputReceipt`, который привязывает уже **физически существующий** результат к UUID оригинального ритуала и registry item ID. Квитанция не разрешает создать второй item.
- `f6bee22` — `PedestalBlockEntity.publishInfusionResultWithReceipt` заменяет ожидаемый оригинальный вход центрального пьедестала на один результат **и одновременно** записывает output proof в собственный `BlockEntity` (та же сериализация, что у предмета); `PortInfusionOutputRitual` + `PortInfusionOutputItem`. Все внешние нормальные мутации inventory (`setItem`, `removeItem`, `removeItemNoUpdate`, `clearContent`) сбрасывают proof. `loadAdditional` fail-closed принимает receipt только если он совпадает с зарегистрированным item ID физического исходного слота. Боковые receipts прежние.
- `47202d9` — live `InfusionMatrixBlockEntity` после fully paid `RESERVED→STALLED` теперь **отказывает в ACK** одного лишь визуально совпавшего item, если в том самом центральном пьедестале нет output receipt от UUID текущего ритуала. Normal real finish теперь записывает result именно через атомарный (внутри одного BE) `publishInfusionResultWithReceipt`, а не обычный swap.
- `b6740fe` — negative/positive Core tests для всех 7 оригинальных outputs: foreign UUID, отсутствующий proof, неправильный output ID, null/empty, подложенный один и тот же BlockItem без proof, no-replay STALLED; положительный ACK разрешается только при совпадении всех квитанций.
- `e137b72` — временный opt-in `.circleci/config.yml run_compile=true` **только в feature** для CI, сразу затем `b6b9d3a` восстановил `default:false`. **[CircleCI #467 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/467)** подтвердил настоящий полный NeoForge 1.21.1 Gradle build, упаковку JAR и dedicated Minecraft server readiness. [Core #465 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/465) проверил новые тесты.
- `908acb2` — записан **отдельный, ещё не пройденный** строгий [план игрового приёмочного испытания](infusion_M06_1_GAME_ACCEPTANCE_RU.md): правильный реальный исследовательский gate `CRYSTALFARMER`, реальные item stacks, семь исходных продуктов, запись источников и cost-ledger, canonical output receipt, реальные Survival и cold world save/reload/abrupt-kill. Без настоящих логов не выдавать за PASS.

**Границы:** сегодня **НЕ выполнены** actual player-paid Survival crafting, измерения реального jar before/after world save, cold world restart/crash-loss test. Output provenance предотвращает ложное признание заранее поставленного блока после restart, но **НЕ делает jar/matrix/center транзакцию атомарной между BlockEntity/chunks**; STALLED с неполной записью остаётся без второй выдачи и возможна потеря ресурсов. Не закрывать M06.1 до честного игрового подтверждения; проект 59/100, M06 5/11, [draft PR #1](https://github.com/craftkot/tgaumcraft/pull/1), никаких merge на main.

## 27.10.2026 — обобщение реального ритуала (работа в процессе)

Авторизация текущего пользователя: одновременно големы + полноценная инфузия. Рабочая ветка `feature/infusion`, исходный HEAD `2e334a967e7740067bbb18e65b4d2e81aead2283`; основной ROADMAP/AGENTS/parallel workflow перечитаны, общие файлы не изменяются этой командой.

Первый аудит: несмотря на универсальный `LegacyInfusionRecipeMatcher`, реальная матрица (914 строк) была привязана к двум ингредиентам и семи Crystal Farmer в start, progress, snapshot, restore и finish. Данные големов без изменения runtime не могли стать оплачиваемыми рецептами.

Текущий конкретный интерфейс:
- `LegacyInfusionRecipeCatalog.Recipe(legacyId,research,center,components,outputItemId,outputCount,aspects,instability)`, `Ingredient(itemIds,tagIds,crystalAspect)`; повторный ID запрещён, стоимость валидируется исходным реестром аспектов.
- Неизменяемые source descriptors: центр, упорядоченные 1..30 боковых ингредиентов (сопоставление остаётся неупорядоченным 1:1), research key, реальные registry/tag IDs. Поддержаны **только обычные output ItemStack count=1**; dynamic/enchantment/component outputs не выдаются по приблизительному совпадению.
- `InfusionRecipeAdapter` преобразует descriptors в настоящие ItemStack predicates, неизвестный output registry ID отключает рецепт (не заменяет на AIR). Опциональный source-модуль `/data/thaumcraft/golem/infusion_recipes.json` поставляется веткой големов; malformed присутствующий файл вызывает явную ошибку. Нет imports классов големов.
- Матрица теперь выполняет общий `beginVerifiedInfusionFromCaster`, тот же физический jar debit, source countdown и consumption N боковых пьедесталов с UUID receipts; выдача только после полной оплаты и одноразового checkpoint. Старый caster entrypoint оставлен delegate для совместимости.
- NBT `Format=2`, `SidePedestals:long[]`, `OriginalSideInputs` source-order. Для старых Crystal Farmer без Format читаются `SeedsPedestal`/`SalisPedestal`; биты 1/2 и существующие физические receipts сохраняют значения. Старые core-классы и регрессии сохранены.

Исходник независимо прочитан через `javap -c -p ConfigRecipes` официального `/workspace/reference/Thaumcraft-1.12.2-6.1.BETA26.jar`: SealHarvest offsets0..151 (6 боковых), SealButcher154..298 (6; **bestia/sensus/humanus10, instability0**), SealBreak301..400 (3), MindBiothaumic3804..3902 (cognitio50,machina25,instability4). Данные и современные item mappings согласованы с владельцем големов. Его ресурс остаётся в его ветке; наличие production loader не является доказательством интегрированной регистрации/игры.

Проверка в процессе: новый `InfusionGenericRuntimeTest` сначала RED (отсутствующий общий runtime), затем чистая Java21 компиляция + GREEN: реальные переходы countdown→consumed для 2/3/6/12/30 inputs, невозможные mask/countdown, полная верификация всех physical receipts до recovery, точные 7 CrystalFarmer цены при x1.35 и отказ другой цены. Это **не Minecraft proof**. Внешняя команда проверки готовит настоящий GameTest с реальным caster, банками/предметами и NBT save/load; результаты будут добавлены только после фактического запуска. Gradle только по выделенному root build slot.

Остаток: полный исходный каталог, dynamic outputs/enchantments, контейнерные остатки ингредиентов (пока такие рецепты отклоняются при старте), полный runtime нестабильности/аварий, исходные визуальные эффекты/renderer, настоящая Survival прогрессия research, холодный restart/межчанковая crash durability. **Нет обещания глобальной атомарности** jar→matrix→pedestal; подтверждение уже существующего output не создаёт новый. M06 остаётся 🟡, прежняя оценка не повышена.


## 27.10.2026 — две команды, финальная проверка generic runtime

Пользователь явно активировал инфузию параллельно големам. Existing feature/infusion продолжена от2e334a9 в отдельном worktree; maina4280834/feature/golems/история других веток не изменяются. Прежние Draft PR#1/#3 сохраняются.

- Реализованы generic catalog/adapter и1..30 физических боковых входов; Format2 checkpoint/legacy formatless crystal migration/полные ItemStack snapshots и UUID receipts. Семь оригинальных Crystal Farmer сохранены; optional golem descriptor загружается без Java-зависимости на golem-классы.
- Независимое ревью нашло Important: same-ID изменённый рецепт мог воспользоваться ранее оплаченной ценой. Добавлен полный RecipeFingerprint, focused RED→GREEN и два настоящих native restore-negative tests. После исправления новых Critical/Important не найдено; known split-BE save window остаётся открытым.
- Java21 fullCore PASS/exit0; финальный direct-API generic test отдельно PASS. Full NeoForge build+10/10 required native PASS/exit0 (3m35s):7 paid variants, old formatless migration без нового fingerprint, missing/altered identity rejection. Каждый положительный случай проходит actual caster/research denial/25 реальных jar debits/side receipts/one output/120native no-replay ticks. Исследования явно fixture-seeded.
- Fresh cold WRITE paid8/25 → отдельная READ JVM paid25/output1/120ticks PASS/exit0(1m15s READ), UUIDe4af369f-99cb-4f3d-8958-fb2741d13799. Independent raw MCA parser подтвердил один crystal_aer, пустые side slots с receipts bits1/2 того же UUID,3jars0,craftingfalse/noledger.
- Совместный source overlay двух веток без git merge: full build и4/4 required GameTest PASS/exit0 за1m25s. Настоящие caster/world ticks, SealHarvest/SealButcher/SealBreak по30 и MindBiothaumic75 эссенции, exact receipts/output, unrelated Aer7 не затронут,120native no-replay ticks.
- Не проверены заново: графический клиент новой инфузии, Survival-research, chunk unload, hard-kill split persistence. Не перенесены: полные instability effects/аварии, полный каталог/компонентные output/container remainders. Все-cost1 при x0.5 пока unsupported edge generic ledger; текущие11 source recipes не затронуты.59/100 не повышается.

Доказательства: [generic evidence](../evidence/infusion/generic_2026_10_27/README.md), [архитектура](../INFUSION_RUNTIME_DESIGN_RU.md). Source commits/HEAD будут записаны после фиксации проверенного снимка.


## Публикация проверенного снимка — 27.10.2026

Совместная проверка не меняет историю веток: отдельный каталог исходников содержит golem tree и только infusion delta от main. Единственное пересечение — Core runner, его регистрации объединены только в тестовой копии. Полный manifest и лог приложены в evidence. Четыре golem-теста остаются в отдельном namespace и запускаются task runGolemInfusionGameTest; без реального golem-модуля они не объявляются пройденными.

Собственный JAR инфузии:469 классов Java21; CRC, отсутствие duplicate entries и уникальные EN/RU ключи проверены. Точные SHA в jar-audit.json. Архитектура, тестовые команды и границы описаны в INFUSION_RUNTIME_DESIGN_RU.md и evidence README. Full instabilities/аварии, весь каталог, component-output/remainders и полная Survival-прогрессия остаются следующим этапом. Общая crash-атомарность нескольких BlockEntity не заявлена. Main/общий roadmap/build.gradle/CI не менялись; Draft PR#1 остаётся открытым и не сливается. Source SHA и итоговый HEAD будут записаны после коммитов.

- `1a8af82206153ab01cafcd0a02543b5c43b696d0` — общий оплаченный runtime, полный recipe fingerprint/legacy migration, реальные10 native и4 cross-module tests/harness, архитектура и evidence. Java21 Core/full build/cold WRITE→READ/raw MCA PASS. Следующий этап — события нестабильности и дальнейшие исходные классы рецептов.


### Необходимая правка общего CI

После обычного push функционального1a8af82 GitHub Actions run40560919689 упал на шаге18: устаревший grep искал прежние Crystal Farmer methods. CircleCI#512 verify-connection SUCCESS подтверждает только подключение. Причина проверена настоящим job log; никаких фиктивных старых method names/comments в production не добавлялось. Два старых grep-блока .github/workflows/ci.yml заменены исполнением реального generic Core regression; tools/verify_source_caster.py проверяет фактический compatibility delegate и новый adapter. Это минимальная необходимая общая правка для координатора, отдельный commit. Все статические run-шаги workflow локально PASS/exit0; Java runtime/проверенные артефакты не менялись. Удалённый CI после нового push проверяется отдельно.

- `951ae9361e2715f2cc2e60265e1ffe7d682db223` — минимальная необходимая CI-правка stale source greps; все workflow static steps локально PASS. Gameplay после1a8af82 не менялся.

Удалённый GitHub Actions Thaumcraft Port CI для проверенного source HEAD `951ae9361e2715f2cc2e60265e1ffe7d682db223`: [run40561533951](https://github.com/craftkot/tgaumcraft/actions/runs/40561533951) и [run40561523794](https://github.com/craftkot/tgaumcraft/actions/runs/40561523794) — SUCCESS (включая full build и dedicated smoke). Полные локальные native/Core/cold результаты перечислены отдельно.

Связанный проверенный код: golems `b50226a`, infusion `1a8af82`, необходимая CI-правка `951ae93`. Draft PR#3 и#1 обновлены и остаются open/draft, merged=false. Main свежепроверен: `a4280834adc5a20b6951098af937d30ca4aeca56`.

Завершающий публикационный commit (эта запись) меняет только журнал и evidence статусов. Его HEAD определяется `git rev-parse HEAD`; игровой код не меняется. `[skip ci]` применяется только к этому docs-only commit, чтобы не повторять полные сборки уже проверенного исходного снимка. Следующий этап — исходные события нестабильности инфузии и дальнейшая Survival-интеграция; полные M06/M10 ещё открыты.
