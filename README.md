# Villager Simulator

A NeoForge 1.21.1 mod: a village simulation engine aiming at a million villagers, with daily lives that keep going when you walk away.

- [docs/DESIGN.md](docs/DESIGN.md): what the game is
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how it's built
- [docs/ROADMAP.md](docs/ROADMAP.md): the phases. **Phase 0 (A Living Hamlet) is implemented.**

## Project layout

| Project | What it is |
|---|---|
| `sim-api` | Public, Minecraft-free contracts: entities, components, tasks, events, registries, activities, views, modules |
| `sim-core` | The engine: storage, event scheduler, runtime thread, SQLite persistence |
| `sim-content` | Base game modules (needs, buildings, villages, plans), built only on `sim-api` |
| `sim-harness` | Headless scenario DSL; scenario tests run in virtual time |
| `neoforge` | The mod: bridge, villager puppets, blueprints, `/vs` commands, GameTests |
| `tools/blueprints` | Structure Lab scripts that generate the blueprints and their building-type JSON |

## Build and test

```bash
./gradlew build
```

Runs the unit and scenario tests (headless, seconds) and builds `neoforge/build/libs/villagersimulator-<version>.jar`.

```bash
./gradlew :neoforge:runGameTestServer
```

Runs the GameTests. Pick namespaces with `-PvsGameTestNamespaces=villagersimulator_bridge` (or `villagersimulator_time`).

```bash
./gradlew checkDependencyRules
```

Checks the project dependency rules from ARCHITECTURE §4.

Regenerate blueprints after editing `tools/blueprints/phase0.py`:

```bash
D:/MinecraftMods/MinecraftStructureInjector/.venv/Scripts/python.exe tools/blueprints/phase0.py
```

## Play Phase 0

```bash
./gradlew :neoforge:runClient
```

In a creative overworld (cheats on):

- `/vs village spawn [villagers] [name]` founds a hamlet around you: a well, a bakery and houses, with 8 villagers by default. They sleep, eat at the bakery, bake, and relax at the well on a daily schedule.
- `/vs inspect [villager]` shows the nearest villager's needs, plan and recent events. Right-clicking a building's anchor block shows the building and its stock.
- `/vs village list` lists villages.
- `/vs time warp <1d|6h|30m|200t>` runs the sim ahead; `/vs time status` shows the clock.
- `/vs tier force all <t0|t2|auto>` forces tiers, for debugging.
- `/vs save` saves the sim now; it also saves with the world.

Walk more than 48 blocks away and the villagers become abstract (T2) while their days carry on. Come back and they're where their schedule says. The sim is saved in `<world>/villagersimulator/sim.db`.

**Config** (`config/villagersimulator-common.toml`): `sim.debugTimeScale` speeds up sim time for playtesting; `tiers.t0Radius` sets the embodiment distance.

**Hot-swap in dev:** `./gradlew :neoforge:runClient -Pvs_hotswap=true` runs on a JetBrains Runtime with enhanced class redefinition.
