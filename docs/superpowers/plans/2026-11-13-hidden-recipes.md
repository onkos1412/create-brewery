# Неизвестные рецепты — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** котёл варит любую смесь по похожести на рецепт, а рецепты, обороты и выдержка нигде не показываются.

**Architecture:**
- Похожесть считается чистым классом `recipe/MixRules`, без Minecraft и с юнит-тестами.
- `KettleBlockEntity` строит из рецептов кандидатов и варит всё содержимое.
- Мутное варево — встроенный `DrinkProfile.MUDDY`.
- Сокрытие — правки JEI, GUI куба, подсказок, Ponder и README.

**Tech Stack:** NeoForge 1.21.1, Create 6.0.10, Java 21, JUnit (`gradlew build`), GameTest (`gradlew runGameTestServer`).

**Spec:** `docs/superpowers/specs/2026-11-13-hidden-recipes-design.md`

## Global Constraints

- **Очки рецепта:** 100 минус штрафы, но не меньше 0.
- **Штраф за количество:** `30 × |G − R| / R`, не больше 60 за предмет.
- **Штраф за отсутствие:** 60, если предмета из рецепта нет совсем.
- **Штраф за чужой предмет:** 35 за каждую штуку.
- **Мутное варево:** если лучший рецепт набирает меньше 30 очков или ни один не подходит по нагреву.
- **Равенство очков:** побеждает рецепт с меньшим id.
- **Звёзды сусла:** `min(QualityRules.starsFromScore(очки), KettleLogic.brewStars(нагрев, нагрев рецепта))`.
- **Мутное варево как напиток:** id `muddy`, крепость 2, цвет `#5b4a32`, нельзя перегнать и выдержать. Всегда 1★, вода 1000 мВ, варка 200 тиков, бутылка с картинкой испорченного напитка.
- **Аромат на дисплее котла:** «слабый» при очках меньше 55 (включая мутное), «знакомый» при 55–89, «отличный» при 90 и больше.
- **Без огня** котёл не варит ничего.
- **Шкала куба:** 0–256 об/мин, без отметки идеала, одним нейтральным цветом.
- **Формат рецептов в datapack** не меняется.
- **Кодировка:** файлы UTF-8 без BOM, концы строк как в файле. Перевод правится через Python JSON с `ensure_ascii=False`.

## Review Focus

1. **Воронка без остановки:** всё содержимое съедается одной варкой, предметы не дублируются и не теряются частично. Тест: `wholeContentsAreEaten`, задача 3.
2. **Содержимое меняется во время варки:** отсчёт сбрасывается, и напиток не выходит раньше срока. Тест: `addingAnItemRestartsTheBrew`, задача 3.
3. **Сохранение мира посреди варки:** отсчёт продолжается, а не сбрасывается из-за пустого снимка содержимого. Тест: в `machineStateSurvivesSaving` добавить проверку прогресса после перезагрузки, задача 3.
4. **Мутное варево у куба и бочки:** куб отказывается его принять, бочка не принимает бутылку. Тест: `muddyBrewGoesNowhere`, задача 3.
5. **Чужой предмет при совпадении типа:** 2 пшеницы и 1 яблоко дают пиво 3★, а не сидр. Тест в `MixRulesTest`, задача 1.

---

### Task 1: Чистые правила похожести

**Files:**
- Create: `src/main/java/com/kostya/createbrewery/recipe/MixRules.java`
- Test: `src/test/java/com/kostya/createbrewery/recipe/MixRulesTest.java`

**Interfaces:**
- Produces:
  - `MixRules.Candidate(String id, List<String> inputs, int idealHeat)`;
  - `MixRules.Choice(int index, int score)`, где `index` — номер кандидата или `-1` для мутного варева;
  - `static int score(List<String> required, List<String> given)`;
  - `static Choice best(List<Candidate> candidates, List<String> given, int heat)`;
  - `static int aroma(int score)`, возвращает `AROMA_WEAK = 1`, `AROMA_FAMILIAR = 2` или `AROMA_EXCELLENT = 3`;
  - константа `MUDDY_THRESHOLD = 30`.

- [ ] **Step 1: Write the failing tests**

Для рецепта медовухи `MEAD = [honeycomb, honeycomb]`:
- `score(MEAD, 2 соты) == 100`;
- `score(MEAD, 3 соты) == 85` и `score(MEAD, 1 сота) == 85`;
- `score(MEAD, 4 соты) == 70`;
- `score(MEAD, 2 соты + яблоко) == 65`;
- `score(MEAD, гнилая плоть) == 5`;
- при 20 сотах штраф за количество ограничен 60, значит `score(MEAD, 20 сот) == 40`.

