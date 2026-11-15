# Выкладка 0.2.0 на Modrinth

Готовые файлы лежат в `release/0.2.0/` (папка не в git). Описание проекта для вставки — `docs/modrinth/description.md` (на английском, это основной язык Modrinth).

## Проект (один на все загрузчики)

| Поле | Значение |
|---|---|
| Название | Create Brewery |
| Краткое описание (summary) | Brewing, distilling and aging for Create, in the spirit of BreweryX: find the recipes, chase 5★, toast with friends. |
| Категории | Technology, Food |
| Клиент / сервер | Required / Required |
| Лицензия | MIT |
| Иконка | `src/main/resources/brewery_logo.png` |
| Исходный код | https://github.com/onkos1412/create-brewery |
| Баг-трекер | https://github.com/onkos1412/create-brewery/issues |

Для галереи подойдут скриншоты линии котёл → куб → бочка, окна машин, книги пивовара и бутылок в инвентаре.

## Версии

Для всех трёх: номер версии из имени файла, канал **Beta** (мод ещё 0.x), список изменений — ниже.

| Файл | Номер версии | Загрузчик | Minecraft | Обязательно | Необязательно |
|---|---|---|---|---|---|
| `create-brewery-0.2.0+mc1.21.1-neoforge.jar` | `0.2.0+mc1.21.1-neoforge` | NeoForge | 1.21.1 | Create (`create`) | JEI (`jei`) |
| `create-brewery-0.2.0+mc1.20.1-forge.jar` | `0.2.0+mc1.20.1-forge` | Forge | 1.20.1 | Create (`create`) | JEI (`jei`) |
| `create-brewery-0.2.0+mc1.20.1-fabric.jar` | `0.2.0+mc1.20.1-fabric` | Fabric | 1.20.1 | Create Fabric (`create-fabric`), Fabric API (`fabric-api`) | JEI (`jei`), Mod Menu (`modmenu`) |

Файл `...-fabric-sources.jar` загружать не нужно, это исходники.

Минимальные версии, которые проверяет сам мод: NeoForge 21.1.235 и Create 6.0.10; Forge 47.3.30 и Create 6.0.8; Fabric Loader 0.17.2 и Create Fabric 6.0.7.

## Список изменений (для каждой версии)

```markdown
- Brewer's signature: drinks show who brewed them and on which day.
- Brewer's book: a personal, owner-only diary of your best results for every drink.
- Keg and wooden mugs: a keg holds 8 portions of one drink and pours them into mugs, keeping quality and signature.
- Toasts: clink drinks with a friend (right-click) for longer effects and half the hangover.
- Treating: brewers earn advancements when other players drink their signed drinks.
- New art: every drink has a vessel of its own.
- Now also available for Minecraft 1.20.1 Forge and 1.20.1 Fabric.
- Note: drinks signed before and after this update don't stack together.
```

## Что проверено перед выкладкой

- Все три jar собраны из веток `main`, `mc1.20.1-forge`, `mc1.20.1-fabric`; юнит-тесты (137 / 137 / 140) и игровые тесты (139 / 144 / 150) зелёные.
- Каждая версия проверена в клиенте: окна машин, книга, Ponder.
- Fabric: проверен на настоящем выделенном сервере с Create Fabric 6.0.7 (сборка 1728) и 6.0.8.1 (сборка 1744) с Modrinth.
