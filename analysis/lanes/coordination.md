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

## 18.10.2026 09:36 МСК — периодический контроль HEAD / CI

- Срез refs: `main=a4280834adc5a20b6951098af937d30ca4aeca56`; `chore/coordination=dac337b87e547d5cfe8db8ad8ee86ae07a245482` (до этого отчёта); `feature/alchemy=b1a243778a312f7a3074c0d96ff128c71a34b79c` (+8 к main), `feature/research=61265a9b7e3a786c40c05a11dc234f37142fe163` (+4), `feature/infusion=fd10ad938abb5685c3da9939f6fdbba45ae887ac` (+15). Все три основаны на том же main; compare с main не показал общих изменённых путей между feature-ветками. Это **не гарантия** отсутствия семантических конфликтов между системами.
- CircleCI на HEAD: [alchemy #246 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/246), [research #250 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/250), [infusion #251 SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/251) по commit statuses. Важная поправка к старому срезу выше: `verify-connection` **запускает** `bash tools/run_core_parity.sh`, проверку ассетов и shell/python проверки; это **не просто проверка связи**, но и **не полная NeoForge-компиляция/сервер/клиент**. В `run_core_parity.sh` отсутствуют новые `ResearchAidDiscoveryParityTest` и `ResearchAidTheoryFlowParityTest`; зелёный #250 **не подтверждает выполнение этих отдельных новых research-тестов** (их независимый runner требует отдельного запуска).
- Для alchemy SHA `6dd35dea20` (временные CI-параметры): [#240 full NeoForge compile SUCCESS](https://circleci.com/gh/craftkot/tgaumcraft/240), **[#241 real-minecraft-client FAILURE](https://circleci.com/gh/craftkot/tgaumcraft/241)**. После него ветка вернула тяжёлые CI-флаги в false; текущий HEAD `b1a2437` имеет #246 Core SUCCESS, **но не новый full/game прогон именно этого HEAD**. Причину #241 нельзя утверждать без job log / client.log. M04.1/M04.2 остаются непринятыми.
- Infusion #251 подтверждает стандартный CoreParity, но не full NeoForge, не оплаченный survival-ритуал, не сохранение/рестарт. Для research #250 не подтверждены отдельный research aid runner, NeoForge/UI и gameplay. У всех трёх HEAD новые GitHub Actions runs отмечены FAILURE; например [alchemy](https://github.com/craftkot/tgaumcraft/actions/runs/39104388965), [research](https://github.com/craftkot/tgaumcraft/actions/runs/39104569715), [infusion](https://github.com/craftkot/tgaumcraft/actions/runs/39104630221). У проверенного GH job research `verify` нет доступных исполнявшихся steps; причину не домысливать.
- [PR #1](https://github.com/craftkot/tgaumcraft/pull/1) и [PR #2](https://github.com/craftkot/tgaumcraft/pull/2) остаются draft; в main не сливать. PR #2 содержит **предыдущий** SHA/CI-срез; его описание обновляется текущим разделом. M04/M06/M07 🟡, общая оценка **59/100** без пересчёта. Старые фразы «M07 ещё не начат» внутри исторических lane-записей — архив, а не инструкция. Массовую чистку/удаление дублей не выполнять.
