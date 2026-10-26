# M58 — оплаченные пластины для компонентов големов

26.10.2026 (МСК), `feature/golems`, base `1bb95d6`; main `a4280834` не менялся. Это реальное дополнение рецептов и исправление типа NBT существующего ironplate, а не завершение всего survival/M10.

## Подтверждённый источник

Официальный Thaumcraft-1.12.2-6.1.BETA26.jar, SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`. `javap -c -p thaumcraft.common.config.ConfigRecipes`, метод `initializeNormalRecipes`:

| Recipe ID | Bytecode offsets | Вход: горизонтальный ряд | Выход |
|---|---|---|---|
| brassplate | 1424–1467 | 3 × ingotBrass | 3 × plate@0 |
| thaumiumplate | 1468–1511 | 3 × ingotThaumium | 3 × plate@2 |
| voidplate | 2075–2118 | 3 × ingotVoid | 3 × plate@3 |

Это normal oreDictRecipe, без knowledge/Vis/crystal gate. Закрытые исходники автора не объявлены опубликованными; бинарники/дизассемблированный класс не добавлены. В текущем порте ingot@0=thaumium, @1=void, @2=brass; plate@0=brass, @1=iron, @2=thaumium, @3=void. Read-only просмотр feature/alchemy/research не обнаружил этих трёх recipe IDs; чужие ветки не менялись.

## Реализация и фактические проверки

- Три стандартных shaped JSON recipe используют собственный ingredient `thaumcraft:golem_ingot`. Нативный общий item ingot всегда проверяет LegacyIngotMeta до generic tags; внешний отдельный item допускается через c/forge:ingots/<metal>. Это современная проекция oreDict, не доказательство работы всех сторонних модов.
- Новый type зарегистрирован только в GolemModule; существующая crafting machinery переиспользована. isSimple=false сохраняет predicate при native recipe sync; actual Ingredient.CONTENTS_STREAM_CODEC roundtrip проверил все три металла и различение native metadata, buffer полностью прочитан/освобождён.
- JSON numeric CUSTOM_DATA давал byte-tag вместо требуемого TAG_INT. В четырёх plate recipes используется SNBT `{LegacyPlateMeta:N}`; runtime Item API не ослаблен. Existing ironplate меняет только это поле.
- 6 новых обычных GameTests используют реальный Survival CraftingMenu.clicked: расход 1 из каждого из трёх slots, все row offsets, bare/explicit thaumium, правый клик whole result, named/unrelated NBT, malformed metadata, mixed/incomplete/extra inputs без списания.
- Последний тест реально крафтит 9 brass plates из 9 ingots и 6 iron plates из 6 vanilla ingots, переносит именно оплаченные stacks в настоящий ArcaneWorkbenchMenu, получает 3 mechanisms и 1 mind. Израсходовано 8 brass plates, 6 iron plates, 3 sticks, 3 panes, comparator, 55 Vis, 4 Ignis/3 Aqua/1 Ordo crystals. Остались 1 brass plate и 2 mechanisms. Ни плат, ни механизмов fixture не выдаёт.
- Только после обычных крафтов arcane fixture явно задаёт уже имеющиеся BASEARTIFICE и MINDCLOCKWORK stage2 и оплачиваемые Vis/crystals. Это проверка доступного crafting участка, не прохождение исследований игроком с нуля.

## Прогоны и ошибки

[assertions.txt](assertions.txt) содержит подлинные выдержки, [jar.json](jar.json) — проверку конечного артефакта.

1. RED до реализации: 269 total / 6 required failures Missing original paid recipe; остальные 263 не сломаны. BUILD FAILED1m16s.
2. Первый compile обнаружил неправильный вызов Iterable.stream(); заменён на StreamSupport/spliterator, без изменения API/Gradle.
3. Следующий native candidate: 5 required failures на output metadata. Разобран CompoundTag JSON codec, исправлен точный тип через SNBT; rejected-input case прошёл. Этот запуск не считается PASS.
4. Финальный Java21 `build -x test runGolemGameTest`: **All269 required tests passed, BUILD SUCCESSFUL1m24s, exit0**. Включены старые263 и новые6. Exit0 получен из процесса, не выведен из marker.
5. Свежий `bash tools/run_core_parity.sh` под JDK21: весь runner/39962 construction/2909 task assertions и финальный CORE_PARITY_OK, exit0.
6. Финальный JAR: ZIP CRC/unique entries/744 Java21 classes/unique EN-RU keys/четыре typed recipe JSON PASS. SHA256 в jar.json.

Независимый fresh-context read-only review: Critical/Important findings нет; reviewer отдельно тесты не запускал. Source/JSON/registration/native codec/payment проверены, решение о PASS основано на фактических командах выше.

Команды (Gradle/JDK из существующей облачной среды, без shared config changes):

```bash
/workspace/reference/gradle-golems --offline --init-script tools/golems.gradle build -x test runGolemGameTest
JAVA_HOME=/workspace/toolchain/jdk-21.0.12.1+1 PATH=/workspace/toolchain/jdk-21.0.12.1+1/bin:$PATH bash tools/run_core_parity.sh
```

Полные локальные логи: `/workspace/reference/golems-m58-{red,green,green-final,final,core}.log`. Суффикс green не означает успеха: результаты каждого явно перечислены выше. Логи UTC, дата checkpoint МСК. Графический клиент, внешний бинарный tag provider, optional33/FD4 и cold restart в M58 повторно не запускались; прежние подтверждения M57 остаются историческими. NBT сущности/печати и сетевые пакеты големов не менялись.

## Открытые зависимости

- M07: текущий Thaumonomicon UI/packet реализует FIRSTSTEPS, общего прохождения MINDCLOCKWORK/UNLOCKGOLEMANCY нет. Сам knowledge gate @2 работает; free unlock не добавлен.
- M12: зарегистрированы Greatwood planks, но отсутствуют log/tree/acquisition. Дерево голема не заменяется oak, бревно не выдаётся бесплатно.
- M06/M08: advanced infusion/levitator и полная аура — отдельные зависимости. Source art/API и inherited M43 split-save limitation не закрыты этими рецептами.

Следующий конкретный шаг: интеграция подтверждённых M07 progression и M12 Greatwood acquisition в basic golem route. До появления зависимостей нельзя честно объявить полный survival путь готовым. Работа остаётся в существующем Draft PR#3 без merge.
