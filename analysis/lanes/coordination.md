# Главный координатор — chore/coordination

**Статус:** 🟡 организационная ветка подготовлена; реальные автономные облачные сеансы запускает пользователь в Work/Codex Cloud.
**Главный план:** `main:ROADMAP_RU.md`.
**Ночная схема:** [`analysis/OVERNIGHT_AUTONOMY_RU.md`](../OVERNIGHT_AUTONOMY_RU.md).
**Ограничение:** код игровых механик запрещён, `main` не меняется, слияние чужих PR без пользователя запрещено.

## Первый цикл аудита
1. Прочитать свежие HEAD `main`, `feature/alchemy`, `feature/research`, `feature/infusion` и проверить CI, статусы работ и drift документации.
2. Найти противоречащие инструкции в старых HANDOFF; безопасно пометить как исторические, не удалять важные исследования.
3. Собрать таблицу несинхронизированных shared files / registry edits / потенциальных конфликтов будущих PR.
4. Отделить безопасный организационный мусор от рискованных/важных файлов; без подтверждения НЕ удалять исходники/ресурсы/доказательства.
5. Сохранить организационный коммит только в `chore/coordination`; подготовить draft PR → main; не merge.

## Журнал
- 18.10.2026: рабочая ветка создана от `main` `a4280834adc5a20b6951098af937d30ca4aeca56`. Код механик не менялся. Составлены правила четырёх слотов; подготовлено безопасное ограничение автодействий.

## 18.10.2026 — проверка текущих веток / структура (coordination)

- База `main`: `a4280834adc5a20b6951098af937d30ca4aeca56`. `feature/alchemy`: `3ac3a47b1f6e37c183f20cc82b7841256025e821` (+4); `feature/research`: `1ac144163da70d98c67063cff6a4282e24bd08cb` (+2); `feature/infusion`: `41eb1c7528606489f0017c4c40b6f9e44df01bea` (+7). Ни одного одинакового изменённого пути в трёх сравнениях с `main`; общий класс на будущее не гарантированно безопасен.
- В `main` были устаревшие M06/M07 🔴 («ещё не начинались»); в рабочих ветках уже реальные кодовые коммиты. В предложенной `ROADMAP_RU.md` они 🟡. **59/100 без пересчёта.**
- CircleCI на исходных HEAD сообщает SUCCESS только `ci/circleci: verify-connection`: [alchemy #224](https://circleci.com/gh/craftkot/tgaumcraft/224), [research #227](https://circleci.com/gh/craftkot/tgaumcraft/227), [infusion #231](https://circleci.com/gh/craftkot/tgaumcraft/231), [coordination #233](https://circleci.com/gh/craftkot/tgaumcraft/233). Для тех же HEAD GitHub Actions FAILURE: [alchemy](https://github.com/craftkot/tgaumcraft/actions/runs/39077562424), [research](https://github.com/craftkot/tgaumcraft/actions/runs/39077769667), [infusion](https://github.com/craftkot/tgaumcraft/actions/runs/39078023887), [coordination](https://github.com/craftkot/tgaumcraft/actions/runs/39078770189). У исследовательского GH job `verify` список исполнявшихся steps пуст — источник ошибки не доказан. Для feature HEAD **нет подтверждённых новых full NeoForge/game/restart прогонов**.
- PR [#1](https://github.com/craftkot/tgaumcraft/pull/1) M06 и PR [#2](https://github.com/craftkot/tgaumcraft/pull/2) coordination — открытые draft; у PR #1 API `mergeable=true`, но проверки недостаточны, **не сливать**.
- `analysis/` содержит 90 tracked blob (~1242 KiB), 3 группы Git blob-дубликатов и множество исторических handoff. Индекс и явные предупреждения добавлены вместо опасного переименования, исходники / bytecode / тесты не удалены.
- GitHub `main` без branch protection: `protected:false`. Владелец может отдельно включить Ruleset `Require a pull request`; координатор не менял настройки и не останавливал другие чаты.
- Допустимые правки только в `chore/coordination`, код игровых механик и три feature-ветки не затронуты. **Ни один PR не объединён.**
