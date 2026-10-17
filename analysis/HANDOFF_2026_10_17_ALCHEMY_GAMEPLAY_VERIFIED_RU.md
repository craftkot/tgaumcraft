# Передача в новый чат — Thaumcraft 6.1.BETA26 → Minecraft 1.21.1 NeoForge
**Фиксация: 17.10.2026; репозиторий:** https://github.com/craftkot/tgaumcraft, ветка `main`.
**HEAD в момент фиксации:** `2b5fe4696d7b16b14df5e7ed085d9fc907421211`. **В новом чате ВСЕГДА заново читать HEAD**.

## Что пользователь хочет и как работать
- НЕ начинай перенос заново. Пользователь просит продолжать код существующего мода; команды «п», «давай», «продолжай» означают сразу работать, без уточняющих вопросов.
- Длительность одного подхода максимум ~20 минут, избегать долгого опроса статусов CircleCI, поскольку ChatGPT может прерваться по сети.
- Делать небольшие атомарные коммиты в `main` через GitHub. Не выдавать непротестированный исходник за проверенную механику.
- Пользователь хочет видеть списки механик, которые ещё остались, с различием: реализовано / Core parity CI / полная NeoForge build / реальный Minecraft.
- В новом чате, если запрошен общий список механик, начать ответ с заголовка «Что осталось перенести в Thaumcraft 6» и сначала перечислить НАЗВАНИЯ механик, а не файлы/CI-задания.
- Базовый аудит готовности **59/100** по зафиксированной шкале, НЕ «86%» из старых сообщений. После успешной новой алхимии нужно пересчитать категорию с объективными критериями и обновить аудит; не повышать автоматически.
- Источник оригинала: Thaumcraft 6.1.BETA26 (MC 1.12.2), репо для сверки `TheDarkTower314/Thaumcraft-6-Source-Code`.

## Последнее реально подтверждённое состояние (ВАЖНО: это уже не «не компилируется»)
1. Full NeoForge compile: **CircleCI #130 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/130 на коммите `d222d71`; закрыты 10 ошибок из пользовательского лога #125 коммитом `5a376a7` (8 вызовов `mayInteract(level,pos)`, метод `writeJarDropData`, лишняя переменная `requested`).
2. Full NeoForge **JAR packaging + dedicated server START**: **CircleCI #136 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/136, коммит `8f8de02`. Скрипт выполняет `gradle build -x test`, тестирует содержимое JAR, запускает `gradle runServer` и проверяет реальное сообщение `Done (`.
3. **РЕАЛЬНЫЙ клиент Minecraft и полная начальная цепочка алхимии: CircleCI #138 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/138, коммит `da231335`. `tools/client_smoke.sh` при `THAUMCRAFT_ALCHEMY_SMOKE=1` создаёт игровой мир, ставит печь + перегонный куб + трубу + банку, кладёт обычный булыжник и уголь (через реальный NBT-инвентарь блока), затем ОБЯЗАТЕЛЬНО требует четыре серверных маркера:
   - `TC_SMOKE_SMELTER_ITEM_CONSUMED=YES`
   - `TC_SMOKE_SMELTER_TO_ALEMBIC=YES`
   - `TC_SMOKE_ALEMBIC_TO_TUBE=YES`
   - `TC_SMOKE_TUBE_TO_JAR=YES`.
   Маркеры ставятся лишь после действительных транзакций в `SmelterBlockEntity`, `EssentiaTubeBlockEntity`, `EssentiaJarBlockEntity`. Провал любого маркера завершает сценарий с ошибкой. Значит, **именно эту базовую цепочку можно считать реально прошедшей игровой smoke**, а НЕ только compile/Core parity. В тесте есть PNG `40-alchemy-before-fuel`, `41-alchemy-smelter-to-jar`, `42-alchemy-jar-nbt-after-physical-transport` в артефактах #138; вид изображений отдельно не просмотрен в этом чате.
   ОГРАНИЧЕНИЯ: тест использует командное помещение обычного предмета в инвентарь печи, а НЕ самостоятельный survival-крафт/UI; не покрывает все виды труб, все печи и аспекты, соседние механизмы, повторную загрузку NBT, уникальные рецепты.
