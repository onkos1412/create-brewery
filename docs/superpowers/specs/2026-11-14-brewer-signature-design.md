# Brewer signature — design

## Intent
For atmosphere and authorship (not anti-forgery): every drink shows who brewed it and on which in-game day,
like BreweryX. Tooltip line under the stars: "Brewed by Kostya, day 142" / «Сварил: Kostya, день 142».

## Goals
- The kettle remembers its owner: the player who placed it (name + UUID in block entity NBT).
- When the kettle finishes a brew, the wort gets a `brewer` data component: owner name + in-game day
  (`level.getDayTime() / 24000`, counted from 1 — the overworld day the brew finished).
- The mark follows the drink through every stage: wort fluid in Create pipes/tanks, the distillery
  (distillate), the barrel (aging), bottling by the kettle/spout/Create spout, and loss-of-quality paths.
- Tooltip of wort bottle, distillate and drink shows the line in grey italics. EN + RU lang.
- The muddy brew is signed too.

## Non-goals
- No anti-forgery, no protection of who may use the kettle.
- No signature from the person who adds ingredients or bottles.
- Stars, effects, recipes are unaffected by the mark.

## Constraints / consequences (accepted by the user)
- The mark lives on the stack, so wort/drinks with different marks (brewer or day) do not stack in
  inventories and do not merge in one tank — same as stars already behave.
- Kettles placed before the update have no owner: they work, drinks get no mark.
- Old drinks have no mark and show no line.

## Approaches
- **A (picked): kettle owner.** Works with full automation; "my brewery, my drinks".
- B: last player who added an ingredient by hand — no mark under full automation.
- C: player who bottles — signs the bottler, not the brewer; dispensers/spouts sign nothing.

## Decided
- Separate component `brewery:brewer` (record `BrewerMark(String name, long day)`), not new fields in
  `DrinkData`, so every existing `new DrinkData(...)` site keeps working; stages copy the mark explicitly.
- Day is the day the kettle finished brewing, stored once; later stages keep it.
- Owner is set in the kettle block's `setPlacedBy` when the placer is a player.

## Left to the builder
- Where exactly each stage copies the mark (distillery stores the input mark in NBT next to
  `InputStars`; barrel keeps it per batch).
- Tests: unit test for the tooltip line format; game tests — a player-placed kettle signs its brew;
  the mark survives distillery, barrel and bottling; an owner-less kettle signs nothing.
