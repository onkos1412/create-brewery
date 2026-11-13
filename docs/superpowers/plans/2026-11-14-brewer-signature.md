# Brewer Signature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every drink carries and shows "Brewed by <owner>, day <N>", where the owner is the player who placed the kettle.

**Architecture:** A new data component `brewery:brewer` holding `BrewerMark(String name, long day)` sits next to
`drink_data` on wort fluid stacks, distillates and drink bottles. The kettle remembers its owner and stamps the mark
when a brew finishes; every later stage copies the mark it received. Tooltips read it.

**Tech Stack:** NeoForge 1.21.1, Create 6.0.10, Java 21, JUnit 5 (`gradlew build`), GameTests (`gradlew runGameTestServer`).

**Spec:** `docs/superpowers/specs/2026-11-14-brewer-signature-design.md`

## Global Constraints

- Build/test with `JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot"`; `gradlew build` (unit) and `gradlew runGameTestServer` (game tests) must both pass.
- Lang files are edited with Python `json` (`ensure_ascii=False, indent=2`), never PowerShell `Set-Content`. Keep each file's line endings.
- Stars, alcohol, effects and recipe matching never depend on the mark.
- Drinks/wort without a mark keep working and show no signature line.
- Commit trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not touch untracked `docs/superpowers/{plans,specs}/2026-10-*` files.

## Review Focus

1. A kettle that already holds wort finishes another brew (maybe on a later day): the wort keeps the FIRST mark (like the weakest-stars rule keeps one value for the lot). Test in Task 2.
2. A kettle placed before this update (no `Owner` NBT) or placed by a non-player: brews work, no mark, no crash on load. Test in Task 2.
3. Wort drained from the distillery back out by a pump keeps its mark. Test in Task 2.
4. Over-aging in the barrel rebuilds the drink stack (`overAge`) — the mark must survive. Covered because `overAge` copies the stack; assert in the barrel test of Task 2.
5. Create spout / any container filler (`WortBottling.BottleHandler`) keeps the mark. Test in Task 2.

---

### Task 1: BrewerMark component and tooltips

**Files:**
- Create: `src/main/java/com/kostya/createbrewery/BrewerMark.java`
- Modify: `src/main/java/com/kostya/createbrewery/registry/ModDataComponents.java` (register `BREWER`)
- Modify: `src/main/java/com/kostya/createbrewery/item/DrinkItem.java` and `item/DistillateItem.java` (`appendHoverText`)
- Modify: `src/main/resources/assets/brewery/lang/en_us.json`, `ru_ru.json`
- Test: `src/test/java/com/kostya/createbrewery/BrewerMarkTest.java`

**Interfaces:**
- Produces:
  - `public record BrewerMark(String name, long day)` with `public static final Codec<BrewerMark> CODEC` (fields `name`, `day`) and `public static final StreamCodec<ByteBuf, BrewerMark> STREAM_CODEC`.
  - `public static long dayOf(long dayTime)` → `dayTime / 24000 + 1` (day 1 is the first day of a world).
  - `public Component tooltip()` → `Component.translatable("tooltip.brewery.brewer", name, day)` styled GRAY + ITALIC.
  - `ModDataComponents.BREWER` : `DeferredHolder<DataComponentType<?>, DataComponentType<BrewerMark>>`, id `brewer`, persistent `CODEC`, `networkSynchronized(STREAM_CODEC)` (so client tooltips see it).

- [ ] **Step 1: Write failing tests** in `BrewerMarkTest`:
  - `dayStartsAtOne`: `dayOf(0) == 1`, `dayOf(23999) == 1`, `dayOf(24000) == 2`, `dayOf(24000L * 141 + 5) == 142`.
  - `codecRoundTrip`: encode `new BrewerMark("Kostya", 142)` with `JsonOps.INSTANCE` (as `DrinkDataTest` does), parse back, `assertEquals`.
- [ ] **Step 2:** `gradlew build` — expected: compile failure (no `BrewerMark`).
- [ ] **Step 3:** Implement `BrewerMark`, register `BREWER`. In both `appendHoverText` add `tooltip.add(mark.tooltip())` right after the stars line when `stack.get(ModDataComponents.BREWER.get())` is non-null.
- [ ] **Step 4:** Lang: en `"tooltip.brewery.brewer": "Brewed by %s, day %s"`, ru `"tooltip.brewery.brewer": "Сварил: %s, день %s"`.
- [ ] **Step 5:** `gradlew build` — expected: BUILD SUCCESSFUL, unit tests 118 passing.
- [ ] **Step 6:** Commit `feat: brewer mark component and tooltip`.

