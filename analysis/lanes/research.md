# Направление: исследования / feature/research

**Статус:** 🟡 M07 активен с 18.10.2026; рабочая ветка `feature/research`, исключительно исследования. Текущий подэтап: M07.1 аудит и M07.2 подключение реально существующих помощников к выбору оригинальных карточек.
**Основной план:** [ROADMAP_RU.md](../../ROADMAP_RU.md).
**Опора:** [THEORYCRAFT_CARD_PROGRESS_RU.md](../THEORYCRAFT_CARD_PROGRESS_RU.md), `analysis/PROGRESS_AUDIT_2026_10_16.md`.

## Уже реализовано частично, не повторять
- 37 аспектов, 148 исследований, 33 исходные карты; основы 14 помощников.
- Реальные игровые сценарии FIRSTSTEPS, создание теории, вытягивание двух карт, активация одной; оригинальный начальный GUI.
- Не доказан полный цикл всех 33 карт/148 ветвей, их расходников, наград и browser UI; это и есть M07.

## Первые задачи после запуска чата
1. 🟡 M07.1 — fresh-read текущего main, своего feature/research, `THEORYCRAFT_CARD_PROGRESS_RU.md`; построить таблицу **непроверенных** карт/действий и указать конкретные пропуски.
2. M07.2 — сделать первый отсутствующий настоящий карточный сценарий без подмены предметов.
3. M07.3 — категории и браузер Таумономикона.
4. M07.4 — end-to-end и сохранение сессий, CI доказательства.
5. PR → main и обновление карты только после подтверждения.

## Журнал
- 18.10.2026: ветка создана в подготовительном режиме. **Не помечать M07 🟡, пока пользователь не дал второму чату команду начать.**


## 18.10.2026 — подход M07.1 / первый узкий M07.2: настоящие блоки-помощники

**Аудит перед работой:** `main` и `feature/research` начинались на `a4280834adc5a20b6951098af937d30ca4aeca56`. Прочитаны `ROADMAP_RU.md`, `AGENTS.md`, `PARALLEL_WORKFLOW_RU.md`, этот lane, `THEORYCRAFT_CARD_PROGRESS_RU.md`, `PROGRESS_AUDIT_2026_10_16.md`, `AID_BYTECODE_AUDIT_2026_10_10.md`; проверены CI и исходные Java-классы. Формулы всех **33/33** исходных карт существуют в core; **игровая проверка только части карт**. Сохранены исторические **14** классов helpers и геометрия сканирования, но реальные NeoForge identity-checks реализованы далеко не для всех. Реальный клиент ранее подтверждал старт теории, draw и активацию одной карты (см. аудит); все карточки/предметы/сохранение при холодном restart ещё НЕ доказаны.

**Обнаруженный дефект:** `NeoForgeTheorycraftAidBridge.discover` не распознавал два УЖЕ перенесённых зарегистрированных блока, нужных оригинальным исследовательским помощникам: `thaumcraft:crucible` → `AidBasicAlchemy` и `thaumcraft:infusion_matrix` → `AidBasicInfusion`. Старое `Set.copyOf(scanner.scan(...))` не обязано сохранять исходный HashMap iteration order обнаруженных помощников.

**Кодовый коммит:** [`9e59ceb4d69ac9a4eb60aba3b465a3ed53f3e052`](https://github.com/craftkot/tgaumcraft/commit/9e59ceb4d69ac9a4eb60aba3b465a3ed53f3e052). Изменён **только** `src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftAidBridge.java`: оба оригинальных блока привязаны к своим BETA26 helpers без fake alternatives, сканирование по-прежнему не загружает чанки; результат Set копируется в `LinkedHashSet` и возвращается read-only с сохранением итерации. Классы алхимии и инфузии не менялись.

**Тесты в коммите:** добавлен `ResearchAidDiscoveryParityTest.java` (14 registries, оригинальные aid-card pools, границы 4/1/4, duplicates, picked-stack, новая привязка двух blocks + проверка серверного chunk guard) и независимый runner `tools/run_research_aid_parity.sh`. Исходные wiring assertions и наличие регистраций проверены инспекцией исходного кода. **Полный javac/NeoForge test и клиентский сценарий в данном подходе НЕ запущены**: есть код и регрессионные тесты, но не доказательство проходящего runtime; тест можно исполнить из checkout Java 21 командой `bash tools/run_research_aid_parity.sh`.

**CI после кода:** GitHub Actions для `feature/research` завершились `failure` ([39077597045](https://github.com/craftkot/tgaumcraft/actions/runs/39077597045), [39077597102](https://github.com/craftkot/tgaumcraft/actions/runs/39077597102)); попытка чтения job logs вернула `BlobNotFound`, точная причина недоказана. CircleCI [#225](https://circleci.com/gh/craftkot/tgaumcraft/225) SUCCESS только для `verify-connection`, **не** для javac/NeoForge/gameplay. Никаких новых баллов по общему индексу: **59/100**.

**Что остаётся в M07:** доказать через настоящий research table, что оба helper появляются в UI, выбираются с реальным debit inspiration и действительно позволяют оригинальные CardConcentrate/Reactions/Synthesis, CardMeasure/Channel/Infuse с честным item/XP debit; не только блок обнаружен. Затем остальные 6 не подключённых helper identities (не подменять отсутствующие блоки), все 33 gameplay активатора с источниками и ресурсами, полное дерево Таумономикона, scan world/entities/sky, save/restart, game+CI. `M07.1` ещё не закрыт — нужны матрица 33 карт × тесты; следующий конкретный шаг M07.2: smoke-сценарий на живом research table с `CRUCIBLE` рядом и исходной `CardConcentrate` (без бесплатных предметов), после восстановления полноценного CI.

**Запрет:** никаких изменений в `main`, чужих feature branches, Gradle/CI/общем ROADMAP, алхимических/инфузионных runtime-классах. PR в `main` открыть только после end-to-end проверки M07, не сливать самостоятельно.
