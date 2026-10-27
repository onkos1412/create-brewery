# Create Brewery — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать скелет мода-аддона Create (NeoForge 1.21.1): своя кипятильня варит сусло из воды+ингредиентов под нагревом, «носик» разливает напиток в бутылку, а выпитый напиток даёт эффект и накапливает уровень опьянения с искажением экрана.

**Architecture:** Свои блоки с BlockEntity (кипятильня, носик), собственный тип рецепта (варка) на datapack JSON, напиток-предмет (бутылка) с метаданными качества, серверный счётчик опьянения у игрока + клиентское искажение экрана. Скелет проверяет весь цикл «нагрев → сусло → розлив → бутылка → опьянение»; перегонка, выдержка, шкала звёзд, речь и отключка — вторым заходом.

**Tech Stack:** Java 21, NeoForge 1.21.1 (NeoGradle/ModDevGradle MDK), Gradle 8, Create 6.0.10-280 (slim) + Ponder + Flywheel + Registrate.

**Spec:** `docs/superpowers/specs/2026-10-27-create-brewery-design.md`

## Global Constraints

- Minecraft 1.21.1, NeoForge 21.1.x, Java 21, Gradle 8.x.
- Зависимость от Create: `com.simibubi.create:create-1.21.1:6.0.10-280:slim` (transitive = false), `net.createmod.ponder:ponder-neoforge:1.0.82+mc1.21.1`, `dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6` (api compileOnly, runtimeOnly полный), `com.tterrag.registrate:Registrate:MC1.21-1.3.0+67`. Репозитории: `https://maven.createmod.net`, `https://maven.ithundxr.dev/snapshots`.
- `neoforge.mods.toml` объявляет обязательную зависимость от `create` с `versionRange="[6.0.10,6.1.0)"`.
- Mod id: `brewery` (плейсхолдер; при необходимости переименовать на этапе скелета через `gradle.properties` и имя пакета).
- Исходники в подпапке `create-brewery/` в корне текущего репозитория.
- Рецепты расширяются datapack JSON, без правки кода.
- Имена пакета/классов едины на весь проект: базовый пакет `com.kostya.createbrewery` (плейсхолдер).

## Review Focus

Пять входов/условий, которые спецификация подразумевает, но ни одна задача «счастливого пути» не покрывает тестом напрямую, — самые вероятные способы сломать мод для игрока:

1. **Кипятильня без источника тепла под ней** — должна стоять, но не варить (не давать сусло), а не падать или варить «бесплатно».
2. **Ингредиент не соответствует рецепту** — машина не должна сожрать ингредиент и ничего не выдать; состояние должно остаться корректным.
3. **Розлив при пустой/недоготовленной кипятильне** — не должен создавать бутылку с напитком.
4. **Игрок с нулевым уровнем опьянения** — не должен получать эффекты искажения/дебаффов.
5. **Уровень опьянения, превышающий максимум** — должен ограничиваться потолком, а не уходить в отрицательное переполнение после вытрезвления.

Каждая из этих строк закреплена тестом в задаче, владеющей соответствующим кодом (см. шаги ниже).

---

### Task 1: Каркас проекта и зависимость от Create

**Files:**
- Create: `create-brewery/` — полный MDK (NeoForge 1.21.1): `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `src/main/resources/META-INF/neoforge.mods.toml`, `src/main/resources/pack.mcmeta`, базовый класс мода, `.gitignore`.
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/CreateBrewery.java` — точка входа мода.

**Interfaces:**
- Produces: мод `brewery` загружается и поднимает `runClient`; константы `CreateBrewery.MOD_ID = "brewery"`, логгер `CreateBrewery.LOGGER`.

- [ ] **Step 1: Скопировать MDK NeoForge 1.21.1 в `create-brewery/`**

Скачать/клонировать шаблон `NeoForgeMDKs/MDK-1.21.1-NeoGradle` (или ModDevGradle) и расположить содержимое в `create-brewery/`, не трогая `watchmy-designer-shell-v2`.

- [ ] **Step 2: Настроить `gradle.properties`**

```properties
minecraft_version=1.21.1
neoforge_version=21.1.x
mod_id=brewery
mod_name=Create Brewery
mod_version=0.1.0
```

- [ ] **Step 3: Добавить зависимость от Create в `build.gradle`**

