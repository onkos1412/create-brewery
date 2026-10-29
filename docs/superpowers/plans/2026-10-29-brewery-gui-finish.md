# Brewery GUI and Vertical Slice Finish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the broken barrel GUI background with a compact, usable Minecraft/Create-style interface and verify the whiskey vertical slice.

**Architecture:** Keep the existing menus and block entities. Replace only the barrel GUI presentation and add the smallest synchronization/validation fixes needed for a usable screen. Preserve the old kettle/spout route.

**Tech Stack:** NeoForge 1.21.1, Create 6.0.10, Java 21, existing `AbstractContainerScreen` menus and PNG resources.

**Spec:** `docs/superpowers/specs/2026-10-28-whiskey-pipeline-design.md`

## Global Constraints

- Ideal heated distillation at 200 ticks and 32 RPM must produce 5★.
- Oak aging takes 72000 game ticks.
- Errors lower quality but do not destroy product or permanently block processing.
- Manual GUI transfer remains; Create pipes are deferred.
- Existing beer route and `DrinkData(int stars)` remain compatible.

## Review Focus

- Barrel GUI must show exactly two inputs, one output, button, and aging status without kettle-only artwork.
- Clicking start with invalid input must not consume items or start aging.
- GUI progress must update while the world ticks and survive reopening.
- Existing kettle GUI and old spout route must remain usable.
- Client resource loading must produce no missing brewery texture/model errors.

---

### Task 1: Replace barrel GUI artwork and layout

**Files:**
- Modify: `src/main/java/com/kostya/createbrewery/block/BarrelScreen.java`
- Modify: `src/main/java/com/kostya/createbrewery/block/BarrelMenu.java`
- Create/replace: `src/main/resources/assets/brewery/textures/gui/barrel.png`
- Modify: `src/main/resources/assets/brewery/lang/en_us.json`
- Modify: `src/main/resources/assets/brewery/lang/ru_ru.json`

**Interfaces:**
- Consume existing `BarrelMenu` slots 0/1 as distillate/wood input and slot 2 as bottle output.
- Consume existing `BarrelMenu.getDays()` and button id 0.

- [ ] Write a focused screen/layout regression test or resource assertion proving the barrel screen uses its own barrel texture and exposes the expected button/status labels.
- [ ] Run the test and observe RED before implementation.
- [ ] Replace the copied kettle texture with a clean 176x166 barrel GUI background: title area, slots at the existing menu coordinates, output slot, button area, and no kettle spout/extra machine slots.
- [ ] Update `BarrelScreen` to draw only the background, button, and status/progress text; preserve tooltip rendering and button click bounds.
- [ ] Ensure menu slot positions match the visual background and reject invalid output placement.
- [ ] Run unit/build checks and confirm GREEN.
- [ ] Commit: `fix: redesign barrel gui`

### Task 2: Finish vertical-slice validation

**Files:**
- Modify only files needed by findings from Task 1 or runtime logs.
- Test: existing unit suite and client startup log.

**Interfaces:**
- Preserve `KettleBlockEntity`, `DistilleryBlockEntity`, `BarrelBlockEntity`, and `SpoutBlock` public behavior.

- [ ] Run `gradlew build` and confirm all tests pass.
- [ ] Run `gradlew runClient`, load/create a world, and inspect barrel, distillery, and kettle screens.
- [ ] Check `run/client/logs/latest.log` for `ERROR`, missing brewery resources, crashes, and the prior recipe-category warning.
- [ ] Fix only reproducible runtime issues, each with a regression test where practical.
- [ ] Commit final runtime fixes if any.

### Task 3: Final verification

- [ ] Run fresh `gradlew build`.
- [ ] Run `git diff --check`.
- [ ] Report remaining risks: interactive route coverage and any non-blocking external Create/Mixin warnings.
