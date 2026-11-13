# Keg and mugs — design

## Intent
A tavern piece for servers: a small keg on the bar holds up to 8 portions of one drink, and anyone pours it into
wooden mugs. Quality (stars, alcohol) and the brewer's signature carry over to every mug.

## Goals
- **Keg** block `brewery:keg` with block entity. Small oak keg on a stand with a brass tap, facing the player when
  placed. Recipe (shaped): planks around, iron ingots as hoops, `create:fluid_valve` as the tap (builder may adjust
  the shape, not the ingredients). Holds up to **8 portions** of one drink.
- **Filling by hand:** use a drink bottle on the keg → the bottle is poured in (+1 portion), the player gets a glass
  bottle back (creative: bottle kept, as vanilla does).
- **Filling by automation:** the keg exposes an item handler: inserting a drink bottle (Create funnel / chute /
  hopper, from top or sides) adds a portion and stores the empty glass bottle in an internal slot (up to 8); bottles
  are extracted from below. Insertion is refused when the keg is full or the empties slot is full.
- **Only identical drinks:** a keg that holds portions accepts only bottles whose `DRINK_DATA` and `BREWER` components
  equal the stored ones (same drink, stars, alcohol, signature). An empty keg takes anything drinkable that is not
  a distillate (distillates aren't drinks).
- **Mug** item `brewery:mug` (empty, stack 16, recipe: planks in a cup shape). Use an empty mug on a keg with
  portions → one portion poured: the player receives a **filled mug** `brewery:drink_mug` (stack 1) carrying the
  same `DRINK_DATA` and `BREWER`.
- **Drinking from a mug** behaves exactly like drinking the bottle (effects, drunkenness, hangover, advancements);
  the empty mug comes back instead of a glass bottle.
- **Seeing the contents:** filled mug tooltip like a bottle's (drink, stars, signature). Keg: Create goggles tooltip
  (`IHaveGoggleInformation`) shows drink, stars, "5/8", signature; empty keg says "Empty". The keg model has a small
  side window whose liquid level (0–4 steps) follows portions (blockstate property `level`).
- Mug texture: wooden mug with a liquid layer tinted by the drink's colour (`DrinkProfile.color()`); empty mug
  without liquid. Art via `tools/gen_art.py`. EN + RU lang.

## Non-goals
- Placing mugs on tables as decoration; owning or locking a keg (anyone may pour — it's a tavern).
- Pouring a keg back into bottles; piping drinks as fluids.

## Approaches
- Vessel: **A (picked) wooden mug** — tavern feel, reusable; B: glass bottles in and out — simpler but a "bottle
  warehouse". One sensible way for the rest.

## Constraints
- Same build/test/art/lang rules as the rest of the mod; all unit and game tests pass.
- Mixing rule matches tanks: identical components only, so stars and signatures never blend.

## Left to the builder
- Model shapes, exact recipe shapes, where the goggles text sits.
- How `DrinkItem` is shared with the filled mug (e.g. a protected "empty container" hook).
