# M10: проверки 18.10.2026

Ветка `feature/golems`, база `a4280834adc5a20b6951098af937d30ca4aeca56`.
Окончательные SHA и результаты — в [журнале](lanes/golems.md).

## Среда и происхождение

- JDK Eclipse Temurin 21.0.12.1+1, Gradle 9.2.1, NeoForge 21.1.250, Minecraft 1.21.1.
- Официальный BETA26 JAR, SHA256 `9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f`; совпадает с эталоном проекта.
- Исследование: API автора, API ZIP из его официальной темы, `javap -c -p` официального JAR. Исходные бинарники и извлечённые оригинальные файлы в Git не добавлялись.
- Пользовательские ветки и `main` не менялись; общие изменения перечислены в журнале.

## Воспроизведение

Нужны Java **21** с `javac`, Gradle **9.2.1**, доступ к Maven/Minecraft зависимостям и штатная настройка доверия CA/прокси среды. В облачной сессии инструменты установлены вне репозитория, общая конфигурация Gradle и CI не менялась.

```bash
bash tools/run_core_parity.sh
gradle --init-script tools/golems.gradle build -x test
gradle --init-script tools/golems.gradle runGolemGameTest
```

`tools/golems.gradle` — необязательный init script: добавляет две M10-конфигурации к существующему ModDev. Не использовать имя `test` как доказательство выполнения standalone Core-тестов: их запускает именно `tools/run_core_parity.sh`.

Для холодного перезапуска используется **отдельный тестовый мир** `build/golem-restart`. Перед первым запуском каталог мира должен отсутствовать. Создать `build/golem-restart/eula.txt` с `eula=true`, а `server.properties` настроить для локального теста: `server-ip=127.0.0.1`, свободный `server-port`, `online-mode=false`, `spawn-protection=0`. Миру GameTest соответствует `build/golem-gametest`; при использовании его настроек также отключить spawn protection.

```bash
gradle --init-script tools/golems.gradle -PgolemRestartPhase=write runGolemRestart
gradle --init-script tools/golems.gradle -PgolemRestartPhase=read runGolemRestart
```

Probe включён только явным system property из этого run. `write` создаёт настоящую сущность и печать, кладёт именованный груз, проверяет входные условия и штатно останавливает сервер. `read` запускает **новый JVM-процесс с тем же миром**, ждёт загрузки chunk/entities и проверяет UUID/конструкцию/владельца/дом/XP/цвет/здоровье/точные item components, одну сущность и отсутствие копии груза на земле. Задачи и reservations намеренно не переживают рестарт.

Положительные строки: `GOLEM_RESTART_WRITE_ASSERTIONS_OK` и **отдельно** `GOLEM_RESTART_READ_ASSERTIONS_OK`. Один лишь exit code Gradle не заменяет обе проверки; при `GOLEM_RESTART_ASSERTIONS_FAILED` результат отрицательный.

## Что проверяют игровые тесты

В `GolemGameTests` находятся восемь серверных сценариев с настоящими `ServerLevel`, навигацией, `ItemEntity`, `ItemStack`, FakePlayer-взаимодействиями и NBT:

1. Survival placer: один spawn и один расход, исходные 16 HP/2 armor/.3 movement attribute, владелец и дом; отказ при препятствии и некорректных props без расхода.
2. Возврат домой с реальным изменением позиции и препятствием на пути.
3. Переносимые stacks: частичное слияние, отдельные components, max stack size и независимость ссылок/remainder.
4. NBT round trip дважды: идентичность, свойства, flags/цвет/XP/здоровье и обе руки; ephemeral working не восстанавливается. Повреждённые props сохраняются в карантине без потери груза.
5. Два работника, одна печать, один реальный stack из 17 diamonds: движение, единственный сборщик, сумма ground+hands неизменна.
6. Резервирование и удаление цели/печати: устаревшее назначение не создаёт предметы.
7. Реальная смерть с грузом: точные количество, имя и повреждение инструмента; один путь сброса, повторный урон не повторяет перевод.
8. Владелец забирает голема в placer: один placer с props/XP, один груз, повторное взаимодействие с удалённой сущностью не создаёт копию.

## Результаты

Таблица отражает фактически завершённые запуски, а не наличие тестовых классов.

| Проверка | Факт |
|---|---|
| Существующий Core runner до добавления construction | PASS |
| Task/SealArea standalone, 922 assertions | PASS |
| `compileJava processResources` с новыми runtime-классами | PASS, 38 s |
| Полный Core runner с обоими новыми тестами | **PASS**, construction 34 808 и tasks 922 assertions, все прежние тесты сохранены |
| Восемь server GameTests | **PASS**, все 8 required, 12:08 UTC, игровой набор 2.372 s; полный запуск 1 min 40 s |
| JAR packaging | **PASS**, финальный `build -x test` после costs: 11 s; ZIP CRC проверен |
| Два запуска dedicated server, write/read | **PASS**, оба сервера достигли Done; WRITE и READ assertions OK, штатные остановки и сохранение |
| Клиент/картинки/анимации | Не запускались: графическая среда не подготовлена |
| Отдельный живой unload/reload chunk в активном мире | Не запускался |
| Аварийное отключение в середине cross-entity transfer | Не проверялось |

## Практические границы

Runtime placer принимает пока **только стандартную деревянную конструкцию**, хотя Core хранит весь исходный каталог. Остальные части не рекламируются как работающие големы с реализованным полётом/боем. Нет survival-изготовления в Golem Press, Fill/Empty/Provide/Stock и полного маршрута в контейнер; фильтры/настройки/GUI печати и bell пока отсутствуют. Pickup использует исходный empty-blacklist default, область 5×1×5 над блоком для UP. Предметы доступны в Creative Tab и командами для разработки; бесплатный survival-рецепт не добавлен.

Модель, текстура и item icons временные, самописные/ванильные. Клиентская регистрация скомпилирована, но визуальный результат не проверен. Нормальный рестарт не доказывает атомарность нескольких entity saves при обрыве питания. Полный M10 и рейтинг 59/100 не меняются по этим узким проверкам.

Стоимость подтверждённой базовой конструкции реализована в `GolemAssembly`: 3 Greatwood planks + 2 Simple Arcane Mechanism + 1 Clockwork Mind (`mind@0`). Это расчёт ингредиентов в Core, не выдача результата/рецепт и не готовый пресс. Остальные стоимости и начисление rank XP оставлены явными пробелами.

Финальный JAR: `build/libs/thaumcraft-6.1.BETA26-port.stage9.jar`, SHA256 `ba5234d8b666aedb9626b2b2b98576345a53d0851872e1332a9de1df8caf80ff`. Java/runtime дерево для этих результатов соответствует `2d6e66d` плюс последующие изменения документации. Добавление Core стоимости базовой сборки не меняло уже прошедшие восемь игровых сценариев; после него повторены полный Core и incremental full build.