Репозитории и зависимости из Global Constraints (Create slim + Ponder + Flywheel + Registrate), `transitive = false` у create.

- [ ] **Step 4: Объявить зависимость в `neoforge.mods.toml`**

`[[dependencies.brewery]] modId="create" type="required" versionRange="[6.0.10,6.1.0)" ordering="NONE" side="BOTH"`.

- [ ] **Step 5: Задать базовый класс `CreateBrewery.java`**

Класс с `@Mod("brewery")`, публичными `MOD_ID` и `LOGGER`.

- [ ] **Step 6: Проверить сборку**

Run: `gradlew build` (в `create-brewery/`).
Expected: BUILD SUCCESSFUL; в `build/libs` лежит `brewery-0.1.0.jar`.

- [ ] **Step 7: Проверить запуск клиента**

Run: `gradlew runClient`.
Expected: клиент стартует, в меню модов виден «Create Brewery», Create загружен.

- [ ] **Step 8: Commit**

```bash
git add create-brewery
git commit -m "feat: scaffold NeoForge 1.21.1 project with Create dependency"
```

---

### Task 2: Жидкость «сусло» и напиток-бутылка

**Files:**
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/registry/ModFluids.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/registry/ModItems.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/item/DrinkItem.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/DrinkData.java` — компонент качества напитка (количество звёзд).
- Test: `create-brewery/src/test/java/com/kostya/createbrewery/DrinkDataTest.java`
- Modify: `create-brewery/src/main/java/com/kostya/createbrewery/CreateBrewery.java` — вызвать регистрацию.

**Interfaces:**
- Produces: `ModFluids.WORT` (Fluid + FlowingFluid + FluidType, ресурс `brewery:wort`); `ModItems.DRINK_BOTTLE` (ItemStack бутылки); `DrinkData` — компонент `DataComponentType<DrinkData>` с полем `int stars` (1..5, в скелете фиксировано 5, реальная шкала — позже); `DrinkItem` — предмет, который при использовании вызывает `DrunkManager.addDrunk(player, amount)` (заглушка-вызов, сам менеджер в Task 5).

- [ ] **Step 1: Написать падающий тест на `DrinkData`**

```java
@Test
void starsClampToRange() {
    assertEquals(1, new DrinkData(0).stars());
    assertEquals(5, new DrinkData(6).stars());
    assertEquals(3, new DrinkData(3).stars());
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `gradlew test`
Expected: FAIL — класс `DrinkData` не существует.

- [ ] **Step 3: Реализовать `DrinkData` (record с `int stars`)**

Record, конструктор зажимает `stars` в `[1,5]`. Зарегистрировать `DataComponentType<DrinkData>` через `DeferredRegister`.

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `gradlew test`
Expected: PASS.

- [ ] **Step 5: Регистрация жидкости и предмета**

`ModFluids` — источник+текущая+тип для `wort`; `ModItems` — `DRINK_BOTTLE` (в скелете статичный ItemStack с `DrinkData(5)`); `DrinkItem` переопределяет `use()` и зовёт `DrunkManager.addDrunk(...)` (метод появится в Task 5 — оставить TODO-вызов, который станет корректным после Task 5).

- [ ] **Step 6: Проверить компиляцию**

Run: `gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add create-brewery/src
git commit -m "feat: add wort fluid, drink bottle item, drink data component"
```

---

### Task 3: Рецепт варки (свой RecipeType + Serializer + datapack JSON)

**Files:**
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/recipe/BrewingRecipe.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/recipe/BrewingRecipeSerializer.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/recipe/ModRecipeTypes.java`
- Create: `create-brewery/src/main/resources/data/brewery/recipe/beer.json` — пример рецепта.
- Modify: `create-brewery/src/main/java/com/kostya/createbrewery/CreateBrewery.java` — регистрация типов рецепта.
- Test: `create-brewery/src/test/java/com/kostya/createbrewery/recipe/BrewingRecipeTest.java`

**Interfaces:**
- Consumes: `ModFluids.WORT`, `DrinkData`.
- Produces: `ModRecipeTypes.BREWING` (RecipeType<BrewingRecipe>), `BrewingRecipeSerializer.INSTANCE`. `BrewingRecipe` хранит: `Ingredient[] inputs` (ингредиенты, кладущиеся в кипятильню), `FluidStack waterInput`, `FluidStack wortOutput`, `int brewTimeTicks` (идеальное время варки), `int idealHeatLevel` (0..2: none/heated/superheated). Метод `matches(items, water, heat)` и `getWort()`.

- [ ] **Step 1: Написать падающий тест на десериализацию рецепта**

```java
@Test
void deserializesBeerRecipe() {
    BrewingRecipe r = BrewingRecipeSerializer.INSTANCE.fromJson(ResourceLocation.parse("brewery:beer"), beerJson(), ...);
    assertEquals(2, r.brewTimeTicks());      // значение из beer.json
    assertEquals(1, r.idealHeatLevel());     // "heated"
    assertNotNull(r.getWort());
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `gradlew test`
Expected: FAIL — `BrewingRecipe` не существует.

- [ ] **Step 3: Реализовать `BrewingRecipe` + `BrewingRecipeSerializer` + `ModRecipeTypes`**

Тип рецепта через `DeferredRegister<RecipeType<?>>` (`RecipeType.simple(...)`); сериализатор читает `inputs` (массив JSON-ингредиентов), `water` (fluid stack), `result` (fluid stack), `brewTimeTicks`, `heatLevel` (string→int: none=0/heated=1/superheated=2). Зарегистрировать `RecipeSerializer`.

- [ ] **Step 4: Создать пример `beer.json`**

```json
{
  "type": "brewery:brewing",
  "inputs": [{ "item": "minecraft:wheat" }, { "item": "minecraft:wheat" }],
  "water": { "fluid": "minecraft:water", "amount": 1000 },
  "result": { "fluid": "brewery:wort", "amount": 1000 },
  "brewTimeTicks": 200,
  "heatLevel": "heated"
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add create-brewery/src
git commit -m "feat: add brewing recipe type, serializer, and example beer recipe"
```

---

### Task 4: Кипятильня (блок + BE: нагрев, время, вход/выход)

**Files:**
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/KettleBlock.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/KettleBlockEntity.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/ModBlocks.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/KettleScreen.java` + `KettleMenu.java` (простое меню: слот входа и слот выхода бутылки)
- Modify: `create-brewery/src/main/java/com/kostya/createbrewery/CreateBrewery.java` — регистрация блоков/BE/menu.
- Test: `create-brewery/src/test/java/com/kostya/createbrewery/block/KettleLogicTest.java`

**Interfaces:**
- Consumes: `ModRecipeTypes.BREWING`, `BrewingRecipe`, `ModFluids.WORT`, `DrinkData`, `ModItems.DRINK_BOTTLE`.
- Produces: `KettleBlockEntity` со свойствами `int heatLevel` (читается из блока под ним через Create `IHeatLevel`/adjacent heat; в скелете — заглушка, читающая blaze burner/campfire), `int brewProgressTicks`, `FluidStack water`, `FluidStack wort`, `ItemStackHandler input`. Публичный метод `boolean tryBrew()` (выдаёт сусло, если нагрев и ингредиенты совпали с рецептом и прошло `brewTimeTicks`) и `ItemStack bottleWort()` (расходует сусло → бутылка с `DrinkData(5)`).

- [ ] **Step 1: Написать падающие тесты на логику варки**

```java
@Test void noHeatDoesNotBrew()          { /* heat=0 → tryBrew()==false, wort пуст */ }
@Test void wrongIngredientDoesNotBrew() { /* heat=1, вход не wheat → tryBrew()==false, вход не съеден */ }
@Test void idealHeatBrewsWort()         { /* heat=1, вход 2 wheat, прогресс>=brewTimeTicks → tryBrew()==true, wort=1000 */ }
```

Логику вынести в чистый класс `KettleLogic` (без MC-состояний), чтобы тестировать без запущенного сервера.

- [ ] **Step 2: Запустить тесты — убедиться, что падают**

Run: `gradlew test`
Expected: FAIL — `KettleLogic` не существует.

- [ ] **Step 3: Реализовать `KettleLogic`**

Чистый класс: вход `(heatLevel, brewTimeTicks идеал, inputIngredients, water, progressTicks)` → `(bool brewed, FluidStack wort, consumed)`. Правила: нагрев должен быть `>= idealHeatLevel`; ингредиенты совпадают; прогресс накапливается только при правильном нагреве; перегрев выше идеала не портит (порча/перегонка — позже).

- [ ] **Step 4: Реализовать `KettleBlock` + `KettleBlockEntity` + `ModBlocks`**

Блок с `BlockEntity`; `tick()` читает heat из блока снизу, вызывает `KettleLogic`, кладёт `wort` в отдельное поле/флюид-слот; `use()` открывает меню; `bottleWort()` создаёт `DRINK_BOTTLE` с `DrinkData(5)`. GUI: простой `AbstractContainerMenu` с двумя слотами.

- [ ] **Step 5: Зарегистрировать блок/BE/menu/блок-предмет, дать текстуры-заглушки и JSON-модели**

Минимальные `blockstate`, `model`, `item model`, `texture` (16×16 заглушка) и перевод в `lang/en_us.json` + `ru_ru.json`.

- [ ] **Step 6: Запустить тесты — убедиться, что проходят**

Run: `gradlew test`
Expected: PASS.

- [ ] **Step 7: Проверить сборку и запуск**

Run: `gradlew build` затем `gradlew runClient`
Expected: кипятильня доступна в креативе, ставится, реагирует на нагрев.

- [ ] **Step 8: Commit**

```bash
git add create-brewery/src
git commit -m "feat: add kettle block with heat-based brewing logic"
```

---

### Task 5: Система опьянения (серверный счётчик + искажение экрана)

**Files:**
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/drunk/DrunkManager.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/drunk/DrunkCapability.java` (или attachment-данные игрока 1.21.1 через `DataAttachments`/capability)
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/drunk/DrunkScreenEffect.java` (клиент: искажение экрана, хуки на render)
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/drunk/DrunkEvents.java` (тик вытрезвления, сброс при смерти)
- Modify: `create-brewery/src/main/java/com/kostya/createbrewery/item/DrinkItem.java` — реальный вызов вместо TODO.
- Test: `create-brewery/src/test/java/com/kostya/createbrewery/drunk/DrunkManagerTest.java`

**Interfaces:**
- Consumes: `DrinkItem`, `DrunkManager.addDrunk(player, amount)`.
- Produces: `DrunkManager.addDrunk(ServerPlayer, int amount)` (увеличивает уровень, зажимает потолком `MAX_DRUNK=100`), `DrunkManager.soberTick(player)` (уменьшает уровень со временем), `DrunkManager.getDrunk(player): int`. Уровень хранится как attachment/capability на игроке. Клиент читает уровень и при `>0` включает искажение (сила пропорциональна уровню).

- [ ] **Step 1: Написать падающие тесты на счётчик**

```java
@Test void zeroDrunkHasNoEffect()       { /* getDrunk(new) == 0; isDrunk() == false */ }
@Test void addDrunkIncrements()         { /* addDrunk(p, 30) → getDrunk==30 */ }
@Test void addDrunkClampsToMax()        { /* addDrunk(p, 200) → getDrunk==100 */ }
@Test void soberTickDecrements()        { /* addDrunk(p,50); soberTick → getDrunk<50 */ }
@Test void soberTickStopsAtZero()       { /* уровень 1, soberTick → 0, не уходит в минус */ }
```

Счётчик вынести в чистый класс `DrunkLevel` (без MC-состояний), тестируемый напрямую.

- [ ] **Step 2: Запустить тесты — убедиться, что падают**

Run: `gradlew test`
Expected: FAIL — `DrunkLevel` не существует.

- [ ] **Step 3: Реализовать `DrunkLevel`**

Класс с `int level`, `add(int)`, `tick()` (декремент со скоростью), `isDrunk()`, зажим в `[0, MAX]`.

- [ ] **Step 4: Подключить `DrunkLevel` к игроку через attachment/capability**

Использовать механизм NeoForge 1.21.1 (attachment или `Capability`), `DrunkManager` оборачивает его. `DrunkEvents` тикает вытрезвление раз в N тиков и сбрасывает при смерти.

- [ ] **Step 5: Клиентское искажение экрана**

`DrunkScreenEffect` читает уровень на клиенте и при `>0` применяет визуальное искажение (например, через `RenderGuiOverlayEvent`/пост-эффект или сдвиг камеры), сила пропорциональна уровню. Подключить через `@EventBusSubscriber` client-side.

- [ ] **Step 6: Связать `DrinkItem` с `DrunkManager`**

Заменить TODO-вызов: при `use()` вызывать `DrunkManager.addDrunk(player, amount)` (amount из компонента напитка, в скелете константа).

- [ ] **Step 7: Запустить тесты — убедиться, что проходят**

Run: `gradlew test`
Expected: PASS.

- [ ] **Step 8: Проверить сборку и запуск**

Run: `gradlew build` затем `gradlew runClient`
Expected: выпить бутылку → уровень растёт, экран искажается, со временем проходит.

- [ ] **Step 9: Commit**

```bash
git add create-brewery/src
git commit -m "feat: add drunk level, sobriety ticking, and screen distortion"
```

---

### Task 6: Носик (розлив) и связывание всего цикла

**Files:**
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/SpoutBlock.java` (или «TapBlock»)
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/SpoutBlockEntity.java`
- Create: `create-brewery/src/main/java/com/kostya/createbrewery/block/ModBlocks.java` — добавить носик
- Modify: `create-brewery/src/main/java/com/kostya/createbrewery/CreateBrewery.java` — регистрация
- Test: `create-brewery/src/test/java/com/kostya/createbrewery/block/SpoutLogicTest.java`

**Interfaces:**
- Consumes: `ModFluids.WORT`, `ModItems.DRINK_BOTTLE`, `DrinkData`, кипятильня (`KettleBlockEntity` через соседство/флюид).
- Produces: `SpoutBlockEntity` с чистым ядром `SpoutLogic.fillBottle(hasWort: boolean, hasEmptyBottle: boolean): boolean` — возвращает `true` и даёт бутылку с напитком, только когда есть и сусло, и пустая бутылка.

- [ ] **Step 1: Написать падающие тесты на логику розлива**

```java
@Test void noWortNoBottle()      { /* fillBottle(false,true)==false */ }
@Test void wortButNoBottle()     { /* fillBottle(true,false)==false */ }
@Test void emptyNoBottle()       { /* fillBottle(false,false)==false */ }
@Test void wortAndBottleFills()  { /* fillBottle(true,true)==true */ }
```

- [ ] **Step 2: Запустить тесты — убедиться, что падают**

Run: `gradlew test`
Expected: FAIL — `SpoutLogic` не существует.

- [ ] **Step 3: Реализовать `SpoutLogic` и `SpoutBlockEntity`**

`SpoutLogic.fillBottle(hasWort, hasEmptyBottle)` — чистая функция. `SpoutBlockEntity` в скелете: проверяет наличие сусла в соседней кипятильне (или своём флюид-слоте) и пустой бутылки, выдаёт `DRINK_BOTTLE` с `DrinkData(5)`.

- [ ] **Step 4: Зарегистрировать блок, дать заглушки-модели/текстуры/перевод**

Как в Task 4.

- [ ] **Step 5: Запустить тесты — убедиться, что проходят**

Run: `gradlew test`
Expected: PASS.

- [ ] **Step 6: Проверить полный цикл в игре**

Run: `gradlew runClient`
Expected: вода+ингредиенты в кипятильне под нагревом → сусло → носик разливает в бутылку → выпить → опьянение с искажением экрана.

- [ ] **Step 7: Commit**

```bash
git add create-brewery/src
git commit -m "feat: add spout/tap block for bottling and wire full cycle"
```

---

## Self-Review Notes

- **Spec coverage:** столпы «идеальная настройка/качество/опьянение» в скелете представлены частично — скелет проверяет архитектуру и базовое опьянение; шкала звёзд (полная), перегонка, выдержка, речь, отключка, негативные эффекты — осознанно вне скоупа скелета и пойдут следующими задачами (зафиксировано в дизайн-доке «План первой итерации»). Это соответствует утверждённому решению «сначала скелет».
- **Review Focus:** все 5 строк закреплены тестами (Task 4: строки 1–2; Task 6: строка 3; Task 5: строки 4–5).
- **Type consistency:** `DrinkData.stars` (Task 2) → `bottleWort()` (Task 4) → `SpoutLogic` (Task 6); `DrunkManager.addDrunk/getDrunk/soberTick` (Task 5) вызываются из `DrinkItem` (Task 2 TODO → Task 5 Step 6). Имена едины.
- **Proportion:** план описывает решения (файлы, сигнатуры, тесты, значения из спеки), тела методов оставлены исполнителю.
