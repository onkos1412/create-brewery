# Достижения и серверный конфиг — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** вкладка достижений Create Brewery и серверный конфиг для механик опьянения.

**Architecture:**
- Достижения — JSON в `data/brewery/advancement/`. Ванильный `inventory_changed` где можно, остальное — `minecraft:impossible`, которые выдаёт `BreweryAdvancements.award`.
- Конфиг — `BreweryConfig` на `ModConfigSpec`, регистрируется как SERVER. Его читают `DrunkManager`, `DrunkEvents` и `DrinkItem`.

**Tech Stack:** NeoForge 1.21.1, Java 21, GameTest.

**Spec:** `docs/superpowers/specs/2026-11-13-advancements-and-config-design.md`

## Global Constraints

- **Значения из спецификации без изменений:** id, названия, родители, скрытость, критерии «Дегустатора», ключи конфига, значения по умолчанию и диапазоны.
- **По умолчанию поведение мода не меняется.**
- **Перевод:** через Python JSON (`ensure_ascii=False, indent=2`).
- **Чистые правила** (`DrunkLevel`, `StaggerRules`, `VomitRules`, `HangoverRules`) не зависят от конфига.

## Review Focus

1. **Повторная выдача достижения** не ломает игру: `award` выдаёт его только один раз.
2. **Выключенное похмелье** не копит долг и не наступает.
3. **Изменённый конфиг в игровых тестах** возвращается к значениям по умолчанию после теста, чтобы не сломать другие.

---

### Task 1: Достижения

**Files:**
- Create:
  - `advancement/BreweryAdvancements.java`: `award(ServerPlayer, String advancement, String criterion)`;
  - 9 JSON в `src/main/resources/data/brewery/advancement/`.
- Modify:
  - `item/DrinkItem.java`;
  - `drunk/DrunkManager.java` (отключка, похмелье);
  - перевод: `advancements.brewery.<id>.title` и `.description`.
- Test:
  - игровые тесты:
    - `drinkingMuddyAwardsAdvancement`;
    - `fiveStarDrinkAwardsMasterBrewer`;
    - `passingOutAwardsOverdone`;
    - `tasterNeedsEveryDrink`: 12 из 13 напитков не дают, 13-й даёт.
  - Мок-игрок должен быть `ServerPlayer`; посмотрите, как существующие тесты делают питьё.
- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: brewing advancements`

### Task 2: Серверный конфиг

**Files:**
- Create: `config/BreweryConfig.java` (`ModConfigSpec`, ключи из спецификации).
- Modify:
  - `CreateBrewery.java`: регистрация `ModConfig.Type.SERVER`;
  - `drunk/DrunkManager.java`, `drunk/DrunkEvents.java`, `item/DrinkItem.java`;
  - перевод для экрана конфига, если NeoForge его показывает;
  - README: короткий раздел «Настройки сервера».
- Test:
  - игровые тесты меняют значение, проверяют и возвращают по умолчанию:
    - `staggerCanBeTurnedOff`;
    - `vomitCanBeTurnedOff`;
    - `hangoverCanBeTurnedOff` (долг не копится);
    - `passOutThresholdIsConfigurable`;
    - `effectMultiplierScalesDuration`.
- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: server config for drunk mechanics`
