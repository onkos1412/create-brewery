# Port to Minecraft 1.20.1 Fabric — design

## Intent
Publish Create Brewery on Modrinth for more players. Create 6 exists for 1.21.1 NeoForge (done), 1.20.1 Forge
(done: branch `mc1.20.1-forge`, commit 46560df) and 1.20.1 Fabric. This document covers **Fabric**, the last target
before Modrinth publishing prep.

## Goals
- Full feature parity with `mc1.20.1-forge` at 46560df (which itself equals `main` eb229a6 in behaviour): kettle,
  distillery (+ top block), barrel, spout, keg, mugs, all drinks and recipes, quality, drunkenness (sway, slur,
  stagger, vomit, pass-out, hangover), drink effects, brewer signature, brewer's book (journal, binding, exclusive
  payload, screen), toasts and treating, advancements, server config + config screen, Ponder scenes, JEI optional,
  art and lang unchanged.
- Same mod id `brewery`, package `com.kostya.createbrewery`, same assets, same behaviour and numbers as seen by the
  player (volumes in mB/bottles, ticks, stars, config values and their defaults/ranges).
- All unit tests (137) and all game tests (144 on Forge) ported and passing on Fabric's GameTest runner.
- Release jar `create-brewery-0.2.0+mc1.20.1-fabric.jar`, checked in a real 1.20.1 Fabric client (screenshots of
  machine screens, book, Ponder, world).

## Non-goals
- New features or bug fixes while porting (known shared bugs — JEI shows one drink bottle; kettle/still drain wort
  without comparing NBT — are fixed later on all branches at once).
- Quilt as a promised target; NeoForge 1.20.1.

## Anti-goals
- A new **mandatory** dependency beyond what Create Fabric already brings (Fabric API, Porting Lib, Forge Config API
  Port, Ponder, Flywheel, Registrate). No Cardinal Components, no Architectury API: players should need only Create.
- Any player-visible number changing because of droplet/mB conversion (e.g. a kettle holding 999.9 mB, a bottle
  pouring 26999 droplets, rounding drift when filling/draining repeatedly).

## Constraints (found in recon)
- **Create Fabric 6.0.7** — `com.simibubi.create:create-fabric-1.20.1:6.0.7.0+mc1.20.1-build.1716` from
  `https://mvn.devos.one/snapshots`. Its POM pins: Porting Lib 2.3.10+1.20.1 (modules base, transfer, networking,
  extensions, entity, …), Registrate-Fabric 1.3.79-MC1.20.1, Flywheel-Fabric 1.0.0-217, Ponder-Fabric 1.0.80,
  Forge Config API Port 8.0.0, Fabric Loader 0.17.2, Fabric API 0.92.6+1.20.1. Use exactly these.
