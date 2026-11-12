# Эффекты напитков и похмелье — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** напитки дают свои эффекты в зависимости от звёзд, а после плохих напитков наступает похмелье.

**Architecture:**
- Эффекты — необязательное поле `effects` в `DrinkProfile`. Их применяет `DrinkItem`, коэффициент длительности считает чистый `quality/QualityRules`.
- Похмелье — чистый `drunk/HangoverRules`, attachment долга и проверка в `DrunkEvents`.

**Tech Stack:** NeoForge 1.21.1, Java 21, Mojang Codec, JUnit, GameTest.

**Spec:** `docs/superpowers/specs/2026-11-13-effects-and-hangover-design.md`

## Global Constraints

- **Коэффициент длительности:** 5★ — 1.0, 4★ — 0.75, 3★ — 0.5, 1–2★ — 0. Испорченный и мутный напиток эффектов не дают.
- **Формат эффекта:** `{"effect": id, "seconds": int ≥ 1, "amplifier": int ≥ 0, по умолчанию 0}`. Ключ карты — стадия этого рецепта (`id`, `distilled` или `aged`), иначе рецепт не загружается.
- **Долг похмелья за напиток:** `round(крепость / 5 × вес)`, вес 5★ — 0.1, 4★ — 0.4, 3★ — 0.7, остальное — 1.0.
- **Порог похмелья:** долг ≥ 8. Длительность `min(долг × 15 с, 300 с)`: усталость (mining fatigue) с усилением 0 и голод. Долг обнуляется, когда опьянение доходит до 0.
- **Вода:** бутылка воды снимает усталость и голод, только если сейчас похмелье.
- **Сообщение:** «Голова раскалывается…» / «Your head is pounding…», ключ `message.brewery.hangover`.
- **Перевод:** через Python JSON (`ensure_ascii=False, indent=2`).
- **Совместимость:** старые рецепты без `effects` работают как раньше.

## Review Focus

1. **Повторная пьянка до трезвости:** долг копится и не гасится раньше времени. Тест `hangoverDebtAccumulatesUntilSober`.
2. **Смерть:** сбрасывает долг. Тест в `HangoverRules`/игровом тесте: долг после смерти 0.
3. **Стадия, которой нет в рецепте, как ключ `effects`:** рецепт отклоняется. Юнит-тест кодека.
4. **Мутный напиток 5★ с эффектами:** эффектов не даёт.

---

### Task 1: Эффекты напитков

**Files:**
- Modify:
  - `recipe/DrinkProfile.java`: поле `Map<String, List<DrinkEffect>> effects` и проверка ключей;
  - `quality/QualityRules.java`: `effectDurationFactor(int stars) -> float`;
  - `item/DrinkItem.java`: применение эффектов;
  - `recipe/BrewingRecipe.java`: `profileWithStage(Level, String stage)`;
  - рецепты `kvass`, `mead`, `potato_mash`, `cane_mash`, `stout`, `pulque`, `wormwood`, `glow` по таблице спецификации.
- Create: `recipe/DrinkEffect.java`, record `(ResourceLocation effect, int seconds, int amplifier)` с кодеком.
- Test:
  - `DrinkProfileTest`: разбор `effects`, отказ на чужой стадии, отказ на `seconds` 0;
  - `QualityRulesTest`: коэффициенты;
  - игровой тест `drinkingTequilaGivesSpeed`: 5★ даёт скорость 1200 тиков ±20, 2★ — без скорости;
  - игровой тест `muddyGivesNoEffects`.

- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: drinks give their own effects, scaled by quality`

### Task 2: Похмелье

**Files:**
- Create: `drunk/HangoverRules.java` (чистый):
  - `debtFor(int alcohol, int stars, boolean spoiledOrMuddy) -> int`;
  - `THRESHOLD = 8`;
  - `durationTicks(int debt) -> int`.
- Modify:
  - `drunk/ModAttachments.java`: attachment `hangover_debt` (int, сериализуется, без синхронизации);
  - `drunk/DrunkManager.java`: `addHangoverDebt`, проверка при трезвости, сброс в `reset`;
  - `drunk/DrunkEvents.java`: снятие похмелья бутылкой воды;
  - `item/DrinkItem.java`: копить долг при питье;
  - перевод `message.brewery.hangover`;
  - README, раздел «Опьянение»: одна фраза о похмелье и что его снимает.
- Test:
  - `HangoverRulesTest`:
    - водка 40, 5★ → 1;
    - 2★ → 8;
    - мутная 2 → 0;
    - порог;
    - длительность до 300 с.
  - игровые тесты:
    - `badDrinksLeaveAHangover`: три водки 2★, опьянение доведено до 0 → усталость и голод;
    - `goodDrinksLeaveNoHangover`: три водки 5★ → без эффектов;
    - `waterCuresHangover`;
    - `hangoverDebtAccumulatesUntilSober`.

- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: a hangover follows bad drinking`
