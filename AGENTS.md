# AGENTS.md — Thaumcraft 6: правила для параллельных чатов

**Главный план — `main:ROADMAP_RU.md`**, порядок совместной работы — `main:analysis/PARALLEL_WORKFLOW_RU.md`. Это продолжение существующего переноса Thaumcraft 6.1.BETA26 на Minecraft 1.21.1 NeoForge. **Никогда не создавать проект заново, не заменять дорожную карту и не менять автоматически свою ветку.**

## Ветки и владельцы задач

| Чат | Работа только в ветке | Этап | Собственный журнал |
|---|---|---|---|
| Алхимия / основной чат | `feature/alchemy` | M04 → M05; сейчас M04.1 (fixture #209 исправлен, новый gameplay не подтверждён) | `analysis/lanes/alchemy.md` |
| Исследования / второй чат | `feature/research` | M07.1/M07.2 🟡; непроверенные помощники/карточки | `analysis/lanes/research.md` |
| Инфузия / третий чат | `feature/infusion` | M06.1 🟡; ledger и checkpoint без подтверждённого survival world restart | `analysis/lanes/infusion.md` |
| Големы / отдельный разработчик | `feature/golems` | M10 🟡; M53 — developer evidence dedicated server + 2 TCP-клиента, owner isolation (exit-коды сессий недоступны); M54 прежний HEAD `052ef662` — developer evidence full NeoForge, 252/252 required + 30/30 optional + 4/4 FD, directional cold WRITE/READ exit0 и read-only disk audit. Координатор не воспроизводил; CircleCI #487 подтверждает только Core, Actions #40204694716 FAILURE без доступных steps/logs. Split-save 13→26 iron/17→34 gold открыт; общий четырёхветочный runtime не проверен; M10 не принят | `analysis/lanes/golems.md` |

**Актуализация M10.55 (25.10.2026, 18:25 МСК):** `feature/golems` HEAD [`9a19b396`](https://github.com/craftkot/tgaumcraft/commit/9a19b3967cfd4cdf4ab537532faef7f1573151b8), два новых коммита после M54 — только gated native tests/evidence/docs, без production. Автор заявляет full NeoForge build + 33/33 optional PASS для UP/DOWN; 252 required/4 FD и vertical cold не повторялись. [CircleCI #490](https://circleci.com/gh/craftkot/tgaumcraft/490) SUCCESS по `verify-connection` (Core), [Actions #40205762871](https://github.com/craftkot/tgaumcraft/actions/runs/40205762871) FAILURE, `steps=null`, причина неизвестна. Сборка четырёх веток, общий сервер/клиент/сохранение мира не проверены; split-save 13→26 iron/17→34 gold открыт, M10 не принят. Предыдущий M54 в таблице — исторический срез.

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