- **Java 17**, Fabric Loom, official Mojang mappings (keeps code identical to the Forge branch's names).
- **Fluids are in droplets** (81000 per bucket, 27000 per bottle) in Create Fabric and the Fabric Transfer API.
- No Forge capabilities: item/fluid exposure goes through the Fabric Transfer API
  (`FluidStorage.SIDED` / `ItemStorage.SIDED` block lookups, `FluidStorage.ITEM` for the glass bottle), which Create
  Fabric's pipes, spouts and basins use.
- No Forge event bus: Fabric API callbacks, Porting Lib events, or mixins (Loom generates the refmap).

## Approaches
- **A. Separate branch `mc1.20.1-fabric` from `mc1.20.1-forge` (picked by the user).** Same Minecraft version, so
  NBT accessors, data folders, recipe formats and the 1.20.1 vanilla API are already done; only the loader layer
  changes. Proven strangler port with tests per slice. Cost: fixes are carried across three branches by hand.
- B. One shared module for Forge and Fabric 1.20.1 (multiloader/Architectury): one place for logic, but it means
  rebuilding the finished Forge branch, and Create's Forge/Fabric APIs differ (capabilities vs Transfer API), so
  much stays split. Rejected: long and risky now.

## Decisions
- Branch `mc1.20.1-fabric` from `mc1.20.1-forge` (46560df), own git worktree
  `.worktrees/create-brewery-fabric`. `main` and `mc1.20.1-forge` are not touched.
- Version `0.2.0+mc1.20.1-fabric`; `fabric.mod.json` with the same metadata as `mods.toml` (name, description,
  authors, license, icon, contact URLs), depends on `minecraft ~1.20.1`, `fabricloader`, `fabric-api`, `create`
  (6.0.7 and later 6.0.x), suggests `jei`. Environment `*` (both sides).
- **Strangler port** as on Forge: park not-yet-ported sources outside the source sets, bring slices back with their
  tests, branch builds green at every commit.
- **Units:** game logic and config keep mB (as now); conversion to droplets happens only at the Transfer API /
  Create Fabric boundary, with exact factor 81 per mB, so every value the player sees is unchanged. A unit test
  covers round-trips of all volumes the mod uses.
- **Server config:** keep `ForgeConfigSpec` via Forge Config API Port (already a Create dependency), same file name
  and keys as on Forge.
- **Player drunk data** (level, knockout, hangover debt saved; stagger position and vomit cooldown not): stored in
  the player's own NBT (via mixin on save/load or Porting Lib's persistent entity data), copied/reset on respawn
  with Fabric's `ServerPlayerEvents.COPY_FROM` following the same rules as Forge (kept on non-death clone, reset on
  death), synced to the client on login, respawn and dimension change.
- **Networking:** Fabric networking API (or Porting Lib networking if simpler) for the drunk sync and book payload;
  stranger still receives an empty book payload.
- **JEI:** optional, compile-only API + runtime in dev, as on Forge.

## Designer's calls after the builder check
- **"Same behaviour" means what the player sees, not the same code.** Where the Forge code uses Create Forge fluid
  classes (tanks, behaviours, `FluidStack`), rewriting them against Create Fabric / Porting Lib / Transfer API
  classes (or a thin mod-owned tank wrapper) is fine.
- **Tanks store droplets internally** (like Create Fabric itself), so foreign pipes moving any droplet amount work
  without clamping or rejection. Rules and config stay in mB and are converted with the exact factor 81 when
  building capacities and portions; displayed values are droplets / 81 (shown as mB, as now).
- **Volumes stay exactly as on Forge** (e.g. wort per bottle and distillate are 1000 mB = 81000 droplets, kettle
  tank 4000 mB), never Fabric's usual 1/3-bucket bottle, so portions and keg/barrel arithmetic match the other
  loaders. Vanilla water/potion bottles are Create Fabric's own business.
- **Saved-world NBT does not need to be compatible between loaders** (worlds don't move between Forge and Fabric).
  Own recipe types keep mB in their JSON (parsed by our code); any recipe JSON read by Create Fabric itself uses
  droplets.
- **Game tests run locally** via Loom's GameTest run (`runGametest`); no CI exists. The existing `.nbt` structures
  are reused unchanged; moving them or mapping template ids is fine.
- **Hooks:** prefer Fabric API callbacks, then Porting Lib events, then a mixin where neither exists (mixins are
  acceptable; keep them few and targeted to avoid conflicts with Create).
- **Config screen:** through Mod Menu as an optional dependency (most Fabric players have it); without Mod Menu the
  config file still works.
- **Modrinth metadata, changelog wording and upload are the next step**, not this port; this port only ships a
  correct `fabric.mod.json`.

## Left to the builder
- Loom and Gradle versions; exact Fabric API modules; Porting Lib vs pure Fabric API per subsystem.
- How each Forge API maps to Fabric (events, menus with extra data, block entity renderers, item colours, sound,
  effect hooks), test by test; mixins where no event exists.
- How game tests register on Fabric (`fabric-gametest` entrypoint, template ids for the existing structures under
  `data/brewery/structures/`); test names and assertions stay.
- Config screen wiring (Catnip `BaseConfigScreen` via Mod Menu if present, else none — Mod Menu stays optional).
- Order of porting (suggested as on Forge: build → core data/items/config → machines + Transfer API → drunkenness →
  journal/book/network/toasts → client/Ponder/JEI → client check → release jar).