Для `best`:
- `bestPicksClosestRecipe`: кандидаты пиво `[wheat, wheat]` и сидр `[apple, apple]`; 1 яблоко даёт сидр (`index` сидра) с 85 очками;
- `foreignItemKeepsTheMatchingRecipe`: 2 пшеницы и 1 яблоко дают пиво с 65 очками;
- `tieGoesToTheSmallerId`: 2 пшеницы и 2 яблока дают пиво с 30 очками, а не сидр;
- `nothingCloseIsMuddy`: гнилая плоть даёт `index == -1`;
- `tooColdIsMuddy`: при `heat` ниже `idealHeat` всех кандидатов `index == -1`;
- `aromaBands`: `aroma(54) == 1`, `aroma(55) == 2`, `aroma(89) == 2`, `aroma(90) == 3`.

- [ ] **Step 2: Run** `gradlew build`. Expected: compilation FAIL, нет `MixRules`.
- [ ] **Step 3: Implement `MixRules`.** Считать количества через `Map<String, Integer>`. Штраф за количество округлять в `score` через `Math.round`.
- [ ] **Step 4: Run** `gradlew build`. Expected: все юнит-тесты PASS.
- [ ] **Step 5: Commit** `feat: score brewing mixes by how close they are to a recipe`

### Task 2: Мутное варево как напиток

**Files:**
- Modify:
  - `recipe/DrinkProfile.java`: константа `MUDDY`;
  - `recipe/BrewingRecipe.java`: `profileOf` возвращает `MUDDY` для id `muddy`;
  - `client/ModClientSetup.java`: в `BOTTLE_ART` добавить `muddy`, картинка 9f;
  - `lang/ru_ru.json`, `lang/en_us.json`: `drink.brewery.muddy` — «Мутное варево» / «Muddy brew».
- Test: `src/test/java/com/kostya/createbrewery/recipe/DrinkProfileTest.java`

**Interfaces:**
- Produces: `DrinkProfile.MUDDY` с `id = "muddy"`, `alcohol = 2`, `canDistill() == false`, `canAge() == false`, `color = 0x5B4A32`.

