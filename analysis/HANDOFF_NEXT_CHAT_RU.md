# HANDOFF — Thaumcraft 6.1.BETA26 -> Minecraft 1.21.1 NeoForge — новый чат

> **АКТУАЛЬНО НА 13.10.2026:** [короткая передача](HANDOFF_TRANSFER_2026_10_13.md) — читать первой после fresh-read `main`. Старые SHA и оценки ниже исторические. Последний базовый кодовый HEAD `2c56f66dd36e6f7a6a7dda352ccf0eae9addc058`, основная CI SUCCESS #38435467940; оценка ~86%.

Дата фиксации: 2026-10-11. Язык пользователя: русский.
Репозиторий: https://github.com/craftkot/tgaumcraft
Ветка: main. Последний проверенный КОДОВЫЙ checkpoint: 5d85db4d6d2106c65874edfe0503b8dd71715be4.
Проверенный GitHub Actions run: 38148914429 (#454) — SUCCESS (Core parity tests, asset checks, Gradle NeoForge build, dedicated server smoke и upload built jars; artifact thaumcraft-port-jars).
Оценка прогресса: **83%** (округление пока не повышено: улучшена полнота playable theory UI, но крупные подсистемы всё ещё отсутствуют). Это приблизительная оценка проекта, **не** процент подтверждённой полной работоспособности или ручного игрового тестирования.

## Начать в новом чате строго с этого
1. Сделать fresh-read **актуальной**, а не сохранённой здесь копии `main` через подключённый GitHub; проверить SHA и свежий CI.
2. Обязательно прочесть `analysis/HANDOFF_NEW_CHAT_RU.md`, **этот файл** (`analysis/HANDOFF_NEXT_CHAT_RU.md`) и `analysis/CONTINUATION_83_PLAYABLE_THEORY_GUI.md`. Если затронута теория, читать также `analysis/CONTINUATION_81_ORIGINAL_33_CARD_FACTORY.md` и `analysis/CONTINUATION_82_ACTIVATION_EFFECTS.md`.
3. Продолжать РЕАЛЬНУЮ разработку и коммиты в существующем репозитории. Не начинать с нуля, не заменять Thaumcraft похожим модом, не симулировать прогресс без коммитов/тестов.
4. Исходная цель — перенести **ВЕСЬ** оригинальный Thaumcraft 6.1.BETA26 (1.12.2) на NeoForge Minecraft 1.21.1, с оригинальными механиками, контентом, ресурсами, всеми исследованиями; сохранить/улучшить русскую локализацию и читаемость шрифта. По возможности исправлять реальные баги оригинала без изменения сути мода.
5. После каждого осмысленного этапа фиксировать исходники и handoff в GitHub, проверять CI, сообщать реальный процент. Пользователь использует `П` как «продолжай», обычно предпочитает ответ только процентом. Избегать вымышленных процентов.

## Что реально сделано к передаче
- Точное сохранение 148 оригинальных исследований и research JSON, 37 аспектов, категорий, player knowledge/warp, FIRSTSTEPS, базовая игровая инфраструктура сканирования/таумометра, небесные записки и часть мировых механизмов.
- Исследовательский стол: оригинальная модель/GUI/инвентарь и работающие кнопки **1** (создать/оставить выбранное), **2/3** (нарисовать 2/3 карты; 3 только при реальном bonusDraws), **4..6** (активировать карточку 0..2), **7** (завершить) и **9** (отменить). Реализованы сохранение original CardChoice NBT, поле lastDraw, 14 видов aid и сканирование части реально портированных соседних блоков.
- **Все 33 оригинальные карты**: веса/регистрация, bytecode-аудированные поля, первичная фабрика, динамические локализованные названия, серверная генерация, оригинальное staged activate поведение, вдохновение, XP, материалы, чернила, бумага, знание, warp, награда аспектным кристаллом. Серверная транзакция перепроверяет ресурсы перед коммитом, не разрешает активацию на клиенте и не даёт переключить ранее выбранную карточку. Случайные карты, которым нужны ещё не портированные материалы, **fail closed**, а не дают выдуманные награды.
- Историческая особенность: **CardScripting** расходует 2 единицы чернил и 1 лист бумаги при активации; обычные карты — 1 единицу чернил, а одна генерация предложений карт — 1 лист бумаги. Особые XP правила Enchantment, Spellbinding, DarkWhispers перенесены.
- Добавлены реальные 7 вариантов Curio, предметы/наполненные пузырьки с 10 essentia, 13 celestial notes, Thaumcraft creative tab, много точных исходных PNG, переводы EN/RU и более читаемый UI.
- Исправлен опасный баг алиасинга live `PlayerKnowledgeData.getResearchList()`, который мог стирать исследования при применении card effects. Есть тест.
- Коммит `5d85db4`: восстановлена важная часть оригинального GUI-контракта карт. Сервер синхронизирует raw inspiration/ink/paper/XP-mode, точные уже сопоставленные ItemStack-требования (до двух, как в BETA26), consumed/kept flags и текущую причину preflight-блокировки. Полный cardNBT клиенту НЕ передаётся. Требования идут через скрытые read-only ghost slots vanilla menu sync; `quickMoveStack` жёстко отсекает эти слоты, чтобы preview нельзя было материализовать. EN/RU tooltip теперь показывает реальную стоимость и нужные предметы. CI #454 зелёный.

## Что НЕ следует считать законченным
- CI **успешен**, но это автоматическая компиляция + dedicated-server smoke, **не** доказательство полного игрового прохождения, работы клиентских GUI/текстур/всех карт и полного контента BETA26.
- Огромные системы оригинала — тигли/алхимия, инфузия, големы, фокусы, существа, измерения, многие блоки и активные механики — остаются не доведены до полной рабочей версии. Многие aid world blocks ещё отсутствуют.
- Legacy ItemStack metadata/NBT и dynamically computed visSize некоторых исходных предметов пока сознательно блокируют отдельные карты. Не ослаблять проверки без bytecode/ресурсного подтверждения.
- `README.md` в корне частично УСТАРЕЛ (написан ещё для Stage 9/10); актуальный статус находится в `analysis/HANDOFF_NEW_CHAT_RU.md` и `analysis/CONTINUATION_83_PLAYABLE_THEORY_GUI.md`. Не регрессировать в старый этап.
- Для исходной сверки оригинальный пользовательский JAR `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`, НЕ хранится в публичном GitHub. В архиве передачи пользователя прилагается как `ORIGINAL/Thaumcraft-1.12.2-6.1.BETA26.jar`; в новом чате нужно распаковать, проверить SHA и использовать `javap -c -p`/исходные ресурсы при сомнениях. Если JAR не приложен, запросить именно его только когда невозможно продолжать без него.

## Приоритет следующей работы
1. Fresh-read `main` и свежей CI перед любым новым изменением; не полагаться на этот checkpoint как на HEAD.
2. При технической возможности провести **ручной NeoForge 1.21.1 клиентский smoke**: создать research table, добавить чернильницу и бумагу, bookshelf aid, draw 2/3, активировать карту с обычным и special-cost путём, keep/finish, проверить точные tooltip-стоимости, списания и читаемость RU. Честно сообщать, если GUI запустить невозможно.
3. Продолжить аудит/восстановление отсутствующих оригинальных предметов/блоков и рецептов по JAR, особенно тех, которые сейчас заставляют Saved ItemStack cards или research aids fail closed. Не добавлять placeholder-замены только ради разблокировки карточек.
4. Затем двигать крупные подсистемы: crucible/alchemy, infusion, foci, golems, entities/dimension и остальные оригинальные assets/mechanics.
5. Тестировать инварианты (нет дюпа наград/бесплатной активации, ghost preview slots нельзя забрать, отказ без списания при нехватке ресурсов, сохранение оригинальных NBT и стадий) и прогонять GitHub Actions.
6. Пользователь просил в **КОНЦЕ** опционально поменять сканирование неба: выдавать рандомную **ещё не полученную** лунную записку вместо жёсткой привязки к фазе луны, чтобы исследование не затягивалось. Пока лунные правила BETA26 остаются оригинальными. Пользователь также просил улучшить нечитаемый оригинальный шрифт; готовые улучшения нельзя терять.

## Ключевые файлы для редактирования
- `src/neoforge/java/dev/ehtid/thaumcraft/blockentity/ResearchTableBlockEntity.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/menu/ResearchTableMenu.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/client/ResearchTableScreen.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftActivationPreflight.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftActivationStager.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftItemTransaction.java`
- `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftDrawBridge.java`
- `src/main/java/dev/ehtid/thaumcraft/port/core/LegacyTheorycraftActivationEffects.java`
- `src/main/java/dev/ehtid/thaumcraft/port/core/LegacyResearchTableData.java`
- `src/test/java/dev/ehtid/thaumcraft/port/core/CoreParityTest.java`
- `.github/workflows/ci.yml`

Всё существенное находится в GitHub; связанный архив несёт только подсказку и оригинальный JAR, а не актуальную копию исходников. Обязательно работать с **fresh main** и поддерживать `analysis/HANDOFF_NEW_CHAT_RU.md` после каждого этапа.
