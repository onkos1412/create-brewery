# Достижения и серверный конфиг

## Зачем

Пользователь одобрил оба пункта.
- **Достижения** дают цель и путь от первой варки к мастерству. Рецептов они не раскрывают.
- **Серверный конфиг** позволяет админу выключать механики опьянения и настраивать их силу.

## Достижения

Своя вкладка, корень — «Create Brewery». Файлы лежат в `data/brewery/advancement/`, тексты в переводе.

| id | Название (ru / en) | Условие | Родитель | Скрытое |
|---|---|---|---|---|
| `root` | Create Brewery / Create Brewery | получить котёл (`inventory_changed`, `brewery:kettle`) | — | нет |
| `first_brew` | Первая варка / First Brew | получить бутылку напитка (`inventory_changed`, `brewery:drink_bottle`) | root | нет |
| `muddy` | Кажется, что-то не то / Something's Off | выпить мутное варево | first_brew | нет |
| `distilled` | Огонь и вал / Fire and Shaft | получить дистиллят (`inventory_changed`, `brewery:distillate`) | first_brew | нет |
| `aged` | Терпение / Patience | выпить выдержанный напиток (стадия равна `aged` своего рецепта) | distilled | нет |
| `master_brewer` | Мастер-пивовар / Master Brewer | выпить напиток 5★ | first_brew | нет |
| `passed_out` | Перебор / Overdone | отключиться от перепоя | first_brew | **да** |
| `hangover` | Утро после / The Morning After | получить похмелье | passed_out | **да** |
| `taster` | Дегустатор / Taster | выпить каждый готовый напиток мода | master_brewer | нет, тип `challenge` |

**«Дегустатор»** состоит из 13 критериев, по одному на напиток: `beer`, `cider`, `kvass`, `wine`, `mead`, `stout`, `aged_whiskey`, `aged_calvados`, `aged_rum`, `aged_tequila`, `vodka`, `absinthe`, `firefly_liqueur`. Нужны все. Окно достижений названия критериев не показывает.

**Условия, которые не выразить ванильными триггерами,** — критерии `minecraft:impossible`. Код выдаёт их через общий помощник `BreweryAdvancements.award(ServerPlayer, String advancement, String criterion)` в трёх местах:
- `DrinkItem` при питье: `muddy`, `aged`, `master_brewer` и критерий `taster`;
- `DrunkManager` при отключке: `passed_out`;
- `DrunkManager` при наступлении похмелья: `hangover`.

## Серверный конфиг

Файл `brewery-server.toml` (NeoForge `ModConfigSpec`, тип SERVER).

| Ключ | По умолчанию | Диапазон | Что делает |
|---|---|---|---|
| `drunk.stagger` | true | — | шатающаяся походка |
| `drunk.vomit` | true | — | рвота |
| `drunk.hangover` | true | — | похмелье; выключенное не копит долг |
| `drunk.passOutAt` | 90 | 50–100 | порог отключки (100 — практически никогда) |
| `drunk.secondsPerSoberStep` | 1 | 1–60 | раз во сколько секунд опьянение падает на единицу |
| `drinks.effectMultiplier` | 1.0 | 0.0–4.0 | множитель длительности эффектов напитков |

Значения по умолчанию сохраняют нынешнее поведение. Чистые правила (`DrunkLevel`, `StaggerRules`, `VomitRules`, `HangoverRules`) не читают конфиг. Его читают `DrunkManager`, `DrunkEvents` и `DrinkItem`.

## Не цели

- Клиентский конфиг.
- Достижения за конкретные рецепты, кроме «Дегустатора», где названия скрыты.

## Подходы

- **Выбрано:** ванильные триггеры, где их хватает, и `impossible` с выдачей из кода для остального. Это проще всего.
- **Отвергнуто — свои типы триггеров:** понадобились бы только ради datapack-гибкости, которая здесь не нужна.
