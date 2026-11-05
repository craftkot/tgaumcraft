# M06 — исходные правила нестабильности BETA26

Источник: оригинальный `Thaumcraft-1.12.2-6.1.BETA26.jar`,
`thaumcraft.common.tiles.crafting.TileInfusionMatrix`; проверено 27.10.2026
через JDK21 `javap -c -p`. Полный вывод находится вне Git:
`/workspace/reference/javap/TileInfusionMatrix.instability.txt`.
Ссылки ниже — смещения байткода внутри названного метода.

## Порядок одного craftCycle

1. `craftCycle:0..81`: один `nextFloat`, существующий
   `LegacyInfusionStability.afterCraftCycle`: потеря по рейтингу, replenishment,
   ограничение `[-100, stabilityCap]`.
2. `81..172`: проверить настоящий центральный пьедестал и его предмет;
   wildcard metadata исходного рецепта `32767` учитывается до сравнения.
3. `172..217`: при неверном центральном предмете событие обязательно, без
   броска вероятности. Иначе нужно `stability < 0` и
   `nextInt(1500) <= abs(stability)`. Сравнение включительное: при `-0.01`
   выпавший ноль уже запускает событие.
4. `217..479`: `nextInt(24)` и исполнение выбранного события в мире.
5. `479..514`: **после** RNG/эффектов обработчика прибавить
   `5 + nextFloat()*5`, без повторного ограничения по cap; затем `inResAdd`.
   При верном центральном предмете немедленный `return`: в этом цикле
   нет дебета XP/эссенции/ингредиентов, countdown не продвигается.
6. `515..571`: неверный центральный предмет после события останавливает
   crafting, очищает остаток эссенции и recipeInstability, sync/dirty,
   звук `craftfail`, громкость `1`, тон `0.6`; рецепт не завершается.
7. При отсутствии события следуют XP (`572..977`), эссенция
   (`1015..1152`), боковые ингредиенты (`1152..1554`), затем finish.
   Неуспешный дебет эссенции отнимает `0.25` за каждый неуспешный аспект;
   уже существующий `LegacyInfusionEssentiaCycle` содержит это правило.

`beginCycle` возвращает состояние до шага 4; сервер исполняет событие,
затем вызывает `recoverAfterEvent`, затем выдаёт research и исполняет
`abortRecipe`/`continueRecipe`. Все методы используют **один серверный RNG**.
Нельзя заранее бросать recovery или игнорировать RNG цвета дуги при отсутствии
подключённых клиентов: это изменяет последующую последовательность событий.

## Все 24 исходных варианта

| Бросок | Исходный обработчик | Core Event |
|---|---|---|
| 0–3 | `inEvEjectItem(0)` | `EJECT_ITEM` |
| 4–6 | `inEvWarp()` | `WARP` |
| 7–9 | `inEvZap(false)` | `ZAP_ONE` |
| 10–11 | `inEvZap(true)` | `ZAP_ALL` |
| 12–13 | `inEvEjectItem(1)` | `EJECT_ITEM_GOO` |
| 14–15 | `inEvEjectItem(2)` | `EJECT_ITEM_FLUX` |
| 16 | `inEvEjectItem(3)` | `EJECT_ITEM_GOO_ALT` |
| 17 | `inEvEjectItem(4)` | `DESTROY_ITEM_FLUX` |
| 18–19 | `inEvHarm(false)` | `HARM_ONE` |
| 20–21 | `inEvEjectItem(5)` | `EJECT_ITEM_EXPLOSION` |
| 22 | `inEvHarm(true)` | `HARM_ALL` |
| 23 | взрыв матрицы | `MATRIX_EXPLOSION` |

Взрыв матрицы (`craftCycle:419..478`): центр блока `+(0.5,0.5,0.5)`,
сила `1.5 + nextFloat()`, источник `null`, разрушение ландшафта `false`.

## Цели, эффекты и аргументы адаптера

Все запросы сущностей используют AABB **единичного блока матрицы**,
расширенный на `10` по каждой оси (границы `x−10 .. x+11`, аналогично y/z).
Это куб, не радиус расстояния. `Zap/Harm(false)` берут первую сущность
в исходном порядке ответа мира; `true` — всех. Случайной перестановки нет.

