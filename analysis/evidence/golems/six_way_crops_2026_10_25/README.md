# M55 — настоящие верхняя и нижняя опоры

25.10.2026 (МСК), Java21/Minecraft1.21.1/NeoForge21.1.250. После M54 production Registry/Farm не менялись. Добавлены только opt-in SixWayPlant/SixWaySeed и три server GameTest. Источник контракта — прежняя подтверждённая ветвь BETA26 BlockDirectional/FACING; не автоматическая поддержка любого блока с одноимённым свойством.

Два native сценария реально ломают зрелый шестисторонний specimen с JUNGLE_LOG сверху или снизу. Pending intent сохраняет правильный support ordinal и точное named non-BlockItem seed при NBT decode. Настоящий useOn кликает сохранённую опору по обратной грани и ставит молодой блок в AIR. Callback сам не списывает семя и меняет компоненты реального резерва: Farm всё равно оплачивает ровно1из2, исходный остаток остаётся named seed1. Проверены FACING/AGE0/настоящий log, XP2, пустые offhand/recovery/intent и отказ повторно выполнить завершённый lease.

Третий сценарий проверяет принадлежность шестистороннего свойства исходному блоку, отказ одноимённого horizontal property, шесть допустимых enum-направлений, exact components и отсутствие автоматической crop registration от одной seed mapping.

Полный build и **33/33 optional native tests PASS**,2m34s, process exit0. [Реальные markers](assertions.txt), [JAR CRC/731 Java21 classes/SHA256](jar.json). Запуск:

```bash
gradle --offline --init-script tools/golems.gradle build -x test runGolemPlantAdapters
```

Новые3 tests дополняют предыдущие30, поэтому основной набор252 и real Farmer’s Delight4 не повторялись без изменения production. Их актуальный результат и cold EAST proof — [M54](../directional_crops_2026_10_25/README.md). Верхняя/нижняя опоры проверены native completion и NBT decode, не отдельным двухпроцессным vertical restart или клиентской картинкой/навигацией.

Отдельный тест неактивной/выгруженной боковой опоры не добавлен и не засчитан. Нативный ticket gradient может держать соседний chunk FULL; имитация missing chunk подменой tracker не является доказательством. Эта граница, конкретный foreign directional binary и полная ABI-совместимость остаются открыты. Все новые blocks/items gated; обычный контент и server mechanics не меняются.
