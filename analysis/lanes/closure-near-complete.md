# Lane: closure-near-complete

- Рабочая ветка: `feature/closure-near-complete`
- PR: [#4](https://github.com/craftkot/tgaumcraft/pull/4) — **Draft / Open / Unmerged**
- База последней проверки: `main` = `a4280834adc5a20b6951098af937d30ca4aeca56`
- Кодовый HEAD после follow-up cleanup: `98d2cfad75d83fc21498baf271ec20e525771652`
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
- `7a84c0fd97921aa52bf630745e1a5b57a4707963` — assertion that tracker stays behind smoke env gate;
- `c95cf79014dbe967e671f81dc7a425b83240cf0f` — remove duplicate platform re-anchor marker;
- `98d2cfad75d83fc21498baf271ec20e525771652` — assert the re-anchor marker is emitted exactly once.

## CI evidence и ограничения

На предыдущем exact SHA `3d087fbe`:
- [Full Port CI #45258224496](https://github.com/craftkot/tgaumcraft/actions/runs/45258224496) — SUCCESS: Core parity 10,054 assertions; Crystal Farmer 159; crystal growth 186; outline 111; jar 51; Essentia source search 15,062; infusion resource ledger 20; NeoForge build SUCCESS; dedicated server `Done (8.770s)`; JAR uploaded.
- [Aura/Crystal GameTests #45258224431](https://github.com/craftkot/tgaumcraft/actions/runs/45258224431) — SUCCESS, 29/29 required tests; Wisp payload/sound/damage and aura/crystal cases; two-process persistence restart passed.
- [Wisp real client #45258224263](https://github.com/craftkot/tgaumcraft/actions/runs/45258224263) — FAILURE только на post-hit camera capture; actual hit/damage/target/payload/render markers присутствуют.

На точном code HEAD `98d2cfad` созданы новые run: Wisp client [#45262255344](https://github.com/craftkot/tgaumcraft/actions/runs/45262255344), Full Port CI [#45262255633](https://github.com/craftkot/tgaumcraft/actions/runs/45262255633), Aura/Crystal GameTests [#45262255796](https://github.com/craftkot/tgaumcraft/actions/runs/45262255796) — все QUEUED на последнем опросе; CircleCI `verify-connection` PENDING. Результатов exact-SHA пока нет. Не выдавать предыдущие SHA за проверку текущего.

## Текущая оценка и следующий шаг

- Общий индекс: **59/100**, без изменения.
- Модели/звуки/частицы/локализация/UI: **4/6**, не закрыто.
- Аура/кристаллы/мир: **5/9**, не закрыто.
- PR #4 остаётся 367 файлов / 1,783 коммита впереди main на момент проверки; это крупный интеграционный риск, не готовность к merge.

Следующее действие: получить exact-SHA Wisp client run на `7a84c0fd`; проверить offline contract, `bash -n`, same-ID real hit, camera tracking, payload, три FX кадра, expiry и pixel delta. Затем — exact-SHA full NeoForge build/server smoke. Если трекинг не удержит aimDot, инструментировать tick/event ordering; не ослаблять порог 0.99 и не синтезировать hit/payload/FX.

## 23.11.2026 — Wisp offline contract regression (run #45262255344)

- Exact-SHA `98d2cfad75d83fc21498baf271ec20e525771652`: [Wisp Actions #45262255344](https://github.com/craftkot/tgaumcraft/actions/runs/45262255344) **FAIL** до запуска Minecraft: `tools/test_wisp_zap_client_smoke.py:489` всё ещё требовал `facing entity @s eyes` в post-hit re-anchor, хотя текущий сценарий намеренно делегирует наведение клиентскому smoke-only tick tracker. Geometry 21/21 и atlas 24/24 PASS.
- Кодовый commit `eb786d709ae4aa824ab54c0207e66591a7e7bd95`: заменено устаревшее положительное требование на отрицательный контракт (`facing entity @s eyes` запрещено в re-anchor). Раннее melee-position наведение остаётся отдельным проверяемым условием; пороги попадания, урон и FX не ослаблены.
- Новый exact-SHA Wisp CI / Minecraft / Gradle на `eb786d7` пока **не подтверждены**. Не считать игровой приёмкой. Индекс 59/100, M16 4/6 неизменны.
- Следующее: проверить CI нового SHA, затем получить настоящий post-hit capture трёх кадров FXBolt, expiry и pixel delta; отдельно — нативный Salis chunk tickets/network-send audit.


## 2026-11-23 — repair Wisp fallback verifier regression

- Fresh main inputs reread for this run: `AGENTS.md`, `ROADMAP_RU.md`, `analysis/PARALLEL_WORKFLOW_RU.md`, and `analysis/PROGRESS_AUDIT_2026_10_16.md`. Main HEAD remains `a4280834adc5a20b6951098af937d30ca4aeca56`; fixed rubric remains **59/100** (M16 **4/6**, M08 **5/9**).
- Exact Wisp workflow [#45264982349](https://github.com/craftkot/tgaumcraft/actions/runs/45264982349) on `eb786d709ae4aa824ab54c0207e66591a7e7bd95` failed in the offline Python contract, before generated Bash syntax and before Minecraft launch. Geometry 21/21 and atlas 24/24 passed.
- Job log pinpointed a test-fixture newline bug: the synthetic fallback status contained the literal two-character sequence `\\n`, so `WISP_ATTACK_CAMERA_REAIM=LIVE_ENTITY_FACING` was not on a new status line. The verifier regex therefore returned `None`; the verifier had already recorded the missing-provenance error but then dereferenced `.group(1)`, raising `AttributeError`.
- Commit [`2a36381f85ad5c6735806caa661625616e760cd4`](https://github.com/craftkot/tgaumcraft/commit/2a36381f85ad5c6735806caa661625616e760cd4) fixes the fixture to emit an actual newline, guards both branches against absent provenance, and adds a regression requiring missing attack-camera provenance to fail closed without an exception.
- Changed files: `tools/test_wisp_zap_client_smoke.py`, `tools/verify_wisp_zap_client.py`. No Java, Wisp AI, damage, network, renderer, Gradle, shared registry, or workflow code changed.
- Overlap check: new M12 PR #9's 64 changed paths contain neither Wisp verifier nor Wisp smoke test. The previous lane audit recorded a full 61-PR overlap scan for the other open PRs; no Wisp-path overlap was reported. PR #4 remains the owning branch/PR.
- On parent SHA `eb786d7`, Full Port CI [#45264982482](https://github.com/craftkot/tgaumcraft/actions/runs/45264982482) passed NeoForge build and dedicated-server smoke; that does not compensate for the failed Wisp offline contract. Aura/Crystal GameTests [#45265013110](https://github.com/craftkot/tgaumcraft/actions/runs/45265013110) passed on the documentation parent `f57e5e18`.
- Exact new-SHA checks on `2a36381`: Wisp real-client [#45266691734](https://github.com/craftkot/tgaumcraft/actions/runs/45266691734) and Full Port CI [#45266691680](https://github.com/craftkot/tgaumcraft/actions/runs/45266691680) were **QUEUED** at the last poll; CircleCI `verify-connection` was **PENDING**. No exact-SHA test PASS and no Minecraft gameplay result are claimed.
- PR [#4](https://github.com/craftkot/tgaumcraft/pull/4) remains **OPEN / DRAFT / UNMERGED**; it is 367 files and roughly 1,787 commits ahead of main, so it remains an integration risk and must not be merged as a unit.
- Next: inspect exact-SHA Wisp run #45266691734 and Full Port CI #45266691680. Only if the offline contract and generated `bash -n` pass should the real client be considered; require same-ID hit, accepted damage, target, payload, three real FX frames, expiry and pixel delta before any M16 progress change.


## 2026-11-23 — exact-head Wisp real-client evidence #45266789911

- На PR #4 HEAD `78556d72a10686b8da861bc5171a8a5c45bf0b91` Wisp real-client [#45266789911](https://github.com/craftkot/tgaumcraft/actions/runs/45266789911) завершился **FAIL**, но уже после реального запуска Minecraft и принятого удара. Подтверждены `TC_SMOKE_WISP_CLIENT_ATTACK=YES`, `TC_SMOKE_WISP_HURT=YES accepted=true`, `TC_SMOKE_WISP_TARGET_SET=YES`, `TC_SMOKE_WISP_CAMERA_TRACK=YES`; захват после удара: тот же Wisp `id=567`, tick=115, distance=6.0100, aimDot=0.9999600135971344, `WISP_CAMERA_CAPTURE_TELEMETRY=PASS`.
- Точный блокер: `WISP_AIM_SCREENSHOT_ERROR: 78-wisp-aim-check.png crosshair ROI contains only 0 Aer-tinted pixels; expected at least 12`. Значит серверное попадание и клиентский трекинг подтверждены, но видимость Wisp на скриншоте и три кадра FXBolt/expiry/pixel delta **не доказаны**. Не ослаблять ROI и не объявлять M16 готовым; сначала исследовать фактический PNG из [артефакта #14864544028](https://github.com/craftkot/tgaumcraft/actions/runs/45266789911/artifacts/14864544028), FOV, окклюзию и проекцию при distance=6.01.
- На том же HEAD [Full Port CI #45266789837](https://github.com/craftkot/tgaumcraft/actions/runs/45266789837), [Aura/Crystal GameTests #45266789612](https://github.com/craftkot/tgaumcraft/actions/runs/45266789612) и [two-client Salis #45266790072](https://github.com/craftkot/tgaumcraft/actions/runs/45266790072) — SUCCESS. Это не независимое доказательство реальных chunk tickets и сетевых отправок при unload/reload.
- В этой итерации Java/NeoForge и тестовые скрипты не изменялись: без анализа PNG нет доказанного дефекта для безопасного патча. Локальные Gradle/client не запускались. Аудит остаётся **59/100**, M16 **4/6**, M08 **5/9**. Следующий шаг — скачать/исследовать aim-check PNG, затем сделать минимальное исправление только при доказанной причине и повторить нативную приёмку.


## 2026-11-23 — Wisp transient capture sequencing (diagnostic + smoke contract)

### Fresh evidence inspected
- Real client artifact: [Wisp Client #45266789911](https://github.com/craftkot/tgaumcraft/actions/runs/45266789911), artifact `wisp-client-45266789911.zip`.
- The log records `TC_SMOKE_WISP_ZAP_RENDERED` at a Wisp tick around 97, while the post-hit aim screenshot is captured at tick 115: at least 18 ticks later. The BETA26 client effect is explicitly three ticks (`age < 3.0`). The previous script captured screenshots 80–83 only after the later aim screenshot, so it could not observe the original transient bolt. This is a confirmed test-driver ordering defect.
- The aim screenshot was taken under `/time set noon`; the crosshair ROI contained a bright sun/halo and zero Aer-tinted pixels. The halo is a plausible washout source for the additive layer, but this is not yet a proven renderer defect.
- The stage9 JAR from Full Port CI #45266789837 contains `particles.png` (1024×1024) and `auranodes.png` (2048×2048); the selected atlas cells are non-empty. The client log has no recorded load failure for those atlas paths. Missing packaged texture files are therefore not the current explanation.

### Changes on `feature/closure-near-complete`
- `src/neoforge/java/dev/ehtid/thaumcraft/client/WispZapClientEffects.java`: smoke-only per-age-tick markers now include source/target, client game time, Wisp tick, createdAt, ageTick, point count and a three-tick expiry marker. Production lifetime remains exactly three ticks; no artificial extension.
- `tools/client_wisp_zap_smoke.py`: fixes fixture time to midnight before both baseline captures; captures age 0/1/2 only after their matching completed AFTER_LEVEL frame and **before** the later camera re-anchor; waits for expiry before screenshot 83; checks `essentia.png`, `auranodes.png` and `particles.png` load errors.
- `tools/verify_wisp_zap_client.py`: requires three distinct captured Wisp ticks for one createdAt event, matching payload/render/expiry evidence, correct ordering before camera re-anchor, and all Wisp textures.
- `tools/test_wisp_zap_client_smoke.py`: adds an offline Bash harness for the age 0/1/2 → expiry capture sequence and updates positive/negative verifier fixtures.

### Code/test commits
- Java per-frame telemetry: [12e4cb0](https://github.com/craftkot/tgaumcraft/commit/12e4cb0bc37b65b9b91cda26cf171981b2386be3)
- Capture order/night fixture: [522e907](https://github.com/craftkot/tgaumcraft/commit/522e907d6abc4677a70e626618be0eda2901cf79)
- Three-frame/expiry verifier: [3e1a56a](https://github.com/craftkot/tgaumcraft/commit/3e1a56a28c3618e2954a51aa83b5eda5d02c403b)
- Offline sequence contract: [a2da410](https://github.com/craftkot/tgaumcraft/commit/a2da4109aa0e5b33906bd52221c3fa969d9c6e86)
- All three texture load checks: [f9586d2](https://github.com/craftkot/tgaumcraft/commit/f9586d247b30c0d4968bc8423cc66bff19294cc1)
- Verifier requires all texture checks: [e417b18](https://github.com/craftkot/tgaumcraft/commit/e417b183ad760fa23f3ef94c797e53bc1de12923)
- Updated regression fixtures: [6f48ba4](https://github.com/craftkot/tgaumcraft/commit/6f48ba4ab87547882399a2717fa0fc21b61a1d1b)

### Validation state on exact code SHA `6f48ba4`
- New exact-SHA workflows are **QUEUED**, not passed: Wisp client [#45278878617](https://github.com/craftkot/tgaumcraft/actions/runs/45278878617), Full Port CI [#45278878403](https://github.com/craftkot/tgaumcraft/actions/runs/45278878403), Wisp geometry [#45278878539](https://github.com/craftkot/tgaumcraft/actions/runs/45278878539), Aura/Crystal GameTests [#45278878899](https://github.com/craftkot/tgaumcraft/actions/runs/45278878899). The combined CircleCI status is still pending.
- No local Python contract, generated Bash `bash -n`, NeoForge compile or new Minecraft client result is claimed in this run. The earlier stage9 JAR/Full Port result is from an older SHA and is not carried forward.
- All 61 other open PRs (#1–#3 and #5–#62) were checked for overlap with the five changed Wisp/tool/lane paths; none overlap. No shared registries, Gradle, CI YAML, global localization, other feature branches or main were changed.

### Acceptance and next action
- Fixed audit remains **59/100**; M16 remains **4/6**, M08 **5/9**. No point awarded and no category closed.
- First inspect the exact-SHA Wisp workflow. It must prove captured ageTick 0, 1, 2 on three distinct Wisp ticks, then expiry; only afterward inspect the midnight aim screenshot for at least 12 Aer-tinted pixels. If the first rendered age is already 2, inspect packet acceptance/client-tick scheduling; do not extend the BETA26 lifetime. If the Wisp remains invisible at midnight, inspect the actual renderer/shader/UV path rather than lowering the pixel threshold.
