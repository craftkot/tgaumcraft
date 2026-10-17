# Контрольная оценка полного порта Thaumcraft 6 — 16.10.2026

**КОНТРОЛЬНЫЙ ИНДЕКС ГОТОВНОСТИ: 59/100 = 59%.**

Это **точное число ПО НАШЕЙ ПОСТОЯННОЙ 100-балльной ОЦЕНОЧНОЙ ШКАЛЕ**, но не объективно измеримая абсолютная доля оригинальной модификации. У разных функций разная трудоёмкость, а полный эталонный перечень каждой механики/ассета BETA26 ещё не формализован. Если меняем методику, указывать старую и новую шкалу; не выдавать число за точность исходного кода.

## Объём: ВЕСЬ оригинальный Thaumcraft 6.1.BETA26

Цель — все оригинальные механики/контент с переносом на Minecraft 1.21.1 NeoForge, НЕ только FIRSTSTEPS, исследования, ядро, демонстрационная инфузия или прохождение тестов. Поэтому предыдущая неаудированная оценка ≈86% **ЗАМЕНЕНА** этим индексом: она не учитывала объём отсутствующих крупных систем.

| Категория | Макс. баллов | Заработано | Остаток и критерий для повышения |
|---|---:|---:|---|
| Инфраструктура, загрузка, сохранение, автоматические тесты | 7 | 7 | Подтверждены CI, сервер и реальный клиент |
| 37 аспектов, 148 исследований, исходные данные и совместимость | 10 | 10 | Каталоги и основные базовые структуры перенесены; полная играбельность считается в других категориях |
| FIRSTSTEPS, магический верстак, сканирование, таумометр | 10 | 9 | Оставшиеся отличия в оригинальных случаях/звуках/цепочках прохождения |
| Теоретические исследования, 33 карты, 14 видов помощников, GUI | 12 | 9 | Не все старые ItemStack-метаданные/материалы и ветви карт доступны в игре, нет полного end-to-end охвата |
| Аура, генерация мира, семь видов кристаллов, естественный рост | 9 | 5 | Современная проекция биомов/ауры приближённая, отсутствуют многочисленные оригинальные объекты, руды, события мира |
| Тигель, алхимия, банки эссенции и транспорт | 12 | 6 | Есть рабочие начальные рецепты/банки/дебет; нет полной алхимии, перегонки и сети труб/зеркал/прочих источников |
| Инфузия, педесталы, матрица, рецепты, ритуал | 11 | 5 | Семь Crystal Farmer, резервирование, списание, базовая перчатка; **нет полного игрового теста оплаченного ритуала**, сотен остальных рецептов, анимаций/нестабильности и гарантий безопасного восстановления после сбоя |
| Перчатки/фокусы и полноценная магия заклинаний | 9 | 1 | Есть базовая перчатка и её щелчок по матрице; фокусы, эффекты, настройки и остальные способности отсутствуют |
| Прочее: предметы, оборудование, големы, существа, порча, Древние | 14 | 3 | Реализована малая часть исходных игровых систем, большинство остаётся |
| Модели, звуки, частицы, русская локализация, полировка интерфейса | 6 | 4 | Есть оригинальные материалы основных проверенных объектов, но многие ресурсы, спецрендеры и FX отсутствуют |
| **ИТОГО** | **100** | **59** | **41 балл до полной версии** |

## Доказательства на момент фиксации

- Live ветка `craftkot/tgaumcraft:main`: **`2db84a3ef1cd10ddb4f9dc69fc3ac390d78a79e7`**.
- Main CI **#38883686079 SUCCESS**: тесты, NeoForge, dedicated-server smoke, JAR.
- Real Minecraft client smoke **#38883686444 SUCCESS**: скриншоты #34-36 показывают BASIC gauntlet, подтверждение `CASTER_CLICK_ACTIVATED_MATRIX_OK` и `CASTER_NO_UNVERIFIED_RECIPE_OK`.
- 37 аспектов; 148 исследований / 271 этап; 33 исходные карточки со значительной частью рабочей серверной GUI; оригинальные модели кристаллов и настоящие клиентские проверки. Семь исходных Crystal Farmer — лишь подмножество всего исходного контента.
- Нет end-to-end доказательства, что игрок создаёт кристалл из **реальных** предметов, оплаченных 25 единиц эссенции и реально тикающего алтаря. Нельзя объявлять ритуал завершённым только за счёт успешного server CI.
- Это качественная **оценочная матрица** с заранее закреплёнными весами; не подменять факт отсутствия функций тысячами «успешных» assertions.

## Правила дальнейшего подсчёта

1. Перечитывать свежий `main` и GitHub Actions перед следующим `П`.
2. Менять баллы **только после функционального достижения** внутри соответствующей категории; перенос одного файла, новая чистая формула или зелёная сборка без геймплея не дают автоматический +1%.
3. Записывать краткое основание каждого изменения и ссылку на коммит/тест. Не завышать по числу строк кода, коммитов или ассетов.
4. За каждую категорию не начислять больше её максимума; не перераспределять веса задним числом для получения более высокого процента.
5. Отдельно сообщать степень игровой проверенности (реальный клиент, интеграционный тест, логическая проверка) и сохранять оценку 59%, пока критерии реально не изменились.
6. Это не оценка оставшегося времени: последние 41 балл содержат потенциально самые трудные системы и баги.

