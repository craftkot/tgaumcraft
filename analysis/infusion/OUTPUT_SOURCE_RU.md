# M06 — семантика входов, результата и доплаты BETA26

Источник: `/workspace/reference/Thaumcraft-1.12.2-6.1.BETA26.jar`.
Полный `javap` остаётся вне Git: `/workspace/reference/javap/OutputMatrixAudit.txt`,
`InfusionSpecialRecipes.txt`. В репозитории находятся адаптер и независимо заданные проверки.

- `TileInfusionMatrix.craftingFinish`, offsets30..142: копируется весь исходный output
  ItemStack, включая count и NBT. Если центр повреждён и damageable, а новый результат
  damageable и ещё не повреждён, переносится `(int)(outputMax * ((float)centerDamage / centerMax))`.
- offsets145..185: Object[]{String,NBTBase} сохраняет центральный ItemStack и меняет
  ровно один указанный NBT-тег. offsets188..250: vanilla Enchantment повышается на1,
  остальные зачарования сохраняются. Наличие этого пути не означает наличие vanilla
  enchant-рецептов в каталоге: среди56 исходных регистраций их не обнаружено.
- `craftCycle`, offsets1371..1442: боковой ItemStack заменяется копией реального
  `getContainerItem(stack)`. В1.21 используется `getCraftingRemainingItem()`, включая
  компоненты результата. Публикация/receipt принадлежат матрице и пьедесталу.
- `IngredientNBTTC.apply`: item, точный metadata и каждый требуемый верхний NBT-ключ;
  значение ключа сравнивается целиком через NBT.equals. Дополнительные верхние ключи
  допустимы, вложенное «подмножество» не вводится. Обычный Ingredient игнорирует NBT.
  Legacy subtype metadata переносится через реально зарегистрированные современные ID;
  StackAlternative дополнительно поддерживает точный damage и обычный wildcard32767.
- `InfusionRunicAugmentRecipe`: копия центра, `TC.RUNIC` byte увеличивается на1;
  стоимость `praemunio=20+(int)(20*2^old)`, `vitreus/potentia=стоимость/2`,
  instability `5+old/2`, компоненты salis, amber и ещё old amber. В доступном runtime
  применим к настоящей ArmorItem. Baubles не подменяются фиктивным API.
- `InfusionEnchantmentRecipe`: source enum/ordinal/max/tool classes; прибавляется1
  к исходному уровню. Аспекты по отдельности `(int)(base * (oldLevel+1+0.33F*otherCount))`,
  затем применяется множитель алтаря. С вероятностью `existingCount/10` повышается
  byte `TC.WARP`; сохранённый случайный результат проверяется по всем компонентам
  против допустимых исходом стэков. Решение фиксируется один раз при старте ритуала.
- `checkCrafting` сохраняет ключи аспектов, даже когда float-множитель округляет цену
  до0. `AspectList.reduce` сохраняет нулевые ключи после оплаты. В `craftCycle1460..1547`
  отсутствие бокового ингредиента с шансом `nextInt(1+index)==0` выбирает любой такой
  ключ, добавляет1 и снижает stability на0.25. Ledger хранит исходную цену отдельно
  от `surchargeCosts`, проверяет `source+surcharges−remaining=paid`; неизвестные ключи,
  отрицательные/нулевые доплаты и значения свыше100000 отклоняются. Общий restore
  без исходного рецепта по-прежнему не принимает отсутствующую цену.

`LegacyInfusionRecipeCatalog` сохраняет старые конструкторы и v1 fingerprint обычных
Crystal Farmer/module определений. Расширения входят в v2 fingerprint; загрузчик
канонизирует JSON и SNBT, сохраняя числовые типы NBT. Повторная загрузка разрешает
динамическое определение только из исходного рецепта и полного центрального снимка.
Отсутствующий исходный контент не заменяется AIR или новым вымышленным предметом.
Полный source-каталог56 отделён от запускаемых строк: ограничения чужих подсистем
перечислены в `beta26_dependencies.json`. Не заявлены Baubles/зарядные интерфейсы,
эффекты каждого infusion enchant на удары/добычу и произвольный datapack reload.

Проверки28.10.2026: отдельный Java21 RED→GREEN для count, нулевых ключей и доплат;
`InfusionOutputParityTest`, `InfusionGenericRuntimeTest`,
`InfusionRitualLedgerParityTest` (170assertions) — PASS. Точечный javac адаптера и
`InfusionOutputGameTests` — PASS. Нативный запуск шести новых GameTest и общая сборка
выполняются последовательно root; здесь они не объявлены пройденными.
