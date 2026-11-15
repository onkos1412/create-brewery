<center><img alt="Create Brewery" width="100%" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/banner.png"></center>

<br>

<center>
<img alt="NeoForge" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/neoforge_vector.svg">
<img alt="Forge" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg">
<img alt="Fabric" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg">
<a href="https://github.com/onkos1412/create-brewery"><img alt="GitHub" height="48" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg"></a>
</center>

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![About](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_about.png)

**Create Brewery** brings brewing, distilling and aging to [Create](https://modrinth.com/mod/create), in the spirit of the BreweryX plugin.
Build a brewery out of kettles, stills and barrels, wire it up with Create pipes and funnels, and find the recipes yourself. A well-tuned line always gives **5★** — every mistake costs stars.

<center><img alt="A brewery line" width="100%" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_line.png"></center>

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Machines](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_machines.png)

| Machine | What it does | What it needs |
|---|---|---|
| **Brewing Kettle** | Water + ingredients → wort | Fire below. Exact heating gives 5★, overheating 3★ |
| **Distillery** | Wort → distillate | Fire below and a shaft into the back port (4 SU per RPM). Two blocks tall |
| **Aging Barrel** | Distillate or young drink → aged drink | Wood in its slot and in-game days. Barrels side by side work as one, up to 384 bottles |
| **Bottling Spout** | Fills bottles from a neighbouring machine | A bottle in hand, or bottles through a funnel |
| **Keg** | Holds 8 portions of one drink and pours them into wooden mugs | Fill it by hand or with funnels; empties come out of the bottom |

Everything connects with Create pipes and funnels. Hold the **Ponder** key over any machine for an animated guide.

| <img alt="Brewing Kettle screen" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_kettle.png" width="400"> | <img alt="Ponder guide for the kettle" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_ponder.png" width="400"> |
| --- | --- |

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Drinks](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_drinks.png)

Beer, stout, kvass, cider, mead, wine, whiskey, vodka, rum, calvados, tequila, pulque, absinthe, firefly liqueur and more — each with its own vessel and effects.

**Nobody tells you the recipes.** The kettle brews any mix, and the closer it is to the real recipe, the more stars it gets. Still speed, aging time and barrel wood are found by feel too. Quality shows in the bottle tooltip, in the machine screens and in Create's goggles.

| <img alt="Signed drinks in the inventory" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_bottles.png" width="400"> | <img alt="Aging Barrel screen" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_barrel.png" width="400"> |
| --- | --- |

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Being drunk](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_drunk.png)

Every drink adds drunkenness by its strength.

- The camera sways, and chat speech starts to slur.
- Walking veers off course; a heavy drinker may throw up or pass out.
- Bad drinks leave a **hangover** once you sober up — a bottle of water or milk cures it.

Everything can be tuned or turned off in the server config.

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Brewers & friends](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_friends.png)

- **Brewer's signature** — every drink shows who brewed it and on which day.
- **Brewer's book** — a personal diary of your best results for every drink. Only its owner can read it; anyone else sees scribbles.
- **Toasts** — clink drinks with a friend (right-click): the next sip's effects last 50% longer with half the hangover.
- **Treating** — brewers earn advancements when other players drink their signed drinks.

<center><img alt="Brewer's book" width="70%" src="https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/shot_book.png"></center>

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Data packs](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_datapacks.png)

Recipes are data-driven: add your own drinks with stages, alcohol, still speed, aging days, wood and effects through a data pack.

![Server config](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_config.png)

`brewery-server.toml` (also shown in the in-game config screen): staggering, vomiting, hangover, pass-out threshold, sober-up speed, drink effect multiplier, toast bonus.
On NeoForge and Forge it lives in the world's `serverconfig` folder, on Fabric in `config/`.

![divider](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/divider.png)

![Requirements](https://raw.githubusercontent.com/onkos1412/create-brewery/main/docs/modrinth/img/h_requirements.png)

| Loader | Minecraft | Needs | Optional |
|---|---|---|---|
| NeoForge 21.1.235+ | 1.21.1 | [Create](https://modrinth.com/mod/create) 6.0.10+ | [JEI](https://modrinth.com/mod/jei) |
| Forge 47.3.30+ | 1.20.1 | [Create](https://modrinth.com/mod/create) 6.0.8+ | [JEI](https://modrinth.com/mod/jei) |
| Fabric Loader 0.17.2+ | 1.20.1 | [Fabric API](https://modrinth.com/mod/fabric-api), [Create Fabric](https://modrinth.com/mod/create-fabric) 6.0.7+ | [JEI](https://modrinth.com/mod/jei), [Mod Menu](https://modrinth.com/mod/modmenu) |

Install on **both the client and the server**. English and Russian translations included.

<center>

**Found a bug or have an idea?** Open an issue on [GitHub](https://github.com/onkos1412/create-brewery/issues). Licensed under MIT.

</center>
