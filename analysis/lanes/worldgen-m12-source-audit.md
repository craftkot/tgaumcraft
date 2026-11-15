# M12 — исходный аудит генерации деревьев (15.11.2026)

## Происхождение и границы

Сверен архивный Java 1.12-код в стороннем форке [SpencerGraffunder/Thaumcraft26](https://github.com/SpencerGraffunder/Thaumcraft26/tree/master/reference/java_old-1.12/common/world), конкретно [ThaumcraftWorldGenerator.java](https://github.com/SpencerGraffunder/Thaumcraft26/blob/master/reference/java_old-1.12/common/world/ThaumcraftWorldGenerator.java), [WorldGenSilverwoodTrees.java](https://github.com/SpencerGraffunder/Thaumcraft26/blob/master/reference/java_old-1.12/common/world/objects/WorldGenSilverwoodTrees.java) и [WorldGenGreatwoodTrees.java](https://github.com/SpencerGraffunder/Thaumcraft26/blob/master/reference/java_old-1.12/common/world/objects/WorldGenGreatwoodTrees.java). Это **архивная копия в стороннем порте**, а не доказанная байтовая идентичность с оригинальным 6.1.BETA26 JAR. Требуется независимая сверка с BETA26 перед заявлением полного паритета.

## Наблюдаемые правила старого генератора

- `generateAll` запускает vegetation при `getDimBlacklist(dimension) == -1`, включённом `generateTrees`, не-flat типе мира и `newGen || regenTrees`.
- `generateVegetation` проверяет biome blacklist в точке `chunk*16+8, Y=50`; если результат не `-1`, vegetation пропускается.
- Для Silverwood сначала выполняется `random.nextInt(80) == 3`, для Greatwood независимо `random.nextInt(25) == 7`. Это **попытки** на чанк, не гарантированные успешные деревья.
- Оба дерева выбирают X/Z как `chunk*16+8+random(-4..4)` (включительно), а Y получают через `getPrecipitationHeight`.
- Greatwood: `getBiomeSupportsGreatwood(biomeId) > random.nextFloat()`; далее `WorldGenGreatwoodTrees(false, random.nextInt(8)==0)`. Spider-variant — условная 1/8 среди прошедших проверку биома, не среди всех чанков.
- Silverwood: `getBiomeSupportsGreatwood(biomeId)/2f > random.nextFloat()` **или** magical biome (кроме собственного Magical Forest) **или** биом с legacy ID 18/28; далее `WorldGenSilverwoodTrees(false,7,4)`. Нельзя заменять это просто тегом magical без явной таблицы эквивалентности legacy биомов.
- Silverwood generator выбирает высоту `7+random.nextInt(4)` (7..10); проверяет вертикальный объём, подходящий грунт и заменяемость блоков. В исходнике имеются широкая крона, крестообразный ствол и дополнительные корни/ветви; обычное vanilla tree feature не воспроизводит форму.
- Greatwood generator имеет двухблочный ствол (`trunkSize=2`), `heightLimitLimit=11`, параметр `heightAttenuation=0.618` и отдельную ветвистую крону. Vanilla oak feature не является паритетной заменой.

## Критические расхождения при прямом переносе в NeoForge

- В [другом порте](https://github.com/SpencerGraffunder/Thaumcraft26/blob/master/src/main/resources/data/thaumcraft/worldgen/placed_feature/silverwood_tree.json) rarity_filter 80 + in_square; для Greatwood rarity_filter 25 + in_square. Эти JSON — **не** доказательство паритета: `in_square` выбирает весь чанк, а исходный диапазон X/Z — только 9×9 около центра; `rarity_filter` не сохраняет исходное `nextInt(80)==3` положение в RNG-последовательности; biome predicates и форма дерева требуют отдельной реализации.
- Старые worldgen операции могут читать/писать соседние чанки (крона Silverwood до 5 блоков от центра). Существующий `WorldgenPlacementGate` проверяет **origin**, а не все будущие block writes. Не считать это доказательством безопасности соседних чанков.
- Legacy ID 18/28, `BiomeDictionary.Type.MAGICAL`, dimension blacklist, flat-world exclusion и конфигурационные regen flags требуют документированного маппинга на 1.21.1, а не подмены произвольными дефолтами.

## Следующий минимальный production slice

Создать в **собственном M12 namespace** один реальный `Feature`/placement consumer, который получает контекст новых чанков и использует проверку origin, с независимой реализацией геометрии Silverwood, сохраняющей форму и soil/height/replacement preconditions. Перед подключением к общим регистраторам согласовать владельца регистраций; не писать в чужие файлы. Добавить точные Java 21 тесты seed/границ и затем NeoForge compile + серверную генерацию новых чанков. Никакого утверждения о работающем worldgen до такого теста.
