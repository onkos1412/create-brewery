# Port to 1.20.1 Fabric — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The whole mod builds, runs and passes all its tests on Minecraft 1.20.1 Fabric with Create Fabric 6.0.7.

**Architecture:** Strangler port from the finished Forge 1.20.1 branch (same Minecraft, so NBT accessors, data folders and vanilla API are done). Task 1 switches the build to Fabric Loom and parks every source file that touches Forge under `port/todo/` (outside the source sets) so the build is green from the first commit. Each later task ports one slice back and brings its unit and game tests with it. When `port/todo/` is empty, the port is complete.

**Tech Stack:** Minecraft 1.20.1, Fabric Loom (Gradle 8.x), Java 17 toolchain, official Mojang mappings, Fabric Loader 0.17.2, Fabric API 0.92.6+1.20.1, Create Fabric `com.simibubi.create:create-fabric-1.20.1:6.0.7.0+mc1.20.1-build.1716` (maven `https://mvn.devos.one/snapshots` and `/releases`), and exactly the versions its POM pins: Porting Lib 2.3.10+1.20.1, Registrate-Fabric 1.3.79-MC1.20.1, Flywheel-Fabric 1.0.0-217, Ponder-Fabric 1.0.80, Forge Config API Port 8.0.0. JEI 1.20.1 Fabric 15.20.0.106 (optional), Mod Menu for 1.20.1 (optional), JUnit 5, Fabric GameTest API.

**Spec:** `docs/superpowers/specs/2026-11-14-port-fabric-1.20.1-design.md` (behaviour reference: branch `mc1.20.1-forge` at 46560df, worktree `.worktrees/create-brewery-forge`).

## Global Constraints