**Пользователь велел остановить разработку до следующего сообщения. Этот документ — аудит статуса, не начало следующего этапа.**

## 16.10.2026 — targeted FIRSTSTEPS/ScanGeneric closure work (not yet awarded +1)

Latest implemented code commit `853ba31f6ee7833beec7fab17d3a553daae5ad0d`. MAIN CI `38889146646` SUCCESS (Java core parity, original source GUI SHA256, NeoForge build, dedicated-server start and JAR artifact). A real Minecraft client workflow `38889146656` must be inspected by screenshots and actual game observations separately. Earlier test artifacts `38887519964` / `38887894809` tested prior scan/bed/Salis interaction sequences.

Three substantive fixes:
1. `GenericScanKeyLogic.forModernItem`: 37 additional BETA26 metadata-bearing item identities (fish, both flower registries, six double plants, stonebrick, charcoal, golden apple, sponge, anvils and six skulls) no longer become duplicate new scan keys on 1.21. Future-only variants remain untouched. Covered in CoreParityTest.
2. `FirstStepsServerProgression`: **security/legitimacy bug fixed**; the client's former `checkRequisites=false` did not enforce FIRSTSTEPS requisites server-side. All later stages now enforce actual craft markers and Basics observation regardless of packet hint. Initial bootstrap allows only genuinely obtained `!gotthaumonomicon` marker. Negative forged-packet and legitimate-start tests added.
3. Thaumonomicon was formerly a nonfunctional bare Item. Now `ThaumonomiconItem` opens client-only `FirstStepsBookScreen` with byte-exact original BETA26 `gui_researchbook.png` SHA256 `4db8e4fad6eaf1fdf486374b9402ae26fed402a920aa939c9746f60545d5f4c8`, visible first-stage requirements and readable RU/EN, sending the already-server-validated FIRSTSTEPS C2S packet. This is a PLAYABLE starter chapter, not a full research browser for unrelated 148 research nodes (the latter belongs to the separate research/theory category). The original starting book texture has been added to GitHub; the full research browser is still an incomplete separate subsystem.

Actual real-client smoke was extended with #08e–g vanilla diamond ore right-click scan, #08h–l crystal pickup/bed/wake/Salis bookshelf attempt and #08m–n in-game Thaumonomicon GUI/progression. **Need inspect real screenshots/diagnostics and confirm whether the vanilla bed was successfully placed, the dream flag earned and Salis transformed bookshelf**; a green CI alone does not prove these world actions. Do not raise FIRSTSTEPS 9/10 -> 10/10 nor whole-port 59 -> 60 until this is validated. Once proven, document exact run and award +1; otherwise fix the in-game defect first.

## 17.10.2026 — после CircleCI #20 SUCCESS: настоящий FIRSTSTEPS GUI smoke

Получен успешный полноценный `real-minecraft-client` прогон CircleCI #20 (сборка, NeoForge, запуск мира, большой сценарий действий). Однако прошедший exit-code **сам по себе не подтверждает** шаг FIRSTSTEPS: в тестовом скрипте исторически использовался жёсткий клик `(512,527)` — вне нижней кнопки Таумономикона, которая находится в исходном атласе 512×356 по логическим координатам `x=183..327,y=311..330`. Исправлено на пересчёт `minecraft_gui_point` с текущей геометрией окна (`255,321` в исходном UI); добавлены чистые тесты преобразования координат для 512×356.

Также ранее клиент давал себе книгу командой `/item replace`, что **не вызывает реальный pickup event** и не может выдать `!gotthaumonomicon`. Теперь после Salis-библиотеки он подтверждает наличие упавшего ItemEntity и телепортируется к нему для фактического подбора, затем использует книгу и нажимает кнопку; результаты видно по соответствующим PNG. Без нового доказанного прогона и проверки последовательности `!gotcrystals` → `!gotdream` → drop → pickup → FIRSTSTEPS прогресс **не начислять** десятый балл FIRSTSTEPS, общий рейтинг всё ещё 59/100.

## 17.10.2026 — CircleCI #24 screenshot forensics: false green game smoke

User-provided `build_24_step_103_container_0.txt` contains 55+ actual Minecraft thumbnail captures. Real `02-after-singleplayer` shows game creation in SURVIVAL with **Allow Commands: OFF**. `03-world-create-commands` continues to show **OFF**: old scripted root click `(512,433)` missed the actual toggle, centred at about root `(512,331)`. It subsequently created a world (`INTEGRATED_WORLD_START_LOG=YES`), but screenshots `05..43` all show the same survival-spawn tree, empty hotbar, repeated red error chat, and sometimes the Vanilla Game Menu. There is **no demonstrated in-world scan, book opening, Salis transform, research table or infusion action in CircleCI #24**. Prior interpretation of a green smoke as full gameplay was overly optimistic. The `CLIENT_SENT_*` flags mean commands were TYPED, not ACCEPTED.

