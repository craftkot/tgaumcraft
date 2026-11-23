# Lane: closure-near-complete

- Рабочая ветка: `feature/closure-near-complete`
- PR: [#4](https://github.com/craftkot/tgaumcraft/pull/4) — **Draft / Open / Unmerged**
- База последней проверки: `main` = `a4280834adc5a20b6951098af937d30ca4aeca56`
- Кодовый HEAD на начало этой записи: `7a84c0fd97921aa52bf630745e1a5b57a4707963`
- Фиксированный аудит: **59/100**. Баллы не начислять без игровых критериев и аудиторского доказательства.

## Границы lane

Продолжение существующего порта Thaumcraft 6.1.BETA26 → Minecraft 1.21.1 NeoForge. Текущий фокус — узкий end-to-end Wisp zap/FX smoke в рамках визуального паритета; это не создание нового мода и не изменение чужих веток. Не менять `main`, чужие feature-ветки, общие регистры, Gradle или общий CI без координации.

## Последняя инженерная итерация

На SHA `3d087fbe0cd0eb0c8882fc4a5c8f607db5d86cdb` реальный клиент уже подтвердил:
- offline geometry/client contract и generated Bash syntax — PASS;
- реальный Survival click попал в тот же Wisp ID 593;
- `TC_SMOKE_WISP_HURT=YES accepted=true`, `TC_SMOKE_WISP_TARGET_SET=YES`;
- Wisp отправил zap, клиент получил payload, renderer сообщил 29 точек, texture `thaumcraft:textures/misc/essentia.png`, blend `SRC_ALPHA_ONE`, depthWrite OFF.

Но run [#45258224263](https://github.com/craftkot/tgaumcraft/actions/runs/45258224263) завершился FAILURE: три post-hit capture попытки не получили camera aimDot ≥ 0.99; screenshot verifier был пропущен. Поэтому три кадра FX, окончание эффекта и transient pixel delta **не доказаны**.

Исправление на текущем HEAD `7a84c0fd`:
- `WispAttackSmokeProbe` включает трекинг только при `THAUMCRAFT_SMOKE=1` и только после реального `AttackEntityEvent` по конкретному Wisp ID;
- `ClientTickEvent.Pre` направляет камеру на центр AABB этого же Wisp, не меняя его AI, позицию, цель, урон или FX;
- shell driver после попадания только возвращает игрока на тестовую платформу; camera aim принадлежит клиентскому smoke probe;
- verifier требует marker `TC_SMOKE_WISP_CAMERA_TRACK=YES` и отдельно проверяет server teleport marker attack fallback;
- offline contract проверяет smoke gate, точный entity ID и отсутствие одноразового server-facing snap при post-hit capture.

Кодовые коммиты:
- `6f641ab9254a10f01e67f40be0e18cb3bbe58683` — smoke-only client camera tracker;
- `99f9be01edbf86848aeacc500fce5898eabd76e0` — re-anchor player only;
- `e83283d1096351d573506875359c6738414d7850` — verifier provenance/tracking gate;
- `c532725c124194376c6bf9cb1d28ff84f3afa17d` — regression contract;
- `7a84c0fd97921aa52bf630745e1a5b57a4707963` — assertion that tracker stays behind smoke env gate.

## CI evidence и ограничения

На предыдущем exact SHA `3d087fbe`:
- [Full Port CI #45258224496](https://github.com/craftkot/tgaumcraft/actions/runs/45258224496) — SUCCESS: Core parity 10,054 assertions; Crystal Farmer 159; crystal growth 186; outline 111; jar 51; Essentia source search 15,062; infusion resource ledger 20; NeoForge build SUCCESS; dedicated server `Done (8.770s)`; JAR uploaded.
- [Aura/Crystal GameTests #45258224431](https://github.com/craftkot/tgaumcraft/actions/runs/45258224431) — SUCCESS, 29/29 required tests; Wisp payload/sound/damage and aura/crystal cases; two-process persistence restart passed.
- [Wisp real client #45258224263](https://github.com/craftkot/tgaumcraft/actions/runs/45258224263) — FAILURE только на post-hit camera capture; actual hit/damage/target/payload/render markers присутствуют.

На текущем HEAD `7a84c0fd` GitHub Actions run не найден через commit workflow lookup, combined status не содержит checks. Из доступных инструментов нет workflow_dispatch; не выдавать предыдущие SHA за проверку текущего. Требуется запустить `closure-wisp-zap-client.yml` и `ci.yml` на точном HEAD через обычный GitHub push/Actions dispatch и получить их логи.

## Текущая оценка и следующий шаг

- Общий индекс: **59/100**, без изменения.
- Модели/звуки/частицы/локализация/UI: **4/6**, не закрыто.
- Аура/кристаллы/мир: **5/9**, не закрыто.
- PR #4 остаётся 367 файлов / 1,779 коммитов впереди main на момент проверки; это крупный интеграционный риск, не готовность к merge.

Следующее действие: получить exact-SHA Wisp client run на `7a84c0fd`; проверить offline contract, `bash -n`, same-ID real hit, camera tracking, payload, три FX кадра, expiry и pixel delta. Затем — exact-SHA full NeoForge build/server smoke. Если трекинг не удержит aimDot, инструментировать tick/event ordering; не ослаблять порог 0.99 и не синтезировать hit/payload/FX.
