# Villager Simulator — Roadmap

> Status: **Draft v1** (2026-09-29)
> Built from [DESIGN.md](DESIGN.md) (what the game is) and [ARCHITECTURE.md](ARCHITECTURE.md) (how it's built). This document supersedes the phase tables in DESIGN §23 and ARCHITECTURE §21.

## How this roadmap works

1. **Phase 0 is a small MVP that everything else builds on.** It's deliberately tiny, but it's built on the real architecture (sim core, modules, bridge, puppets, saves), so later phases extend it instead of replacing it.
2. **Every phase is playable in game**, Phase 0 included. Each phase lists what you can *do* in Minecraft when it's finished.
3. **Phases are small and vertical.** Each one adds a thin slice through sim, bridge, content and UI rather than finishing one layer at a time.
4. **Nothing gets thrown away.** Architecture rules that are expensive to retrofit (no Minecraft imports in the sim, modules on the public API, plan-as-truth, individuals-first, event records with actor/cause/witnesses, procedures for decisions) are in place from Phase 0.
5. **Testing follows `D:\MinecraftMods\ModTesting.md`:** implement a coherent chunk first, run only the tests that cover what changed, keep GameTests under 1 minute of game time. Most logic is verified with headless JUnit and scenario tests.
6. **At the end of every phase:** the previous phases still play correctly, new tests exist for the new systems, and the docs are updated if a decision changed.

**Sizes are rough:** S ≈ 1 week, M ≈ 2–3 weeks, L ≈ 4+ weeks.

### Milestones at a glance

| Milestone | Phases | You can… |
|---|---|---|
| **A. Foundation** | 0–7 | Walk through a living village that keeps going when you leave; befriend villagers; mod it |
| **B. Life** | 8–11 | Watch generations live, marry, have children and die, including your own family |
| **C. Economy & Growth** | 12–18 | Trade, run shops, commission buildings, and watch a hamlet grow into a town |
| **D. World & Culture** | 19–21 | Explore a world of distinct pre-spawned villages; build your own blueprints in game |
| **E. Rule & Justice** | 22–29 | Found or win a village, rule it, write its laws, judge its criminals, defend it |
| **F. Faith, Knowledge & Society** | 30–33 | Shape a village's religion, schools and class structure |
| **G. Realms** | 34–40 | Trade, ally and war between villages; found dynasties; play at full 1M scale |
| **H. Intrigue** | 41–43 | Crusader Kings-style secrets, schemes and factions |

### Dependencies
Phases are ordered: each phase builds on **every earlier phase in its milestone**. Milestones need these earlier phases:

| Milestone | Needs |
|---|---|
| B. Life | A (0–7) |
| C. Economy & Growth | A; Phase 9 (households) |
| D. World & Culture | C through Phase 17 (construction, growth, levels) |
| E. Rule & Justice | C (money, growth); Phase 19 for culture-specific laws |
| F. Faith, Knowledge & Society | E through Phase 25 (laws, morality) |
| G. Realms | D (many villages); E (governance, offices) |
| H. Intrigue | Phases 28 (offices) and 39 (dynasty) |

---

# Milestone A — Foundation

## Phase 0 — MVP: A Living Hamlet · L
**Goal:** a tiny village of ~8 villagers that lives by a daily schedule, keeps living when you walk away, and survives a restart, all on the real architecture.

**Build**
- **Project structure:** Gradle subprojects `sim-api`, `sim-core`, `sim-content`, `sim-harness`, `neoforge`, with the dependency-rule check ([ARCH §4](ARCHITECTURE.md#4-project-layout)).
- **Engine (`sim-api` + `sim-core`), minimum viable:**
  - entity handles (index + generation); dense and sparse components
  - **one shard** with an event queue and a monotonic sim clock derived from game time
  - `SimModule` registration (our modules register exactly like addons will)
  - one data-driven registry loaded from JSON (`BuildingType`)
  - commands in, views out, sim on its own thread; the server thread never waits
  - event records with actor, cause and witnesses (unused for now, but in the format from day one)
- **Content modules:** `needs` (Hunger, Energy, lazily evaluated), `plans` (daily itinerary: sleep → work → eat → free time), `buildings` (house, bakery, well).
  - **Activities:** Sleep, Work (bakery produces bread into a simple counter), Eat, Wander.
  - Each Activity has `simulateAbstract`; embodied behaviour is "walk to the spot and idle".
- **Two tiers only:** **T0** (near a player) and **T2** (everywhere else). T1 and T3 come in Phase 5.
- **Bridge:**
  - `SimVillagerEntity` puppets, **never saved to chunks**, with a placeholder look (vanilla villager model, custom texture)
  - promotion and demotion based on plan-as-truth
  - vanilla navigation for walking
- **Buildings in the world:** anchor block + 3 blueprints (house, bakery, well) as `.nbt` + metadata JSON, **built with Structure Lab**.
- **`/vs village spawn`** places the blueprints and creates the villagers.
- **Persistence:** SQLite `sim.db` with a simple snapshot on world save.
- **Debug:** `/vs inspect <villager>` (needs, plan, current Activity); `/vs time warp`; a name tag showing the current Activity.

**Playable:** spawn a hamlet, watch villagers wake up, walk to the bakery, work, eat and go home to sleep. Walk 500 blocks away, come back hours later, and find them where their schedule says. Quit and reload the world; everything is where it was.

**Done when**
- JUnit: scheduler ordering, component storage, save/load round trip.
- Scenario (headless): 5 sim-days, nobody starves, the bakery produces bread.
- GameTest: promote → demote → promote leaves exactly one entity per villager; puppets are never written to chunk data.

---

## Phase 1 — Smart Objects & Choices · M
**Goal:** villagers choose what to do based on needs and what buildings offer.

**Build**
- **Advertisements** on buildings and the **utility-AI** loop (Considerations scoring advertisements) ([DESIGN §9.1](DESIGN.md#91-smart-objects--advertisements-sims)).
- More needs: **Social, Fun, Hygiene, Comfort**.
- **VS expression language v1:** parser, type checker, compiler, core functions; used for advertisement scores and conditions ([ARCH §9](ARCHITECTURE.md#9-vs-expression-language)).
- **Stats & modifiers** framework; **conditions & effects** framework (a few core types).
- New buildings: tavern, market stall.
- Building types, advertisements and scores fully defined in **datapacks**.
- **Vanilla workstations & beds integration:** vanilla workstation blocks and beds inside blueprints are detected automatically ([ARCH §13.5](ARCHITECTURE.md#135-optional-vanilla-integrations)).
- `/vs expr eval`.

**Playable:** villagers visibly choose (tired ones go home, bored ones go to the tavern). Add a new building type through a datapack and villagers start using it after `/reload`.

**Done when:** expression parser/type-checker tests; a scenario shows needs driving choices; a datapack-defined building is used in game.

---

## Phase 2 — Venues & Relationships · M
**Goal:** villagers meet, talk and form friendships and rivalries.

**Build**
- **Relationship graph store** (packed adjacency; friendship axis; typed bonds) ([ARCH §5.4](ARCHITECTURE.md#54-specialised-stores)).
- **Interactions at venues:** embodied conversations at T0 (face each other, talk animation placeholder); statistical pairing at T2 ([DESIGN §4.5](DESIGN.md#45-the-venues-tier-sets-interaction-fidelity)).
- **Memories v1:** references to shared event records, with fading mood effects.
- **Personality facets** (a first set) that affect interactions.
- **Appointments v1:** friends arrange to meet at the tavern after work; **avoidance:** rivals skip each other's venues.
- `/vs inspect` shows relationships and memories.

**Playable:** watch villagers chat in the tavern, see friendships form over days, and notice two rivals avoiding each other.

**Done when:** graph store tests; scenario: friendships form among co-workers; appointments are kept across tiers.

---

## Phase 3 — The Player Joins the Village · M
**Goal:** the player is a sim agent that villagers know and remember.

**Build**
- **Player sim record** (NeoForge attachment holding the sim handle).
- **Internal UI toolkit v1:** layout, lists, buttons, tooltips ([ARCH §16](ARCHITECTURE.md#16-client-rendering-animation-ui)).
- **Dialogue screen:** talk, compliment, joke, ask about their day; options built from sim state.
- **Gifts:** use an item on a villager; preferences come from personality.
- **Player relationships** and **gossip v1:** villagers talk about the player, so reputation spreads.
- **Invitations:** invite a villager to the tavern; it becomes an appointment on their plan.

**Playable:** befriend villagers, give gifts, invite them out, and hear that people you've never met have heard of you.

**Done when:** gossip propagation scenario; GameTest: a gift changes the relationship; dialogue options reflect sim state.

---

## Phase 4 — Villagers With a Face · M
**Goal:** villagers look like individuals.

**Build**
- **GeckoLib model** for villagers, with a modular body and layers (skin, hair, face, outfit) ([ARCH §16](ARCHITECTURE.md#16-client-rendering-animation-ui)).
- **Appearance genes** stored in the sim; texture layers composited and cached.
- **Animations v1:** walk, idle, work (generic), talk, sleep, eat.
- Animation state driven by `EmbodiedBehaviorKey`.
- Networking for appearance and animation state.

**Playable:** every villager looks different, and you can tell at a glance who is talking, working or sleeping.

**Done when:** appearance round trip through save and network; visual check in a short client run; Sodium/Iris smoke test.

---

## Phase 5 — Districts & Seamless Tiers · L
**Goal:** larger villages split into districts, with all four tiers and parallel simulation.

**Build**
- **Semantic districts**, each a **shard**, with a global entity directory ([ARCH §5.3](ARCHITECTURE.md#53-storage--sharding)).
- **Windowed parallelism** across worker threads and cross-shard messages ([ARCH §6.3](ARCHITECTURE.md#63-windowed-parallelism)).
- **T1** (loaded, no player nearby: road-graph movement) and **T3** (daily batch).
- **Road/building graph** for movement outside T0.
- Venue-tier rules in full; mid-window player entry.
- **Attention pinning** (the player's friends stay at least T1).
- **Debug overlay:** chunk colours by tier, district borders, plan routes.

**Playable:** a two-district village of ~60 villagers. Walk between districts and watch the overlay: villagers move seamlessly between tiers with no teleporting, and your friends' lives stay detailed.

**Done when:** determinism test across thread counts; GameTest: villagers crossing a tier boundary keep their state; scenario: cross-shard appointments are kept.

---

## Phase 6 — Scale Gate · M
**Goal:** prove the engine can reach 1M before building everything on it. **Go/no-go checkpoint.**

**Build**
- **Headless benchmark:** 1M villagers across ~5,000 generated villages (flat stand-in world), JMH micro-benchmarks for hot paths.
- Memory-per-villager and events-per-second reports; profiler per system (`/vs profile`).
- Optimisation pass on whatever the numbers show (storage layout, queue, graph encoding).
- **In-game stress:** `/vs stress <n>` spawns abstract villagers at T2/T3 to check server TPS.

**Playable:** everything from Phases 0–5, plus a stress world where tens of thousands of villagers are simulated in the background while the server stays at 20 TPS.

**Done when:** the benchmark report shows 1M villagers at a sustainable real-time rate within the memory budget ([DESIGN §4.10](DESIGN.md#410-budgets-targets-to-validate-in-phase-1)), or the gap and plan to close it are written down and accepted.

---

## Phase 7 — Modding Surface v1 · M
**Goal:** the public API is real and used by something other than us.

**Build**
- **`mod-api`** subproject (embodied behaviours, animation keys, blueprint metadata, UI panel hooks).
- `RegisterSimModulesEvent`, API package split, `@ApiStatus` annotations, **japicmp** check in CI.
- **Reference addon** (`examples/`) built only against the public APIs, e.g. a "fountain" building with its own advertisements and Activity.
- **KubeJS integration v1** (`compat/kubejs`): events out through the outbox, commands back, view reads, ProbeJS typings ([ARCH §10](ARCHITECTURE.md#10-kubejs-integration)).
- **Scenario DSL** exposed to KubeJS; `/vs scenario run`.
- Javadoc and a generated expression-function reference.

**Playable:** install the reference addon and see its building work; write a KubeJS script that reacts to villagers (e.g. announces in chat when two villagers become best friends).

**Done when:** the reference addon builds in CI with no internal imports; a KubeJS scenario runs in a GameTest.

---

# Milestone B — Life

## Phase 8 — Aging & Death · M
**Goal:** villagers are born into life stages, age and die.

**Build**
- **Life clock** and life stages (infant → elder); **lifespan config** (default ~5 real hours) with individual variance ([DESIGN §5](DESIGN.md#5-time-aging--lifecycle)).
- Children and elders behave differently (play, reduced work).
- **Death:** old age first; grief memories for relatives and friends.
- **Graves:** named graves for notable villagers, a **crypt/ossuary ledger** for everyone else ([DESIGN §9.2](DESIGN.md#92-building-catalogue)).
- **Chronicle v1:** notable events recorded in SQLite and browsable with a simple screen.
- Cheap ID recycling at high birth/death rates.

**Playable:** watch villagers grow old and die; visit the graveyard and read the crypt ledger; browse the village chronicle.

**Done when:** scenario over a full lifespan; config lifespan changes are respected; ID recycling benchmark.

---

## Phase 9 — Households & Housing · M
**Goal:** people live in homes that fit them.

**Build**
- **Households** (shared budget, shared home) separate from **families** (kinship) ([DESIGN §12.6](DESIGN.md#126-families--households)).
- **Beds as housing capacity**; household → home assignment.
- **Moving out** rules (culture-dependent default: stay until married or able to afford a place); boarding houses and inns.
- **Homelessness** and overcrowding effects.
- Home **inheritance** on death.
- New blueprints: hovel, cottage, family house, boarding house.

**Playable:** see which family lives in which house, watch grown children move out, and notice unhappiness when the village runs out of beds.

**Done when:** housing assignment scenario; bed detection in blueprints; inheritance on death.

---

## Phase 10 — Romance & Families · M
**Goal:** villagers fall in love, marry and have children.

**Build**
- **Romance axis**, courtship, dating, marriage, divorce ([DESIGN §8.2](DESIGN.md#82-romance--family)).
- **Births:** children inherit traits and appearance genes from both parents.
- **Population balancing** (birth rate responds to housing, food, wealth).
- **Rituals:** date nights, family dinners.
- **Family tree screen.**
- Weddings as chronicle events.

**Playable:** watch a couple meet at the tavern, marry, and raise children who look like them. Open the family tree.

**Done when:** genetics inheritance tests; population-stability scenario over several generations.

---

## Phase 11 — Your Own Family · M
**Goal:** the player can marry a villager and raise children who become full sim villagers.

**Build**
- Flirting and courtship in dialogue; **marriage to the player**; the player joins or forms a household.
- **Player-villager children:** traits from the villager parent and from how the player plays ([DESIGN §11.4](DESIGN.md#114-children)).
- **Heirs** recorded (used later by dynasty phases).
- **"What's new" feed** for the player's family and friends.
- **Player-bonded aging config** (`lifespan.playerBondedAgingMultiplier`), resolving DESIGN open question 1.

**Playable:** court and marry a villager, have children, watch them grow up, go to work and start families of their own.

**Done when:** GameTest: marriage and birth with the player as a parent; children persist across save/load.

---

# Milestone C — Economy & Growth

## Phase 12 — Goods & Production Chains · M
**Goal:** things are made from other things.

**Build**
- **Goods registry** (tags, perishability, quality) ([DESIGN §17.1](DESIGN.md#171-goods)).
- **Firms** (buildings as businesses) with stockpiles: **one dataset, two views** (ledger at T2, real containers at T0) ([ARCH §13.4](ARCHITECTURE.md#134-physical-inventories)).
- Chain: **farm → mill → bakery**; plus lumber camp, carpenter.
- Villagers eat real bread; shortages hurt.
- New blueprints: farm, windmill, lumber camp, carpenter, warehouse.

**Playable:** open the bakery's chest and see today's bread; burn the wheat field and watch the village go hungry.

**Done when:** ledger ↔ container consistency GameTest; production-chain scenario.

---

## Phase 13 — Money, Shops & Jobs · M
**Goal:** a working local economy.

**Build**
- **Currency:** emeralds first (realm coins arrive in Phase 35).
- **Household budgets**, wages, rent.
- **Markets:** per-district clearing, prices from supply and demand ([DESIGN §17.4](DESIGN.md#174-markets--prices)).
- **Labour market:** firms post jobs; villagers choose by pay, skill, commute and relationships.
- **Shop UI** backed by the firm's real stock and live prices.
- **Economy invariants** in the harness (money conservation, sources and sinks).
- Theft from containers detected (no crime system yet: just a relationship and reputation hit).

**Playable:** buy bread from the baker, sell wheat and watch the price drop, hire a villager.

**Done when:** economy soak test stable for 100 sim-years (headless); price response scenario.

---

## Phase 14 — Construction v1 · M
**Goal:** buildings get built by villagers.

**Build**
- **Construction projects** (plot + blueprint + materials + builders) and the **builder job** ([DESIGN §10.5](DESIGN.md#105-who-builds)).
- Blueprints **split per chunk section**; physical building at T0; **reconciliation** of progress on chunk load with a per-tick budget and intent-based placement ([ARCH §13.3](ARCHITECTURE.md#133-reconciliation)).
- **Player commissions:** pick a blueprint, pick a spot, pay or supply materials.
- Obstruction events when something's in the way.

**Playable:** commission a house, supply the planks, watch builders place blocks. Leave, come back, and find it further along.

**Done when:** GameTest: reconciliation applies the right blocks after unload/reload; intent-based placement doesn't overwrite player blocks.

---

## Phase 15 — The Village Grows · M
**Goal:** the village expands on its own.

**Build**
- **Village planner:** builds to meet demand (housing shortage, missing services, high prices).
- **Plots and roads v1:** growth along roads, plot allocation, road upgrades by traffic ([DESIGN §10.6](DESIGN.md#106-plots-roads--walls)).
- **Village stages** Camp → Hamlet → Village, with requirements and unlocks ([DESIGN §12.4](DESIGN.md#124-village-stages)).
- **Founding by settlers:** a camp can start from nothing.

**Playable:** spawn a camp of 5 settlers and watch it become a village with new houses, roads and services over a few real hours.

**Done when:** growth scenario (headless) from Camp to Village; GameTest: planner projects reconcile correctly.

---

## Phase 16 — Mining & Resources · M
**Goal:** raw materials come from the world.

**Build**
- **Mine** and **quarry** buildings; tunnels as construction projects.
- **Statistical yields** from biome and depth distributions; **real blocks** at T1+ ([DESIGN §10.3](DESIGN.md#103-abstract-resource-yields)).
- **Farm, forest and fishery yields** from the actual terrain; **depletion**.
- Smelter and smithy chain (ore → ingot → tools).

**Playable:** follow miners into a tunnel that keeps getting longer while you're away; see ore reach the smithy.

**Done when:** tunnel reconciliation GameTest; depletion scenario.

---

## Phase 17 — Building Levels & Lifecycle · M
**Goal:** buildings improve, change and decay.

**Build**
- **Levels 1–5** per building type, with upgrade projects ([DESIGN §10.7](DESIGN.md#107-building-lifecycle)).
- **Repurposing** (shell stays, interior and sign change).
- **Neglect and decay**, repairs.
- **Damage** (fire first).
- Blueprints for level 1/3/5 of the core buildings.

**Playable:** fund an upgrade and watch a wattle bakery become a stone one; see a bankrupt shop become a tavern; see an abandoned house decay.

**Done when:** upgrade and repurpose GameTests; decay-over-time scenario.

---

## Phase 18 — Quests & Favours · S
**Goal:** villagers ask the player for help based on what they actually want.

**Build**
- **Wants & fears** in the villager model.
- **Quests generated from real wants** (the baker needs wheat, a family needs a bigger house) ([DESIGN §11.5](DESIGN.md#115-quests)).
- Quest log UI; rewards (money, relationship, reputation).

**Playable:** accept a request from the baker, bring the wheat, and see the bakery's output and your friendship rise.

**Done when:** quest generation scenario; GameTest: quest completion updates the sim.

---

# Milestone D — World & Culture

## Phase 19 — A World of Villages · L
**Goal:** new worlds generate living sim villages.

**Build**
- **Deterministic village sites** from the world seed; **worldgen as reconciliation** of a complete plan ([ARCH §13.2](ARCHITECTURE.md#132-worldgen)).
- **Coexistence with vanilla:** site selection avoids vanilla villages.
- **Two cultures:** farming hamlet and merchant town, each with default layout ([DESIGN §12.5](DESIGN.md#125-layout-by-culture)).
- **Palette swapping v1:** pieces remapped per culture and a few biomes.
- **Cartographer maps** to sim villages (vanilla integration).

**Playable:** create a new world and explore it; find farming hamlets and merchant towns that were already living before you arrived.

**Done when:** worldgen determinism test (same seed, same sites); GameTest: generated chunks match the plan.

---

## Phase 20 — Blueprint Editor & Modular Kits · L
**Goal:** building content can be made fast, in game and by agents.

**Build**
- **In-game structure editor:** capture a region, place markers (workstations, beds, doors, storage, sockets), tag palette roles, preview every palette, validate, export to datapack ([DESIGN §12.9](DESIGN.md#129-content-pipeline)).
- **Modular kits:** houses and shops assembled from pieces, sized to the plot.
- **Structure Lab workflow** documented: agent builds a piece, walking tests confirm villagers can path through it, export, add metadata.

**Playable:** build a house in survival or creative, turn it into a blueprint with the editor, and watch villagers build copies of it in their own style.

**Done when:** editor round trip (capture → export → load → build); kit assembly produces walkable buildings.

---

## Phase 21 — All Cultures & Styles · L
**Goal:** every culture and biome looks distinct and alive.

**Build**
- **All eight cultures** with values, layouts, building mixes and styles ([DESIGN §12.2](DESIGN.md#122-village-cultures), [§12.8](DESIGN.md#128-architecture--look)).
- **Biome variants** (desert, snow, jungle, swamp, badlands, cherry…).
- **Living decoration:** stall stock, festival banners, mourning cloth, decay and damage visuals.
- **Shop signs.**
- **Custom blocks**, only where vanilla can't do it (thatch, market stall, shop sign), each with a written reason.
- **Cultural drift** v1.

**Playable:** travel between a mining hold carved into a cliff, a whitewashed monastery town and a stilted outlaw haven, each looking and behaving differently.

**Done when:** each culture generates, grows and stays within its style in a growth scenario.

---

# Milestone E — Rule & Justice

## Phase 22 — Found & Rule · M
**Goal:** the player can found a village and run it.

**Build**
- **Found path:** place a town hall charter; settlers arrive ([DESIGN §13.1](DESIGN.md#131-paths-to-rule-all-five)).
- **Offices exist in data** (auto-filled, passive) from this phase on ([DESIGN §19](DESIGN.md#19-macro--micro-compatibility-rules)).
- **Treasury and taxes** (income, sales, property).
- **Zoning:** paint districts with a purpose; the planner builds within zones.
- **Town hall UI v1:** ledger, district list.
- **Decisions go through a procedure** (`decree` only for now).

**Playable:** claim a site, attract settlers, set taxes, zone a market district and a residential district, and watch the planner follow your zoning.

**Done when:** zoning-driven growth scenario; treasury invariants.

---

## Phase 23 — Laws & Approval · M
**Goal:** the ruler's decisions change daily life and the population reacts.

**Build**
- **Laws as plan constraints:** curfew, day of rest, work-hour limits, mandatory schooling (placeholder until Phase 31), tavern ban ([DESIGN §13.5](DESIGN.md#135-laws-that-change-schedules)).
- **Opinions and legitimacy** from outcomes, values alignment, relationships and gossip; **approval per district**.
- **Consequences:** grumbling, petitions, strikes, emigration.
- **Petitions inbox.**
- **Law book** with projected reactions.
- **District map** with heatmaps (approval, housing, food).
- **Festivals** (spend treasury → mood, social burst).

**Playable:** pass a curfew and watch the streets empty at night; see approval drop in the tavern district; answer petitions; throw a festival to win people back.

**Done when:** law-constraint scenario; approval responds to value alignment; strike behaviour GameTest.

---

## Phase 24 — Crime & Guards · M
**Goal:** crime happens, is witnessed, and is punished.

**Build**
- **Act catalogue v1 (~10 acts):** theft, assault, murder, trespass, vandalism, public drunkenness, tax evasion, poaching, blasphemy, desertion ([DESIGN §14.2](DESIGN.md#142-act-catalogue-initial)).
- **Act detection** for players and villagers ([ARCH §14](ARCHITECTURE.md#14-player-integration)).
- **Witnesses → reports → investigation → magistrate trial.**
- **Guards and guardhouse**; T0 pursuit and arrest; T2 statistical crime with named culprits.
- **Punishments:** light and moderate (fines, restitution, stocks, jail, labour sentence).
- **Villager crime propensity** (need × personality × deterrence).
- **Iron golems** as guards (vanilla integration).

**Playable:** steal from the baker and get chased by guards; sit in the stocks; watch a hungry villager steal bread and stand trial.

**Done when:** witness/report scenario; GameTest: player theft is detected and reported.

---

## Phase 25 — Writing the Law · M
**Goal:** the ruler decides what is a crime; cultures have their own codes.

**Build**
- **Law editor** with rich conditions (where, when, who, exemptions, severity, punishment) using the condition builder UI ([DESIGN §14.3](DESIGN.md#143-law-structure)).
- **Morality vs. legality:** prohibition effects, vigilantism, non-reporting ([DESIGN §14.4](DESIGN.md#144-morality-vs-legality)).
- **Default legal codes per culture.**
- **Severe punishments** behind the `punishments.maxSeverity` config (flogging, branding with visible marks, exile, execution).
- **Grief and revenge memories** from punishments.
- Full act catalogue.

**Playable:** outlaw drinking in a mining hold and watch secret taverns appear; brand a thief and see how the village treats them afterwards.

**Done when:** morality-vs-legality scenario; config gating GameTest.

---

## Phase 26 — Threats at the Gate · M
**Goal:** vanilla threats become part of village life.

**Build**
- **Pillager raids** target sim villages; guards and militia respond; fear and grief memories; chronicle entries ([ARCH §13.5](ARCHITECTURE.md#135-optional-vanilla-integrations)).
- **Hero of the Village** boosts sim reputation.
- **Zombie sieges;** infected villagers keep their sim identity, and **curing restores the same person**.
- **Bells:** alarm (go home, guards respond) or gathering.
- **Palisades and walls v1** as defence.

**Playable:** defend a village from a raid and become its hero; cure a zombified friend and get them back with their memories.

**Done when:** GameTests: raid targets a sim village; cure restores identity; each integration can be switched off in config.

---

## Phase 27 — Councils & Elections · M
**Goal:** governments beyond decree.

**Build**
- **Procedures:** council vote, referendum, veto ([ARCH §8.6](ARCHITECTURE.md#86-procedures-macro--micro)).
- **Government types:** chiefdom, monarchy, council/oligarchy, representative democracy (the rest in later phases).
- **Elected path:** campaigns, votes, terms ([DESIGN §13.3](DESIGN.md#133-government-types-data-driven)).
- **Villager politicians** with platforms from their values.
- **Multiplayer:** council seats for players, co-rule, voting against each other.
- Bribery as an act.

**Playable:** run for mayor, campaign, win or lose; as mayor, propose a law and watch the council vote it down.

**Done when:** election scenario; GameTest: a council vote with player and villager members.

---

## Phase 28 — Offices & Delegation · M
**Goal:** rule a big village through officials.

**Build**
- **Active offices:** treasurer, captain of the guard, magistrate, master builder, district governors ([DESIGN §13.6](DESIGN.md#136-offices--delegation)).
- Officials act on **standing orders** with their own skills and personality; loyalty, competence, basic corruption (embezzlement).
- **Regent** while the ruler is offline.
- **Appointed path** to rule.
- **Trial procedures** by government (magistrate, jury, trial by combat).

**Playable:** appoint a treasurer and a guard captain, log off, and come back to find the village governed, well or badly.

**Done when:** offline-governance scenario; corruption detectable in the ledger.

---

## Phase 29 — Chronicle, Map & Town Hall v2 · S
**Goal:** a ruler can understand a big village at a glance.

**Build**
- **Chronicle/legends viewer** with filters (people, district, event type).
- **District map v2** with all heatmaps (approval, crime, food, housing, wealth).
- **Town hall v2:** ledger charts, council panel, petition history.
- Chronicle selectivity rules (resolves DESIGN open question 6).

**Playable:** open the town hall and follow your village's history, finances and mood over time.

**Done when:** UI performance with a village of thousands; chronicle stays readable at high event rates.

---

# Milestone F — Faith, Knowledge & Society

## Phase 30 — Religion · M
**Goal:** faith shapes morality and daily life.

**Build**
- **Faiths** as data (tenets, sins and virtues, holy days, dietary rules) ([DESIGN §15](DESIGN.md#15-religion)).
- Temples and shrines as smart objects (Spiritual need); services as venues.
- **Clergy** career and offices; **theocracy** government.
- **Conversion** through gossip, family and missionaries; devotion levels.
- Sins feed the morality used by crime (Phase 25); heresy; tolerance/persecution laws.

**Playable:** build a temple, watch a new faith spread through the village, and see its sins change what villagers report to the guards.

**Done when:** conversion scenario; sins affect crime reporting.

---

## Phase 31 — Schools & Learning · M
**Goal:** education shapes careers and values.

**Build**
- **School levels:** village school, grammar school, university; apprenticeships; seminary and military academy ([DESIGN §16](DESIGN.md#16-education)).
- **Teacher bootstrapping** (teachers need a higher level than they teach).
- Education gates jobs and raises skill caps; values shift with education.
- **Skills** grow with practice and rust.
- Mandatory schooling law becomes real; tuition vs. public funding; libraries and books as goods.
- Classmate bonds.

**Playable:** open a school, see children attend, and watch the first educated generation take new jobs and start asking for more rights.

**Done when:** bootstrapping scenario; job gating tests.

---

## Phase 32 — Classes & Migration · M
**Goal:** a society with rich and poor, and people who move.

**Build**
- **Social classes** and **consumption tiers** ([DESIGN §17.2](DESIGN.md#172-consumption-tiers-by-class-anno)).
- Class mobility through wealth and education; sumptuary laws visible in outfits.
- More housing types (townhouse, tenement, manor, estate, almshouse).
- **Migration between villages**, driven by needs met, jobs and approval.
- **Vanilla villager migration** into sim villages (vanilla integration).
- Village stages up to **Town** and **City**.

**Playable:** see a noble quarter form, watch families climb the social ladder, and see people leave a badly run village for yours.

**Done when:** migration scenario between two villages; class mobility scenario.

---

## Phase 33 — Health & Medicine · S
**Goal:** health, illness and medicine affect lifespans. Rebuilt as the upgraded **reference addon** to prove the extensibility test ([ARCH §3](ARCHITECTURE.md#3-guiding-principles)).

**Build**
- Health and Illness components; herbalist, physician, hospital.
- Lifespan modifiers (medicine, wealth, occupation risk).
- Built only on the public APIs, without editing existing modules.

**Playable:** build a hospital, see villagers treated, and see lifespans rise in the chronicle statistics.

**Done when:** the addon builds against public APIs only; lifespan-modifier scenario.

---

# Milestone G — Realms

## Phase 34 — Trade & Caravans · M
**Goal:** villages trade with each other.

**Build**
- **Merchant caravans** on the road graph between villages, visible and robbable at T0 ([DESIGN §17.9](DESIGN.md#179-trade-between-villages)).
- Arbitrage evens out prices; caravans carry gossip and culture.
- **Wandering traders** as caravans (vanilla integration).
- **Bandits** (an act and a career).
- Tariffs.

**Playable:** follow a caravan to the next town, rob one (and face the consequences), or protect one for pay.

**Done when:** inter-village price convergence scenario; caravan tier-transition GameTest.

---

## Phase 35 — Coins & Credit · S
**Goal:** realms have their own money.

**Build**
- **Realm coins** and the mint; emeralds as the cross-realm standard ([DESIGN §17.7](DESIGN.md#177-money)).
- Inflation from over-minting; floating exchange rates.
- Moneylenders and banks, loans, interest, debt default (culture-dependent crime), debtors' prison.
- Counterfeiting as an act.

**Playable:** mint your village's coin, debase it to pay for walls, and watch prices rise.

**Done when:** inflation scenario; money invariants include minting.

---

## Phase 36 — Diplomacy · M
**Goal:** relations between realms.

**Build**
- Relations between villages; trade agreements, alliances, vassalage, embargoes ([DESIGN §18.1](DESIGN.md#181-diplomacy)).
- **Royal marriages** between ruling families.
- Diplomacy UI.
- Multiplayer: player-ruled villages negotiating.

**Playable:** sign a trade deal with a neighbouring town and marry your heir into its ruling family.

**Done when:** alliance and embargo effects in scenarios.

---

## Phase 37 — War · L
**Goal:** villages go to war.

**Build**
- **Casus belli** from existing systems ([DESIGN §18.2](DESIGN.md#182-casus-belli-from-existing-systems)).
- **Armies** from conscription and military offices; equipment from the armory.
- **Battles across tiers:** real combat at T0, abstract resolution at T2/T3 ([DESIGN §18.4](DESIGN.md#184-battles-use-the-tiers)).
- War memories, widows and orphans, chronicle entries.

**Playable:** raise an army, march on a rival town, and fight alongside your soldiers, or let the battle resolve while you're away.

**Done when:** battle resolution scenario; GameTest: a battle spanning a tier boundary.

---

## Phase 38 — Sieges & Conquest · M
**Goal:** wars can end with a village changing hands.

**Build**
- **Sieges:** blockades cut caravans; starvation collapses approval ([DESIGN §18.5](DESIGN.md#185-sieges--conquest)).
- **Conquest** and the **Seize path** (conquest or coup) to rule.
- **Resentment** in conquered villagers; cultural clash and drift.
- Siege workshop; walls, gates and towers v2.

**Playable:** besiege a walled town, starve it out, take it over, and then deal with a population that hates you.

**Done when:** siege and conquest scenario.

---

## Phase 39 — Dynasty & Succession · M
**Goal:** rule passes down through families.

**Build**
- **Succession laws** (eldest child, elected heir, council choice) ([DESIGN §13.8](DESIGN.md#138-dynasty)).
- **Inherit path** to rule (marry in, children become heirs).
- Player death or abdication: the heir rules as AI; the player can reclaim.
- Remaining government types (constitutional monarchy, merchant republic, direct democracy, junta, commune).

**Playable:** grow old as a ruler, pass the throne to your child, and watch your dynasty rule for generations.

**Done when:** multi-generation succession scenario.

---

## Phase 40 — Full Scale · L
**Goal:** 1M villagers in a real, playable world.

**Build**
- Thousands of villages generated and simulated in a real world.
- **Crowd figures (T0.5):** render-only villagers streamed to the client and drawn with instanced GeckoLib assets ([ARCH §12.4](ARCHITECTURE.md#124-puppet-entities)).
- Village stages up to **Metropolis**; mega-city districts.
- Optimisation pass from real-world profiling; SQLite tuning.
- Multiplayer load testing.

**Playable:** walk through a packed city market with hundreds of villagers on screen, in a world of a million, at playable FPS and 20 TPS.

**Done when:** 1M-villager world benchmark in game; crowd rendering FPS target met.

---

# Milestone H — Intrigue

## Phase 41 — Secrets & Knowledge · M
**Goal:** who knows what matters.

**Build**
- Secrets from the event log's knowledge links (affairs, crimes, embezzlement) ([DESIGN §19](DESIGN.md#19-macro--micro-compatibility-rules)).
- **Hidden plan entries** discoverable by others.
- **Blackmail** and **hooks/favours**.
- Personal ambitions for villagers.

**Playable:** discover that your treasurer has been embezzling, then decide whether to expose them or use it.

**Done when:** secret discovery scenario.

---

## Phase 42 — Schemes · M
**Goal:** villagers (and players) plot.

**Build**
- **Schemes** as plans with hidden entries: assassination, seduction, fabricated claims, coups.
- Co-conspirator bonds; discovery chances; consequences.
- Players can start and uncover schemes.

**Playable:** uncover a coup plotted by your guard captain, or plot one yourself against another player's regime.

**Done when:** scheme lifecycle scenario.

---

## Phase 43 — Factions & Politics · M
**Goal:** groups push the ruler.

**Build**
- **Factions** forming around shared values and grievances (guilds, clergy, nobles, peasants).
- Faction demands, ultimatums and revolts.
- Heirs as rivals; rival claimants.

**Playable:** balance the guilds, the clergy and the nobility, or face a faction revolt led by your own neglected child.

**Done when:** faction formation and revolt scenario.

---

## After Phase 43
Ideas that are out of scope until the phases above are done: more content packs (cultures, faiths, goods), more vanilla integrations, deeper KubeJS surface, a full script engine (only if a real need appears, [ARCH A14](ARCHITECTURE.md#2-architecture-decision-log)), and hosting the API on Maven ([ARCH A23](ARCHITECTURE.md#2-architecture-decision-log)).