- `inEvZap:0..257`: LivingEntity; для каждой цели сначала дуга с
  `r=0.3−nextFloat()*0.1, g=0, b=0.3−nextFloat()*0.1`, затем
  magic damage `4+nextInt(4)`. PacketFXBlockArc распространяется на `32`.
- `inEvHarm:124..195`: один `nextBoolean` на цель. `true` — Flux Taint,
  `120` ticks, amplifier `0`, ambient `false`, visible `true`.
  `false` — Vis Exhaustion, `2400` ticks, amplifier `0`, ambient `true`,
  visible `true`, список лечебных предметов очищается.
- `inEvWarp:78..175`: выбрать одного игрока через `nextInt(playerCount)`;
  затем `nextFloat()<0.25` даёт `NORMAL 1`, иначе `TEMPORARY 2+nextInt(4)`.
  При отсутствии игроков нет дополнительных бросков.
- `inResAdd:0..198`: каждому игроку того же куба, ещё не знающему
  `!INSTABILITY`, добавить research, sync knowledge, показать actionbar
  `got.instability` тёмно-фиолетового цвета. Дополнительного RNG нет.

## Выброс с пьедестала

`inEvEjectItem:0..96`: до **25** выборок `nextInt(pedestalCount)`
с возвращением, пропуская отсутствующие/неподходящие/пустые пьедесталы.
Список содержит **все** боковые пьедесталы сканирования, включая пустые
и не участвующие в рецепте (`getSurroundings:45..170`): x/z `−8..8`,
relative y `+3..−7`, вся центральная колонна исключается;
порядок x, z, убывающий y. `LegacyInfusionMatrixLayout.scanPedestals`
уже задаёт этот порядок. Поиск первой непустой цели завершает событие;
отсутствие цели после 25 попыток всё равно даёт восстановление/research.

`inEvEjectItem:96..373`: если найден настоящий TileStabilizer,
`mitigate(5+nextInt(6))` — диапазон **5..10 включительно**.
Успешная защита сохраняет предмет, отправляет block event `(5,0)`,
две дуги matrix→pedestal.above и pedestal.above→stabilizer,
по два float-броска цвета на дугу, затем `return`.

Если защиты нет или она не сработала (`374..559`):

| Mode | Предмет | Дополнительное действие |
|---|---|---|
| 0 | выбросить весь slot через InventoryUtils.dropItems | нет |
| 1 | выбросить | безусловно поставить fluxGoo над пьедесталом |
| 2 | выбросить | polluteAura у пьедестала, `5+nextInt(5)`, effect=`true` |
| 3 | выбросить | то же fluxGoo, что mode 1 |
| 4 | **удалить slot**, без выброса | pollution как mode 2 |
| 5 | выбросить | взрыв центра пьедестала, сила `1`, source=null, terrain=false |

После удаления/выброса dirty/sync пьедестала. Goo сопровождается исходным
`SoundEvents.field_187615_H`, volume `0.3`, pitch `1`. Затем для каждого
незащищённого режима block event `(11,0)` и одна дуга
matrix→pedestal.above с двумя float-бросками (`560..677`).
Core не заменяет эти действия статистикой или записью имени события.

## Проверки и граница доказательства

`InfusionInstabilityParityTest`: все 24 варианта, включительные границы
вероятности, forced event при неверном центре, последовательность RNG
loss→chance→event→arc→damage→recovery, отсутствие cap после recovery,
25 попыток с возвращением, все шесть ejection modes, диапазоны warp,
harm, pollution, mitigation и explosion. RED: отсутствующий Core класс;
GREEN: отдельный JDK21 `javac/java`, **198 assertions**, builddir
`/tmp/infusion-instability-parity-green`; Gradle и общий runner не менялись.

## Нативный адаптер, 28.10.2026

`InfusionInstabilityEffects.perform` исполняет все выбранные Core-варианты
на сервере: реальные пьедесталы/предметы, поиск настоящего mitigator,
аура `ThaumcraftAuraSavedData.polluteFlux`, warp attachment и sync,
LivingEntity magic damage, настоящие MobEffectInstance, взрывы без
разрушения блоков, research. При выбросе сначала извлекается физический
предмет с очисткой receipt; снимок рецепта не превращается в новый предмет.
`Containers.dropItemStack` — аналог исходного InventoryHelper со своим RNG.

