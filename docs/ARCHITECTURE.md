# Villager Simulator — Architecture

> Status: **Draft v1** (2026-09-29)
> Companion to [DESIGN.md](DESIGN.md), which describes *what* the game is. This document describes *how* it is built.
> Target: Minecraft 1.21.1, NeoForge 21.1.x, Java 21
> Mod ID: `villagersimulator` · Base package: `com.ewitulsk.villagersimulator`

Tags: **[DECIDED]** the user explicitly chose it · **[PROPOSED]** a recommendation agreed in direction · **[OPEN]** unresolved (see [§20](#20-open-questions)).

---

## Table of Contents
1. [Goals](#1-goals)
2. [Architecture Decision Log](#2-architecture-decision-log)
3. [Guiding Principles](#3-guiding-principles)
4. [Project Layout](#4-project-layout)
5. [Entity & Component Model](#5-entity--component-model)
6. [Time, Scheduling & Threading](#6-time-scheduling--threading)
7. [Communication: Events, Commands, Messages, Views](#7-communication-events-commands-messages-views)
8. [Extension Points](#8-extension-points)
9. [VS Expression Language](#9-vs-expression-language)
10. [KubeJS Integration](#10-kubejs-integration)
11. [Public Addon API](#11-public-addon-api)
12. [The Minecraft Bridge](#12-the-minecraft-bridge)
13. [Worldgen, Buildings & Reconciliation](#13-worldgen-buildings--reconciliation)
14. [Player Integration](#14-player-integration)
15. [Networking](#15-networking)
16. [Client: Rendering, Animation, UI](#16-client-rendering-animation-ui)
17. [Persistence](#17-persistence)
18. [Debug Tooling](#18-debug-tooling)
19. [Testing Strategy](#19-testing-strategy)
20. [Open Questions](#20-open-questions)
21. [Phase 1 Plan: Vertical Slice](#21-phase-1-plan-vertical-slice)

---

## 1. Goals

1. **Scale:** 1M simulated villagers ([DESIGN §4](DESIGN.md#4-simulation-architecture)) on one server.
2. **Extensibility:** new systems can be added without editing existing ones, by us, addon authors, and pack developers.
3. **In Minecraft from day one** [DECIDED]: the first milestone is a playable in-game slice, not a headless prototype.
4. **Minecraft never waits for the simulation.** Server TPS is protected at all times.
5. **Testable:** almost all logic can be verified headless in seconds.

---

## 2. Architecture Decision Log

| # | Decision | Status |
|---|---|---|
| A1 | The sim core has **no Minecraft imports**; Minecraft is a port into it | DECIDED |
| A2 | Build into Minecraft **from day one** (vertical slice); headless harness runs the same code alongside | DECIDED |
| A3 | **ECS** model: no `Villager` class; entities are handles, state lives in components | PROPOSED (agreed) |
| A4 | **Dogfood the API:** all base game features are modules built on the public API | PROPOSED (agreed) |
| A5 | **Discrete-event simulation**, partitioned into **district shards**, with windowed synchronisation | PROPOSED (agreed) |
| A6 | Villager behaviour is expressed as **Activities** with per-tier implementations | PROPOSED (agreed) |
| A7 | Modules communicate only through **events, commands, cross-shard messages and views** | PROPOSED (agreed) |
| A8 | **Data-driven registries** loaded from datapacks; Mojang `Codec` (DataFixerUpper) used in `sim-api` | PROPOSED (agreed) |
| A9 | Shared **condition & effect system**, reused by every feature | PROPOSED (agreed) |
| A10 | CK-style **stats & modifiers** for all tunable numbers | PROPOSED (agreed) |
| A11 | **Public addon API from the start**, built as separate artifacts with semver and CI compatibility checks | DECIDED |
| A12 | **KubeJS** is the primary scripting layer, as an **optional integration** (not a hard dependency) | DECIDED |
| A13 | A secondary layer: the **VS expression language**, pure and compiled, running on sim threads | DECIDED |
| A14 | **No full secondary script engine** for now; revisit only if a real need appears | DECIDED |
| A15 | Villager entities are **puppets** of sim state and are **never saved to chunks** | PROPOSED (agreed) |
| A16 | **Worldgen = reconciliation** of an already-complete village plan | PROPOSED (agreed) |
| A17 | Scenario tests: **Java DSL** headless + **KubeJS binding** in-game/GameTest | DECIDED |
| A18 | Animation: **GeckoLib** (crowd figures reuse the same GeckoLib assets through an instanced render path) | DECIDED |
| A19 | Persistence backend: **SQLite** | DECIDED |
| A20 | UI toolkit: **internal**, built on vanilla screens | DECIDED |
| A21 | Blueprint format: **vanilla `.nbt` + metadata JSON** | DECIDED |
| A22 | Vanilla villages & villagers: **coexist** | DECIDED |
| A23 | API artifact hosting: **none for now**; addons build against locally published artifacts | DECIDED |
| A24 | Expression language: **custom grammar** (Molang-style syntax), not an existing Molang library | DECIDED (delegated) |
| A25 | **Optional vanilla integrations** as separate switchable modules, in the priority order of [§13.5](#135-optional-vanilla-integrations) | DECIDED |

---

## 3. Guiding Principles

1. **The simulation knows nothing about Minecraft.** `sim-api`, `sim-core` and `sim-content` compile and run with no Minecraft on the classpath.
2. **Dogfood the extension API.** Needs, economy, crime, religion, etc. are modules built only against `sim-api`. *If we can't build a feature through the API, the API is missing something, and we fix the API.*
3. **Composition over inheritance.** Features add components and systems; they never edit a central class.
4. **Data for content, expressions for small logic, Java for new behaviour.** (See [§8.8](#88-the-extensibility-stack).)
5. **Modules talk through events and components, never direct calls.** Every module is removable and replaceable.
6. **Fast and flexible live side by side.** Hot data is primitive columns; cold or extension data may be objects. Both sit behind one component API.
7. **Deterministic and serialisable.** Same seed + same inputs = same state. Every stateful type has a versioned codec.
8. **Minecraft never blocks on the sim.** The server thread only enqueues commands and reads the latest views.

### The extensibility test
Every new feature must be addable as a **new module without editing existing modules**. Example ("Hospitals"):
1. Register a `Health` component (dense) and an `Illness` component (sparse).
2. Add a `BuildingType` JSON with advertisements ("Health +X, costs Y").
3. Register a `TreatPatient` Activity (abstract implementation first).
4. Add a stat modifier to `lifespan.multiplier` for villagers with hospital access.
5. Subscribe to `VillagerInjured` events from the war and crime modules.

If a feature fails this test, the API gets extended rather than the feature being hard-wired.

---

## 4. Project Layout

```
VillagerSimulator/
├── sim-api/            PUBLIC  · MC-free contracts: entities, components, events, commands,
│                                  registries, modules, conditions/effects, stats, activities,
│                                  expression API, WorldPort, scenario DSL interfaces
├── sim-core/           internal · engine: storage, shards, scheduler, event bus, threading,
│                                  expression compiler, persistence, registry freezing
├── sim-content/        internal · base game modules (each depends only on sim-api):
│   ├── lifecycle/   needs/        plans/       social/      buildings/
│   ├── economy/     governance/   crime/       religion/    education/
│   └── construction/ war/
├── sim-harness/        internal · headless runner, JMH benchmarks, scenario runner,
│                                  soak tests, FakeWorldPort
├── mod-api/            PUBLIC  · MC-side extension points: embodied behaviours, animation keys,
│                                  blueprint metadata, UI panels, client renderers hooks
├── neoforge/           internal · the mod: bootstrap, bridge, entities, worldgen, reconciliation,
│                                  networking, client, UI, commands, debug overlay
├── compat/kubejs/      internal · optional KubeJS integration (loaded only if KubeJS present)
├── integrations/vanilla/ internal · optional vanilla integration modules (§13.5), built on the public APIs only
└── examples/hospitals/ example  · reference addon built in CI against the public APIs only
```

**Dependency rules** (checked in CI):

```
neoforge ──▶ mod-api ──▶ sim-api ◀── sim-core
   │                        ▲           ▲
   ├──▶ sim-core ───────────┘           │
   ├──▶ sim-content ──▶ sim-api         │
   └──▶ compat/kubejs (optional)        │
sim-harness ──▶ sim-core, sim-content ──┘
examples/*, integrations/* ──▶ sim-api, mod-api  (only)
```

- `sim-content` must **never** depend on `sim-core` internals.
- Only `sim-api` and `mod-api` are public artifacts (versioned; publishable, not yet hosted).

**Package naming:**
| Module | Package |
|---|---|
| sim-api | `com.ewitulsk.villagersimulator.api.sim` |
| mod-api | `com.ewitulsk.villagersimulator.api.mod` |
| sim-core | `com.ewitulsk.villagersimulator.core` |
| sim-content | `com.ewitulsk.villagersimulator.content.<module>` |
| neoforge | `com.ewitulsk.villagersimulator.neoforge` |
| compat/kubejs | `com.ewitulsk.villagersimulator.compat.kubejs` |

---

## 5. Entity & Component Model

### 5.1 Entities
- **32-bit handles** made of an index plus a generation counter. Stale handles are detectable; IDs are recycled cheaply (~55 births/deaths per second at 1M, [DESIGN §5](DESIGN.md#5-time-aging--lifecycle)).
- **Everything is an entity:** villagers, households, firms, buildings, districts, villages, player agents, caravans, armies, faiths, offices.
- **Entities are typed by archetype tags** (e.g. `villager`, `firm`), not by class.

### 5.2 Components
Components are declared by modules at registration time, with a storage kind, a codec and a version:

| Kind | Storage | Use for |
|---|---|---|
| **Dense** | Structure-of-arrays primitive columns per shard | Hot numeric data: needs (value, rate, t0), location node, tier, household ID, wealth |
| **Sparse** | Object per entity in a sparse map per shard | Cold or variable data: memories, legal record, schemes, extension data |
| **Tag** | Bitset | Flags: `is_child`, `is_official`, `is_pinned` |

```java
public static final ComponentType<Hunger> HUNGER = r.denseComponent(
    id("needs:hunger"),
    Schema.of(FLOAT("value"), FLOAT("rate"), LONG("t0")),
    Hunger.CODEC, /*version*/ 1);
```

### 5.3 Storage & sharding
- **Storage is per shard** (one shard per district). Rows are local to the shard's thread: cache-friendly and lock-free.
- **A global directory** maps `entity → (shard, row)`. Moving between districts moves the row and updates the directory.
- **Queries** iterate the entities that have a set of components (`query(HUNGER, LOCATION).without(ASLEEP)`), per shard.
- **As built (Phase 5):** one shard per **village**, plus shard 0 for world-level entities (players). A village's districts share its shard; splitting a mega-city into district shards is deferred to Phase 40. Stores are shared structures that are safe for disjoint shards to write concurrently (a concurrent sparse map, dense columns reserved at entity creation); during a window a shard may read anything but writes to other shards' entities (and relationship edges between shards) are deferred to the boundary. Windows are 200 sim ticks. Per-shard event writers are merged at the boundary in `(time, shard)` order, so results are identical for any thread count.

### 5.4 Specialised stores
- **Relationship graph:** per-villager adjacency in packed `long[]`, each entry holding target handle + friendship + romance + bond flags. Weak ties decay out. It gets its own store because it's the main memory risk.
- **Event log & memories:** notable events are stored once as **shared event records** (actor, cause, witnesses, who knows). Villager memories hold *references* to records, and gossip copies references. This saves memory and is the foundation for intrigue ([DESIGN §19](DESIGN.md#19-macro--micro-compatibility-rules)).
- **Inventories/stockpiles:** ledger-style per firm/household, shared with the real containers at T0 ([§13.4](#134-physical-inventories)).

### 5.5 Hot-path rules
- No allocation per villager per event on hot paths.
- No boxed numbers in dense components.
- No script execution on hot paths (expressions are fine: compiled and allocation-free, [§9](#9-vs-expression-language)).
- Every system reports its time per window to the profiler.

---

## 6. Time, Scheduling & Threading

### 6.1 Clocks
- **Sim clock:** monotonic `long` (sim ticks), derived from game time. Never goes backwards.
- **Day clock** (schedules) and **life clock** (aging) are both derived from the sim clock ([DESIGN §5.1](DESIGN.md#51-two-clocks-proposed)).
- Sleeping through the night or `/time add` is just a larger window. `/time set` backwards shifts the day phase but never rewinds the sim.

### 6.2 Discrete-event simulation
- **Each shard has its own event queue** (calendar queue / timing wheel).
- **Event handlers** react to scheduled and published events (arrive at the bakery, shift ends, birth).
- **Periodic systems** run at fixed intervals over queries (market clearing each sim-hour, the T3 daily batch, aging).
- **Deterministic ordering:** `(time, priority, sequence number)`.

### 6.3 Windowed parallelism
```
window N:   [shard A] [shard B] [shard C] ... all run in parallel up to boundary T
boundary:   exchange cross-shard messages, apply commands, publish views
window N+1: ...
```
- Conservative parallel discrete-event simulation. The window length (default 1 sim-hour, configurable) is the lookahead.
- Cross-shard messages sent during window N are delivered at the start of window N+1.

### 6.4 Threads

| Thread | Owns | Talks to others via |
|---|---|---|
| **Server thread** (Minecraft) | Levels, entities, blocks, players | Enqueues **commands**; reads latest **views**; dispatches events to KubeJS |
| **Sim coordinator** | Window scheduling, boundaries, command application, view publishing | Command queue in, view buffers out |
| **Sim workers** (pool, configurable) | One shard at a time during a window | Cross-shard message queues |
| **Save thread** | Async serialisation of snapshots | Snapshot buffers |
| **Worldgen threads** (Minecraft) | Chunk generation | Read-only **plan snapshots** ([§13.2](#132-worldgen)) |

- Each server tick the bridge sets a **target sim time**; the coordinator runs windows up to it.
- **If the sim falls behind** it catches up and logs; Minecraft never waits.

### 6.5 Tiered execution: Activities
Everything a villager does is an **Activity** with tier-specific implementations:

```java
public interface Activity {
    /** T2/T3: resolve a whole span at once. Required. */
    ActivityOutcome simulateAbstract(ActivityContext ctx, long fromTime, long toTime);

    /** T1: optional finer stepping; default delegates to simulateAbstract. */
    default void stepLocal(ActivityContext ctx, long dt) { /* default */ }

    /** T0: embodied behaviour key resolved by the bridge; default = generic work animation. */
    default EmbodiedBehaviorKey embodied() { return EmbodiedBehaviorKey.GENERIC; }
}
```
- Only `simulateAbstract` is required, so a new Activity works at every tier immediately and just looks generic when observed up close.
- Embodied behaviours are implemented MC-side through `mod-api` ([§12.4](#124-puppet-entities)).

### 6.6 Plan-as-truth & venue tiers
The plan and venue-tier rules from [DESIGN §4.4–4.5](DESIGN.md#44-the-plan-is-the-source-of-truth) are enforced by the engine: promotion reads the villager's plan position at "now"; interactions resolve at the venue's tier.

---

## 7. Communication: Events, Commands, Messages, Views

| Channel | Direction | Semantics | Examples |
|---|---|---|---|
| **Events** | Published inside the sim; any module subscribes | **Facts**: something happened. Immutable. | `ActCommitted`, `VillagerBorn`, `PriceChanged`, `LawEnacted` |
| **Commands** | Outside → sim (bridge, UI, KubeJS, other modules) | **Requests**: validated, then applied at a window boundary | `PlayerGiveGift`, `ProposeLaw`, `CommissionBuilding`, `ForceTier` |
| **Cross-shard messages** | Shard → shard | Delivered at the next boundary | Villager crossing, appointment invite, gossip, caravan arrival |
| **Views** | Sim → server thread | **Read-only, double-buffered snapshots** | Embodiment state, district stats, town hall data |
| **Outbox** | Sim → server thread | Events flagged `external` for MC/KubeJS consumers | Chronicle entries, KubeJS-subscribed events |

- Events flagged `recordable` also go into the **event log** (actor, cause, witnesses, knowledge).
- **Modules never call each other directly.** Crime publishes `ActCommitted`; economy, social and governance react if they care.

---

## 8. Extension Points

### 8.1 Modules
```java
public interface SimModule {
    ModuleId id();
    Set<ModuleId> dependencies();          // load ordering; cycles are errors
    void register(SimRegistrar r);         // components, events, commands, systems, activities,
                                           // registries, codecs, expression functions,
                                           // condition/effect types, stats, config
}
```
- **In-game:** collected through `RegisterSimModulesEvent` on the NeoForge mod bus.
- **Headless:** found through `ServiceLoader`.
- Each module can be enabled or disabled in config; dependents are disabled with a clear log message.

### 8.2 Data-driven registries
Typed registries: `NeedType`, `Trait`, `Value`, `Good`, `BuildingType`, `ActType`, `Culture`, `Faith`, `GovernmentType`, `LawTemplate`, `JobType`, `Skill`, `EducationLevel`, `ProcedureType`, `Activity`, `ConditionType`, `EffectType`, `ExpressionFunction`.
- Definitions load from **datapacks** in-game and resource folders headless:
  ```
  data/<ns>/villagersimulator/goods/bread.json
  data/<ns>/villagersimulator/cultures/mining_hold.json
  data/<ns>/villagersimulator/acts/trespass.json
  data/<ns>/villagersimulator/laws/night_trespass.json
  ```
- **Registries freeze at load** and assign numeric IDs; saves store string IDs plus a mapping table.
- **Codecs:** Mojang `Codec` / DataFixerUpper is a standalone library, so `sim-api` uses it without Minecraft. It's the same style as mod-side code.
- **Client-visible definitions** (culture names, goods, building types) use **NeoForge datapack registries** so they sync to clients; the bridge converts them to sim registries.
- **`/reload`** reloads data, expressions and KubeJS scripts. **Component schemas change only on restart.**

### 8.3 Conditions & effects
One predicate system and one action system, reused by laws, crime, advertisements, religious tenets, culture defaults, quests, petitions and festivals:

```json
{ "act": "villagersimulator:trespass",
  "conditions": [
    { "type": "in_district", "tag": "noble_quarter" },
    { "type": "time_between", "from": "22:00", "to": "06:00" },
    { "type": "expr", "value": "actor.class != 'noble' && !actor.has_office('guard')" } ],
  "punishment": { "type": "any_of", "options": [
    { "type": "fine", "amount": 20 }, { "type": "stocks", "days": 3 } ] } }
```
- Condition and effect **types are registrable**: a module adds a type and every system can use it.
- The `expr` condition type embeds a VS expression ([§9](#9-vs-expression-language)).
- Everything compiles to fast predicates at load time.

### 8.4 Stats & modifiers
- Every tunable number is a **stat = base + stacked modifiers**, keyed (`crime.propensity`, `production.rate`, `approval`, `learning.rate`, `lifespan.multiplier`).
- Traits, laws, buildings, faith, culture, events and addons add modifiers by key, with optional duration.
- Stat bases can be expressions.

### 8.5 Decision pipelines
- **Activity selection (utility AI):** advertisements are scored by an ordered list of registered **Considerations** (needs, personality, cost, distance, relationships, legality, faith). Considerations can be Java or expressions.
- **Plan generation:** an ordered chain of **PlanContributors**: law constraints → obligations → needs → social → player invitations → free time. Modules add contributors (governance adds law constraints, social adds appointments and avoidance, a future module adds pilgrimages).

### 8.6 Procedures (macro → micro)
Laws and policies go through *Proposal → Procedure → Enactment*. `ProcedureType` is a registry: `decree` (MVP), `council_vote`, `referendum`, `veto`, … ([DESIGN §19](DESIGN.md#19-macro--micro-compatibility-rules)).

### 8.7 WorldPort
The sim never touches `Level`. It talks to the world through a port:
```java
public interface WorldPort {
    GeologySample sampleGeology(ColumnPos pos);             // statistical, no chunk generation
    void queueBlockChanges(SectionKey section, BlockDiff d); // applied on chunk load
    TierSnapshot tiers();                                    // chunk tiers from the bridge
}
```
The harness uses `FakeWorldPort`.

### 8.8 The extensibility stack

| Layer | Runs on | Used for | Written by |
|---|---|---|---|
| **JSON data** | Load time | Structure: goods, buildings, cultures, laws, acts | Pack developers, us |
| **VS expressions** | Sim threads, headless | Small logic inside data: conditions, scores, formulas, yields | Pack developers, us |
| **KubeJS** (optional) | Server thread, event reactions | Content registration, reacting to events (commands back), quests, commands, in-game scenarios | Pack developers |
| **Java API** | Anywhere | New systems, components, Activities, considerations, condition/effect types, expression functions | Addon developers, us |

---

## 9. VS Expression Language

[DECIDED: A13] A small, typed, **pure** expression language compiled at load time and safe to run on sim threads.

### 9.1 Why
Data-driven content needs small pieces of logic, not programs. Without expressions, every formula needs a new Java type. With them, JSON and KubeJS content can express real logic that runs on sim threads and headless.

### 9.2 Examples
```json
"score": "need('social') * 1.5 + need('fun') - distance() / 200 - (price() > wealth() * 0.1 ? 30 : 0)"
"when":  "actor.class != 'noble' && time.hour >= 22 && district.has_tag('noble_quarter')"
"crime.propensity": "(1 - trait('honesty')) * 40 + need('hunger') * 0.5 - district.stat('patrol_coverage') * 20"
"yield": "2 + skill('baking') / 25 * quality_multiplier()"
```

### 9.3 Rules
- **Pure:** expressions compute values and never change state. Changes go through effects.
- **No loops, no allocation.** Aggregates (`count_nearby('guard', 16)`) are functions backed by precomputed queries, so cost is bounded.
- **Typed and checked at load:** `number`, `bool`, `string`, entity refs, registry IDs, tags. Errors are reported at `/reload` with file and position.
- **Context-typed:** every usage site declares a **context type** (e.g. `ConsiderationContext` provides `actor`, `venue`, `advertisement`; `LawContext` provides `actor`, `target`, `district`, `act`). The type checker knows which variables exist.
- **Compiled:** parse → type-check → compile to a tree of Java lambdas. Bytecode generation later if profiling demands it, with no language changes.
- **Deterministic:** `random()` draws from the sim's seeded RNG.
- **Extensible:** Java modules register `ExpressionFunction`s through the public API, and they become available in every expression.

### 9.4 Syntax
**Molang-like** (GeckoLib uses Molang, so modellers already know the style): arithmetic, comparisons, `&&`/`||`/`!`, ternary, member access, function calls, string and ID literals.

[DECIDED: A24] **Custom grammar and compiler**, not an existing Molang library. It's more powerful for our needs:
- **Static typing** with entity refs, registry IDs and tags as first-class types. Molang is dynamically typed around floats.
- **Typed member access** (`actor.class`, `district.stat('x')`, `venue.owner.faith`) checked against the context type at load time.
- **Precise error messages** (file, line, column, expected type) at `/reload`.
- **Full control over compilation and performance** (lambda trees now, bytecode later) and over determinism.
- **Our own extension model:** addon-registered functions and member accessors become first-class, type-checked parts of the language.

### 9.5 Estimated cost
~2–3 weeks for parser, type checker, compiler and core function library. Built in Phase 1, since the first considerations and conditions need it.

---

## 10. KubeJS Integration

[DECIDED: A12] KubeJS is the primary scripting layer for pack developers, shipped as an **optional integration** (`compat/kubejs`), loaded only when KubeJS is installed. We do **not** embed our own general-purpose script engine [A14].

### 10.1 Why optional integration instead of a hard dependency
- **Threading:** KubeJS and the Minecraft objects it wraps expect the server thread; the sim runs on worker threads.
- **Headless:** KubeJS needs Minecraft loaded, so it can't run in the fast harness.
- **Dependency weight and release coupling:** players who never script shouldn't need KubeJS installed, and we shouldn't be tied to KubeJS's major-version cycle.

### 10.2 Execution model
```
Sim threads ──events──▶ outbox ──▶ bridge dispatches on server thread ──▶ KubeJS handlers
                                                                              │
Sim threads ◀──────── commands (applied at next window boundary) ◀────────────┘
```
- **The sim never waits for scripts.** Script reactions are commands applied at the next boundary.
- Reads go through **views**, which are safe on the server thread.

### 10.3 What KubeJS scripts can do
- **Startup scripts:** register content (goods, acts, buildings, cultures, laws) as an alternative to JSON.
- **Server scripts:** subscribe to sim events and send commands; read views (villager, district, village stats); add `/vs` subcommands, quests and festivals.
- **Compose conditions and effects**, including VS expressions.
- **In-game scenarios** through the scenario DSL binding.
- **Typings** through ProbeJS for autocomplete.

```js
// server_scripts/market_fear.js
VillagerSimEvents.actCommitted(e => {
  if (e.act == 'villagersimulator:theft' && e.district.hasTag('market'))
    e.sim.command('add_modifier', {
      target: e.district, stat: 'crime.fear', id: 'recent_theft', value: 5, duration: '2d' })
})
```

### 10.4 What stays Java-only
Anything that runs **synchronously inside sim decisions**: Activity `simulateAbstract`, Considerations, new condition/effect types, expression functions. Pack developers combine existing types via JSON, expressions or KubeJS, and write new *types* in a small Java addon.

---

## 11. Public Addon API

[DECIDED: A11] Public from the start.

- **Two public artifacts:**
  - `villagersimulator-sim-api` (MC-free): modules, components, events, commands, registries, conditions/effects, stats, activities, expression functions, scenario DSL.
  - `villagersimulator-mod-api` (MC-side): embodied behaviours, animation keys, blueprint metadata, UI panels, client hooks.
- **Package split:** `…api.*` is public and versioned; everything else is internal.
- **Stability annotations:** `@ApiStatus.Experimental`, `@ApiStatus.Internal`, and deprecation windows before removal.
- **Semantic versioning.** Breaking changes only in major versions, with a pre-1.0 grace period where breaks are allowed but documented.
- **CI compatibility check** (japicmp) fails the build on unannounced breaking changes.
- **Reference addon** (`examples/hospitals`) built in CI against the public APIs only. It proves the extensibility test.
- **Docs:** Javadoc + generated expression-function reference + KubeJS reference, from the same source.
- **Distribution** [DECIDED: A23]: **no Maven hosting for now.**
  - The API subprojects are still set up as publishable artifacts (Gradle `maven-publish`), so hosting can be switched on later with one repository block.
  - Addons, including the reference addon, build against them through `publishToMavenLocal` or as Gradle subprojects in the same build.
  - The mod itself is distributed through the usual mod platforms.

---

## 12. The Minecraft Bridge

### 12.1 Bootstrap
- `@Mod` entry registers Minecraft content through `DeferredRegister` (blocks, items, entity types, menus, sounds, attachment types, payloads).
- Fires `RegisterSimModulesEvent` to collect our modules and addon modules.
- Hooks datapack reload listeners for sim definitions, expressions and KubeJS reload.

### 12.2 Server lifecycle

| Hook | Action |
|---|---|
| `ServerStartingEvent` | Build `SimWorld`, load save from `world/villagersimulator/`, freeze registries, start workers |
| `ServerTickEvent.Post` | Apply queued commands, read latest views, update tiers (every ~20 ticks), set target sim time, dispatch outbox to KubeJS |
| `LevelEvent.Save` | Request async snapshot at the next boundary |
| `ServerStoppingEvent` | Drain queues, final save, stop workers |

### 12.3 Tier manager
Every ~20 ticks:
- **T0:** chunks within the embodiment radius of a player, and within simulation distance.
- **T1:** other loaded, entity-ticking chunks (`ChunkEvent.Load/Unload`, `ServerLevel.isPositionEntityTicking`).
- **T2/T3:** unloaded, split by distance to the nearest player.
- Changes are sent as commands. The sim answers with promote/demote requests.

### 12.4 Puppet entities
`SimVillagerEntity extends PathfinderMob`. **Not** a vanilla `Villager`.
- **Holds a sim handle; the sim is the truth.**
- **Never saved to chunks.** An entity without a valid handle is removed on sight (crash safety, no duplicates).
- **Behaviour:** the current Activity's `EmbodiedBehaviorKey`, resolved to an implementation registered through `mod-api`. It walks to workstations, plays animations, interacts and reports outcomes back as events.
- **Pathfinding:** road-graph waypoints for long trips + vanilla navigation for local stretches; requests go through a **per-tick budget queue**.
- **Entity cap near players.** Beyond it, villagers become **render-only crowd figures** (tier "T0.5"): positions and animation state streamed to the client and drawn with instancing, with no server entity.
- **Damage/death** become sim events; attackers are recorded as acts.

### 12.5 Promotion / demotion sequence
```
Promotion: tier manager → PromoteCommand → sim reads plan position at "now"
           → view: SpawnRequest(handle, pos, appearance, activity) → bridge spawns puppet
Demotion:  tier manager → DemoteCommand → bridge sends final entity state
           → sim writes it into components, resumes plan → bridge discards puppet
```

---

## 13. Worldgen, Buildings & Reconciliation

### 13.1 Buildings
- **Anchor block** + block entity linking a physical building to its sim building entity.
- **Blueprints** [DECIDED: A21]: vanilla structure templates (`.nbt`, made with structure blocks) plus metadata JSON:
  ```json
  { "type": "villagersimulator:bakery",
    "workstations": [{ "pos": [3,1,2], "activity": "bake", "facing": "north" }],
    "beds": [], "doors": [[0,1,4]], "storage": [[5,1,1]], "capacity": 3 }
  ```
- **Integrity tracking:** block place/break events inside building bounds update condition, emit vandalism acts (if a player did it) and create repair jobs.

### 13.2 Worldgen
**Worldgen is reconciliation of an already-complete plan.**
1. Village sites are chosen **deterministically from the world seed** (grid + noise).
2. The village plan (districts, roads, buildings, culture, starting population) is created in the sim when first needed.
3. A custom worldgen feature places each chunk's sections from the plan as chunks generate, reading an **immutable plan snapshot** (worldgen is multi-threaded).
4. Terrain fitting (flattening, foundations, road grading) per section.
5. The village is alive in the sim before the player ever sees it.

**Vanilla coexistence** [DECIDED: A22]: vanilla villages keep generating and vanilla villagers are untouched. Our site selection avoids vanilla village structure starts (and other large structures) so the two never overlap. Links between vanilla systems and ours are optional modules ([§13.5](#135-optional-vanilla-integrations)).

### 13.3 Reconciliation
- Each chunk section stores a **reconciled version** in a NeoForge **chunk data attachment**.
- On `ChunkEvent.Load`, pending changes are queued and applied **with a per-tick block budget**, using `setBlock` flags that skip neighbour-update cascades; lighting is fixed afterwards.
- **Intent-based, not blind overwrites:** a change applies only if the current block matches what the plan expects. Otherwise the sim gets an **obstruction** event.
- **Mining:** tunnels carved where blocks match expectations. Unloaded mines use **statistical yields** from biome and depth distributions (from placed-feature configs). Loaded mines (T1+) use real blocks. Offscreen yield won't exactly match the ore actually there; this is accepted.

### 13.4 Physical inventories
Firm and household stock is **one dataset with two views**: a ledger in the sim, and real containers at T0 (the blueprint's `storage` positions). Container interactions are turned into ledger commands; theft is detected against ownership.

### 13.5 Optional vanilla integrations
[DECIDED: A25] Vanilla is left alone by default (A22). These integrations link vanilla systems to ours. Each is a **separate module that can be switched off in config**, built only against the public APIs, so each one also exercises the addon API.

| Integration | Behaviour | Depends on | Priority |
|---|---|---|---|
| **Vanilla workstations & beds** | Composters, smithing tables, lecterns, blast furnaces, beds, etc. inside a blueprint are detected automatically as workstation/bed markers, without listing them in the metadata JSON. A mapping table (block → Activity) is data-driven. | Blueprints (§13.1) | **1** (Phase 1) |
| **Pillager raids** | Raids can target sim villages. Guards and militia fight back; villagers form fear and grief memories; the raid becomes a chronicle entry; repelling it counts as a heroic act for the player's reputation. | Guards, memories, chronicle | **2** |
| **Zombie sieges & zombie villagers** | Night sieges can hit sim villages. An infected sim villager becomes a zombie that **keeps its sim identity**; curing it restores the same person with memories and relationships intact. | Puppet entities, combat, memories | **2** |
| **Bells** | Ringing a sim village's bell raises an alarm (villagers go home, guards respond) or, in peacetime, calls a gathering or festival. | Plans, guards, festivals | **3** |
| **Hero of the Village** | The vanilla effect also gives a reputation boost in sim villages. | Reputation/gossip | **3** |
| **Iron golems** | Golems a village builds or buys serve as guards: patrol routes, count toward the guard office, reduce district crime. | Governance offices, crime | 4 |
| **Wandering traders** | Become one kind of merchant caravan travelling between sim villages, carrying goods and gossip, robbable by bandits. | Caravans, economy | 4 |
| **Vanilla villager migration** | Vanilla villagers can migrate into a sim village (led there or recruited) and become full sim villagers, with traits based on their profession and level. | Migration, lifecycle | 4 |
| **Cats & village animals** | Households can own pets, which affect mood and Social/Comfort needs. | Households, needs | 4 |
| **Cartographer maps** | Vanilla cartographers can sell maps pointing to sim villages. | Worldgen sites | 4 |
| **Emerald economy** | Emeralds are the cross-realm currency ([DESIGN §17.7](DESIGN.md#177-money)); vanilla trading and emerald farms feed sim economies directly. | Economy | Built into the economy |

**Order:** workstations & beds first (they make blueprint authoring easier from Phase 1), then raids and zombie sieges (cheap early conflict before the full war system), then bells and Hero of the Village (quick wins), then the rest as caravans, guards, migration and households come online.

---

## 14. Player Integration

- **Player sim record:** a NeoForge `AttachmentType` on the player stores the sim handle; all state lives in the sim.
- **Act detection:**

| Act | Hook |
|---|---|
| Theft | Taking from firm storage (container/item-handler hooks); picking up owned item entities |
| Assault / murder | `LivingIncomingDamageEvent` / `LivingDeathEvent` |
| Vandalism / arson | `BlockEvent.BreakEvent`; fire inside building bounds |
| Trespass | Position vs. district/building bounds, ~1 Hz |
| Public drunkenness | Consuming our alcohol goods |
| Poaching | Killing animals in owned or forbidden zones |

- **Witnesses:** embodied villagers within range and line of sight at T0; probabilistic check against T1 villagers near chunk edges.
- **Dialogue:** right-click opens a screen whose options the server builds from sim state (topics, gifts, quests, invitations, flirting, trade). Using an item on a villager offers it as a gift.
- **Trading:** custom shop UI backed by the firm's real inventory and live prices.

---

## 15. Networking

- NeoForge `CustomPacketPayload` via `RegisterPayloadHandlersEvent`.
- **Entity state:** appearance genes, outfit, animation state, visible marks via synced entity data and small payloads.
- **UI data is pulled on demand** (request → view response), never pushed wholesale. The client never holds the full sim.
- **Crowd stream:** low-rate position and animation updates for crowd figures.
- **Required on both sides** (custom entities, rendering, UIs).

---

## 16. Client: Rendering, Animation, UI

- **Models:** modular body + layers (skin, hair, face, outfit by class and culture, marks). Genes pick variants.
- **Textures:** layers composited into cached dynamic textures per appearance combination.
- **Animation** [DECIDED: A18]: **GeckoLib** models and animations (authored in Blockbench), driven by a state machine keyed by `EmbodiedBehaviorKey`/Activity.
  - Real entities use GeckoLib's standard entity renderer.
  - Crowd figures (T0.5) reuse the **same GeckoLib model and animation assets** through our own instanced render path, so there's one set of art and no second model format.
- **Levels of detail** by distance: fewer bones, lower animation rate, simpler models.
- **Compatibility:** Sodium/Iris tested early.
- **UI** [DECIDED: A20]: a small **internal UI toolkit** on top of vanilla `Screen`s, with no external UI library.
  - Toolkit pieces: layout containers (row/column/grid), scrollable lists with virtualisation (for 40k-resident villages), tabs, tooltips, text input, dropdowns, charts (ledger/approval over time), a node/list editor for conditions, and a map widget.
  - Screens built on it: town hall tabs, law editor with condition builder, chronicle, family tree, district map (top-down render with overlays and heatmaps), dialogue, inspector.
  - Exposed through `mod-api` so addons can add panels and tabs.

---

## 17. Persistence

[DECIDED: A19] **SQLite**, one database per world, via the `sqlite-jdbc` driver bundled with jar-in-jar (includes native libraries for Windows, macOS and Linux).

- **Location:** `world/villagersimulator/sim.db` (via `LevelResource.ROOT`), in **WAL mode**.
- **Hybrid layout: blobs for bulk state, tables for queryable history.**

| Table | Contents | Why |
|---|---|---|
| `meta` | Format version, sim clock, settings | |
| `registry_ids` | String ID ↔ numeric ID mappings | Stable IDs across registry changes |
| `shard_snapshot` | One row per district shard: component columns serialised as versioned binary blobs | Fast bulk load/save; no row-per-villager overhead |
| `graph_segment` | Relationship graph segments as packed blobs | Largest dataset, stored compactly |
| `event_log` | Notable event records (actor, cause, time, district, type) with indexes | **Queryable**: chronicle UI, "what did my spouse do today", intrigue lookups |
| `knowledge` | Event ↔ entity "who knows" links | Gossip and secrets |
| `village_plan` | Village plans and per-section build progress | Worldgen & reconciliation |
| `pending_diff` | Block changes awaiting chunk load | Reconciliation queue |

- **All writes happen on the save thread** in a single transaction per snapshot: pause at a boundary, swap buffers, serialise shards in parallel, write in one transaction. SQLite's transactions give **atomic, crash-safe saves**.
- **Per-component codecs with versions** and migration functions.
- **Unknown components** (e.g. from a removed addon) are preserved as opaque blobs.
- **Event log reads** (chronicle, inspector) run on a separate read connection, which WAL mode allows alongside writes.
- **Storage stays behind an interface** so the harness can use an in-memory SQLite database.

---

## 18. Debug Tooling

Built from day one.
- **`/vs` commands** (Brigadier):
  - `inspect <villager>`: needs, plan, relationships, memories, stats with modifier breakdown
  - `tier force`, `time warp <duration>`
  - `village spawn <culture>`
  - `expr eval <expression>` against a chosen context
  - `scenario run <name>`
  - `profile` (time per system and per KubeJS handler), `dump`
- **Debug overlay:** chunk colours by tier, district borders, a villager's plan route and current Activity, appointment links, reconciliation queue.
- **Inspector screen:** click any villager to browse full sim state.

---

## 19. Testing Strategy

| Level | Tool | Runs | Covers |
|---|---|---|---|
| **Unit** | JUnit | Headless, seconds | Components, expressions, codecs, registries |
| **Scenario** | Java scenario DSL (JUnit) | Headless, seconds | Sim behaviour over sim-days/years |
| **Determinism** | Harness | Headless | Same seed → same state hash |
| **Invariants** | Harness | Headless | Money conservation (sources/sinks only), no orphaned entities, relationship consistency |
| **Benchmarks** | JMH + harness | Headless | Events/s, memory per villager, the 1M target |
| **Soak** | Harness | Headless, long | Economy stability over 100 sim-years |
| **Bridge** | NeoForge GameTest (`runGameTestServer`) | Headless MC, ~1 min startup | Promotion/demotion, never-saved puppets, reconciliation, act detection |
| **KubeJS scenarios** | KubeJS binding + GameTest | Headless MC | Integration of scripts with the sim |
| **Manual** | `/vs scenario run`, debug overlay | In-game, short | Visual checks only |

```java
@Scenario
void bakeryFeedsSmallTown(ScenarioContext s) {
    var v = s.spawnVillage(Culture.FARMING_HAMLET, 40);
    s.warp(Duration.days(5));
    s.assertThat(v.stockpile(Goods.BREAD)).isPositive();
    s.assertThat(v.deaths(DeathCause.STARVATION)).isZero();
}
```
In line with the project testing rules: almost all logic is verified headless; in-game checks are short and targeted.

**CI:** build all modules; unit + scenario + determinism tests; API compatibility check (japicmp); dependency-rule check; build the reference addon; GameTests; nightly benchmarks and soak tests with trend tracking.

---

## 20. Open Questions

All architecture questions raised so far are resolved (A1–A25).

Implementation details to be tuned from benchmarks rather than decided up front: SQLite blob layout and checkpoint cadence, sync-window length, tier radii, entity cap near players.

---

## 21. Phase 1 Plan: Vertical Slice

> Superseded by [ROADMAP.md](ROADMAP.md) (this vertical slice is spread across Phases 0–7 there). Kept for reference.

Goal: a small, living village **in Minecraft**, running on the real engine, with the headless benchmark growing alongside.

1. **Project restructure:** multi-project Gradle build per [§4](#4-project-layout); dependency-rule check.
2. **`sim-api` + `sim-core`:** entities, dense/sparse/tag components, shards, scheduler, windowed parallelism, event bus, commands, views, registries, stats & modifiers, conditions & effects.
3. **VS expression language:** parser, type checker, compiler, core functions, `expr` condition type.
4. **Base modules:** `lifecycle`, `needs`, `plans`, `buildings` (advertisements + Activities), `social` (graph store).
5. **Bridge:** bootstrap, `RegisterSimModulesEvent`, lifecycle hooks, tier manager, puppet entities (never saved), custom save directory.
6. **Buildings:** anchor block, 3 blueprints (house, bakery, tavern) with metadata, plus the **vanilla workstations & beds** integration (§13.5).
7. **Debug:** `/vs inspect`, `tier force`, `time warp`, `profile`, tier overlay.
8. **Harness:** scenario DSL, determinism test, first benchmark.
9. **KubeJS integration skeleton:** one event (`actCommitted` or `villagerBorn`) and the command path end to end.
10. **Reference addon skeleton** built in CI.

**Exit criteria:**
- Walk through a ~30-villager village; villagers promote and demote with no teleporting, duplicates or lost state.
- Leave and return; the village has visibly moved on (plans, needs, bakery stock).
- The headless benchmark reports events/s and memory per villager, with a trend toward 1M.
