# M06: окружение и оплаченная стабилизация

Источники: `Thaumcraft-1.12.2-6.1.BETA26.jar`, `TileInfusionMatrix.getSurroundings`,
`calcDeminishingReturns`, `TilePedestal.findInstabilityMitigator`,
`BlockInlay.calculateChanges`, `TileStabilizer.update/mitigate`, `ConfigRecipes`.
Полные `javap` остаются в `/workspace/reference/javap`, вне Git.

`InfusionStabilizerAdapter.scan` читает реальные загруженные блоки. Область:
x/z −8…8, y +3…−7; центральная колонна исключена. Порядок чтения x/z/y
совпадает с оригиналом. Пьедесталы и их поправки стоимости сохраняют этот
порядок. Для пар используется исходный `HashSet<Long>` с упаковкой координат
1.12 x26/y12/z26, а не новая `BlockPos.asLong` или сортировка смещений.
Отражение меняет x/z и сохраняет y. Одинокий блок сравнивается с базовой
силой 0.1. Идентичные блоки с одинаковой силой получают diminishing
`strength * 0.75^previousPairsOfSameBlock`; penalty callback может проверить
произвольное состояние реального мира. Публичный `InfusionStabilizer`
позволяет настоящим зарегистрированным блокам расширять эти правила.

Поддерживаются существующие свечи 0.1, исходные шесть типов черепов/голов
(включая настенные варианты), все три пьедестала, реальные новые inlay 0.025
и stabilizer 0.25. Обычные рудные кристаллы не входят в исходный API и
стабилизацию не дают. Четыре foundation-слота glyph находятся на y−3,
в исходном порядке (−1,−1), (1,−1), (1,1), (−1,1).

Новый stabilizer хранит `energy` 0…15 в NBT и получает 1 единицу за каждые
20 серверных тиков. Каждая единица загрязняет существующий aura chunk на
0.25 flux. Энергия идёт по настоящему горизонтальному inlay/pedestal CHARGE,
с потерей 1 на следующий проводящий блок. Поиск источника inlay использует
N/E/S/W; поиск mitigator из пьедестала — S/W/N/E и строго возрастающий CHARGE.
Найденный источник имеет минимум 5, но случайная исходная цена 5…10 ещё
может оказаться недоступна. Только успешный `mitigate` списывает сохранённую
энергию. Пассивные 0.25 не зависят от запаса энергии.

`InfusionStabilityArcaneAdapter` подключён к настоящему меню верстака:

| Результат | Research | Vis | Примальные кристаллы | Предметы |
|---|---|---:|---|---|
| matrix_speed ×1 | INFUSIONBOOST | 500 | aer, ordo | SNS/NGN/SNS: arcane stone, nitor, diamond block |
| matrix_cost ×1 | INFUSIONBOOST | 500 | aer, aqua, perditio | SAS/AGA/SAS: arcane stone, alumentum, diamond block |
| inlay ×2 | INFUSIONSTABLE | 25 | aqua | shapeless gold ingot + redstone dust |
| stabilizer ×1 | INFUSIONSTABLE | 250 | aqua, terra, perditio | SRS/BVB/IMI: arcane slab/redstone block/arcane stone/vis resonator/inlay/complex mechanism |
| mirrored_glass ×1 | BASEARTIFICE | 50 | aqua, ordo | shapeless quicksilver + glass pane |

Отсутствующие `slab_arcane_stone`/`mechanism_complex` не заменяются другими
предметами. До их настоящей регистрации survival-рецепт stabilizer недоступен.
Нормальное и shift-click извлечение используют одну оплату; разбиение
двойного выхода inlay возвращает все оплаченные предметы.

Новые модели используют vanilla-текстуры и собственную простую геометрию;
оригинальное artwork не копируется. Динамическая исходная визуализация
энергии/inlay и стабилизация flux rift относятся к отдельным интеграциям.
Нативные fixtures явно дают prerequisite research и aura; это не доказательство
натурального прохождения исследований.

Изолированные Core-проверки: `InfusionSurroundingsParityTest` (20),
`InfusionMitigationParityTest` (4111), `InfusionStabilityRecipeParityTest` (1229).
Нативные fixtures: `InfusionStabilityGameTests`, `InfusionMitigationGameTests`,
`InfusionStabilityArcaneGameTests`; факт выполнения фиксируется root отдельно.
