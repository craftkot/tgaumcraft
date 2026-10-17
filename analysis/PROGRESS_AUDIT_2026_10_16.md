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
