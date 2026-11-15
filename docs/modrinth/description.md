# Create Brewery

**Brewing, distilling and aging for [Create](https://modrinth.com/mod/create), in the spirit of the BreweryX plugin.**
Build a brewery line out of kettles, stills and barrels, wire it up with Create pipes and funnels, and find the recipes yourself. A well-tuned line always gives 5★; every mistake costs stars.

Available for **Minecraft 1.21.1 NeoForge**, **1.20.1 Forge** and **1.20.1 Fabric**. English and Russian translations included.

## Machines

| Machine | What it does | What it needs |
|---|---|---|
| **Brewing Kettle** | Water + ingredients → wort | Fire below. Exact heating gives 5★, overheating 3★ |
| **Distillery** | Wort → distillate | Fire below and a shaft into the back port (4 SU per RPM). Two blocks tall |
| **Aging Barrel** | Distillate or young drink → aged drink | Wood in its slot and in-game days. Neighbouring barrels facing the same way work as one, up to 384 bottles |
| **Bottling Spout** | Fills bottles from a neighbouring machine | A bottle in hand, or bottles through a funnel |
| **Keg** | Holds 8 portions of one drink and pours them into wooden mugs | Fill it by hand or with Create funnels; empties come out of the bottom |

Everything connects with Create pipes and funnels. Hold the Ponder key over any machine for an animated guide.

## Drinks

Beer, stout, kvass, cider, mead, wine, whiskey, vodka, rum, calvados, tequila, pulque, absinthe, firefly liqueur and more, each with its own vessel and effects.
**Nobody tells you the recipes:** the kettle brews any mix, and the closer it is to the real recipe, the more stars it gets. Still speed, aging time and barrel wood are found by feel too. Quality shows in the bottle tooltip, in the machine screens and in Create's goggles.

## Being drunk

Every drink adds drunkenness by its strength. The camera sways, chat speech starts to slur, walking veers off course, a heavy drinker may throw up or pass out. Bad drinks leave a hangover once you sober up; a bottle of water or milk cures it. Everything can be tuned or turned off in the server config.

## Brewers and friends

- **Brewer's signature:** every drink shows who brewed it and on which day.
- **Brewer's book:** a personal diary of your best results for every drink. Only its owner can read it; anyone else sees scribbles.
- **Toasts:** clink drinks with a friend (right-click) and the next sip's effects last 50% longer with half the hangover.
- **Treating:** brewers earn advancements when other players drink their signed drinks.

## Data packs

Recipes are data-driven: add your own drinks with stages, alcohol, still speed, aging days, wood and effects through a data pack.

## Server config

`brewery-server.toml` (also shown in the in-game config screen): staggering, vomiting, hangover, pass-out threshold, sober-up speed, drink effect multiplier, toast bonus.
On NeoForge and Forge it is in the world's `serverconfig` folder, on Fabric in `config/`.

## Requirements

| Loader | Minecraft | Needs | Optional |
|---|---|---|---|
| NeoForge 21.1.235+ | 1.21.1 | Create 6.0.10+ | JEI |
| Forge 47.3.30+ | 1.20.1 | Create 6.0.8+ | JEI |
| Fabric Loader 0.17.2+ | 1.20.1 | Fabric API, Create Fabric 6.0.7+ | JEI, Mod Menu (config screen) |

Install on both the client and the server.

Source code and issues: [GitHub](https://github.com/onkos1412/create-brewery). License: MIT.
