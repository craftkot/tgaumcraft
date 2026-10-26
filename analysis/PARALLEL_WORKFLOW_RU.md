# Параллельная разработка в нескольких ChatGPT-чатах — порядок работы

**Создано:** 18.10.2026 · **Цель:** безопасно продолжать ОДИН порт Thaumcraft 6.1.BETA26, не перезапуская проект и не перетирая код друг друга.

## 1. Ветки и область ответственности

| Ветка | Этапы | Основная область | Статус / последний датированный срез |
|---|---|---|---|
| [feature/alchemy](https://github.com/craftkot/tgaumcraft/tree/feature/alchemy) | M04 и затем M05 | Зеркала, эссенция, тигель, банки, печь, трубы, аламбики; соответствующие `LegacyEssentia*`, `Essentia*`, `Smelter*`, `Alembic*`, локальные ресурсы и тесты | **Основная рабочая ветка**, M04.1 |
| [feature/research](https://github.com/craftkot/tgaumcraft/tree/feature/research) | M07 | Таумономикон, исследовательский стол, 33 карты, 14 помощников, прогресс, сканирование, связанные чистые core-пакеты и UI | 🟡 В работе по feature-commits; gameplay не подтверждён |
| [feature/infusion](https://github.com/craftkot/tgaumcraft/tree/feature/infusion) | M06 | Ритуалы, пьедесталы, матрица, нестабильность, стабилизаторы, инфузионные рецепты, соответствующие UI/NBT | 🟡 В работе по feature-commits; gameplay не подтверждён |
| [feature/golems](https://github.com/craftkot/tgaumcraft/tree/feature/golems) | M10 | Големы, печати, Bell, сборка, логистика, бой, навигация, серверные тесты и NBT; общие регистры/локализации/Core runner — только с координацией | 🟡 Draft [PR #3](https://github.com/craftkot/tgaumcraft/pull/3), M10 🟡; **M10.58** HEAD [`431a8d57`](https://github.com/craftkot/tgaumcraft/commit/431a8d5704ecd89e3c5b2f4b92ec50b77f039e51) (только обновление журнала; код M58 — [`fea4aacf`](https://github.com/craftkot/tgaumcraft/commit/fea4aacf18f3091bb6e6b3711d8c3912388407a8)) (26.10): автор сообщает 3 BETA26 plate recipes, исправление typed ironplate, full NeoForge/JAR +269/269 required GameTests и Core PASS (независимого прогона нет); optional33/FD4, cold restart и клиент M58 не повторялись. [CircleCI #501](https://circleci.com/gh/craftkot/tgaumcraft/501) SUCCESS только Core, [Actions #40294907435](https://github.com/craftkot/tgaumcraft/actions/runs/40294907435) FAILURE без steps. M07/M12 survival-зависимости и M43 split-save открыты, совместный runtime не проверен, M10 не принят |
| [main](https://github.com/craftkot/tgaumcraft/tree/main) | Общая сборка | Только интегрированные изменения после проверенного PR | Основная интеграционная ветка |

**Актуальный M10.58:** **M10.58, HEAD [`431a8d57`](https://github.com/craftkot/tgaumcraft/commit/431a8d5704ecd89e3c5b2f4b92ec50b77f039e51) (только обновление журнала; код M58 — [`fea4aacf`](https://github.com/craftkot/tgaumcraft/commit/fea4aacf18f3091bb6e6b3711d8c3912388407a8)) (26.10.2026):** разработчик добавил 3 оплачиваемых plate-рецепта BETA26 и исправил тип LegacyPlateMeta у ironplate; отчёт [M58](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/paid_plates_2026_10_26/README.md) заявляет fresh Core, полную NeoForge/JAR и **269/269 required GameTests PASS**. Это авторские локальные прогоны, координатор не воспроизводил; **33 optional/4 FD, cold restart и графический клиент на M58 не повторялись**. [CircleCI #501](https://circleci.com/gh/craftkot/tgaumcraft/501) SUCCESS (только Core), [Actions #40294907435](https://github.com/craftkot/tgaumcraft/actions/runs/40294907435) FAILURE (`verify.steps=null`, причина неизвестна). Для полного survival-пути остаются M07 knowledge progression и M12 Greatwood acquisition; M43 split-save 13→26 iron/17→34 gold не исправлен. По полному PR #3 **316 файлов**, прямых совпадений путей с M04/M06/M07 нет; общий четырёхветочный NeoForge/server/client/world-save не проверен. M10 остаётся Draft/🟡; индекс 59/100 без пересчёта.

**Исторический M10.57 (26.10.2026, 02:01 МСК):** `feature/golems` HEAD [`1bb95d6a`](https://github.com/craftkot/tgaumcraft/commit/1bb95d6aa6b0e9c4bbd07e51b3650142f6086193), +2 коммита после M56 (`ad0327b5` — production fix, `1bb95d6a` — evidence/docs). [M57 evidence](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/transfer_ownership_2026_10_26/README.md): разработчик сообщает исправление обычного синхронного callback reentry (ранее один paper9 мог дублироваться), потери независимо внесённого груза и неправильного возврата remainder; добавлен per-worker ownership scope. Это **не** исправление аварийной межфайловой атомарности. Автор сообщает отдельную полную NeoForge/JAR сборку, **263/263 required +33/33 optional +4/4 Farmer's Delight**, fresh Core и cold server WRITE/READ с read-only disk audit. Координатор исходники/evidence прочитал, но Java/GameTests/сервер независимо **не запускал**; нового графического клиента нет. [CircleCI #496](https://circleci.com/gh/craftkot/tgaumcraft/496) SUCCESS по точному GitHub commit status **только Core/verify-connection**; GitHub Actions [#40237377621](https://github.com/craftkot/tgaumcraft/actions/runs/40237377621) и [#40237375247](https://github.com/craftkot/tgaumcraft/actions/runs/40237375247) FAILURE, `verify.steps=null`, причина не установлена. Полный paginated список [PR #3](https://github.com/craftkot/tgaumcraft/pull/3/files) содержит **307 файлов**, сравнение GitHub `main...feature/golems` обрезано на 300; прямых пересечений с alchemy(7), research(35), infusion(18) и между ними **нет** по полным спискам, но общие hooks/registries/API и интеграция runtime не доказаны. Split-save **13→26 iron /17→34 gold** остаётся открытым; совместные NeoForge/JAR, server, client и world-save четырёх веток не проверены. M10 — Draft, 59/100 без пересчёта. Старые M56/M55/M54 ниже — история.

**Исторический M10.56 (25.10.2026, вечер МСК):** `feature/golems` HEAD [`60de47b5`](https://github.com/craftkot/tgaumcraft/commit/60de47b5744f5bf99c6c519c67285abd4a66db24), +2 коммита после M55 (`45daf322` — opt-in native side-support probe; `60de47b5` — публикация журнала). [M56 evidence](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/side_support_2026_10_25/README.md): разработчик сообщает отдельный full NeoForge/JAR и 252/252 required +33/33 optional +4/4 Farmer's Delight, native unload обоих чанков, 40 тиков без загрузки, последующий reload/AI resume и read-only disk audit; тест в одном серверном процессе, **не** новый cold restart и **не** клиент. Отрицательный mutation-контроль ожидаемо упал по assertion marker; это не green run. Независимого воспроизведения координатором нет. [CircleCI #493](https://circleci.com/gh/craftkot/tgaumcraft/493) SUCCESS **только Core/verify-connection** по GitHub status; GitHub Actions [#40230415764](https://github.com/craftkot/tgaumcraft/actions/runs/40230415764) и [#40230412756](https://github.com/craftkot/tgaumcraft/actions/runs/40230412756) FAILURE, `verify.steps=[]`, причина не установлена. Split-save 13→26 iron /17→34 gold, общие NeoForge/server/client/world-save четырёх веток и полная приёмка M10 не подтверждены. Старые срезы M55/M54 ниже — история.

**Исторический M10.55 (25.10.2026, 18:25 МСК):** `feature/golems` [`9a19b396`](https://github.com/craftkot/tgaumcraft/commit/9a19b3967cfd4cdf4ab537532faef7f1573151b8), +2 test-only/docs commits после M54; [M55 six-way proof](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/six_way_crops_2026_10_25/README.md) — разработчик сообщает full NeoForge + 33/33 optional PASS, без повторного 252/FD4 и без vertical cold/GUI. [CircleCI #490](https://circleci.com/gh/craftkot/tgaumcraft/490) SUCCESS только Core по commit status; [Actions #40205762871](https://github.com/craftkot/tgaumcraft/actions/runs/40205762871) и [#40205760161](https://github.com/craftkot/tgaumcraft/actions/runs/40205760161) FAILURE, steps=null, причина неизвестна. M10 остаётся Draft; split-save 13→26 iron/17→34 gold и общий четырёхветочный runtime не проверены. M54 в таблице — исторический срез.

Вся работа **на существующих файлах**. Ни одна ветка не должна пересоздавать каталог проекта.

**Общий модуль может понадобиться нескольким чатам:** регистры блоков и предметов, `ThaumcraftSmokeAudit`, CoreParity runner, `.circleci/config.yml`, `build.gradle`, `README.md`, `ROADMAP_RU.md`, `AGENTS.md`, `src/main/resources/data/thaumcraft/research/**`, глобальные локализации/ресурсы, `ThaumcraftPort.java`, `ArcaneWorkbenchMenu.java`, `CrucibleBlockEntity.java`. M10 уже затрагивает последние общие точки; на проверенном срезе 19.10.2026 прямых пересечений изменённых путей между четырьмя feature-ветками нет, но совместная сборка не проводилась. Изменения там требуют координации. Если никак не обойтись — отдельный малый коммит и PR **раньше** зависимых изменений, либо работать в своей ветке и сообщить о конфликте. Не перезаписывать чужие изменения.

## 2. Как начинает новый чат

Передать ему одну конкретную ветку. **Сначала** прочитать свежие `main:ROADMAP_RU.md`, `main:AGENTS.md`, этот файл, затем HEAD назначенной `feature/*`, свой lane-status `analysis/lanes/<lane>.md` и профильный audit/handoff. Сообщение «П» — продолжать этот этап, делать коммиты **в назначенную feature-ветку**, а не main.

- Для исследований: `feature/research`, M07, `analysis/lanes/research.md`. Первый подход — аудит реально проверенных игровых карточек/GUI и первый отсутствующий цикл, а не перенос с нуля.
- Для инфузии: `feature/infusion`, M06, `analysis/lanes/infusion.md`. Первый подход — проверить реально оплаченный ритуал + сохранение без дюпа.
- Для алхимии: `feature/alchemy`, M04.1, `analysis/lanes/alchemy.md`. Причина #209 в fixture найдена по логу; следующий шаг — успешно повторить Minecraft smoke после патча.
- Для големов: `feature/golems`, M10, журнал `feature/golems:analysis/lanes/golems.md` (его пока нет в `main`/`chore/coordination`). Проверять реальный HEAD, [draft PR #3](https://github.com/craftkot/tgaumcraft/pull/3), отдельные full NeoForge, server GameTests, client и cold/crash save; нельзя переносить локальные утверждения в статус подтверждённого CI.

## 3. Внутри одной ветки

1. Всегда читать **актуальную branch HEAD** перед записью и использовать optimistic ref lease `expected_sha`; отказ — не делать force-push, заново синхронизироваться и повторить без потерь.
2. Менять только код своего этапа и свой файл `analysis/lanes/<lane>.md`. Содержательные коммиты небольшие; Core parity + по необходимости **полная NeoForge-компиляция и Minecraft smoke**.
3. Ни при каких обстоятельствах не считать `core tests SUCCESS` подтверждением игры. В каждом статусе явно различать *написано*, *скомпилировано*, *проверено в мире*.
4. Обновить свой lane-status по завершении подхода: сделано, commit SHA, CI-URL, осталось, следующий подпункт. Не переписывать на feature-ветке общий `ROADMAP_RU.md`.
5. CircleCI `run_minecraft` и `run_compile` по умолчанию `false`; если временно включаете, вернуть `false` в ТУ ЖЕ feature-ветку и не менять настройки на main.

## 4. Интеграция в main

1. Проверить список файлов другого активного PR; если пересечения есть, согласовать один порядок.
2. Синхронизировать свою feature-ветку с последней main **без force** (например, обычным merge commit; сохранить работы других веток). Не делать слепых перезаписей.
3. Открыть **Pull Request feature/... → main** с `.github/pull_request_template.md`. Приложить исходники, тесты, статусы CI, скриншоты игры (если действительно проверялись), результаты тестов сохранения/NBT и честный перечень неиспытанного.
4. **Только после проверки и разрешения пользователя** сливать PR в main. Если на платформе нет контроля branch protection, это правило остаётся обязательным организационно.
5. После слияния обновить **единую карту на main**, отметив законченные подэтапы/фактические результаты; другие ветки подтягивают новую main при необходимости.
6. **Не проводить merge по одному зелёному core check** для большой механики, когда критерии требуют full game. В случае недостающих тестов — оставить PR открытым.

## 5. Статусы и цвета

- 🟢 принятая, подтверждённая часть; не каждая линия Java означает «готово».
- 🟡 текущая задача активного чата в его ветке (одна в каждой рабочей ветке).
- 🔴 ещё не завершено / ветка подготовлена, но работа не началась. **Создание ветки не переводит этап в жёлтый.**
- Четыре направления (M04/M06/M07/M10) уже 🟡 по кодовым коммитам на 18–19.10.2026. Это не даёт баллов сверх 59/100, не означает завершённость и не разрешает merge.

## 6. Ограничения и конфликты

- GitHub пока **не защищает main от прямых push правилами**. Отдельные ветки + PR снижают риск, но защита остаётся организационной; администратор может включить Ruleset «Require pull request» в Settings → Rules → Rulesets, при желании.
- Тяжёлые CircleCI pipeline расходуют время и ресурсы, даже если вызваны из разных feature-веток; запускать осмысленно.
- При неразрешимом конфликте общего файла не переписывать код наугад: зафиксировать конфликт в lane-status и поднять вопрос интеграции.
- Старые handoff с рекомендацией пушить `main` устарели в части процедуры: актуальные `AGENTS.md` и эта инструкция имеют приоритет.
