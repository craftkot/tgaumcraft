# Навигатор документов — Thaumcraft 6

**Приоритет:** свежий SHA/код/подтверждённый CI → действующие [ROADMAP](../ROADMAP_RU.md), [AGENTS](../AGENTS.md), [правила параллельной работы](PARALLEL_WORKFLOW_RU.md) → журнала своей ветки → исторические отчёты. Контрольный индекс: **59/100**, [аудит 16 октября](PROGRESS_AUDIT_2026_10_16.md); не путать со старыми 27%, 65%, 86%.

## Единственные действующие документы

- [ROADMAP_RU.md](../ROADMAP_RU.md) — этапы M00–M17, контрольные критерии и интеграционные статусы.
- [AGENTS.md](../AGENTS.md) — права и ограничения всех чатов.
- [PARALLEL_WORKFLOW_RU.md](PARALLEL_WORKFLOW_RU.md) — ответственность команд, общие файлы, порядок PR.
- [lanes/alchemy.md](lanes/alchemy.md) (`feature/alchemy`, M04/M05); [lanes/research.md](lanes/research.md) (`feature/research`, M07); [lanes/infusion.md](lanes/infusion.md) (`feature/infusion`, M06). **Читать именно журнал соответствующей ветки**, не копию в `main`.
- [lanes/coordination.md](lanes/coordination.md) (`chore/coordination`) — организационный аудит. [OVERNIGHT_AUTONOMY_RU.md](OVERNIGHT_AUTONOMY_RU.md) — правила облачных сеансов, пока только в coordination-ветке.
- [README.md](../README.md) — общая точка входа, но ранняя техническая сводка в нём историческая.

## Полезные предметные доказательства — сохранить

[THEORYCRAFT_CARD_PROGRESS_RU.md](THEORYCRAFT_CARD_PROGRESS_RU.md), [AID_BYTECODE_AUDIT_2026_10_10.md](AID_BYTECODE_AUDIT_2026_10_10.md), [HANDOFF_2026_10_17_ALCHEMY_GAMEPLAY_VERIFIED_RU.md](HANDOFF_2026_10_17_ALCHEMY_GAMEPLAY_VERIFIED_RU.md), [PORT_QUALITY_IMPROVEMENTS_RU.md](PORT_QUALITY_IMPROVEMENTS_RU.md). Прошлые игровые проверки **не доказывают текущий HEAD**.

## Исторические снимки — логический архив, физически НЕ перемещены

`HANDOFF_*.md`, `HANDOFF_NEXT_CHAT*.md`, `NEXT_CHAT_START_PROMPT_RU.md`, `CONTINUATION_73_*.md`…`CONTINUATION_83_*.md`, `CURRENT_STATE_2026_10_12.md`, `midpoint_audit_50.md`, `progress_53.md`, [GITHUB_MIGRATION.md](../GITHUB_MIGRATION.md) — история. Слова «актуально», «читать первым», старые SHA и советы писать прямо в `main` **не действуют**. Переименования/перемещения отложены: прежние ссылки должны остаться работоспособными.

`analysis/javap/**`, `*javap*.txt`, `*audit*.txt`, `core_parity_*.txt` — bytecode/аудиторские доказательства, не удалять автоматически.

## Найденные совпадения байтов (один Git blob SHA)

| Группа | Что выяснено | Действие |
|---|---|---|
| `build_baseline.log` / `jar_inventory.json` | Полные дубликаты; **оба содержат JSON инвентаризации оригинального JAR**, `.log` неверно описывает содержимое | Кандидат удалить первый **после проверки зависимостей и ссылок**, пока оставить |
| `core_parity_test_recovery.txt` / `core_parity_test_stage10.txt` | Дублированы старые результаты 4667 assertions | Оставить как возможные исторические checkpoints |
| `javap/ThaumcraftCapabilities.txt` / `javap/ThaumcraftCapabilities_full.txt` | Точный дубль bytecode-справки | Оставить до проверки использования |

**Инвентаризация `main`:** 1069 Git tree entries; в `analysis/` 90 blob-файлов (~1242 KiB). Не удалены тесты, ресурсы Thaumcraft, исходники, коммиты или пользовательские данные.

## CI / слияния

CircleCI `verify-connection` (в том числе CoreParity) ≠ полный NeoForge/JAR ≠ работающий dedicated server ≠ клиентский игровой сценарий ≠ world save/unload/restart. GitHub Actions `failure` без job-step logs сам по себе не доказывает дефект Java. Сначала проверить точный SHA и CI ссылки в [журнале координатора](lanes/coordination.md); затем потребовать нужную проверку от профильного чата.

**Координатор:** только `chore/coordination`, только документы и безопасный контроль PR, **никаких merge без разрешения владельца**.