Fixed `tools/client_smoke.sh` to click actual Allow Commands button using live window geometry, capture ON screen, then after world creation **fail-fast by inspecting REAL Minecraft `run/saves/*/level.dat` for gzip-NBT `Data/allowCommands` TAG_Byte=1**. This is checked BEFORE any `/give` or `/gamemode`. Added `tools/assert_client_world_commands.py` and offline regression `tools/test_client_world_permissions.py`: accept only exactly one generated world with correct authoritative persisted game flag. Real game must be rerun. **Keep index 59/100** until real in-world evidence is supplied; still no +1 for FIRSTSTEPS.

## 17.10.2026 — CircleCI #28, впервые разрешены реальные команды

Реальный игровой лог №28: `REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON`, интегрированный сервер запущен, 60 реальных скриншотов. В игре фактически отображаются исходный OBJ-таумометр, diamond ore, стол исследований, crucible и др. Но последовательность FIRSTSTEPS и теория **не доказаны**: изображения Thaumonomicon `08m`/`08n` практически неизменны в области книги (0 изменённых пикселей в области x=70..240,y=40..157), `38..41` сохраняют меню исследовательского стола до создания теории, `42` показывает красное сообщение `Found no elements matching note` (нет сессии). Условия `CLIENT_SENT_*=YES` по-прежнему означали только отправку команд.

Добавлены серверные event probes под `THAUMCRAFT_SMOKE=1`: реальный pickup кристалла, wake-up, Salis on bookshelf, отложенная трансформация, реальный pickup Таумономикона, ответ серверного FIRSTSTEPS progression handler, установка scribing tools/paper в слоты и реальное создание теории. `tools/client_smoke.sh` теперь ОСТАНАВЛИВАЕТ тест с ошибкой `REAL_GAMEPLAY_MILESTONE_MISSING=<key>` в точке первого отсутствующего события. Probes не включены в обычных игровых запусках. Это инструмент выявления ошибки, а не подтверждение паритета. 59/100 до успешной проверки.

## 17.10.2026 — CircleCI #32: первый точный красный этап — ванильный сон

Настоящий игровый тест #32 остановился на обязательном `REAL_GAMEPLAY_MILESTONE_MISSING=WAKE_UNLOCK`. Сервером подтверждено `TC_SMOKE_CRYSTAL_PICKUP=YES` и `REAL_MINECRAFT_WORLD_ALLOW_COMMANDS=ON`; прочие проверки не запускались из-за честного fail-fast. Фотографии `08i`/`08j`: ванильная красная кровать установлена на каменной площадке, после `/time set night` персонаж остаётся стоять, прицел направлен под матрас в блок камня. Это дефект геометрии тестового взаимодействия (скрипт использовал наклон 45°), а не доказанный дефект NeoForge PlayerWakeUpEvent. Скрипт после установки кровати поворачивает камеру на pitch=22°, снимает кадр перед сном и проверяет ТОТ ЖЕ авторитетный серверный WAKE_UNLOCK. Поднимать 59/100 рано.

## 17.10.2026 — CircleCI #36: сон исправлен, предмет книги не подобран

В реальном игровом логе #36 последовательно подтверждены **`CRYSTAL_PICKUP`, `WAKE_UNLOCK`, `BOOKSHELF_SALIS_USED`, `BOOKSHELF_TRANSFORMED`**, а также `FIRSTSTEPS_REAL_BOOK_DROPPED_OK` (настоящая сущность Thaumonomicon после разрушения книжной полки). Жёсткая проверка затем остановилась на `REAL_GAMEPLAY_MILESTONE_MISSING=BOOK_PICKUP`. Скрипт телепортировал игрока в `(2.5,151,12.5)`, на 1 блок над платформой `y=149`; выпавшая сущность после падения лежит на верхней грани камня, около `y=150`. Creative flight способен оставлять игрока висеть над радиусом pickup. Исправлено: телепорт на y=150 перед дропом (z=11.5), короткое реальное W-перемещение к z=12.5, прежняя обязательная серверная проверка `BOOK_PICKUP`. Пока снова не протестировано. Готовность **59/100** без повышения.

## 17.10.2026 — CircleCI #40: BOOK_PICKUP подтверждён, исследовательская кнопка не нажата

Реальный игровой тест #40 подтверждает все этапы до **`BOOK_PICKUP`** включительно (в логах `CRYSTAL_PICKUP`, `WAKE_UNLOCK`, `BOOKSHELF_SALIS_USED`, `BOOKSHELF_TRANSFORMED`, `BOOK_PICKUP`). Скриншоты `08m`/`08n` показывают GUI Таумономикона с видимой кнопкой `Continue research`, но `FIRSTSTEPS_STAGE_ADVANCED` отсутствует: её нажатие не подтверждено. Скрипт нажимал `(512,691)`, рассчитанные из xdotool window geometry 1024x768 и fixed scale 2; реальные пиксели GUI на скриншоте расположены заметно ниже. Корень вероятнее в несовпадении X11-геометрии окна с масштабом игрового framebuffer, а не в серверных исследованиях — это следует проверить независимым вводом.

