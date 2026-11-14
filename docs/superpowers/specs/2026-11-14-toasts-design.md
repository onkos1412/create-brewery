# Toasts and treating — design

## Intent
Drinking in company should be better and more fun than drinking alone, and treating others to your own drinks
should be an honour. For taverns on servers, together with the keg and mugs.

## Goals
- **Clinking (approach A, picked):** a player holding a drink (bottle or filled mug) right-clicks another player
  who also holds a drink in the main hand → glass clink sound and particles over both; both get the
  **toast** marker: a custom mob effect `brewery:toast` (beneficial, visible icon), duration = toast window
  (default 10 s). One drink can be raised with several players in a row; clinking again refreshes the window.
- **Toast bonus:** drinking while the toast marker is on: this drink's own effects last **50 % longer**
  (factor 1.5 on top of the existing star factor and `effectMultiplier`) and the hangover debt from this drink is
  **halved** (rounded down). The marker is removed by that drink. The muddy brew and spoiled drinks get no bonus
  (the marker is still consumed).
- **Treating:** when a player drinks a drink whose `BrewerMark` has an owner UUID different from the drinker,
  the owner is credited with that drinker (per brewer: set of distinct drinker UUIDs, world SavedData
  `brewery_hospitality`).
- **Advancements** (children in the existing tab, `minecraft:impossible` criteria awarded from code):
  - `toast` — «За здоровье!» / "Cheers!": drink with the toast bonus for the first time.
  - `treat` — «Угощаю!» / "On the house!": someone else drank your drink.
  - `life_of_the_party` — «Душа компании» / "Life of the party": drinks of yours drunk by 5 distinct players.
  - If the brewer is offline, pending awards are stored in the same SavedData and granted at their next login.
- **Server config** (`drinks` section): `toastBonus` (boolean, default true; false → clinking still works and
  counts the advancement, but no duration/hangover bonus), `toastSeconds` (int 3–60, default 10).
- EN + RU lang; effect icon via `tools/gen_art.py` (two clinking mugs).

## Non-goals
- Toasts with mobs or villagers; bonus growing with group size.

## Approaches
- Trigger: **A (picked) right-click clinking** — deliberate and visible; B automatic when drinking near each other —
  accidental and invisible.
- Bonus: **picked** longer effects + softer hangover; rejected: a generic "feast" effect (same for every drink),
  scaling with group size (complex).

## Left to the builder
- Particle choice, sound choice, exact effect colour.
- Where the clink handler lives (an `EntityInteract` event handler is expected).