4. **CircleCI #137 verify-connection SUCCESS**, для `da231335`; **#139 SUCCESS** для текущего `main`, после отключения постоянного тяжёлого запуска `run_minecraft`. Текущие параметры `.circleci/config.yml`: `run_minecraft=false`, `run_compile=false` по умолчанию. Клиентский smoke выполняется с `THAUMCRAFT_ALCHEMY_SMOKE=1` при явном параметре.
5. Предыдущие реальные FIRSTSTEPS / теоретические исследования / матрица инфузии уже проверялись ранее (CircleCI #52, #63 и GitHub Actions 38883686079, 38883686444); не утверждать, что всё это не тестировалось.
6. Большая часть остальных подсистем оригинала ещё отсутствует/частична: фокусы и заклинания, големы, порча, Древние, существа, модели/генерация, полное инфузионное дерево и исследования.

## Перенесённая алхимия, которую нельзя снова считать отсутствующей
- Базовая алхимическая печь, таумиевая и пустотная печи (80/90/95%), инвентарь, топливо, GUI, эссенция/Flux.
- Настоящий перегонный куб на 128 единиц; физическая передача в трубную сеть.
- Обычная, фильтрующая, односторонняя, ограничительная, клапанная и буферная труба (буфер 10 смешанных единиц); модели/иконки оригинала.
- Обычная и пустотная банка, фиалы, этикетки, скобы, транспорт.
- Мехи, насадки, вентиляция. Некоторые устройства кодом уже есть, но **не все их режимы проверены в живой игре**.
- Алументум (4800 ticks furnace fuel), начальные рецепты/алхимия тигля.
- Последние коммиты: `ad8e64e`, `fd479f7`, `2ea9eeb` (три печи), `0b32337` + `ae7ad8b` + `3e3734b` (типы труб), `af37671` + `c1aa615` + `b34d28f` (буфер), `3b9ed84` + `f468a3a` + `49f951a` (bellows/aux/vent), `5a376a7` (fix compile), `8f8de02` (JAR/server), `da231335` (gameplay smoke), `2b5fe469` (выключить автозапуск тяжёлого smoke).

## Дополнение — кодовые рецепты после игрового smoke #138 (17.10.2026)

**Последний проверенный исходник: `ea873c1624d4b4783ed4d02b7fee5e87576f5348`.** Перед дальнейшей работой всё равно перечитывать текущий `main`.
Рецепты ниже внедрены через `ArcaneWorkbenchBlockEntity` + `ArcaneWorkbenchMenu` и Core Parity; **НЕ проходили свежую полную NeoForge compile и клиентский/survival-тест**.

- `2be89a1`, `87c1e06`: arcane-контроль рецептов клапана, ограничительной и односторонней трубы, Core parity; #142 success.
- `09184e3`: оригинальный рецепт магических мехов (BELLOWS, 25 Vis, Aer); #143 success.
- `1dff706`, `1e846fc`: буфер эссенции (TUBES, 25 Vis, PVP/TWT/PRP), защита от неверного фиала; #144/#145 success.
- `4cd859e`, `f208645`: зарегистрированы `filter`, `plank_greatwood`, добавлен оригинальный arcane-рецепт перегонного куба (ESSENTIASMELTER, 50 Vis, Aqua); #146/#147 success.
- `c9d9fcb`, `ddb6710`: `log_silverwood` и `plank_silverwood` плюс рецепт досок; arcane-класс рецепта фильтра (BASEALCHEMY, 15 Vis, Aqua, 2 шт.); #148/#149 success.
- `38f8d65`: фильтрующая труба из обычной трубы + фильтра (TUBES, 10 Vis); #150 success.
- `7a4bdcb`: регистрация `metal_alchemical` и `metal_alchemical_advanced`, предметы/модели/loot; #151 success.
- `b97127a`: оригинальный 2-output рецепт обычной алхимической конструкции (IVI/TWT/IVI, TUBES, 75 Vis, Aqua/Ordo/Perditio); #152 success.
- `ea873c1`: arcane-рецепты **обеих** улучшенных печей из оригинала (BFB/IGI/III): таумиевая ESSENTIASMELTERTHAUMIUM, 250 Vis, 2 Ignis; пустотная ESSENTIASMELTERVOID, 750 Vis, 3 Ignis; Core parity #153 **SUCCESS**.

**ВАЖНЫЕ незакрытые зависимости/проверки:**
1. У `metal_alchemical_advanced` пока **нет оригинального рецепта** (его крафт использует обычную конструкцию, пустотные пластины и primordial pearl); без него пустотная печь не имеет полной survival-цепочки.
2. Исходные текстуры конструкций/древесины ещё заменены временными; worldgen серебродерева/великого дерева и собственные рецепты всех материалов отсутствуют. Не называть их реализованной игровой механикой.
3. Эти CI `verify-connection` проверяют Java Core parity, **не** NeoForge компиляцию новых BlockEntity/Menu классов и **не** реальную игру. Запустить отдельно полный build и клиентский UI craft-тест, но без бесконечного опроса CI.
4. Нужны тесты серверных транзакций крафта (в том числе x2/x3 Ignis, 2-item outputs, shift-click) и исследовательских ключей в живом сохранении.
5. Сохранение алхимических блоков после рестарта, редстоун/варианты труб, зеркала эссенции, рецепты тигля и survival GUI остаются открытыми.

## Дополнение после #156: зеркала эссенции и полная сборка
- В `main` внесены `90b0d58` — BETA26 AdvAlchemyConstruct 200 Vis, Terra/Ignis, Primordial Pearl с восемью износами; Core parity **#155 SUCCESS**.
- `e19157f` — original SmelterAux (100 Vis, Aer+Terra) и SmelterVent (150 Vis, Aer), рецепты в серверном arcane workbench; Core parity **#156 SUCCESS**.
- `c4d51e3` — `LegacyEssentiaMirrorLogic`: взаимная валидация ссылок и измерений, отказ при выгруженном мире, лимит переноса 1, радиус 8, backoff link recovery, instability+Flux+decay; Core parity **#157 SUCCESS**. **ВАЖНО: это только core, нет регистрации mirror_essentia, NBT block entity, предметного связывания и физического world transfer. Нельзя говорить, что зеркала работают в клиенте.**
- `1baffdc` — разовый включатель CircleCI `run_compile.default=true` через push, реально запущен **NeoForge compile #159**, ссылка https://circleci.com/gh/craftkot/tgaumcraft/159; статус на момент этой записи **PENDING**, результат не подтверждён.
- `dcac55f` — тут же восстановлен `run_compile.default=false`; дешёвый Core CI в новых пушах. История предыдущего коммита сохраняет параметр true для сборки #159.
- НЕ считать `verify-connection` доказательством сборки Minecraft; `#138` остаётся последним реальным client smoke алхимии на старом коммите.
- Ближайший следующий этап: проверить итог **#159**, при ошибках исправить реальные Java/NeoForge compiler errors; затем переносить **Essentia Mirror** как блок, сохранённые NBT links, взаимную привязку, серверный transfer без дюпов, тест в живом мире; позднее survival GUI/recipes/worldgen.

## Следующие СОДЕРЖАТЕЛЬНЫЕ задачи
1. Не повторять #136/#138. Проверить **открытие GUI, ручное помещение топлива/сырья, работу в survival**, и **сохранение после перезапуска** печи/перегонного куба/труб/банок. При запуске игрового теста тратить средства CI экономно; можно сначала добавить тесты+механики и только потом один игровой прогон.
2. Закончить алхимию: **зеркала эссенции**, **оригинальные рецепты перегонного куба и улучшенных печей**, проверить фильтры/клапаны/буферы/редстоун, мехи, насадки и вентиляцию в игре, полноценные рецепты тигля и обмен фиалами.
3. После закрытия алхимии — исследования и инфузия, далее фокусы, големы, мир, порча, существа и Древние.
4. Отдельно проверить подозрительные игровые случаи: сохранение NBT в разных блок-сущностях, односторонность труб, лишний Flux, переполнение, дюпы при разрыве связи. Факты из source-кода ≠ доказанный gameplay.

## Полезные пути
- `analysis/PROGRESS_AUDIT_2026_10_16.md` — оценка по категориям.
- `analysis/HANDOFF_2026_10_17_CHAT_TRANSFER_RU.md` — полный старый список всех крупных недоделанных механик; предупреждение: статусы раздела алхимии на дату этого документа устарели.
- `tools/client_smoke.sh` и `src/neoforge/java/dev/ehtid/thaumcraft/util/ThaumcraftSmokeAudit.java` — живой smoke.
- `.circleci/config.yml` — дешёвая CI всегда и тяжёлые параметры по запросу.
- Java: `blockentity/SmelterBlockEntity.java`, `blockentity/AlembicBlockEntity.java`, `blockentity/EssentiaTubeBlockEntity.java`, `blockentity/EssentiaBufferBlockEntity.java`, `blockentity/EssentiaJarBlockEntity.java`.
- Команды GitHub connector: `fetch_file`, `create_tree`, `create_commit`, `update_ref`, `get_commit_combined_status`.

## Первая инструкция после перехода
Проверить свежий HEAD и статусы CI, прочитать ЭТОТ документ, выбрать ближайшую небольшую недоделанную механику из алхимии, сделать реальный коммит(ы) за один короткий подход, отчёт — ссылка на commit + какая проверка. Не ждать долго CI, не просить снова историю проекта.

## Дополнение 17.10: полноценная сборка и начало мира зеркал
- Full NeoForge #159 **SUCCESS**: https://circleci.com/gh/craftkot/tgaumcraft/159 — сборка JAR и запуск сервера на коде **до регистрации зеркала**.
- `ae2c38a`: добавлены `mirror_essentia` (блок/предмет), `EssentiaMirrorBlockEntity`, NBT-ссылка с измерением, взаимная проверка связи без форсированной загрузки чанков, привязка через предмет и размещение. Модель временная; **реального транспорта эссенции в mirror пока нет**. Core CI #162 SUCCESS. Это не Minecraft-прохождение.
- Разовый полный NeoForge compile зеркала #164: https://circleci.com/gh/craftkot/tgaumcraft/164, состояние на момент записи **PENDING**. Проверить итог до следующих крупных изменений.
- `4c9fa87`: временное `run_compile.default=true` для #164; `753b66c`: восстановлен `run_compile.default=false`.
- Ближайшая механика: **проводить реальную эссенцию через взаимно связанные зеркала**, проверяя локальную и удалённую сущности на сервере, физический перенос одного аспекта/единицы, отсутствие дюпов и NBT после перезагрузки; не выдавать core-паритет за готовность мира.


## Дополнение: реальный удалённый дебет через зеркало и сборка
- Полная NeoForge compile **#164 SUCCESS**: https://circleci.com/gh/craftkot/tgaumcraft/164, включала предыдущие `mirror_essentia` block/BE/item, JAR и server startup.
- Коммит `3359dfd`: `EssentiaMirrorBlockEntity implements EssentiaTransportPort`, подключён **первый реальный способ транспорта**: локальная труба/банка забирает `1` единицу эссенции через взаимно связанные зеркала из реального загруженного `EssentiaTransportPort` в радиусе 8 от партнёра. Сначала проверяет тип/количество и загрузку, после вызывает **фактическое** удалённое `takeTransport(aspect,1,face)`, начисляет нестабильность зеркалам только после удачи. Нет локального буфера, зеркала не получают бесплатную эссенцию. RemoteSource кэшируется на 1 tick, перед дебетом повторно валидируется; зеркала как удалённые источники исключены, чтобы не было рекурсивного зеркального переноса. Core parity **#167 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/167.
- IMPORTANT: `addTransport` в зеркало всё ещё возвращает 0 — направление **локальное хранилище -> удалённый приёмник пока отсутствует**. Перенос `takeTransport` пока не протестирован в живом мире Minecraft; только core+compile pending. Не называй всю механику завершённой.
- One-shot full NeoForge compile запущен на `39bfc36`, **#169** https://circleci.com/gh/craftkot/tgaumcraft/169. При фиксации статус **PENDING**. Проверить результат в новом чате.
- `30fd536`: экономный `run_compile.default=false` восстановлен; новые обычные пуши не гоняют сервер. `#168` и `#170` Core-success.
- Ближайший этап: если `#169` красный — починить compilation; если зелёный — добавить реальный клиентский smoke связанной пары/банок, затем безопасную удалённую операцию приёма `addTransport` с атомарными preflight/debit/credit, отдельно NBT/reload, инфузионный рецепт и текстуры. Базовый процент проекта 59/100 требует новой полной оценки; не повышать только из-за Core parity.


## Дополнение — Mirror physical gameplay smoke #174
- Полная NeoForge compilation + JAR + startup **#169 SUCCESS** (https://circleci.com/gh/craftkot/tgaumcraft/169), включает `3359dfd` с физическим зеркальным дебетом.
- `bba96e7`: отдельная ветвь `THAUMCRAFT_MIRROR_SMOKE=1` в `tools/client_smoke.sh`; два связанных зеркала в интегрированном мире, дальняя банка с 12 Aer, локальная пустая банка, обязательные серверные события `TC_SMOKE_MIRROR_REMOTE_DEBIT` и `TC_SMOKE_MIRROR_TO_JAR`, скриншоты + ванильный NBT запрос. **Важно: это проверка реального Java транспорта, не доказанный survival craft/restart.**
- `533ac23`: временно активирован real Minecraft client #174 (https://circleci.com/gh/craftkot/tgaumcraft/174), с `THAUMCRAFT_ALCHEMY_SMOKE=0`, `THAUMCRAFT_MIRROR_SMOKE=1`; на момент записи **PENDING**, не называть пройденным.
- `666af1c`: `run_minecraft.default=false` восстановлен, обычная проверка сборки не запускает дорогой клиент.
- `c9200d1`: BETA26 `BlockMirror.dropMirror` поведение перенесено: при добыче связанного `mirror_essentia` её единственный разрешённый loot-table предмет получает сохранённые `linkDim/linkX/linkY/linkZ` через `minecraft:custom_data`; оригинальная логика `setPlacedBy` может восстановить ссылку при повторной установке. **Новые изменения после #174: эта конкретная обработка выпадающего предмета не проходила full NeoForge compile/client test.**
- Приоритет следующего подхода: проверить **#174**, если ошибка — править исходя из снимков и логов (не утверждать «сеть упала» без доказательств); затем full NeoForge compile для `c9200d1`, настоящий loot test зеркала, NBT/reload; и только затем расширение двустороннего переноса, remote sink preflight, текстур и инфузионного рецепта.

## Дополнение 18.10 — Mirror #174 игровой успех, добыча и отложенная связь
- **#174 real Minecraft client SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/174: два реально размещённых и взаимно привязанных зеркала, удалённый источник-банка с Aer, локальная банка-приёмник, два обязательных события после физического debit/credit. Этим подтверждена **только передача из remote source через linked mirror** в запущенном клиенте, не обратная загрузка/infusion.
- `edec268`: NeoForge `EssentiaMirrorBlock#onRemove` при реальном разрушении отключает загруженного взаимного партнёра; `EssentiaMirrorBlockEntity#writeLinkedItem` записывает `linkDim/linkX/linkY/linkZ` в loot ItemStack и выдаёт gated smoke markers. Real client `THAUMCRAFT_MIRROR_SMOKE` расширен /setblock destroy, NBT query для сохранённого предмета и парного mirror, assert server markers.
- `b639bb2`: разовый **NeoForge compile #180 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/180 и **Minecraft client #181 PENDING** https://circleci.com/gh/craftkot/tgaumcraft/181. Снимки и логи проверки добычи #181 ещё НЕ проверены; не выдавать за успешный прогон.
- `9fc87ab`: CircleCI default (compile=false,minecraft=false) немедленно восстановлен после старта этих прогонов.
- `843ecfb`: исправлен разрыв ссылки при размещении linked ItemStack, если его второе зеркало выгружено. Адрес хранится как pending NBT, серверная периодическая попытка повторного связывания при загрузке нужного чанка, защита уже занятого другого зеркала и от self-link; mined item сохраняет даже pending destination. Core Parity **#183 SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/183. **НО** этот последний код ещё не прошёл full NeoForge/client тест.
- Следующая задача: посмотреть результат #181, затем **full NeoForge build для #843ecfb** (и клиентская проверка pending link/reload), затем бездюповая подача эссенции от локальной трубы в удалённое хранилище, только с валидированным удалённым sink. Нельзя делать вид, что двухсторонняя подача завершена. Также infusion recipe MirrorEssentia, оригинальная геометрия/текстуры, reload smoke.

## Дополнение 18.10 — обратный транспорт зеркала
- `#181` **SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/181: игроком созданная linked-пара; при разрушении у одного зеркала выпадает item с `minecraft:custom_data` координат и загрузка партнёра помечается несвязанной, два подтверждённых серверных smoke-marker.
- `0e369d3` добавил первую реализацию **обратного направления** зеркал: зеркало раз в 5 тиков забирает одну единицу из локального реального `EssentiaTransportPort` с лицевой стороны и помещает в обычную удалённую `EssentiaJarBlockEntity` в 8 блоках от второго зеркала. Только при наличии всасывания получателя больше всасывания источника; source debit делается в синхронном callback jar, который preflight-проверяет иммутабельный результат ещё до дебета, и затем commit в jar. Запрет зеркал в качестве источника и same-jar identity, без принудительной загрузки чанков. **Это ограниченная механика, пока только обычные банки-приёмники, не общая двухсторонняя сеть!**
- `e4ab1b0`: расширен реальный smoke `THAUMCRAFT_MIRROR_SMOKE` дополнительным сценарием на отдельной паре — обычная банка Aer=12 ниже левого зеркала и пустая подписанная Aer банка рядом с правым, с маркерами `MIRROR_TO_REMOTE_JAR`, `MIRROR_REVERSE_TRANSFER` и NBT-скриншотами.
- `#188` full compile **FAILURE** https://circleci.com/gh/craftkot/tgaumcraft/188 и `#189` real client **FAILURE** https://circleci.com/gh/craftkot/tgaumcraft/189. Точный текст ошибок из CircleCI недоступен в GitHub statuses; очевидный дефект найденный в коде — отсутствующий импорт `ServerLevel` в `EssentiaJarBlockEntity`, также отсутствовавшая явная проверка jar label перед `LegacyEssentiaJarLogic.add`.
- `891422d` добавил `LegacyEssentiaJarLogic.accepts(stored,aspect)` в `canReceiveMirror` + core parity тесты на несовместимый filter; `b4b6c73` добавил отсутствующий import `net.minecraft.server.level.ServerLevel`.
- Повторная полная сборка **#194**, https://circleci.com/gh/craftkot/tgaumcraft/194, статус при записи **PENDING**. Когда будет SUCCESS, повторить игровой #189 с исправленным HEAD, только затем говорить об обратной передаче как подтверждённой Minecraft gameplay.
- `c2a5f68` и `bce19a3` восстановили on-demand Compile/Client CircleCI defaults=false.
- Pending mirror linkage `843ecfb` только Core Parity подтверждено; real restart/chunk-unload link recovery не проверен. Отдельно: reverse relay имеет потенциально дорогой поиск remote jar в радиусе 8 блоков; оптимизировать/кэшировать при нагрузке.

## Дополнение: mirror suction type / статус сборки
- `a5c8c9e`: исправлен `EssentiaMirrorBlockEntity.transportSuctionAspect`: теперь зеркало отдает соседней трубе реальный `suctionAspect` найденной удалённой банки, чтобы трубы не тянули неподходящие аспекты по фильтрованной зеркальной линии. Изменение **пока НЕ прошло полную сборку/клиент**, Core CI вызван push.
- Повторная полная NeoForge-сборка после missing `ServerLevel` import и label fix **#194** (https://circleci.com/gh/craftkot/tgaumcraft/194): состояние при записи **SUCCESS**; она НЕ включает последующий `a5c8c9e`.
- **Не заявлять об успешной обратной передаче в игре:** предыдущая клиентская CI #189 упала вместе с full compile #188; новый клиент запускать лишь после зелёной сборки исправленного кода. Старое направление удалённый источник → местная банка подтверждено #174 и #181.

## Дополнение 18.10 — исправления и повторные проверки #200/#201
- **#194 full NeoForge SUCCESS** https://circleci.com/gh/craftkot/tgaumcraft/194 — исправления `ServerLevel` import в EssentiaJarBlockEntity и `LegacyEssentiaJarLogic.accepts()` в зеркальном приёмнике подтверждены полной сборкой.
- `a5c8c9e` — remote jar filter/aspect теперь передаётся по `EssentiaMirrorBlockEntity.transportSuctionAspect` локальным трубам, чтобы те тянули совместимую эссенцию. Это изменение включено в следующий full-compile и real-world smoke.
- `e8e1c0b`: разовый запуск **NeoForge #200** https://circleci.com/gh/craftkot/tgaumcraft/200 и **Minecraft GUI/world #201** https://circleci.com/gh/craftkot/tgaumcraft/201 — `THAUMCRAFT_MIRROR_SMOKE=1`, проверка обеих зеркальных операций и NBT, включая новый reverse кейс (от удалённого источника в банку и из местного источника в удалённую подписанную банку). На момент передачи оба **PENDING**. Проверить статусы; нельзя заявлять success до проверки.
- `bc89167` сразу вернул `run_compile=false`, `run_minecraft=false`, обычный тест алхимии вместо зеркального по умолчанию. Повторно включать дорогие задачи только по необходимости.
- Важно: pending mirror link / reload сохранения мира всё ещё **не прошёл игровой тест**, infusions crafting для mirror_essentia и модели/текстуры ещё недоделаны. При падении #201 искать клиентский лог и сравнивать фактические smoke markers. Через GitHub connector возвращаются только CircleCI commit statuses, текст CircleCI job logs отдельно недоступен.

## Дополнение — #200/#201 и исправление зеркальной обратной тяги
- `#200` **NeoForge compile SUCCESS** (https://circleci.com/gh/craftkot/tgaumcraft/200) для прежнего состояния: у двухстороннего зеркального транспорта код компилируется и сервер стартует. Однако `#201` **real Minecraft client FAILURE** (https://circleci.com/gh/craftkot/tgaumcraft/201); точную причину по одному GitHub combined-status подтвердить невозможно — CircleCI job logs не доступны через текущее подключение.
- **Найдена потенциальная причина игровой проблемы:** зеркалу с локальной банкой-источником при активном reverse transfer разрешался одновременный `canTransportOutput`. Тогда эта банка могла **сосать обратно** из удалённой банки через то же зеркало, ломая устойчивость направления (помпа <=> возврат).
- `bd21cad`: добавлены `activeReverseSource` + core `suppressReturnOutput`; зеркало блокирует пассивную выдачу локальному источнику, когда есть реальная удалённая банка с более сильным всасыванием. Не блокирует другой обычный режим, когда локальная банка пустая и принимает из remote. Пять новых core assertions.
- Core CI **#204 SUCCESS** (https://circleci.com/gh/craftkot/tgaumcraft/204), полная NeoForge **#206 SUCCESS** (https://circleci.com/gh/craftkot/tgaumcraft/206) после исправления. Не говорить, что этот конкретный bug был доказанной причиной предыдущего failure — это статически найденная возможность, которую новый тест должен проверить.
- `3b6ee86` запустил реальный client smoke с обоими направлениями #209 (https://circleci.com/gh/craftkot/tgaumcraft/209): статус в момент записи **PENDING**. Проверить после завершения! `0ce1b32` вернул CI defaults в экономный режим.
- Следующее: выяснить #209; при failure попросить/добыть реальный лог CircleCI и не угадывать причину. Только после подтверждения двусторонней передачи — NBT сохранение после выгрузки чанков и рестарта, MirrorEssentia infusion craft, оригинальные текстуры/модель.