Исправлено: использовать реальную клавиатурную навигацию Minecraft `Tab` → `Return` по единственной кнопке экрана. Введены опциональные (`THAUMCRAFT_SMOKE=1`) клиентские и серверные контрольные события `FIRSTSTEPS_GUI_BUTTON_PRESSED`, `FIRSTSTEPS_GUI_GATE_ACCEPTED/BLOCKED`, `FIRSTSTEPS_C2S_RECEIVED/REJECTED` и прежнее `FIRSTSTEPS_STAGE_ADVANCED`. Скрипт жёстко проверяет весь путь client GUI → C2S → server progression, а не только успешный ввод клавиши. Предыдущая игра доказала более полную цепочку, но **59/100** сохраняется до полной игровой проверки последующих исследований.

## 17.10.2026 — CircleCI #44: FIRSTSTEPS server stage пройден, Research Table не доказан

В #44 реально подтверждён весь first-steps путь: `CRYSTAL_PICKUP`, `WAKE_UNLOCK`, `BOOKSHELF_SALIS_USED`, `BOOKSHELF_TRANSFORMED`, `BOOK_PICKUP`, `FIRSTSTEPS_GUI_BUTTON_PRESSED`, `FIRSTSTEPS_GUI_GATE_ACCEPTED`, `FIRSTSTEPS_C2S_RECEIVED`, **`FIRSTSTEPS_STAGE_ADVANCED`**. Первая блокирующая отметка `REAL_GAMEPLAY_MILESTONE_MISSING=THEORY_CREATED`, при успешных `TABLE_SCRIBING_TOOLS_INSERTED` и `TABLE_PAPER_INSERTED`. Скриншоты `38`/`39` показывают `Create Theory` и неизменившуюся панель исследования; количество отличающихся пикселей внутри самой GUI всего 18/38400. В источнике обнаружен ДЕФЕКТ ДИАГНОСТИКИ: `THEORY_CREATED` ошибочно печатался в `keepSelectedTheoryCard`, а НЕ в `startNewTheory`. Этот баг устранён. Теперь `THEORY_CREATED` фиксирует только настоящее создание сессии, `THEORY_CARD_KEPT` — сохранение выбора. Отдельно добавлены probes клика мышью, клавиатуры, прихода серверного menu ID=1, отказа и принятия; для `Return` добавлена обычная клавиатурная доступность Create Theory без обхода server gate. GUI smoke пробует реальную мышь, а если результат не подтверждён — реальный Return через игровой экран и записывает `TABLE_CREATE_MOUSE_PATH_UNCONFIRMED`. Первый вариант паритета кадров подтверждён, полная теория всё ещё требует игрового прогона. Оценка **59/100** неизменна до повторной оценки рубрики.

## 17.10.2026 — CircleCI #48: реальное создание теории, клики по нижней границе GUI

В Minecraft #48 строго подтверждены `TABLE_CREATE_MENU_RECEIVED`, `TABLE_CREATE_MENU_ACCEPTED`, `THEORY_CREATED` после реальной клавиши Return. `TABLE_CREATE_MOUSE_PATH_UNCONFIRMED=YES`: мышь по кнопке на Y=211 не вызвала обработчик. Скриншоты 38/39 показывают, что точка находится под видимой зелёной кнопкой; исправлена точка локального клика 140,31 → 140,25. Дальше UI показывал Inspiration 5/5, Draw 2 Cards, но картинки 39a/40/41 идентичны: клик draw (140,130) также попал ниже кнопки (root Y=409); исправлено на 140,124. Добавлены раздельные проверки `TABLE_DRAW_GUI_MOUSE_PRESSED`, `TABLE_DRAW_MENU_RECEIVED`, `TABLE_DRAW_MENU_ACCEPTED`, `THEORY_CARDS_DRAWN`, `TABLE_CARD_GUI_MOUSE_PRESSED`, `TABLE_CARD_MENU_RECEIVED`, а для принятой карточки `THEORY_CARD_ACTIVATED`. Неподтверждённые клики теперь валят CI, а не скрываются за общим `PASS`. Блокированные из-за материалов карточки не выдаются за успешно активированные. Оценка 59/100 неизменна до полного аудита.

## 17.10.2026 — CircleCI #52: первый полный цикл исходных карточек в Minecraft

В игровом тесте #52 реально подтверждены **мышью** `TABLE_CREATE_GUI_MOUSE_PRESSED`, `TABLE_CREATE_MENU_RECEIVED/ACCEPTED`, `THEORY_CREATED`; `TABLE_DRAW_GUI_MOUSE_PRESSED`, `TABLE_DRAW_MENU_RECEIVED/ACCEPTED`, `THEORY_CARDS_DRAWN`; `TABLE_CARD_GUI_MOUSE_PRESSED`, `TABLE_CARD_MENU_RECEIVED`, `THEORY_CARD_ACTIVATED`. Скриншоты 38–43 показывают создание теории (Inspiration 5/5), реальную карточку Experiment, её принятие (3/5), сохранённый `note` блока и отображение прежней сессии при повторном открытии стола. Клиентский тест #52 завершился `PASS`. Но это **одна** активация случайной карточки, а не доказательство полной совместимости 33 классов или сохранения при холодной перезагрузке мира. Контрольная оценка 59/100 пока остаётся.

