# Brewer's Book Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bindable Brewer's book that shows its owner their best result per drink and stage, and shows strangers only scribbles.

**Architecture:** A pure-Java journal model (`journal/`) with "replace only when strictly better" rules and codecs; a world `SavedData` keyed by player UUID; hooks in kettle, distillery and barrel credit the owner named in the `BrewerMark` (which gains the owner's UUID); a book item that binds on first use and sends a server→client payload (empty for strangers); a client screen that renders the spread.

**Tech Stack:** NeoForge 1.21.1, Create 6.0.10, Java 21, JUnit 5, GameTests, Python+Pillow art generator.

**Spec:** `docs/superpowers/specs/2026-11-14-brewer-book-design.md`

## Global Constraints

- `JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot"`; `./gradlew build` (unit, currently 118) and `./gradlew runGameTestServer` (game, currently 98) must pass before each commit.
- Art only through `tools/gen_art.py` (run `python tools/gen_art.py`); never hand-edit generated PNG/JSON it writes.
- Lang edited with Python json (`ensure_ascii=False, indent=2`), keep line endings; never PowerShell `Set-Content`.
- Only real players (not `FakePlayer`) bind books. Only the kettle owner (via the mark's `owner` UUID) is credited; a mark without `owner` credits nobody; the muddy brew and spoiled distillates record nothing.
- A stage entry is replaced only when new stars are strictly higher.
- Strangers' payload carries no journal data, no count, no drink ids.
- Commit trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Don't touch untracked `docs/superpowers/{plans,specs}/2026-10-*`.

## Review Focus

1. Old `BrewerMark` data without `owner` (drinks made before this update) loads, shows the name, credits nobody — Task 2 test.
2. A drink whose stage doesn't exist (beer: no still/barrel) — page lists only its real stages — Task 4 (screen) uses the payload's `hasStill/hasBarrel`; Task 3 test asserts the flags for beer.
3. Two players: A's kettle brews, B opens A's book → B's payload is unreadable and empty; B opens own fresh book → own (empty) journal — Task 3 test.
4. Journal survives a save/reload (SavedData codec) — Task 1 unit round-trip + Task 2 game test via `setDirty`/reload of the data.
5. A worse later result never overwrites a better one, per stage independently (a 2-star barrel run doesn't erase a 5-star kettle record) — Task 1 unit tests.

---

### Task 1: Journal model and saved data

**Files:**
- Create: `src/main/java/com/kostya/createbrewery/journal/KettleRecord.java`, `StillRecord.java`, `BarrelRecord.java`, `DrinkJournal.java`, `BreweryJournalData.java`
- Test: `src/test/java/com/kostya/createbrewery/journal/DrinkJournalTest.java`

**Interfaces (Produces):**
- `record KettleRecord(Map<String,Integer> mix, int heat, int stars)` — mix: item id → count (sorted map, `TreeMap`).
- `record StillRecord(int rpm, int stars)`; `record BarrelRecord(String wood, int days, int stars)`.
- `record DrinkJournal(Optional<KettleRecord> kettle, Optional<StillRecord> still, Optional<BarrelRecord> barrel)` with `static final DrinkJournal EMPTY`, and `DrinkJournal withKettle(KettleRecord r)`, `withStill(StillRecord r)`, `withBarrel(BarrelRecord r)` — each returns `this` unless the slot is empty or `r.stars() >` existing stars.
- Each record has `CODEC` and `STREAM_CODEC` (`StreamCodec<ByteBuf, …>`); `DrinkJournal.CODEC`/`STREAM_CODEC`.
- `KettleRecord.mixOf(List<String> ids)` → counts map (the kettle's `inputIds()` list has one id per item).
- `class BreweryJournalData extends SavedData` (file name `brewery_journal`): `static BreweryJournalData get(MinecraftServer server)` (overworld data storage, `SavedData.Factory`); `Map<String, DrinkJournal> journalOf(UUID player)` (unmodifiable, empty map if none); `void recordKettle(UUID, String drinkId, KettleRecord)`, `recordStill(UUID, String drinkId, StillRecord)`, `recordBarrel(UUID, String drinkId, BarrelRecord)` — apply `with…`, `setDirty()` only when changed. NBT via the codecs.

- [ ] **Step 1: Failing unit tests** in `DrinkJournalTest`:
  - `firstRecordIsKept`: `EMPTY.withKettle(k(3))` has kettle with stars 3.
  - `betterReplacesWorseDoesNot`: `withKettle(k(3)).withKettle(k(4))` → 4; `…withKettle(k(4)).withKettle(k(2))` → 4; equal stars keep the older record (compare `mix`).
  - `stagesAreIndependent`: kettle 5 then `withBarrel(b(2))` → kettle still 5, barrel 2.
  - `mixOfCounts`: `mixOf(List.of("minecraft:wheat","minecraft:wheat","minecraft:sugar"))` → `{minecraft:sugar=1, minecraft:wheat=2}`.
  - `codecRoundTrip`: full `DrinkJournal` with all three stages through `JsonOps` equals itself; `EMPTY` too.
- [ ] **Step 2:** `./gradlew build` → fails to compile.
- [ ] **Step 3:** Implement the records and `BreweryJournalData`.
- [ ] **Step 4:** `./gradlew build` → 123 unit tests pass.
- [ ] **Step 5:** Commit `feat: brewing journal model and saved data`.

### Task 2: Owner UUID on the mark and crediting hooks

**Files:**
- Modify: `BrewerMark.java`, `block/KettleBlockEntity.java`, `block/DistilleryBlockEntity.java`, `block/BarrelBlockEntity.java`
- Test: `src/test/java/com/kostya/createbrewery/BrewerMarkTest.java`, `gametest/BreweryGameTests.java`

**Interfaces:**
- Consumes: Task 1 `BreweryJournalData`, records, `KettleRecord.mixOf`.
- Produces: `record BrewerMark(String name, long day, Optional<UUID> owner)`; keep a 2-arg constructor `BrewerMark(String name, long day)` (owner empty) so existing code/tests compile. Codec: `owner` optional (`UUIDUtil.CODEC.optionalFieldOf("owner")`); stream codec with `ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC)`.

Hooks (server side only):
- Kettle, where a brew finishes: stamp `owner = ownerId`. Then, if `ownerId != null` and `brew.drink()` is not `DrinkProfile.MUDDY.id()`, `recordKettle(ownerId, brew.drink(), new KettleRecord(KettleRecord.mixOf(contents-at-finish), heatLevel, stars))` — `stars` = this brew's own stars (not the tank's min).
- Distillery, where a non-spoiled distillate is made: if `inputMark` has an owner → `recordStill(owner, wortDrink, new StillRecord(Math.round(Math.abs(rpm)), result.stars()))`.
- Barrel `finish`: if the batch's mark has an owner → `recordBarrel(owner, profile.id(), new BarrelRecord(woodId, (int) (getElapsedTicks() / AgingRules.TICKS_PER_DAY), result.stars()))`.

- [ ] **Step 1: Failing tests:**
  - Unit `BrewerMarkTest.oldMarkWithoutOwnerLoads`: parse JSON `{"name":"Kostya","day":7}` → owner empty; `codecRoundTripWithOwner` with a fixed UUID.
  - Game `ownersKettleRecordsDrink`: kettle with `setOwner(uuid,"Kostya")` brews (reuse an existing brew-to-finish setup) → `BreweryJournalData.get(server).journalOf(uuid)` has the drink with a kettle record whose stars equal the wort's stars and whose mix matches the ingredients.
  - Game `muddyBrewRecordsNothing` and `ownerlessKettleRecordsNothing`.
  - Game `distilleryCreditsMarkOwner`: wort with `new BrewerMark("Kostya", 7, Optional.of(uuid))` distilled → still record present; with a mark without owner → nothing.
  - Game `barrelCreditsMarkOwner`: aged batch with an owner mark → barrel record with the barrel's wood id.
  - Game `worseRunKeepsBestRecord`: pre-record a 5-star kettle record, brew a worse one → record still 5.
  Use unique player UUIDs per test (`UUID.randomUUID()`) so parallel tests don't share journal entries.
- [ ] **Step 2:** run → fail.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `./gradlew build` and `./gradlew runGameTestServer` pass (unit +2, game +6).
- [ ] **Step 5:** Commit `feat: kettle owner's results go into the brewing journal`.

### Task 3: Brewer's book item, binding and the open payload

**Files:**
- Create: `item/BrewerBookItem.java`, `item/BookOwner.java`, `network/OpenBrewerBookPayload.java`, `network/ModNetwork.java`
- Modify: `registry/ModItems.java`, `registry/ModDataComponents.java`, `registry/ModCreativeTabs.java`, `CreateBrewery.java` (register network), lang en/ru, `tools/gen_art.py` (item texture `brewer_book` + item model), `src/main/resources/data/brewery/recipe/brewer_book.json`
- Test: `gametest/BreweryGameTests.java`

**Interfaces:**
- Produces:
  - `record BookOwner(UUID id, String name)` with `CODEC`/`STREAM_CODEC`; component `ModDataComponents.BOOK_OWNER` (`book_owner`, persistent + synced).
  - `ModItems.BREWER_BOOK` (`brewer_book`, `stacksTo(1)`), class `BrewerBookItem`.
  - `record OpenBrewerBookPayload(boolean readable, String ownerName, List<Page> pages)` with `record Page(String drinkId, boolean hasStill, boolean hasBarrel, Optional<DrinkJournal> journal)`; `TYPE` id `brewery:open_brewer_book`; `STREAM_CODEC`.
  - `static OpenBrewerBookPayload BrewerBookItem.payloadFor(ServerPlayer reader, ItemStack book)` — binds first if unbound (real players only; returns the owner's view), owner → readable with one `Page` per distinct drink of all BREWING recipes in recipe-id order (flags from `DrinkProfile.canDistill()/canAge()`), stranger → `new OpenBrewerBookPayload(false, "", List.of())`.
  - `ModNetwork.register(RegisterPayloadHandlersEvent)` registers the payload `playToClient`; the client handler calls `client.BrewerBookClient.open(payload)` (Task 4 creates it — in this task add a stub class `client/BrewerBookClient` with `static void open(OpenBrewerBookPayload p)` that does nothing yet).
- `use`: server side → `PacketDistributor.sendToPlayer(player, payloadFor(player, stack))`; `sidedSuccess`.
- Tooltip: bound → `tooltip.brewery.book_owner` en "Brewer's book of %s" / ru "Книга пивовара %s"; unbound → `tooltip.brewery.book_unbound` en "Not yet signed" / ru "Ещё не подписана". Item name: en "Brewer's Book" / ru "Книга пивовара".
- Recipe: shapeless `minecraft:book` + `minecraft:glass_bottle` + `minecraft:wheat` → 1 book. Add to the mod's creative tab.
- Texture: 16×16 brown leather book with a brass clasp and a small amber bottle emblem, drawn in `gen_art.py` like the other item textures.

- [ ] **Step 1: Failing game tests:**
  - `bookBindsToFirstReader`: `payloadFor(playerA, unbound)` → book has `BOOK_OWNER` with A's UUID/name, payload `readable`.
  - `strangerGetsNothing`: A's book read by B → `readable == false`, `ownerName` empty, `pages` empty; book owner unchanged.
  - `fakePlayerDoesNotBind`: `FakePlayerFactory.getMinecraft(level)` → book stays unbound, payload unreadable.
  - `ownerSeesAllDrinksWithStageFlags`: owner payload has one page per distinct drink; beer page `hasStill == false && hasBarrel == false`; whiskey's base drink page `hasStill == true`; a drink recorded in the journal has `journal` present, others empty.
  Use `freshPlayer`-style real `ServerPlayer` helpers already in the test file.
- [ ] **Step 2:** run → fail.
- [ ] **Step 3:** Implement, generate art (`python tools/gen_art.py`), add lang and recipe.
- [ ] **Step 4:** build + game tests pass (game +4).
- [ ] **Step 5:** Commit `feat: brewer's book binds to its reader and sends the journal`.

### Task 4: Book screen

**Files:**
- Create/replace stub: `client/BrewerBookClient.java`, `client/BrewerBookScreen.java`
- Modify: `tools/gen_art.py` (GUI texture `textures/gui/brewer_book.png`), lang en/ru
- Test: `src/test/java/com/kostya/createbrewery/client/BrewerBookLayoutTest.java` (pure layout math only)

**Interfaces:**
- Consumes: `OpenBrewerBookPayload` (Task 3).
- `BrewerBookClient.open(p)` → `Minecraft.getInstance().setScreen(new BrewerBookScreen(p))`.
- `BrewerBookScreen extends Screen`: parchment two-page spread (GUI texture ~ 292×180 from gen_art: two parchment pages, leather border, brass corners, page-turn arrows).
  - Readable: spread 0 = title page: "Brewer's book of %s" + "Discovered %s of %s" (count pages with a journal). Then one spread per page: left page = drink name (`drink.brewery.<id>`), kettle record (item icons via `GuiGraphics.renderItem` with count text, heat name, stars via `StarLine`); right page = still (if `hasStill`) and barrel (if `hasBarrel`) records — missing record shows "—". Undiscovered page shows "???" only.
  - Unreadable: three spreads of scribbles: lines of random-looking text styled with font `minecraft:alt` (`Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"))`), plus "The handwriting is illegible…" / «Почерк неразборчив…» at top.
  - Turning: arrow buttons, mouse wheel, left/right keys; Esc closes; `isPauseScreen() == false`.
  - `static int spreadCount(OpenBrewerBookPayload p)` → readable: `1 + pages.size()`; unreadable: `3`.
- Lang keys (en / ru): `gui.brewery.book.title` "Brewer's book of %s"/«Книга пивовара %s», `gui.brewery.book.discovered` "Discovered %s of %s"/«Открыто %s из %s», `gui.brewery.book.unknown` "???", `gui.brewery.book.kettle` "Kettle"/«Котёл», `gui.brewery.book.still` "Still"/«Куб», `gui.brewery.book.barrel` "Barrel"/«Бочка», `gui.brewery.book.rpm` "%s RPM"/«%s об/мин», `gui.brewery.book.days` "%s days"/«%s дн.», `gui.brewery.book.none` "—", `gui.brewery.book.illegible` "The handwriting is illegible…"/«Почерк неразборчив…». Heat names and wood names: reuse existing lang keys if present, else add `gui.brewery.book.heat.<level>`.

- [ ] **Step 1: Failing unit test** `BrewerBookLayoutTest`: `spreadCount` for readable with 11 pages → 12; unreadable → 3.
- [ ] **Step 2:** run → fail.
- [ ] **Step 3:** Implement screen + art + lang.
- [ ] **Step 4:** build + game tests pass.
- [ ] **Step 5:** Commit `feat: brewer's book screen`.
- (Controller verifies the screen in the client with screenshots afterwards: owner view with records, stranger view.)