- Work only in worktree `C:/Users/kostya/Documents/Проект по умолчанию/.worktrees/create-brewery-fabric`, branch `mc1.20.1-fabric`. Never touch the other worktrees.
- Mod id `brewery`, package `com.kostya.createbrewery`, assets, lang, art pipeline, numbers and rules stay identical to the Forge branch. "Same behaviour" = what the player sees; rewriting Forge-specific code against Fabric/Porting Lib is fine.
- No new mandatory dependency beyond Fabric API and what Create Fabric brings (Porting Lib, Forge Config API Port, Ponder, Flywheel, Registrate). No Cardinal Components, no Architectury. JEI and Mod Menu optional.
- Fluid volumes: logic and config keep mB; tanks store droplets internally; conversion factor exactly 81 droplets per mB (`FluidUnits.toDroplets(int mb) -> long`, `FluidUnits.toMb(long droplets) -> long` floor). Kettle tank 4000 mB, wort per brew/bottle 1000 mB, muddy 1000 mB, distillate 1000 mB — never 1/3-bucket bottles.
- Server config: same `ForgeConfigSpec` through Forge Config API Port, same file name `brewery-server.toml`, same keys, defaults and ranges.
- Hooks: Fabric API callbacks first, then Porting Lib events, then a targeted mixin (`brewery.mixins.json`, refmap by Loom).
- Version `0.2.0+mc1.20.1-fabric`, `archivesName = "create-brewery"`; `fabric.mod.json` with the metadata of the Forge `mods.toml` (name, description, authors, license, icon, contact URLs), environment `*`, depends `minecraft ~1.20.1`, `fabricloader >=0.15`, `fabric-api *`, `create >=6.0.7 <6.1` (verify the range matches Create Fabric's version string `6.0.7.0+mc1.20.1-build.1716`), suggests `jei`, `modmenu`.
- Every task ends with `./gradlew build` (unit tests) and, from Task 2 on, the game-test run green, and a commit with trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Java builds use `JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot"` to run Gradle (toolchain downloads 17).
- Game tests keep their names and assertions; only setup/API calls change. Tests run locally only.
- Untracked `docs/superpowers/{plans,specs}/2026-10-*` files are not part of this branch.

## Review Focus

1. Foreign Create Fabric pipes/spouts/basins inserting or extracting arbitrary droplet amounts (e.g. 1 droplet, 27000-droplet potion bottles) into kettle, distillery and keg: no loss, duplication or rounding drift; a full brew still yields exactly the Forge results. Test in Task 3: `pipeDropletsRoundTrip` — insert 81000 droplets in 3 uneven chunks via the Transfer API, extract all, totals equal and wort NBT intact.
2. Transfer API transactions: every `insert`/`extract` honours the `Transaction` (aborted transaction leaves tanks and inventories unchanged; Create simulates with aborted transactions all the time). Test in Task 3: `abortedTransactionChangesNothing` for kettle, distillery and keg.
3. Fluid NBT (drink data, brewer mark) carried by `FluidVariant` through pipes, spouts and bottling; variants with different NBT never merge in one tank. Covered by the ported mark-flow tests plus Task 3 `differentWortsNeverMerge`.
4. Client classes never loaded on the dedicated server: client code only behind the `client` entrypoint; `@Environment(EnvType.CLIENT)` where needed. Task 6 verifies by running the game-test server (dedicated) with all content.
5. Drunk player data survives relog and follows Forge's clone rules (kept on non-death clone e.g. End return, reset on death), and stays synced to the client after respawn/dimension change. Ported tests `drunkDataSurvivesRelog`, `drunkDataFollowsCloneRules`, `deathSobersCompletely` in Task 4.

---

### Task 1: Build on Fabric, park the Forge code

**Files:** `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle/wrapper/*` if needed, `src/main/resources/fabric.mod.json` (replaces `META-INF/mods.toml`), `src/main/resources/brewery.mixins.json` (empty mixin list for now), `src/main/java/com/kostya/createbrewery/CreateBrewery.java` (`ModInitializer`), `port/todo/**`, `port/README.md`.

- [ ] `git mv` every main/test Java file that imports `net.minecraftforge.*` (or depends on one that does) to `port/todo/java/main|test/...`; pure-Java classes and their unit tests stay. `port/README.md` says what the folder is.
- [ ] Rewrite the build for Fabric Loom with the Tech Stack versions; repositories: devos snapshots + releases, tterrag (`maven.tterrag.com`), Fabric, Modrinth maven for Mod Menu if used, JEI's maven (with the same BlameJared timeout workaround the Forge build has). Keep the existing `runUnitTests` JUnit console setup. Add Loom run `gametest` (server, `-Dfabric-api.gametest`, `-Dfabric-api.gametest.report-file=build/gametest.xml`) and a `runGametest` task.
- [ ] `fabric.mod.json` per Global Constraints; `CreateBrewery implements ModInitializer` that logs and does nothing else yet.
- [ ] Add trivial game test `buildBoots` (class registered via `fabric-gametest` entrypoint) if the runner refuses zero tests.
- [ ] `./gradlew build` green; `./gradlew runGametest` passes. Commit `build: switch to 1.20.1 Fabric and park Forge code for porting`.

### Task 2: Core content — units, data, registries, items, config

**Interfaces — Produces:** `com.kostya.createbrewery.FluidUnits { static final long DROPLETS_PER_MB = 81; static long toDroplets(int mb); static long toMb(long droplets); }`; registries as plain `Registry.register` / Registrate-Fabric holders with the same accessor names the Forge code uses (`ModItems.DRINK_BOTTLE.get()`, `ModBlocks.KETTLE_ITEM.get()` …) so later slices port with minimal edits; `BreweryConfig` via Forge Config API Port (`ForgeConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.SERVER, SPEC)`).

- [ ] Unit test `FluidUnitsTest`: `toDroplets(1000) == 81000`, `toDroplets(4000) == 324000`, `toMb(81000) == 1000`, `toMb(80999) == 999`, and round trip of every volume constant the mod uses (4000, 1000).
- [ ] Port back `registry/**` (items, effects incl. `toast`, fluids with Fabric fluid rendering left to Task 6), `item/**` without screens/network, `recipe/**`, `quality/**`, `config/**`, `advancement/**`, creative tab, drink effects on drinking (Fabric: `Item.finishUsingItem` already vanilla), with their unit tests and the game tests that only need items/recipes, including `allRecipesAndAdvancementsLoad` and `drinkDataLivesInStackNbt`.
- [ ] Game tests: Fabric `fabric-gametest` entrypoint class(es); template `brewery:empty` from the existing `data/brewery/structures/empty.nbt` (keep or move as the runner needs; test names unchanged).
- [ ] Build + game tests green. Commit `port: drinks, recipes, items and config on Fabric`.

### Task 3: Machines and the Transfer API

Port back kettle, distillery (+ top block), barrel, spout, keg, menus (Fabric `ExtendedScreenHandlerType` or Porting Lib menu extra data), their logic, Create kinetics/goggles integration and every machine game test (kettle, distillery, barrel, spout, keg, bottling, mark-flow, contraption).

**Interfaces — Produces:** fluid storage for each block entity registered with `FluidStorage.SIDED.registerForBlockEntity(...)` with the Forge side rules: keg DOWN = extract only (empties), every other side and `null` = insert only; kettle, distillery, barrel, spout ignore side; `DistilleryTopBlockEntity` delegates to the still below. Glass-bottle wort/drink handler via `FluidStorage.ITEM` (or Porting Lib's equivalent) matching the Forge `AttachCapabilitiesEvent<ItemStack>` handler. Item storages via `ItemStorage.SIDED` where Forge exposed `IItemHandler`.

- [ ] Write game tests `pipeDropletsRoundTrip`, `abortedTransactionChangesNothing`, `differentWortsNeverMerge` (Review Focus 1–3) next to the ported machine tests; they fail before the storages exist.
- [ ] Port machines: tanks store droplets (Porting Lib `FluidTank` or `SingleVariantStorage<FluidVariant>` with snapshot participants), capacities from `FluidUnits.toDroplets`; drink data and brewer mark ride in the `FluidVariant` NBT exactly as in Forge `FluidStack` NBT; `FakePlayer` checks become Porting Lib/Fabric fake-player checks (`instanceof FakePlayer` from fabric-api `fabric-events-interaction`… or Porting Lib) with the same meaning.
- [ ] Build + all game tests green. Commit `port: brewing machines on Fabric`.

### Task 4: Drunkenness and effects

Port back `drunk/**` (levels, speech via chat callback, stagger, vomit, hangover, pass-out, death sobering), the toast effect, and their tests.

**Interfaces — Produces:** `DrunkData.of(Player) -> DrunkData` backed by player NBT (mixin on `Player#addAdditionalSaveData/readAdditionalSaveData` under key `brewery:drunk`, or Porting Lib persistent data); saved fields level, knockout, hangover debt; stagger position and vomit cooldown transient. Clone rules via `ServerPlayerEvents.COPY_FROM` (copy when `alive`, reset on death). Sync packet on `ServerPlayConnectionEvents.JOIN`, `ServerPlayerEvents.AFTER_RESPAWN`, `ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD`.

- [ ] Port with tests incl. `drunkDataSurvivesRelog`, `drunkDataFollowsCloneRules`, `deathSobersCompletely`. Build + game tests green. Commit `port: drunkenness on Fabric`.

### Task 5: Journal, brewer's book, network, toasts, treating

Port back `journal/**`, `network/**` (Fabric networking: `ServerPlayNetworking`/`ClientPlayNetworking` with ids `brewery:drunk_sync`, `brewery:open_book`; stranger gets the empty book payload), book binding, `social/**` (clinking via `UseEntityCallback`, toast bonus, hospitality SavedData, login awards on JOIN) and their tests.

- [ ] Port, build, game tests green. Commit `port: brewer's book, toasts and treating on Fabric`.

### Task 6: Client, Ponder, JEI, Mod Menu — port complete

Port back `client/**` via `ClientModInitializer` (`client` entrypoint): screens (`MenuScreens.register`), distillery renderer/Flywheel visual, item colours (`ColorProviderRegistry`), fluid render handlers (`FluidRenderHandlerRegistry`), drunk camera/input effects (Porting Lib client events or mixins), book screen, Ponder plugin (`PonderIndex.addPlugin`), JEI plugin if any, config screen via Mod Menu `ModMenuApi` entrypoint returning Catnip `BaseConfigScreen` (optional dependency). `port/todo/` must be empty and is deleted.

- [ ] Port, build, all unit and game tests green (counts: 137 unit + Task-2 `FluidUnitsTest` cases, 144 game + the 3 new Task-3 tests — any other difference explained in the report). `./gradlew build` jar `build/libs/create-brewery-0.2.0+mc1.20.1-fabric.jar` contains `fabric.mod.json`, refmap and no Forge classes. Commit `port: client, Ponder and JEI on Fabric; port complete`.
