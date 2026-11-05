# Полный исходный каталог инфузии BETA26

Источник — оригинальный `Thaumcraft-1.12.2-6.1.BETA26.jar`, SHA-256
`9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`.
Полные дизассемблированные классы остаются в `/workspace/reference/javap`, вне Git.
В репозитории находятся извлечённые определения и проверяющий код.

`ConfigRecipes.initializeInfusionRecipes` действительно регистрирует **56** рецептов:
47 `InfusionRecipe`, 8 `InfusionEnchantmentRecipe`, один `InfusionRunicAugmentRecipe`.
Отдельные **11** `addFakeCraftingRecipe` — три примера рунического усиления и
восемь примеров зачарований — не становятся исполняемыми рецептами.
Просмотр всех class-файлов JAR подтверждает, что вызовы
`addInfusionCraftingRecipe` находятся только в `ConfigRecipes` и самом API.

## Данные

- `data/thaumcraft/infusion/beta26_source_catalog.json`: все 56 реальных регистраций,
  research, класс, offsets конструктора/регистрации, центральный и упорядоченные
  боковые входы, метаданные, исходные ore aliases, NBT, аспекты, instability и output.
- `beta26_legacy_mappings.json`: все 128 используемых item/block-полей
  (50 vanilla, 78 Thaumcraft), точные registry ID, варианты metadata, flattening
  vanilla, соответствие 37 констант аспектов их тегам и SHA исходных классов.
  Имена vanilla разрешены по `mcp_stable-39-1.12.zip:fields.csv`; имена Thaumcraft —
  по реальным конструкторам регистрации. Все 77 конкретных семейств Thaumcraft
  проверены по исходным model/blockstate assets.
- `runtime_recipes.json`: десять определений с реальными зарегистрированными
  входами: девять динамических (RunicArmor и восемь infusion enchantments) и
  фиксированный MirrorEssentia. Последний использует настоящий mirrored_glass,
  получаемый исходным arcane-рецептом, и выдаёт настоящий mirror_essentia.
  `outputItemId` у динамических определений — зарегистрированный представитель;
  фактический результат создаётся из оплаченного центрального предмета.
- `beta26_dependencies.json`: состояние каждого из 56 рецептов относительно
  зарегистрированного контента ветки. Первоначальный срез: 7 встроенных Crystal
  Farmer, 9 динамических и 1 фиксированный runtime, 4 в отдельном golem-модуле,
  35 требуют настоящего
  отсутствующего контента. Этот файл не регистрирует предметы и не заменяет их AIR.
- `beta26_acquisition_source.json`: пять исходных arcane-рецептов получения
  mirrored_glass, matrix_speed, matrix_cost, stabilizer, inlay. Это исходные
  определения, не заявление об их подключении к survival workbench.

## Предикаты и результаты

`stack.metadata=32767` означает исходный wildcard; обычная численная metadata
сохраняется точно. `item_any_metadata` соответствует `Ingredient.fromItem` или
сырому Item/Block через Forge `CraftingHelper`. `any_of` сохраняет весь исходный
массив альтернатив; у CausalityCollapser две позиции принимают любую из **16**
расцветок nitor. `aspect_crystal` сохраняет аспект исходного makeCrystal; его NBT
нельзя заменить голым `crystal_essence`. `seal_stack` хранит точный ключ seal,
поскольку его численная metadata зависит от регистрации настоящего golem-модуля.

Исходный `ThaumcraftApiHelper.getIngredient` автоматически делает
`IngredientNBTTC` для ItemStack с NBT. Он сравнивает item, metadata и обязательные
NBT-теги, разрешая дополнительные теги входного предмета. Поэтому обе NBT Potion
в VerdantHeartLife/Sustain обязательны: `strong_healing` и `strong_regeneration`.
Исходный IngredientNBTTC для пустого `enchanted_book` имеет пустой NBT-предикат и
принимает книги с дополнительными тегами; конкретное vanilla-зачарование не задано.

