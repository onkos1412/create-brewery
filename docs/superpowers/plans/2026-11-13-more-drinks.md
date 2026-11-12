# Новые напитки — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** пять новых цепочек напитков как данные: рецепты, перевод, цвета и картинки бутылок, с игровыми тестами.

**Architecture:** каждый напиток — рецепт `brewery:brewing` с блоком `drink`, который читают существующие котёл, куб и бочка. Картинки рисуются в `tools/gen_art.py` и выбираются свойством `brewery:drink` (`BOTTLE_ART` в `ModClientSetup`).

**Tech Stack:** NeoForge 1.21.1, Create 6.0.10, Java 21, Python + Pillow (`gen_art.py`), GameTest.

**Spec:** `docs/superpowers/specs/2026-11-13-more-drinks-design.md`

## Global Constraints

- **Значения из таблицы спецификации:** id, сырьё, нагрев, крепость, цвет, тики, об/мин, дни и дерево берутся точно оттуда.
- **Вода и сусло:** у каждого рецепта 1000 мВ воды, 1000 мВ сусла и 200 тиков варки, как у существующих.
- **Сгенерированные файлы:** не правятся руками. Картинки и модели появляются после `python tools/gen_art.py`.
- **Перевод:** правится через Python JSON (`ensure_ascii=False, indent=2`, перевод строки в конце).
- **Секретность:** рецепты нигде не раскрываются игроку.

## Review Focus

1. **3 пшеницы** дают пиво 4★, а не стаут. Это уже проверяет существующий `extraWheatCostsAStar`; после добавления стаута он должен остаться зелёным.
2. **Полынь на обычном огне:** папоротник и сахар на кострище дают мутное варево, на Blaze Burner с тортом — полынную брагу 5★. Тест `wormwoodNeedsSuperheat`.
3. **Абсент без бочки:** разливается из руки или носиком, как водка. Тест `wormwoodDistillsToAbsinthe`.
4. **Дерево выдержки:** выдержанная текила в акации даёт 5★, в дубе — меньше. Тест `pulqueAgesIntoTequilaInAcacia`.

---

### Task 1: Пять новых цепочек

**Files:**
- Create: `src/main/resources/data/brewery/recipe/{kvass,stout,pulque,wormwood,glow}.json`.
- Modify:
  - `tools/gen_art.py`: новые бутылки и overrides в `drink_bottle.json`, индексы 10–14 по порядку `kvass, stout, tequila, absinthe, firefly`;
  - `client/ModClientSetup.java`: `BOTTLE_ART` для новых стадий, брага → 3f;
  - оба файла перевода;
  - `README.md`: пример «Свои напитки» с `"wood": "mangrove"`.
- Test: `gametest/BreweryGameTests.java`.

- [ ] **Step 1: Write the failing game tests**, по образцу `applesBrewCider`, `potatoesDistillToVodkaWithoutABarrel` и `applesAgeIntoCalvadosInBirch`:
  - `breadBrewsKvass`: 2 хлеба на огне дают сусло `kvass` 5★, бутылка `kvass` с крепостью 3.
  - `wheatAndCocoaBrewStout`: 2 пшеницы и какао-бобы дают `young_stout` 5★.
  - `wormwoodNeedsSuperheat`: папоротник и сахар на кострище дают `muddy`, на Blaze Burner с тортом — `wormwood_mash` 5★.
  - `wormwoodDistillsToAbsinthe`: куб на 112 об/мин даёт дистиллят `absinthe`, который разливается без бочки.
  - `pulqueAgesIntoTequilaInAcacia`: свой `batch`, перевод дня как у `applesAgeIntoCalvadosInBirch`. Даёт `aged_tequila` 5★ в акации.
  - `glowBerriesBrewGlowMash`: 2 светящиеся ягоды дают `glow_mash`.
- [ ] **Step 2: Run** `gradlew runGameTestServer`. Expected: новые тесты FAIL.
- [ ] **Step 3: Implement** рецепты, перевод, `BOTTLE_ART`, картинки в `gen_art.py`, затем `python tools/gen_art.py`, README.
- [ ] **Step 4: Run** `gradlew build` и `gradlew runGameTestServer`. Expected: все тесты PASS.
- [ ] **Step 5: Commit** `feat: add kvass, stout, tequila, absinthe and firefly liqueur`

### Task 2: Проверка картинок в клиенте

- [ ] Временный `TempShots` кладёт в инвентарь по бутылке каждой новой стадии. Скриншот инвентаря: у каждого напитка своя картинка, у браги — общая.
- [ ] Удалить `TempShots` и копию мира. Прогнать `gradlew build` и `gradlew runGameTestServer`. Expected: PASS.
