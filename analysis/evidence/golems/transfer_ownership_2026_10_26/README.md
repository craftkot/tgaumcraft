# M57 — исправлен реальный reentry-дюп и потеря груза

26.10.2026 (МСК; raw logs UTC). Изменён production-код Containers/owner reclaim, добавлен общий `GolemInventoryTransfer` scope. Это исправление обычных server callbacks, не обещание атомарности независимых region/entity stores при аварии.

## Исправленное поведение

- Перед native spawn/insert фактический payload уже снят с руки/recovery. Вложенный deposit/extract/reclaim того же работника отклоняется, другие работники не блокируются. Старый spawn callback принимал ещё9 из тех же9; теперь в мире остаётся ровно один paper9.
- После callback возвращается фактический remainder с его компонентами. Старый original snapshot больше не перезаписывает руку, в которую callback передал независимый груз.
- При canceled spawn фактический payload снимается с rejected ItemEntity и возвращается ровно один раз. Failed reclaim recovery восстанавливается в начало своей очереди, сохраняя прежний retry-контракт.
- Если во время reclaim callback добавил реальный новый груз, работник сохраняется живым. Созданный именно этой операцией placer при необходимости очищается/отзывается; обычная следующая попытка возвращает оставшийся груз и один placer.
- После внешних stock getters проверяется, что рука всё ещё владеет тем же payload. Если callback заменил paper9 на реально профинансированные paper12, устаревшая операция возвращает0, а следующий вызов передаёт все12.

Scope transient/server-thread/per-worker, освобождается через try-with-resources. NBT-формат, цены, рецепты и обычные AI правила не менялись. Ownership guards — современная защита NeoForge-интеграции, не новая игровая награда.

## Проверки

11 новых required native GameTest используют реальные scoped EntityJoinLevelEvent и ItemStackHandler, независимо профинансированные callback inputs, actual drops/remainders и успешные retries. Они входят в обычный namespace thaumcraft, общий required набор252→263.

TDD/execution evidence сохранено без превращения провалов в PASS: первоначальные8 failures; затем обнаруженная регрессия recovery placement исправлена в production без ослабления старого теста;2 funded-reclaim failures; отдельный stock-getter failure. Уточнены только отрицательные interaction expectations: публичный Mob.interact превращает внутренний FAIL в PASS, поэтому проверяется отсутствие consumesAction. Полные counts/metadata проверки сохранены.

Финал: **263/263 required +33/33 optional +4/4 real Farmer’s Delight PASS**, full build3m40s/exit0 и GOLEM_RESTART_READ_ASSERTIONS_OK. Предыдущая отдельная WRITE JVM с тем же свежим миром также прошла,3m58s combined build/native/write/exit0. Свежий Java21 Core39962/2909/allrunner PASS. [Фактические excerpts](assertions.txt).

Независимый read-only MCA/SavedData после READ: один исходный UUID/owner, named iron37, recovery diamond3, destination gold11/source emerald23,3 seals,0grounditems. [Disk observations](disk_observations.json). Native source/ghost pnbt/limits/owner/rank/color и отсутствие stale work проверены самой restart-пробой. World NBT не редактировался.

[JAR](jar.json): CRC/740Java21classes/unique EN-RU JSON PASS; SHA256 `9ddae2452516b08318e135d7810ab374aa1b8bd984762e686b2bc47ec8afb8b9`. Новый graphical run не запускался: изменена серверная ownership-логика, client packets/NBT schema не менялись.

## Границы

M43 остаётся известным vanilla crash-consistency пределом: block chunks и entity regions сохраняются независимо, в том числе у vanilla hopper. Этот дефект не объявлен исправленным; перестановка flush/standalone WAL без общего participant protocol дала бы дюп или потерю. Исправление такой глобальной атомарности не подменяет перенос игровых механик M10.

IItemHandler не обещает rollback при исключении после частичного принятия. Поэтому нет опасного возврата копии whole request. Произвольные внешние inventory/world side effects также не становятся атомарными. Native recipe/progression/visual зависимости остаются в design/lane, новые бесплатные recipes не добавлялись.

Воспроизведение основных regressions: `gradle --offline --init-script tools/golems.gradle build -x test runGolemGameTest`. Остальные команды и fresh-world restart flags соответствуют существующему `nbt` протоколу; каталог этой проверки `build/golem-ownership-m57-restart`, порт25630. Старые миры не перезаписывались. Полные логи `/workspace/reference/golems-m57-*.log`.