- [ ] **Step 1: Write the failing test.** `muddyCannotGoFurther`: `assertFalse(MUDDY.canDistill())`, `assertFalse(MUDDY.canAge())`, `assertEquals(0x5B4A32, MUDDY.color())`.
- [ ] **Step 2: Run** `gradlew build`. Expected: FAIL.
- [ ] **Step 3: Implement** константу и ветку в `profileOf`.
- [ ] **Step 4: Run** `gradlew build`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add the muddy brew for mixes that match nothing`

### Task 3: Котёл варит всё содержимое

**Files:**
- Modify:
  - `block/KettleBlockEntity.java`: кандидаты, снимок содержимого, варка всего содержимого, аромат;
  - `block/KettleLogic.java`: убрать `RecipeView`, `Result`, `tryBrew`, `advanceProgress`, `brewsAtAll`, оставить `brewStars`, добавить `mixStars`;
  - `block/KettleMenu.java`: слот данных `DATA_AROMA = 8`, `DATA_COUNT = 9`;
  - `block/KettleScreen.java`: аромат на дисплее во время варки;
  - перевод: `gui.brewery.kettle_aroma_1..3` — «Аромат: слабый / знакомый / отличный», «Aroma: faint / familiar / excellent».
- Test:
  - `src/test/java/com/kostya/createbrewery/block/KettleLogicTest.java`: убрать тесты удалённых методов, оставить `onlyTheExactHeatIsPerfect`, добавить тест `mixStars`;
  - `gametest/BreweryGameTests.java`.

**Interfaces:**
- Consumes: `MixRules.best`, `MixRules.aroma`, `DrinkProfile.MUDDY`.
- Produces:
  - `KettleLogic.mixStars(int score, int heatStars) -> int`, возвращает `min(starsFromScore(score), heatStars)`;
  - `KettleBlockEntity.getAroma() -> int`, 0 когда котёл не варит.

Поведение тика:
1. Нет огня или в слотах пусто: прогресс 0, состояние «не варит».
2. Снимок содержимого: отсортированный список id с повторами. Если он отличается от прошлого тика, прогресс 0. Снимок сохраняется в NBT под ключом `Contents`, чтобы перезагрузка не сбрасывала варку.
3. `MixRules.best` по рецептам с id `holder.id().toString()`. Нужная вода — `recipe.getWort().getAmount()`, у мутного 1000. Время — из рецепта, у мутного 200.
4. Прогресс растёт, только если хватает воды и в баке есть место под сусло того же напитка. Иначе сбрасывается, как сейчас.
5. По готовности:
   - очищаются все слоты;
   - вода уменьшается;
   - добавляется сусло напитка победителя (`muddy` для мутного);
   - звёзды: `mixStars` и худший нагрев за варку, у мутного всегда 1.

- [ ] **Step 1: Write the failing game tests.**
  - `extraWheatCostsAStar`: заменяет `surplusWheatBrewsOneBatchAtATime`. 3 пшеницы на огне дают 1000 мВ сусла `beer` на 4★, слоты пусты.
  - `wholeContentsAreEaten`: 14 пшеницы дают сусло `beer`, все 14 съедены.
  - `strangeMixBrewsMuddy`: гнилая плоть даёт сусло `muddy` на 1★.
  - `addingAnItemRestartsTheBrew`: 2 пшеницы, через 150 тиков ещё одна. Через 60 тиков сусла нет, через 200 тиков после добавления — 4★.
  - `muddyBrewGoesNowhere`: куб не принимает мутное сусло через `fill`, `fill == 0`. Бочка не принимает бутылку мутного варева во входной слот.
  - В `machineStateSurvivesSaving`: прогресс котла переживает перезагрузку.
- [ ] **Step 2: Run** `gradlew runGameTestServer`. Expected: новые тесты FAIL.
- [ ] **Step 3: Implement** котёл, меню, экран и перевод.
- [ ] **Step 4: Run** `gradlew build`, затем `gradlew runGameTestServer`. Expected: все тесты PASS. Старые тесты, которые опирались на остаток сырья, переписываются под варку всего содержимого с тем же смыслом.
- [ ] **Step 5: Commit** `feat: brew whatever is in the kettle by its closest recipe`

### Task 4: Убрать рецепты из открытого доступа

**Files:**
- Delete: `compat/jei/BreweryJeiPlugin.java`, `compat/jei/BrewingCategory.java`.
- Modify:
  - `block/DistilleryScreen.java`, `block/MachineWidgets.java`:
    - `speedGauge(graphics, x, y, w, h, float rpm, int maxRpm, int color)` без отметки идеала;
    - `MAX_RPM = 256`, цвет `IDLE`;
    - текст `gui.brewery.distillery_stats` с одним аргументом: «%s об/мин» / «%s RPM».
  - `block/DistilleryMenu.java`: убрать `getIdealRpm`.
  - `item/DrinkItem.java`, `item/DistillateItem.java`: убрать строки выдержки и «ПКМ: разлить», ключи `tooltip.brewery.distillate_aging` и `tooltip.brewery.distillate_pour` удалить из перевода.
  - Перевод Ponder, русский и английский:
    - `kettle.text_3`: «Котёл варит всё, что в нём лежит. Положили ещё — варка начнётся заново»;
    - `kettle.text_5`: «Носик у котла сразу разливает сусло по бутылкам»;
    - `distillery.text_3`: «Подведите вращение к валу сзади. У каждого напитка своя скорость — подберите её по звёздам»;
    - `distillery.text_5`: «Дистиллят идёт в бочку, а некоторые можно разлить сразу носиком или из руки»;
    - `barrel.text_1`: «Бочка принимает дистиллят или бутылку молодого напитка».
  - `README.md`:
    - таблица «Напитки» заменяется абзацем о том, что рецепты неизвестны: котёл варит любую смесь, похожесть даёт звёзды, непохожее превращается в мутное варево, для 5★ на автоматике нужна точная подача;
    - в таблице машин убрать «Нужная скорость видна в GUI»;
    - пример `drink` заменить на вымышленный `kvass`.
- Test:
  - `block/MachineGuiLayoutTest.java`, `block/DistilleryGuiResourcesTest.java`: проверки под новый текст;
  - новый `src/test/java/com/kostya/createbrewery/HiddenRecipesTest.java`:
    - нет файлов в `compat/jei`;
    - в `README.md` нет строк `Виски (32)` и `Выдержанный виски`;
    - в `ru_ru.json` нет ключа `tooltip.brewery.distillate_aging`;
    - в `DistilleryScreen.java` нет `getIdealRpm`.

- [ ] **Step 1: Write the failing test** `HiddenRecipesTest` с проверками выше.
- [ ] **Step 2: Run** `gradlew build`. Expected: FAIL.
- [ ] **Step 3: Implement** все правки списка.
- [ ] **Step 4: Run** `gradlew build` и `gradlew runGameTestServer`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: hide recipes, speeds and aging from players`

### Task 5: Проверка в клиенте

Временный `client/TempShots.java` по обычной схеме, не коммитится.

- [ ] Снять скриншоты:
  - котёл с 3 сотами во время варки: аромат «знакомый»;
  - куб: только обороты и шкала без отметки;
  - бутылку мутного варева с подсказкой.
- [ ] Удалить `TempShots` и скриншоты. Прогнать `gradlew build` и `gradlew runGameTestServer`. Expected: всё PASS.