У `perform` пять аргументов: `ServerLevel`, позиция матрицы, Core Event,
все отсканированные боковые пьедесталы, общий серверный RandomSource.
`bridge(level)` использует обычный `level.getRandom()`. Root вызывает
`perform` перед `recoverAfterEvent`. Research помещён внутрь `perform`
по контракту интеграции; он не потребляет RNG, затем матрица сразу
выполняет восстановление. Видимые серверные частицы дуги воспроизводят
исходные два броска цвета без дополнительного RNG мира; старый shader
PacketFXBlockArc пока не перенесён.

Свежая проверка `mcp_stable-39-1.12.zip/fields.csv` уточнила имена звуков:
`field_187615_H` = **ITEM_BOTTLE_FILL** у Goo (`0.3,1`),
`field_187659_cY` = **BLOCK_LAVA_EXTINGUISH** у XP (`1,2+float*0.4`).

`InfusionHazardContent.register(modBus)` регистрирует два отдельных
эффекта и настоящий блок Goo. `PotionFluxTaint.performEffect:0..97`
наносит не-undead существам по единице taint damage каждые `40>>amp`
ticks (`isReady:0..24`). DamageType `thaumcraft:taint` имеет
bypasses_armor, magic resistance tag, exhaustion `0`: исходный
setDamageBypassesArmor обнулял food exhaustion.

У `PotionVisExhaust.performEffect` исходно **пустое тело**. Реальная цена
находится в `CasterManager.getTotalVisDiscount:165..252`:
вычесть `(max(visExhaustAmp, infectiousAmp)+1)*10` из процентов скидки,
затем разделить на `100`. `visExhaustDiscountPenalty` сохраняет правило
для обычного эффекта; **производственного потребителя focus casting
пока нет**. Существующий BasicCasterItem активирует матрицу без Vis-цены
так же, как исходный matrix caster handler. Не начисляем ему выдуманную
цену только ради демонстрации debuff.

Goo поддерживает source LEVEL `0..7`, default `7`, контактное торможение
XZ множителем `1-(LEVEL+1)/8`, реальный некюрируемый VisExhaust `600`
ticks с amplifier `LEVEL/3`, уменьшение уровня/flux в ограниченной
ветви испарения. Полный Forge1.12 BlockFluidFinite flow, ThaumicSlime
spawn/feeding, taintFibre conversion и tainted/champion13 healing
FluxTaint — отсутствующая M11-интеграция. Эти ветви не подменены
ванильными slime/блоками и не заявляются завершёнными. Визуальная модель
Goo временная, ресурс purple_terracotta; старый жидкостный renderer не перенесён.

`InfusionInstabilityGameTests` содержит семь настоящих native сценариев:
физический выброс/повтор, удаление+аура, Goo collision, ticking Taint damage,
uncurable Exhaust state/цена helper, zap+террейн взрыва, player warp/research.
В первой общей прогонке шесть hazard-тестов прошли; player fixture упал
**до вызова адаптера** на встроенном vanilla mock login без NeoForge channel
negotiation. Исправлено на поддержанный `FakePlayerFactory` с native
no-client connection и прямым `ServerLevel.addNewPlayer`, без подавления
ошибок отправки в production. Повторный общий запуск выполняет root.

## Обновление окружения во время ритуала

`update:0..26`: увеличить count; при checkSurroundings=true сбросить флаг
и вызвать getSurroundings **на следующем tick** до client/server ветвления.
Проверка validLocation — каждые `20` crafting или `100` idle ticks
(`43..87`), это **не** повторный getSurroundings.
Конструктор ставит checkSurroundings=true; craftingStart всегда вызывает
getSurroundings. Исчерпание всех попыток essentia drain ставит флаг
(`craftCycle:1146..1151`). Клиентский caster active&&!crafting тоже ставит
его (`onCasterRightClick:0..26`).

Проверка constant pool всех классов оригинального JAR нашла строку/поле
checkSurroundings только в TileInfusionMatrix; BlockInfusionMatrix,
BlockPedestal и BlockPillar его не записывают. Следовательно установка
или снятие стабилизатора посреди оплаченного ритуала сама по себе не
вызывает исходный пересчёт; он ждёт перечисленного флага/start/load.