Следующий прогон дополнительно проверит `Keep Selected Card` именно после закрытия/переоткрытия того же блока, затем **второй** оплаченный draw. Добавлены `TABLE_CARD_KEEP_MENU_RECEIVED/ACCEPTED/REJECTED` и обязательный счётчик двух серверных `THEORY_CARDS_DRAWN`/меню-событий вместо единожды встреченного маркера. Для изменения оценки полного мода нужны также полноценные другие ветви/системы.

## 17.10.2026 — игровые фиалы ↔ сосуд с эссенцией (малые механики)

Добавлен реальный ручной обмен фиалов с `jar_normal` без команд оператора: **1 пустой фиал + 10 настоящих единиц эссенции сосуда → полный фиал; полный или частично заполненный фиал → реальная ёмкость сосуда + 1 пустой фиал**. Работает с выбранной рукой и стопками, сохраняет замену в инвентаре или выбрасывает предмет только при заполненном инвентаре. Каждая мутация атомарно проверяется сервером через `LegacyPhialJarExchange`, который отвергает неполный вывод, перелив 250, несовпадающий аспект или фильтр. Нет генерации бесплатной эссенции и нет перезаписи существующего аспекта. Добавлен чистый независимый `PhialJarExchangeTest` на 21+ случаев, подключён к обычному CoreParity CI. **Точная исходная BETA26 последовательность взаимодействия с фиалом ещё требует проверки по эталонному ItemPhial/TileJarFillable; это ограниченный работающий игровой перенос**, а не окончательная сеть EssentiaHandler или оригинальные частицы. Глобальный индекс 59/100 не менять до игрового подтверждения.

## 17.10.2026 — малые детали визуала сосудов и безопасного изготовления стола

Новый `EssentiaJarItem` — сохраняющий vanilla BlockItem механизм, который читает **уже сохранённый** `minecraft:block_entity_data` у добытого `jar_normal` и показывает в подсказке реальную эссенцию `Aspect/Amount` (из 250) и `AspectFilter`. Английская и русская локализации дополнены симметрично; интерактивная часть банки не меняется. Добавлена проверка `mayUseItemAt` перед конвертацией деревянного стола с помощью `ScribingToolsItem` и откат исходного блока в неожиданном случае отсутствия после замены `ResearchTableBlockEntity`. Инструмент остаётся у игрока, если операция не удалась. Это небольшие законченные исправления без искусственного роста 59/100. Нужен NeoForge compile и клиентская проверка.

## 17.10.2026 — чтение кристаллов и русские названия стартовых предметов

`CrystalEssenceItem` теперь показывает оригинально сохранённый аспект `Aspects:[{key,amount}]` через реальную локализацию, вместо одинакового безымянного предмета для 37 аспектов. Базовые ключи `crystal_essence`, `salis_mundus`, `thaumonomicon`, `thaumometer` добавлены в обе языковые таблицы; для кристалла — имя с типом и численность аспекта. Это не меняет NBT, генерацию, сканирование или рецепты. Независимый дешёвый тест `tools/test_starter_item_localization.py` предотвращает пропущенные названия и несовпадающие плейсхолдеры переводов. Готовность полного порта остаётся 59/100 до игрового подтверждения.

## 17.10.2026 — разбор реального CircleCI #56 (лог и 08h)

Геймплейный тест #56 завершился `REAL_GAMEPLAY_MILESTONE_MISSING=CRYSTAL_PICKUP`, однако первопричина установлена по логу и PNG: в 13:33:32 игрок Dev вошёл в новый мир в Survival/Normal, в 13:34:20 был **убит Zombie**, пока скрипт слепо ждал фиксированные 110 секунд после клика Create World. Даже `05-after-world-start` уже показывает `You Died!`; последующие попытки /gamemode, сканирования, /difficulty выполнялись поверх экранa гибели. Это не регрессия исследовательских карт или кристаллов.