### Task 2: Kettle owner, stamping and carrying the mark through every stage

**Files:**
- Modify: `block/KettleBlock.java` (add `setPlacedBy`), `block/KettleBlockEntity.java`, `block/WortBottling.java`, `block/DistilleryBlockEntity.java`, `block/BarrelBlockEntity.java`
- Test: `gametest/BreweryGameTests.java`

**Interfaces:**
- Consumes: `BrewerMark`, `BrewerMark.dayOf`, `ModDataComponents.BREWER` (Task 1).
- Produces:
  - `KettleBlockEntity.setOwner(UUID id, String name)`; `@Nullable UUID getOwnerId()`; NBT keys `OwnerId` (UUID via `putUUID`), `OwnerName`, and `WortBrewer` (the current wort's mark, encoded with `BrewerMark.CODEC` under `NbtOps`, absent when none).
  - `@Nullable BrewerMark getWortMark()` on kettle.
  - `KettleBlockEntity.wortStack(int amount, int stars, String drink, @Nullable BrewerMark mark)` — replaces the 3-arg version; sets `BREWER` only when mark non-null.
  - `WortBottling.bottle(Level level, int stars, String drink, @Nullable BrewerMark mark)` — replaces the 3-arg version.

Rules:
- `KettleBlock.setPlacedBy`: if placer is a `Player`, `setOwner(player.getUUID(), player.getGameProfile().getName())`.
- Brew finishes (where `wortStars`/`wortDrink` are set): if `wortAmount > 0` before adding, keep the existing mark (first one wins); else mark = owner present ? `new BrewerMark(ownerName, BrewerMark.dayOf(level.getDayTime()))` : null. When wort runs out (amount 0) the mark is cleared.
- Kettle tap (`WortBottling.bottle(level, wortStars, wortDrink, wortMark)`) and kettle fluid handler pass the mark.
- `BottleHandler.fill` passes `resource.get(BREWER)`.
- Distillery: `fill` stores the incoming mark in `inputMark` when the cube was empty (first wins otherwise); NBT key `InputBrewer`; `drain`/`getFluidInTank` pass it; the finished distillate (also spoiled) gets it; `takeFinishedBottle` copies it onto the bottle; cleared when the cube empties.
- Barrel `finish`: `aged` gets `batch.get(BREWER)` if present. `overAge` already copies — leave it.

- [ ] **Step 1: Write failing game tests** (`@GameTest(template = "empty", ...)`, using `placeKettle`, existing brewing helpers and `helper.makeMockPlayer` where a Player is needed):
  - `kettlePlacedByPlayerSignsBrew`: `KettleBlock.setPlacedBy` with a mock player → `getOwnerId()` equals the player's UUID; let the brew finish; tapped bottle has `BREWER` with that player's name and `day == BrewerMark.dayOf(level.getDayTime())`.
  - `ownerlessKettleLeavesNoMark`: kettle with no owner; brew finishes; tapped bottle has no `BREWER`.
  - `secondBrewKeepsFirstMark`: owner set, first brew done; set day time `+24000`; second brew done; `getWortMark().day()` is still the first day.
  - `markSurvivesDistillery`: fill a distillery with `wortStack(…, new BrewerMark("Kostya", 7))`; draining returns a stack with the same mark; after distilling, the distillate (and for vodka the bottle from `takeFinishedBottle`) carries it.
  - `markSurvivesBarrel`: age a distillate carrying `BrewerMark("Kostya", 7)` (reuse the aging-test setup); the aged bottle carries it, and it is still there after `overAge` lowers stars.
  - `spoutBottlingKeepsMark`: `new WortBottling.BottleHandler(new ItemStack(Items.GLASS_BOTTLE)).fill(marked wort, EXECUTE)` → container has the mark.
  - `kettleOwnerSurvivesSaveLoad`: `saveWithoutMetadata` then `loadWithComponents` into a fresh kettle entity → same owner and wort mark.
- [ ] **Step 2:** `gradlew runGameTestServer` — expected: compile failure / new tests failing.
- [ ] **Step 3:** Implement the rules above; update all callers of the old `wortStack`/`bottle` signatures, including existing game tests (pass `null`).
- [ ] **Step 4:** `gradlew build` and `gradlew runGameTestServer` — expected: all unit tests and all game tests (88 + 7) pass.
- [ ] **Step 5:** Commit `feat: kettle owner signs brews; mark follows the drink`.
