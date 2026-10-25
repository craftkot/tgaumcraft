# AGENTS.md — Thaumcraft 6: правила для параллельных чатов

**Главный план — `main:ROADMAP_RU.md`**, порядок совместной работы — `main:analysis/PARALLEL_WORKFLOW_RU.md`. Это продолжение существующего переноса Thaumcraft 6.1.BETA26 на Minecraft 1.21.1 NeoForge. **Никогда не создавать проект заново, не заменять дорожную карту и не менять автоматически свою ветку.**

## Ветки и владельцы задач

| Чат | Работа только в ветке | Этап | Собственный журнал |
|---|---|---|---|
| Алхимия / основной чат | `feature/alchemy` | M04 → M05; сейчас M04.1 (fixture #209 исправлен, новый gameplay не подтверждён) | `analysis/lanes/alchemy.md` |
| Исследования / второй чат | `feature/research` | M07.1/M07.2 🟡; непроверенные помощники/карточки | `analysis/lanes/research.md` |
| Инфузия / третий чат | `feature/infusion` | M06.1 🟡; ledger и checkpoint без подтверждённого survival world restart | `analysis/lanes/infusion.md` |
| Големы / отдельный разработчик | `feature/golems` | M10 🟡; M53 — developer evidence dedicated server + 2 TCP-клиента, owner isolation (exit-коды сессий недоступны); M54 прежний HEAD `052ef662` — developer evidence full NeoForge, 252/252 required + 30/30 optional + 4/4 FD, directional cold WRITE/READ exit0 и read-only disk audit. Координатор не воспроизводил; CircleCI #487 подтверждает только Core, Actions #40204694716 FAILURE без доступных steps/logs. Split-save 13→26 iron/17→34 gold открыт; общий четырёхветочный runtime не проверен; M10 не принят | `analysis/lanes/golems.md` |

**Актуальный M10.57 (26.10.2026, 02:01 МСК):** `feature/golems` HEAD [`1bb95d6a`](https://github.com/craftkot/tgaumcraft/commit/1bb95d6aa6b0e9c4bbd07e51b3650142f6086193), +2 коммита после M56 (`ad0327b5` — production fix, `1bb95d6a` — evidence/docs). [M57 evidence](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/transfer_ownership_2026_10_26/README.md): разработчик сообщает исправление обычного синхронного callback reentry (ранее один paper9 мог дублироваться), потери независимо внесённого груза и неправильного возврата remainder; добавлен per-worker ownership scope. Это **не** исправление аварийной межфайловой атомарности. Автор сообщает отдельную полную NeoForge/JAR сборку, **263/263 required +33/33 optional +4/4 Farmer's Delight**, fresh Core и cold server WRITE/READ с read-only disk audit. Координатор исходники/evidence прочитал, но Java/GameTests/сервер независимо **не запускал**; нового графического клиента нет. [CircleCI #496](https://circleci.com/gh/craftkot/tgaumcraft/496) SUCCESS по точному GitHub commit status **только Core/verify-connection**; GitHub Actions [#40237377621](https://github.com/craftkot/tgaumcraft/actions/runs/40237377621) и [#40237375247](https://github.com/craftkot/tgaumcraft/actions/runs/40237375247) FAILURE, `verify.steps=null`, причина не установлена. Полный paginated список [PR #3](https://github.com/craftkot/tgaumcraft/pull/3/files) содержит **307 файлов**, сравнение GitHub `main...feature/golems` обрезано на 300; прямых пересечений с alchemy(7), research(35), infusion(18) и между ними **нет** по полным спискам, но общие hooks/registries/API и интеграция runtime не доказаны. Split-save **13→26 iron /17→34 gold** остаётся открытым; совместные NeoForge/JAR, server, client и world-save четырёх веток не проверены. M10 — Draft, 59/100 без пересчёта. Старые M56/M55/M54 ниже — история.

**Актуальный M10.56 (25.10.2026, вечер МСК):** `feature/golems` HEAD [`60de47b5`](https://github.com/craftkot/tgaumcraft/commit/60de47b5744f5bf99c6c519c67285abd4a66db24), +2 коммита после M55 (`45daf322` — opt-in native side-support probe; `60de47b5` — публикация журнала). [M56 evidence](https://github.com/craftkot/tgaumcraft/blob/feature/golems/analysis/evidence/golems/side_support_2026_10_25/README.md): разработчик сообщает отдельный full NeoForge/JAR и 252/252 required +33/33 optional +4/4 Farmer's Delight, native unload обоих чанков, 40 тиков без загрузки, последующий reload/AI resume и read-only disk audit; тест в одном серверном процессе, **не** новый cold restart и **не** клиент. Отрицательный mutation-контроль ожидаемо упал по assertion marker; это не green run. Независимого воспроизведения координатором нет. [CircleCI #493](https://circleci.com/gh/craftkot/tgaumcraft/493) SUCCESS **только Core/verify-connection** по GitHub status; GitHub Actions [#40230415764](https://github.com/craftkot/tgaumcraft/actions/runs/40230415764) и [#40230412756](https://github.com/craftkot/tgaumcraft/actions/runs/40230412756) FAILURE, `verify.steps=[]`, причина не установлена. Split-save 13→26 iron /17→34 gold, общие NeoForge/server/client/world-save четырёх веток и полная приёмка M10 не подтверждены. Старые срезы M55/M54 ниже — история.

**Историческая актуализация M10.55 (25.10.2026, 18:25 МСК):** `feature/golems` HEAD [`9a19b396`](https://github.com/craftkot/tgaumcraft/commit/9a19b3967cfd4cdf4ab537532faef7f1573151b8), два новых коммита после M54 — только gated native tests/evidence/docs, без production. Автор заявляет full NeoForge build + 33/33 optional PASS для UP/DOWN; 252 required/4 FD и vertical cold не повторялись. [CircleCI #490](https://circleci.com/gh/craftkot/tgaumcraft/490) SUCCESS по `verify-connection` (Core), [Actions #40205762871](https://github.com/craftkot/tgaumcraft/actions/runs/40205762871) FAILURE, `steps=null`, причина неизвестна. Сборка четырёх веток, общий сервер/клиент/сохранение мира не проверены; split-save 13→26 iron/17→34 gold открыт, M10 не принят. Предыдущий M54 в таблице — исторический срез.

`main` — только для интеграции проверенных PR. На 18.10.2026 во всех четырёх feature-ветках есть кодовые коммиты: **M04/M06/M07/M10 🟡**; это не означает завершения этапов. Перед каждым подходом сверять живой HEAD, lane-журнал и CI, а не эту дату; индекс остаётся **59/100**. По одному 🟡 этапу на ветку.

## Каждый раз при «П» / «продолжай»

1. Fresh-read `main` HEAD + `ROADMAP_RU.md` + `analysis/PARALLEL_WORKFLOW_RU.md`.
2. Fresh-read **свою** `feature/*` HEAD и `analysis/lanes/<name>.md`, проверить статусы последних CI по своей ветке. Если пользователя не спрашивали о назначении, использовать **ветку данного чата**, не чужую.
3. Продолжить **текущий подпункт назначенного этапа**: проверить исходный BETA26, писать реальный Java/NeoForge-код, тестировать и фиксировать маленькими коммитами **только в своей ветке**. Не начинать с чистого листа.
4. В конце обновить **свой lane-status**, указать commit SHA, проверку, остаток. Не переписывать общий ROADMAP на feature-ветке.
5. Когда часть готова — открыть PR → `main`, перечислить доказательства. **Не выполнять merge без разрешения владельца.** После интеграции обновить общую карту на `main`.
6. Не трогать чужие классы, общий регистр/Gradle/CI без координации. Нет общего автоматического блокирования записи в GitHub: соблюдать границы особенно строго.
7. Полная сборка сервера и настоящий игровой клиент — разные доказательства. Не начислять баллы 59/100 без полного аудита. Не выдавать набранные команды за игровую механику.

Документ-источник для работы: `ROADMAP_RU.md`, дополнительные инструкции: `analysis/PARALLEL_WORKFLOW_RU.md`; старые handoff — исторический контекст, а не разрешение писать прямо в `main`.

## Координатор и ночная автономность

- Координатор: `chore/coordination` (только организационная документация; код механик запрещён).
- Контрольный план для 4 облачных сеансов: `chore/coordination:analysis/OVERNIGHT_AUTONOMY_RU.md`; журнал: `chore/coordination:analysis/lanes/coordination.md`.
- Полноценная автономная работа вне активного диалога требует **реально запущенных** задач Work / Codex Cloud; наличие веток само по себе ничего не запускает.
- Общая граница: коммиты в собственные ветки — допустимы; force-push, merge, удаление исходников/веток, прямые коммиты в `main` — запрещены без отдельного одобрения владельца.

## Навигация и coordinator lane

[Индекс документации](analysis/DOCUMENT_INDEX_RU.md); координатор работает только в `chore/coordination` и предлагает draft PR. Никаких merge, прямых коммитов в `main`, редактирования чужих веток. Красный GitHub Actions и зелёный CircleCI `verify-connection` — не заменяют полный game/restart тест конкретной механики.
