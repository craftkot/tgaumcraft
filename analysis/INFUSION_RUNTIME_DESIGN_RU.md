# M06 — архитектура оплаченного runtime инфузии

27.10.2026. Дополнение к `main:ROADMAP_RU.md`, а не новая дорожная карта.
Пользователь явно активировал отдельную команду инфузии вместе с командой големов.
Ветка `feature/infusion`, существующий Draft PR #1; големы остаются в `feature/golems`, PR #3.

## Исполняемая цепочка

`BasicCasterItem` → `InfusionMatrixBlockEntity.beginVerifiedInfusionFromCaster` →
`InfusionRecipeAdapter` → существующий `LegacyInfusionStartPlanner` →
штатный серверный ticker матрицы → реальные банки/пьедесталы → единственный результат.
Прежний Crystal Farmer entry point сохраняется как делегат, без второй реализации ритуала.

`LegacyInfusionRecipeCatalog` хранит подтверждённые определения: research, центральный
предмет, упорядоченные боковые ингредиенты, аспекты, нестабильность и зарегистрированный output.
Семь Crystal Farmer встроены. Необязательный golem-модуль загружается из
`data/thaumcraft/golem/infusion_recipes.json` без Java-зависимости на классы големов.
Отсутствующий модуль допустим; некорректный присутствующий модуль отклоняется явно.
Это classpath-каталог, не обещание поддержки произвольных datapack/reload/API-рецептов.

## Оплата и сохранение

- Существующий ledger фиксирует исходную цену с BETA26-множителем и подтверждённый
  физический дебет каждой единицы эссенции; Core не списывает предметы мира.
- Generic ingredient progress обрабатывает от 1 до 30 боковых входов, последовательную
  битовую маску, исходный countdown и восстановление по реальным UUID receipts.
- `PortInfusionLedger.Format=2`: `SidePedestals:long[]`, точные component-bearing
  снимки центрального/боковых ItemStack, UUID, paid/remaining, multiplier, finish phase.
- `RecipeFingerprint` связывает сохранение с полным определением рецепта: research,
  предикаты и порядок входов, output/count, исходные аспекты и instability.
  Изменённый или отсутствующий fingerprint Format2 прекращает ритуал до новых расходов.
- Для старых formatless Crystal Farmer явно мигрируются `SeedsPedestal`/`SalisPedestal`
  и прежние bits1/2; произвольный модуль не может выдать себя за такой legacy checkpoint.
- Пьедестал сохраняет удаление вместе с receipt, публикацию вместе с output receipt.
  ACK требует UUID и полного физического результата, включая components.

Отдельные BlockEntity сохраняются Minecraft независимо. Это не транзакция между jar,
matrix и pedestal при аварийном завершении процесса; безубыточная crash-атомарность
не заявлена. Нормальная остановка/новая JVM проверяются отдельно от Core/NBT roundtrip.

## Границы и следующий функциональный этап

Работает ограниченный класс оригинальных рецептов: один фиксированный зарегистрированный
output и предметы без crafting remainder. Результаты с произвольными components,
контейнерные остатки, полный каталог BETA26, нестабильность/аварии/FX и вся Survival-цепочка
исследований ещё требуют переноса. Сохранение числа instability само по себе не является
реализацией событий нестабильности.

При подключении големов проверяются SealHarvest, SealButcher, SealBreak и MindBiothaumic
через настоящий caster и серверные тики. Их source descriptor принадлежит golem-ветке;
инфузия содержит отдельные интеграционные GameTest в namespace `thaumcraft_golem_infusion`.
Ветки не сливаются автоматически: root проверяет отдельную копию исходников с overlay.

Критерии/сценарии остаются в [M06.1 acceptance](lanes/infusion_M06_1_GAME_ACCEPTANCE_RU.md).
Свежие результаты, точные команды, SHA и оставшиеся пробелы записываются в
[журнале инфузии](lanes/infusion.md); глобальный индекс 59/100 не пересчитывается.
