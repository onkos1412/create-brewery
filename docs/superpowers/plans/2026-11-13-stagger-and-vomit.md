# Шатающаяся походка и рвота — план работ

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** пьяного игрока сносит в сторону, а при перепое его может стошнить.

**Architecture:** чистые `drunk/StaggerRules` и `drunk/VomitRules` принимают решения, `DrunkManager` применяет их к игроку, `DrunkEvents` вызывает раз в секунду на сервере.

**Tech Stack:** NeoForge 1.21.1, Java 21, JUnit, GameTest.

**Spec:** `docs/superpowers/specs/2026-11-13-stagger-and-vomit-design.md`

## Global Constraints

**Походка:**
- шанс `(уровень − 25) / 150`, при уровне ≤ 25 — 0;
- сила `0.1 + уровень / 400`;
- не сносит, если игрок крадётся, летает, сидит верхом, в отключке или его горизонтальная скорость меньше 0.01.

**Рвота:**
- уровень ≥ 70 и перерыв 600 тиков;
- шанс `min(0.01 + долг × 0.002, 0.05)`;
- последствия: еда −4 (не ниже 0), тошнота 100 тиков, опьянение −15, частицы и звук;
- сообщение `message.brewery.vomited` — «Вас стошнило» / «You threw up».

**Общее:**
- перерыв рвоты хранится в несохраняемом attachment и сбрасывается при смерти;
- перевод правится через Python JSON (`ensure_ascii=False, indent=2`).

## Review Focus

1. **Игрок стоит на месте** — его не сносит.
2. **Перерыв:** две рвоты подряд невозможны раньше 600 тиков.
3. **Отключка:** не сносит и не тошнит.

---

### Task 1: Шатающаяся походка

**Files:**
- Create: `drunk/StaggerRules.java`:
  - `chance(int level) -> float`;
  - `strength(int level) -> double`;
  - `shouldStagger(int level, float roll) -> boolean`.
- Modify:
  - `drunk/DrunkManager.java`: `staggerTick(Player, RandomSource)`;
  - `drunk/DrunkEvents.java`: вызов раз в 20 тиков.
- Test:
  - `StaggerRulesTest`: 25 → 0, 100 → 0.5, сила на 100 — 0.35;
  - игровые тесты:
    - `drunkWalkerIsPushed`: уровень 100, игрок движется, бросок выпадает в «сносит» → горизонтальная скорость изменилась;
    - `standingStillIsNotPushed`;
    - `sneakingIsNotPushed`.
- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: drunk players stagger as they walk`

### Task 2: Рвота

**Files:**
- Create: `drunk/VomitRules.java`:
  - `chance(int debt) -> float`;
  - `shouldVomit(int level, int debt, int cooldown, float roll) -> boolean`;
  - константы `FROM_LEVEL = 70`, `COOLDOWN_TICKS = 600`, `SOBERS_BY = 15`, `FOOD_LOST = 4`.
- Modify:
  - `drunk/ModAttachments.java`: `vomit_cooldown` (int, не сохраняется);
  - `drunk/DrunkManager.java`: `vomitTick(Player, RandomSource)` и `reset`;
  - `drunk/DrunkEvents.java`: вызов раз в 20 тиков;
  - перевод;
  - README, раздел «Опьянение»: одна фраза о шатании и рвоте.
- Test:
  - `VomitRulesTest`: шанс 0.01 при долге 0, 0.05 при долге 20 и больше, ниже 70 не тошнит, при идущем перерыве не тошнит;
  - игровые тесты:
    - `heavyDrinkerThrowsUp`: уровень 90, бросок «тошнит» → еда −4, опьянение −15, тошнота;
    - `vomitingHasACooldown`;
    - `knockedOutPlayerDoesNotVomit`.
- [ ] Сначала падающие тесты, затем реализация, затем `gradlew build` и `gradlew runGameTestServer` — PASS.
- [ ] Commit `feat: heavy drinking can make players throw up`