Шесть обычных регистраций используют `Object[]{key,NBT}`: HelmGoggles,
MaskGrinningDevil, MaskAngryGhost, MaskSippingFiend, VerdantHeartLife,
VerdantHeartSustain. Результат сохраняет центральный предмет и меняет один тег.
Другие stack-результаты сохраняют metadata и предустановленные infusion enchantments.
`EnumInfusionEnchantment.addInfusionEnchantment` записывает `infench` как список
compound с `id:short`, `lvl:short`, а не vanilla enchantment components.

Для RunicArmor исходный `getComponents(center)` возвращает salis mundus и
`1 + max(0, oldRunic)` amber. Стоимость: `PROTECT=20+int(20*2^oldRunic)`,
`CRYSTAL=ENERGY=PROTECT/2`, instability `5+oldRunic/2`; результат повышает
`TC.RUNIC:byte` на один. Исходный центральный предмет — armor или настоящий Bauble.
Наличие vanilla armor-пути не означает перенос отсутствующей Baubles-системы.

Восемь infusion enchantments имеют исходную instability=4. Стоимость каждого
аспекта масштабируется Java float-формулой
`int(base * ((oldLevel+1) + otherEnchantmentCount*0.33f))`.
Результат повышает уровень и с исходной вероятностью добавляет `TC.WARP:byte`.
MaxLevel и категории инструментов берутся из enum; они сохранены в полном каталоге.

## Получение вспомогательных блоков

| Исходная регистрация | Research | Vis | Кристаллы | Определение |
|---|---|---:|---|---|
| mirrorglass | BASEARTIFICE | 50 | WATER1 ORDER1 | shapeless: quicksilver + paneGlass → 1 mirrored_glass |
| MatrixMotion | INFUSIONBOOST | 500 | AIR1 ORDER1 | SNS/NGN/SNS; S=stone_arcane, N=nitor, G=diamond_block |
| MatrixCost | INFUSIONBOOST | 500 | AIR1 WATER1 ENTROPY1 | SAS/AGA/SAS; S=stone_arcane, A=alumentum, G=diamond_block |
| Stabilizer | INFUSIONSTABLE | 250 | EARTH1 WATER1 ENTROPY1 | SRS/BVB/IMI; S=slab_arcane_stone, R=blockRedstone, B=stone_arcane, V=vis_resonator, I=inlay, M=mechanism_complex |
| RedstoneInlay | INFUSIONSTABLE | 25 | WATER1 | shapeless: dustRedstone + ingotGold → **2** inlay |

У glyph-блоков исходный SRG `field_150484_ah` — **DIAMOND_BLOCK**.
Таблица разрешения MCP проверена; догадка о glowstone была отвергнута.
Stabilizer не является infusion-рецептом; он зарегистрирован в arcane-методе.

## Проверка

`python3 tools/extract_or_verify_infusion_catalog.py` заново интерпретирует все
3396 инструкций исходного метода, включая циклы, и сравнивает JSON целиком.
Неизвестный opcode/вызов останавливает извлечение. Фактические регистрации
snapshot-ятся до последующих изменений локальных объектов.
Проверяются полный JAR hash, SHA четырёх классов, отсутствие других регистраторов,
56/11 и 47/8/1, независимые вручную проверенные случаи: CrystalClusterAir,
NBT goggles/три mask, два potion, pre-enchanted axe, метаданные VisAmulet,
RunicArmor, maxLevel ESSENCE, seal key, wildcard primordial pearl и nitor16.
Пять acquisition-рецептов извлекаются из точных bytecode-диапазонов и тоже
сравниваются с хранимым файлом.

Для дополнительного прямого сопоставления с JAR:

```sh
python3 tools/extract_or_verify_infusion_catalog.py \
  --javap /workspace/toolchain/jdk-21.0.12.1+1/bin/javap
```

`--write` намеренно обновляет четыре производных JSON-файла после изменения
извлечения или реальных регистраций контента. Не создаёт дизассемблированные
Java-файлы, не запускает Gradle и не является игровым доказательством M06.
