# port/

Sources from the 1.20.1 Forge build (branch `mc1.20.1-forge`) that are not yet ported to 1.20.1 Fabric.

- `todo/java/main/...` - former `src/main/java` files; `todo/java/test/...` - former `src/test/java` files.
- They sit outside every Gradle source set, so they are not compiled. Each port task moves a slice back
  into `src/main/java` / `src/test/java` with `git mv` (history kept) and adapts it to Fabric
  (Fabric API, Porting Lib, Create Fabric, the Transfer API).
- Pure-Java rules and logic classes, and their unit tests, never left `src/`.
- `todo/java/main/com/kostya/createbrewery/CreateBrewery.java` is the Forge mod class kept for reference
  (its registry wiring, config and network registration); the live `src/main/java` one grows as slices
  come back, and this copy is deleted once nothing in it is still missing.
- When `todo/` is empty the port is complete and this folder is deleted.

Plan: `docs/superpowers/plans/2026-11-14-port-fabric-1.20.1.md`.