Исправление тестового сценария: на вкладке Game **ДО создания мира** выбрать Creative и Peaceful двумя последовательными кликами по каждой соответствующей кнопке (основываясь на фактическом #56 GUI), сохранить дополнительный снимок `03a`. `assert_client_world_commands.py` теперь читает созданный ванильным Minecraft `level.dat` и fail-closed подтверждает сразу `allowCommands=1`, `GameType=1` (Creative), `Difficulty=0` (Peaceful). Вместо 110-секундного незащищённого сна ожидать фактический `Dev joined the game` и ещё 6 секунд для загрузки рендера. Отдельный тест перебирает все 32 комбинации cheats/GameType/difficulty и повреждённое/отсутствующее NBT. Считать это **стабилизацией теста**, а не ростом готовности: 59/100.

## 17.10.2026 — исправление сборки сохранения наполненного сосуда + реальный фиал

Проверка кода вскрыла недостающий метод: `EssentiaJarBlock.getDrops` вызывал **не существовавший** `EssentiaJarBlockEntity.saveToItem` — это явный Java compile blocker, который быстрые CircleCI core-only тесты не видели. Реализован `saveToItem(ItemStack, HolderLookup.Provider)`: он переносит реальные поля `Aspect/Amount/AspectFilter/facing/blocked` и **регистрационный id** `BlockEntityType` в `minecraft:block_entity_data` выпавшего jar BlockItem. Ровно реальный loot handler выдаёт smoke-маркер `JAR_DROP_SAVED_COMPONENT`. В `PhialItem.useOn` добавлены проверка прав редактирования и запрет считать повреждённый `Aspects` NBT пустым фиалом. Настоящие серверные маркеры `PHIAL_JAR_WITHDRAWN/DEPOSITED` фиксируют ручной двухсторонний обмен без бесплатной эссенции.

Расширен полноценный Minecraft сценарий: требовать именно сохранённый компонент выпавшей банки, поставить новую 25-Aer банку, настоящим пустым фиалом снять 10, убедиться в остатке 15, затем вернуть 10 и убедиться в исходных 25. Никакие исследовательские данные для этого не выдаются командами. Контрольный процент **59/100** без увеличения до игровых логов нового клиента (и полного NeoForge compile).

## 17.10.2026 — survival drops: тигель, магический верстак, все 16 Nitor

На старой базе BETA26 сверены классы `BlockCrucible extends BlockTCTile`, `BlockArcaneWorkbench extends BlockTCDevice`, `BlockNitor extends BlockTC`: они **не переопределяют** стандартное `getItemDropped` для специальных альтернативных предметов. В современном 1.21, однако, было вообще **нет таблиц добычи** для `crucible`, `arcane_workbench`, 16 цветных `nitor_*`: в выживании при разбивании соответствующий предмет не гарантирован. Внесено 18 `minecraft:block` loot-таблиц с тождественным `thaumcraft:<block>` и условием `survives_explosion`, чтобы не порождать предмет сквозь взрывы. Оригинальные полные мелкие эффекты разрушения тигля (spillRemnants, pollution) по-прежнему не перенесены: отсутствие полного сходства явно сохраняется. Python CI `verify_survival_loot_tables.py` гарантирует правильные имена и все 16 вариантов. Оценка 59/100 без автоматического +1.

## 17.10.2026 — редстоунный сигнал полного/частичного сосуда

По оригинальному `BlockJar#getComparatorInputOverride` BETA26 добавлен полноценный ванильный сигнал компаратора от банки: `0` при 0, `floor(amount / 250 * 14)+1` при 1..250 (максимум 15). Не зависит от фильтра/blocked. Изменение количества при реальном переливе фиала, движении эссенции или дебете инфузии теперь оповещает соседние компараторы через `updateNeighbourForOutputSignal`. В `EssentiaJarParityTest` добавлены 7 граничных проверок (1/17/18/125/249/250). Это не добавляет ещё неподключённую сеть труб; 59/100 неизменно до комплексного игрового теста.

## 17.10.2026 — источник BlockJar: железная скоба и опустошение сосуда

Добавлен оригинальный предмет `jar_brace` с регистрацией, локализациями и Creative Tab. Предмет применяется реальным right-click только к `jar_normal`; сервер проверяет права, повторное использование запрещено, скоба расходуется, `blocked` сохраняется и синхронизируется. При разрушении предметный loot возвращает **ровно одну скобу** вместе с реально выпавшим jar, без генерации от взрыва, уничтожившего сам jar. Восстановлено исходное действие `BlockJar.onBlockActivated`: Shift + пустая рука выливает содержимое в соответствующий chunk Flux через `ThaumcraftAuraSavedData.polluteFlux` и очищает количество, но сохраняет `AspectFilter`. Нет эмуляции исходных звуков/частиц. Временно использован vanilla iron-ingot item layer для `jar_brace` до переноса собственного PNG из BETA26, чтобы не давать missing-model. **Изменения не проверены клиентом/NeoForge**: не выдавать это за подтверждённую готовность и не повышать 59/100.

## 17.10.2026 — этикетки магических сосудов BETA26 (без игрового прогона)

Добавлен `label` (ItemLabel) — blank 0 и selectedAspect/amount=1 для filled 1 в сохранённом `Aspects:[{key,amount}]`, фактический `useOn` на `jar_normal`: пустая этикетка маркирует сосуд с эссенцией, заранее заполненная по NBT может задать фильтр пустому сосуду; расходуется только после успеха серверной установки. Клик Shift + пустая рука по стороне `facing` открепляет этикетку, создавая **реальный выброшенный пустой Label item** и снимая фильтр без удаления содержимого. По исходному `ConfigRecipes` добавлен shapeless рецепт: 4× paper + black dye + slimeball → 4× blank labels (рецепты создания filled labels из конкретного полного фиала требуют NBT-aware ingredient и остались TODO). Имя и тип сохраняются в NBT/Tooltip, Creative Tab. Временная vanilla paper texture вместо исходной overlay-иконки. Игрового подтверждения нет; индекс 59/100 не менять.

## 17.10.2026 — реальные метаданные в рецептах скоб и окрашенных этикеток

По исходному `ConfigRecipes.java` реализованы новые `CustomRecipe` и сериализаторы (формат 1.21.1): `BrassBrace` по узору `NSN/S S/NSN` с четырьмя **строго латунными** `ThaumcraftNuggetItem.legacyMetadata==8` (а не произвольным TC nugget), 2 stick, результат 2 jar_brace. `label_<aspect>`: одна пустая этикетка + один 10/10 фиал строго конкретного валидного аспекта → метаданные оригинального filled label с 1 аспектом. `JarLabelNull`: одна full typed label → blank. Никаких иных предметов в сетке рецепты не принимают; наполненный фиал расходуется как ингредиент, оригинальный ItemPhial не имел container-item. Это code-only реализация: без прогона NeoForge и UI регистрации рецептов; контрольный индекс 59/100.

## 17.10.2026 — исходная физика glass jar и Nitor без игрового теста

Сверено с BETA26 `BlockJar`: AABB (0.1875,0,0.1875)–(0.8125,0.75,0.8125) соответствует современной `box(3,0,3,13,12,13)`, раньше высота ошибочно была 14/16; прочность glass jar выставлена `hardness=0.3`, `blastResistance=20` по наследованию от BlockTCTile. BETA26 `BlockNitor` имеет material-color по `EnumDyeColor` и тканевый звук: все 16 современных регистраций теперь `mapColor(dye.getMapColor())` + `SoundType.WOOL` вместо `GLASS`. Наконец, заполненный `blocked=true` после установки скобы теперь **не принимает и не выдаёт** эссенцию по интерфейсу труб `addFromTransport/takeForTransport`, но фиалы и серверная цена инфузии остаются отдельными путями. `isBlocked()` доступен будущему IEssentiaTransport adapter. Игра пока не прогонялась; 59/100 без начислений.

## 17.10.2026 — 16 свечей из сала BETA26 и зеркальная стабильность инфузии

По оригиналам `BlockCandle` и `ConfigRecipes` перенесены 16 самостоятельных `candle_<dye>` блоков: опорный блок, отсутствие коллизии, 4×8×4 хитбокс, 14 свет, дым/пламя, оригинальная геометрия, PNG и анимированная текстура воска, tint воска по цвету, русский/английский переводы, весь Creative каталог, 16 loot таблиц. В крафт перенесены 2× tallow + string => 3 white candles, и 16 перекрасок любого цвета с красителем. Настоящая InfusionMatrix регистрирует отдельные свечи в том же радиусе X/Z ±8, Y-7..Y+3: зеркальная одинаковая пара +0.1 с diminishing returns, несимметричная — штраф. Изменения отправлены без NeoForge/игровой проверки, индекс 59/100 пока не растёт.

## 17.10.2026 — контроль оригинального узора BrassBrace

В исходной `ConfigRecipes.java` узор `NSN/S S/NSN` означает **4** латунных самородка по четырём углам и **4** палочки по четырём средним боковым позициям. Исправлен старый `BrassBraceRecipe`, ранее по ошибке требовавший 6 самородков + 2 палочки. Продолжает использоваться проверка metadata=8, исключающая другие 10 вариантов самородков. Реальный крафт ожидает проверки полного NeoForge.

## 17.10.2026 — Essentia Tube, Void Jar and original arcane recipes (follow-up)

New additions on `main`:
- `4c6cf8d` — original void-jar overflow/suction core, with parity checks.
- `8c6d177` — jar_void block, saved contents, source-era textures and phial sink.
- `d5dea62` — research/Vis-gated WardedJar (5 Vis) and JarVoid (50 Vis + Perditio) Arcane Workbench crafting; original jar NBT preserved on conversion.
- `d7dbfa6`, `7a7ea59` — skulls as paired, diminishing-return infusion stabilizers.
- `2a9e3fb` — original TileTube core pressure, one-essentia buffer, vent/conflict checks and 6-sided toggling.
- `5117492` — server world transport ports, tube block/block entity, original connected arms, NBT, jar 5-tick suction.
- `d37d0c9` — original TUBES recipe: 10 Vis, research gate, quicksilver nugget/iron plates/glass block/brass nugget, eight tube output.
- `ad66a1b` — original tube inventory sprite.
- `b2ca362` — source PNG Git blob identity and tube model geometry guard.

Observed CI: core/assets CircleCI #87-91 SUCCESS, #93 SUCCESS, #95 SUCCESS, #96 SUCCESS. Re-run `neoforge-compile` #94 FAILED on `955d21d` (after tube source/recipe/sprite; before the separate source asset-guard). Failure details are UNKNOWN through the available GitHub commit status; `compile-audit/neoforge-compile.log` was configured as a CircleCI artifact in `c73a670`, but a failed download/pre-execution phase may leave no artifact. Do not assign a Java cause without the actual log.

Build `run_compile` was reset to false immediately after #94 started, in `ef8c6e4`. An earlier real Minecraft smoke #63 tests older code. **None of these new tube/void-jar commits have verified NeoForge compilation or actual gameplay.** The transport system is PARTIAL: furnace, alembic, full tube network variants (valve, filter, buffer, restriction, oneway), mirror essentia, complete world tests remain missing.

**Audited rating unchanged: 59/100**, by permanent 100-point rubric. Do not inflate for source parity, CI core green, assets or untested systems. Before updating status: obtain #94 compiler error/Gradle log, correct the exact failure, and run new game transport scenario with real server-side essentia debit and deposition.

## 17.10.2026 — живые алхимическая печь и перегонка, после tube/void jar

Коммиты `8d86340` (ядро source TileAlembic/TileSmelter + тесты), `7d8e37a` (реальный block/tile alembic, оригинальная модель/PNG, этикетки, 128 ед., ручной слив в ауру, redstone 0–15, transport output), `f3df2a3` (базовая печь: server-tick, реальные object tags из ThaumometerScanDispatcher, 2 слота и сохранение NBT, топливо, 256 ед., pollution и первая действительная передача наверх в contiguous колонну alembic), `3f6119d` (исходный GUI 176×166 и 5 DataSlot полей), `bbade13` (сквозной pure-core conservation smelter→alembic→tube→jar), `22d5371` (оригинальный ESSENTIASMELTER@2 крафт 50 Vis, 1 Ignis, BCB/SFS/SSS), `03f9f64` (исходный Alumentum fuel 4800 ticks, NeoForge data map), `81574fd` (оригинальные GUI/PNG/model provenance verification).

**Ограничения:** пока только **BASIC** furnace (Thaumium/Void ещё не зарегистрированы), не готовы bellows, vent, aux, полный оригинальный рецепт alembic (требует Greatwood и filter), исходные трубы клапан/фильтр/буфер/односторонние/с ограничением, Essentia Mirror; полный проход в Minecraft отсутствует. Исправлен лишь явно опасный source bug `speed *= (int)0.8` (нулевая задержка): при Alumentum интервал сейчас округлён до ≥1, не делается деление по модулю нуля. Валевая проверка целостности `bbade13` — pure Java, не полноценная игровая проверка.

CI: базовые parity #98–102, #105–107 SUCCESS; **полная компиляция #103 FAILURE**, подробная ошибка недоступна из GitHub commit status; доступ в CircleCI artifact `neoforge/compile.log` нужен, прежде чем считать код валидным. Автокомпиляция уже возвращена на `run_compile=false` коммитом `7a6ac95`. Не выдавать быстрые GREEN за NeoForge compiled. Рейтинг по исходной шкале **59/100 — НЕ повышать без игрового подтверждения**.

## 17.10.2026 — начало этапа исправления сборки после расширения алхимии

Fresh-read HEAD `49f951a` (новые smelter tiers, 5 вариантов труб, 10-ед. buffer, bellows, aux/vent). Последний реальный игровой smoke #63 относится к **старому** `942b522`. Последний на тот момент полный NeoForge compile #103 — **FAILURE**, подробная ошибка через GitHub status недоступна.

В `76d3b21` исправлен CircleCI `neoforge-compile`: запись **всего** stdout/stderr подготовки окружения, curl Gradle и собственно `compileJava processResources` в `compile-audit/neoforge-compile.log` с `store_artifacts: when: always`. До этого tee начинался лишь при компиляции; при более раннем обрыве лог мог отсутствовать. Диагностический запуск включён `f478daa`; **#124 verify-connection SUCCESS, #125 neoforge-compile FAILURE**. Сам текст `neoforge/compile.log` из приватной CircleCI не удалось получить через имеющееся GitHub подключение. **НЕ объявлять причиной Java, сетевую загрузку или Gradle без артефакта.** Опция дорогостоящих сборок возвращена на `run_compile=false` коммитом `5ae9154`; дешёвая CI #126 SUCCESS.

Следующий технический шаг: скачать из CircleCI #125 артефакт `neoforge/compile.log` либо скопировать лог падающего шага; найти первую строку `error:`, `FAILURE:`, `Could not` или `Exception`, а затем исправить и повторно прогнать compile. Без журнала нельзя гарантировать исправление. Оценка мода по шкале предыдущего аудита **59/100**, новая алхимия всё ещё не прошла NeoForge compile/gameplay.

## 17.10.2026 — снят блокер полной компиляции NeoForge

Пользователь прислал реальный лог `build_125_step_102_container_0.txt` для CircleCI #125. Java 21, Gradle 9.2.1 и Minecraft/NeoForge скачались штатно; `compileJava` завершился ровно **10 errors / 6 warnings**:

- 8 вызовов `player.mayInteract(pos)` не совместимы с 1.21.1 (`mayInteract(Level, BlockPos)`);
- `EssentiaJarBlockEntity.saveToItem(ItemStack, Provider)` конфликтовал с унаследованным `BlockEntity.saveToItem` (`boolean` против `void`), переименован в `writeJarDropData`, вызов в `EssentiaJarBlock` обновлён;
- повторное объявление `String requested` в `JarLabelItem.useOn`.

Все 10 исправлены коммитом **`5a376a7`** (семь Java-файлов). Запуск `d222d71` включил однократный полный CI; **CircleCI #129 verify-connection SUCCESS, #130 neoforge-compile SUCCESS** (`compileJava processResources`). Это первая проверенная *актуальная* компиляция после переноса новых алхимических механизмов, НЕ игровой запуск и НЕ подтверждение работы цепочки эссенции. Постоянный опциональный режим `run_compile=false` восстановлен `cb11898`.

**Следующий этап:** `jar`/сервер и реальный Minecraft smoke для обновлённой алхимии. Больше не писать, что текущий NeoForge compile не прошёл. Общий рейтинг готовности оставлен 59/100 до игрового аудита.
