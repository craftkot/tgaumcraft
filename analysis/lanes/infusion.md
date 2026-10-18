# Направление: инфузия / feature/infusion

**Статус:** 🟡 M06.1 — реальный оплаченный ритуал Crystal Farmer, проверка сохранения и восстановления.
**Основной план:** [ROADMAP_RU.md](../../ROADMAP_RU.md).
**Общий аудит:** [PROGRESS_AUDIT_2026_10_16.md](../PROGRESS_AUDIT_2026_10_16.md) — M06 5/11, проект 59/100, без пересчёта.

## Проверено в исходном коде

- Настоящая активация матрицы Basic Caster на собранной структуре.
- Оригинальные семь Crystal Farmer: центр — аспектный кристалл, боковые пьедесталы — пшеничные семена и Salis Mundus; исходная стоимость при множителе 1.0 — **25** эссенции (10 конкретного аспекта, 10 Vitreus, 5 Vinculum).
- Серверный адаптер списывает по одной единице из реально находящихся в мире банок. Пьедесталы удаляют настоящие ингредиенты только после countdown. Результат помещается на центральный пьедестал через однократную reservation/checkpoint.
- **Это подтверждение путём чтения реализации**, НЕ новым end-to-end тестом в Minecraft. Атомарность сохранения между несколькими BlockEntity пока не гарантирована; авария между сохранениями может остановить ритуал или потерять материалы.

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

1. 🟡 **M06.1** — выполнить `InfusionRitualLedgerParityTest` через CoreParity CI и полную NeoForge сборку, затем настоящий survival ритуал от построенного алтаря до выданного кристалла: сравнить начальный/конечный jar баланс, два реальных input item и отсутствие второго результата после рестарта.
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
