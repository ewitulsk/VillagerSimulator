# Villager Simulator — Design Document

> Status: **Draft v1** (brainstorm consolidation, 2026-09-28)
> Code architecture: see [ARCHITECTURE.md](ARCHITECTURE.md)
> Target: Minecraft 1.21.1, NeoForge 21.1.x
> Mod ID: `villagersimulator` · Package: `com.ewitulsk.villagersimulator`

A MineColonies-like village mod at massive scale: a simulation engine targeting **1,000,000 villagers** with Sims-style daily lives, Dwarf Fortress-style depth, and Crusader Kings-style rulership. The simulation keeps running when chunks are unloaded, and the player takes part in it: they form relationships, raise families, commit crimes, and rule.

Throughout this document:
- **[DECIDED]** means the user explicitly chose it during design discussions.
- **[PROPOSED]** means a design recommendation that was agreed in direction but not yet locked in detail.
- **[OPEN]** means an unresolved question (collected in [§22](#22-open-questions--risks)).

---

## Table of Contents
1. [Vision & Pillars](#1-vision--pillars)
2. [Decision Log](#2-decision-log)
3. [Inspirations](#3-inspirations)
4. [Simulation Architecture](#4-simulation-architecture)
5. [Time, Aging & Lifecycle](#5-time-aging--lifecycle)
6. [The Villager Model](#6-the-villager-model)
7. [Plans & Schedules](#7-plans--schedules)
8. [Relationships & Social Life](#8-relationships--social-life)
9. [Buildings & Smart Objects](#9-buildings--smart-objects)
10. [Construction & Mining](#10-construction--mining)
11. [The Player as a Sim Agent](#11-the-player-as-a-sim-agent)
12. [Villages: Districts, Cultures, Growth, Housing & Architecture](#12-villages-districts-cultures-growth-housing--architecture)
13. [Governance](#13-governance)
14. [Crime & Justice](#14-crime--justice)
15. [Religion](#15-religion)
16. [Education](#16-education)
17. [Economy](#17-economy)
18. [Diplomacy & War](#18-diplomacy--war)
19. [Macro → Micro Compatibility Rules](#19-macro--micro-compatibility-rules)
20. [Presentation: Models, Animation, UI](#20-presentation-models-animation-ui)
21. [Configuration](#21-configuration)
22. [Open Questions & Risks](#22-open-questions--risks)
23. [Roadmap](#23-roadmap)

---

## 1. Vision & Pillars

1. **Massive scale.** 1M simulated villagers across thousands of villages, with villages that can be *huge* (tens of thousands of residents).
2. **The world never stops.** The simulation continues when chunks are unloaded. Only its level of detail changes.
3. **Every villager is a person.** Each one has needs, personality, values, memories, skills, a daily plan, and social and romantic relationships. No villager is ever reduced to a statistic.
4. **The player takes part.** The player is an agent inside the simulation. They can befriend, marry and raise children with villagers, trade, commit crimes, and **rule**.
5. **Emergent story.** Drama comes from interacting systems (morality vs. law, gossip, ambition, scarcity), not scripts. A chronicle captures it so players can experience a sim they can't watch directly.
6. **Macro first, micro-ready.** Ship systemic, SimCity-style management first. Every system is built so Crusader Kings-style personal intrigue can be layered on later without rewrites.
7. **Grounded medieval setting.** **No magic.** [DECIDED]

---

## 2. Decision Log

| # | Topic | Decision | Status |
|---|---|---|---|
| D1 | Scale target | 1,000,000 villagers | DECIDED |
| D2 | Offline simulation | Sim continues in unloaded chunks | DECIDED |
| D3 | Sim architecture | Villagers are sim records, not entities; tiered level-of-detail (T0–T3) | PROPOSED (agreed) |
| D4 | Construction & mining offscreen | Deferred reconciliation + abstract resource yields | DECIDED ("really like") |
| D5 | Huge villages | Parts of one village can be at different tiers at once | DECIDED |
| D6 | Player in the sim | Player affects the sim and forms relationships with villagers | DECIDED |
| D7 | Relationships affect schedules | Yes: appointments, rituals, avoidance | DECIDED |
| D8 | Districts | **Semantic districts** (not a plain grid) | DECIDED |
| D9 | Player-villager children | Grow up into full sim villagers | DECIDED |
| D10 | Rulership | Player can rule a village | DECIDED |
| D11 | Paths to rule | All five: Found, Elected, Inherit, Appointed, Seize | DECIDED |
| D12 | Governance depth | CK-style; macro first with a **clear compatibility path** to micro | DECIDED |
| D13 | War | In scope | DECIDED |
| D14 | Government types | Multiple (democracy, monarchy, etc.), not just dictatorship | DECIDED |
| D15 | Multiplayer governance | Co-rule, council seats, citizens under another player's rule | DECIDED |
| D16 | Crime definitions | Defined crimes; the ruler decides what is a crime | DECIDED |
| D17 | Village crime culture | Pre-spawned villages get crimes based on type/vibe | DECIDED |
| D18 | Crime authoring model | Fixed **act catalogue + rich conditions** (no free-form acts) | DECIDED |
| D19 | Punishment range | **Very dark available**, plus light punishments | DECIDED |
| D20 | Religion | Full system | DECIDED |
| D21 | Education | Schools with levels | DECIDED |
| D22 | Economy | Full simulation (see §17) | DECIDED (direction) |
| D23 | Lifespan | Default **~5 real hours**, with individual variance, **server configurable** | DECIDED |
| D24 | Currency | Custom coins per realm + emeralds as the cross-realm standard | DECIDED |
| D25 | Magic | **None** | DECIDED |
| D26 | Village stages | Six stages (Camp → Metropolis) gated by population + institutions, **with decline and ruins** | DECIDED |
| D27 | Building content | **Hand-built landmarks + modular kits + palette swapping** | DECIDED |
| D28 | Structure authoring | Agentic building with **MinecraftStructureInjector (Structure Lab)** + an **in-game structure editor** | DECIDED |
| D29 | Moving out | **Culture-dependent**; default "stay until married or can afford a place" | DECIDED |
| D30 | Blocks | Custom blocks allowed, but **prefer vanilla whenever possible** | DECIDED |
| D31 | Graveyards | **Named graves for notable villagers**; everyone else in a browsable crypt/ossuary ledger | DECIDED |
| D32 | Households | Household (lives together, shares a budget) is separate from family (kinship); the **bed** is the unit of housing capacity | DECIDED |
| D33 | Building levels | Each building type has **levels 1–5**; buildings can be upgraded, repurposed, neglected, damaged and ruined | DECIDED |

---

## 3. Inspirations

| Source | What we take |
|---|---|
| **MineColonies** | Colony building, blueprints, citizen jobs, builder-driven construction |
| **The Sims** | Needs/motives, **smart objects & advertisements**, two-axis relationships, life stages, wants & fears, off-screen "story progression" |
| **Dwarf Fortress** | Personality facets & values, memories & thoughts, skills with rust, labours, stockpiles, production chains, legends/history, cultural drift |
| **Crusader Kings** | Dynasties, succession, councils/offices, schemes & secrets, casus belli, legitimacy |
| **Victoria 3** | Population groups, production methods, supply/demand-driven prices |
| **Anno** | Production chains, tiered consumption needs by social class |
| **SimCity** | Zoning; the sim builds to meet demand |
| **Doran & Parberry, "Emergent Economies for Role Playing Games" (Bazaar Bot)** | Agent price beliefs + clearing-house markets |

Cautionary note: Dwarf Fortress removed its original economy because it was too hard to balance. Economy stability must be designed and tested headless from day one (§17.10).

---

## 4. Simulation Architecture

### 4.1 Core principle
**Minecraft is a viewport onto the simulation, not the simulation itself.** A million entities is impossible. MineColonies already struggles at a few hundred full-AI citizens. Every villager is a compact data record in a custom simulation engine. Only villagers near players are *embodied* as entities.

### 4.2 Level-of-detail tiers

| Tier | Where | Fidelity | Update model |
|---|---|---|---|
| **T0 Embodied** | Near a player (~64 blocks) | Real entity, custom model & animations, block-level pathfinding, real block placement/mining, real item exchange | Every tick |
| **T1 Local** | Loaded chunks, no player nearby | No entity; moves along the road/building graph; needs & plans update | ~1 Hz |
| **T2 Abstract** | Unloaded chunks (≈99.9% of villagers) | Event-driven: jumps between plan waypoints; interactions resolved statistically per venue window | On events |
| **T3 Coarse** | Far from any player | **Still individual** (never aggregated away, because players can have relationships with anyone). Advanced in one batch per in-game day. Only the economy aggregates. | Daily batch |

### 4.3 Tiers attach to places, not villages [DECIDED: D5]
- A single huge village can have T0, T1, T2 and T3 regions at the same time.
- **Semantic districts** [DECIDED: D8] are the social, political and threading unit (e.g. "Market Quarter", "Mining District"). A district can itself be partly loaded, so **the loading tier is computed per chunk inside a district.**
  - *Districts for meaning, chunks for loading.*
- A villager's tier is the tier of the chunk they're in (plus attention pinning, §11.6). A commuter can pass T3 → T2 → T0 in one morning.

### 4.4 The plan is the source of truth
Every villager always holds a **timestamped itinerary** (§7). Tiers only change how the plan is carried out:
- T2 jumps between waypoints.
- T1 moves along the road graph.
- T0 walks it physically.

**Promotion:** spawn the entity where the plan says the villager should be *now*. **Demotion:** write the entity's state back into the record and continue the plan. This removes teleporting on tier transitions.

**Deviation:** when T0 reality breaks the plan (the player blocks a door, a mob attack, a conversation), the villager replans. Knock-on effects (being late for an appointment) go out as events to the villagers they affect, whatever tier those villagers are in.

### 4.5 The venue's tier sets interaction fidelity
Interactions are simulated at the fidelity of the **venue** (building or location), not the participants:
- **T0 venue:** embodied conversations and animations.
- **T2 venue:** everyone present during a time window is paired off statistically when the window closes.
- **The player enters mid-window:** the elapsed part is resolved statistically and the rest plays out live.
- A T0 villager entering a T2 venue is demoted for the duration.

### 4.6 Lazy evaluation
Needs and other continuous values are stored as `(value at t0, rate)` and evaluated only when a decision needs them. There is no per-tick decay loop.

### 4.7 Threading & partitioning
- **Standalone sim core:** plain Java, **no Minecraft imports**, runnable and benchmarkable headless, in its own Gradle subprojects (see [ARCHITECTURE.md §4](ARCHITECTURE.md#4-project-layout)). [DECIDED]
- **Data-oriented layout:** struct-of-arrays for hot fields and primitive collections; avoid per-villager object graphs in hot paths.
- **Partitioned by district** across worker threads. Traffic between districts (villagers crossing, appointments, gossip, trade) goes through **message queues**.
- **Conservative parallel discrete-event simulation:** districts synchronise at fixed sim-time boundaries (e.g. every sim-hour). Deterministic enough to debug.
- **Minecraft bridge layer:** promotion/demotion, block reconciliation, turning player actions into events, networking.

### 4.8 Movement
- **T1/T2/T3:** a road/building **graph** (nodes = buildings and intersections, edges = travel times).
- **T0 only:** block-level pathfinding (custom, cheaper than vanilla where possible).

### 4.9 Persistence
- Vanilla `SavedData` (one NBT blob) will not scale.
- **Custom storage: SQLite**, one database per world (see [ARCHITECTURE.md §17](ARCHITECTURE.md#17-persistence)). [DECIDED]
- **Format versioning from day one.**

### 4.10 Budgets (targets to validate in Phase 1)
| Item | Estimate |
|---|---|
| Core state per villager | ~150–300 bytes → 150–300 MB for 1M |
| Relationships | Sparse, ~30–50 significant per villager, packed encoding → ~400–600 MB (**main memory risk**) |
| T2 event rate | ~10–30 events/villager/in-game day → ~10–25k events/s at 1M |
| Full-fidelity cost (why tiers are necessary) | 1M × 20 TPS = 20M agent updates/s: impossible |
| Births/deaths at default lifespan | ~1M / 18,000 s ≈ **~55 births and ~55 deaths per real second** at steady state (§5) |

---

## 5. Time, Aging & Lifecycle

### 5.1 Two clocks [PROPOSED]
With the default lifespan of **~5 real hours** [DECIDED: D23] and a Minecraft day of 20 real minutes, a villager lives about **15 Minecraft days**. Daily schedules can't also serve as the aging clock, so there are two clocks, like The Sims:
- **Day clock:** the normal Minecraft day. Drives plans, shifts, sleep, shop hours, festivals.
- **Life clock:** drives aging. Default **1 life-year ≈ 4 real minutes** (1 MC day ≈ 5 life-years), so a ~75-year life lasts ~300 real minutes.

### 5.2 Life stages (defaults, configurable)
| Stage | Life-years | Real time (default) | Notes |
|---|---|---|---|
| Infant | 0–5 | ~20 min | Cared for by household |
| Child | 6–11 | ~24 min | Primary school |
| Adolescent | 12–17 | ~24 min | Secondary school / apprenticeship begins; first romances |
| Young adult | 18–24 | ~28 min | Higher education or trade; work; courtship |
| Adult | 25–59 | ~2.3 h | Career, family, politics |
| Elder | 60+ | ~1 h+ | Reduced work; wisdom; inheritance |

### 5.3 Lifespan variance
Individual lifespan = base × modifiers:
- **Genetics:** heritable longevity trait.
- **Health & wealth:** diet quality, housing, class.
- **Medicine:** access to physicians (a university-trained job) and hospitals.
- **Occupation risk:** miners, soldiers, guards.
- **Violence:** crime, war, **execution**.
- **Random variance:** configurable distribution.

### 5.4 Implications to design around
- **Generations turn over every ~1–2 real hours.** That's a great fit for dynasties and CK-style succession: players see heirs grow up within a session.
- **The player's spouse and friends will die within hours.** [OPEN] Options: a config setting for slower aging for player-bonded villagers, or simply embrace it (grief memories, inheritance, the player's children carry on).
- **Education and careers are compressed:** schooling levels last minutes, not hours (§16).
- **Birth/death volume is high** (~55/s each at 1M): allocation and ID recycling must be cheap and the chronicle must be selective.
- **Offline behaviour:** the sim runs while the server/world runs. When a single-player world is closed, time is frozen. [PROPOSED]

---

## 6. The Villager Model

| Component | Description |
|---|---|
| **Identity** | Name, family, household, home, birth/death, appearance genes (for custom models) |
| **Needs (Sims)** | Hunger, Energy, Social, Fun, Hygiene, Comfort, **Spiritual** (religion), Safety; lazily evaluated |
| **Personality facets (DF)** | Dozens of traits: greed, bravery, gregariousness, honesty, ambition, piety, etc. Heritable with variance. |
| **Values (DF)** | What the villager believes is right: law, tradition, family, wealth, faith, freedom, etc. Drives moral judgement (§14) and political opinion (§13). |
| **Morality** | Culture + religion + personality → which acts this villager considers wrong |
| **Memories & thoughts (DF)** | Timestamped events with mood effects that fade ("friend died −30, fades over a season"). Include *who was involved* and *who knows* (§19). |
| **Skills** | Grow with practice, rust with disuse; education raises caps and learning rate |
| **Education level** | None / Primary / Secondary / Higher, plus apprenticeships and specialist schools (§16) |
| **Social class** | Labourer / Artisan / Merchant / Noble (and Clergy); set by wealth + education (§17.2) |
| **Wants & fears** | Short-term goals that generate plans and **player quests** |
| **Plan** | Today's itinerary (§7) |
| **Relationships** | Sparse graph (§8) |
| **Opinions** | Of the ruler, officials, laws, the player (feeds legitimacy) |
| **Faith** | Religion + devotion level (§15) |
| **Legal record** | Crimes, sentences, status (branded, exiled, disenfranchised) |

---

## 7. Plans & Schedules

### 7.1 Daily plan generation
Each villager generates the next day's plan from layered inputs, highest priority first:

1. **Laws as constraints** (§13.5): curfew, day of rest, work-hour limits, mandatory schooling, conscription.
2. **Obligations:** work shifts, school, household chores, office duties, jail/labour sentences.
3. **Needs:** eat, sleep, hygiene, worship.
4. **Social intentions** [DECIDED: D7]:
   - **Appointments:** *shared* plan entries between two or more villagers (e.g. meet at the tavern at 18:00). If one is late, the other waits, gets annoyed or leaves.
   - **Rituals:** date nights, family dinners, weekly market trips, religious services.
   - **Care:** visiting sick friends or relatives, childcare.
   - **Avoidance:** skipping venues or routes where rivals or enemies will be.
5. **Player-driven entries:** player invitations become appointments.
6. **Free time:** chosen from smart-object advertisements (§9).

### 7.2 Hidden entries
Plans support **hidden entries** that other villagers can discover (secret meetings, affairs, plots, smuggling runs). They're unused in the macro phase, required for intrigue (§19).

### 7.3 Cross-tier behaviour
Appointments cross districts and tiers with no special handling. A T3 villager keeping a date with a T0 villager is promoted on arrival.

---

## 8. Relationships & Social Life

### 8.1 Relationship model
- **Two axes (Sims):** Friendship (−100…100) and Romance (0…100), plus **typed bonds**: family, spouse, ex, rival, mentor/apprentice, colleague, classmate, co-religionist, co-conspirator.
- **Sparse storage:** only significant relationships are stored; acquaintances decay out.
- **Relationships change schedules** (§7.1) and **job choice** (working alongside friends, avoiding a rival boss).

### 8.2 Romance & family
- Courtship → dating → marriage (subject to culture/religion marriage laws) → children.
- Divorce, affairs (hidden plan entries), jealousy.
- Households form on marriage. Children inherit traits and appearance genes from both parents.
- **Population balancing:** birth rates respond to housing, food, wealth and culture so the population doesn't explode.

### 8.3 Gossip
- Memories spread during social interactions, including T2 statistical ones.
- Gossip carries **reputation** (of the player, officials, other villagers), **political opinion**, **religious belief**, and **news** of crimes, scandals and heroics.
- It spreads at a realistic pace: word reaches the far side of a big village in days.

### 8.4 Social venues
Taverns, markets, temples, schools, festivals and workplaces are all venues where interactions (and gossip) happen.

---

## 9. Buildings & Smart Objects

### 9.1 Smart objects & advertisements (Sims)
Every building (and many objects inside it) **advertises** what it offers:
> Bakery: "Hunger +40, costs 3 coins, open 06:00–18:00"
> Tavern: "Social +30, Fun +20, costs 2 coins; Drinking"
> Temple: "Spiritual +50; Worship (faith X)"

Villagers score the advertisements they can reach against their needs, personality, values, wealth, laws and relationships, then pick one. **Adding a building type means writing its advertisements, with no villager AI changes.** Laws can switch advertisements off (e.g. a tavern ban).

### 9.2 Building catalogue
Buildings unlock by village stage (§12.4). Every type has **levels 1–5** (§10.7).

| Category | Buildings (unlock stage) |
|---|---|
| **Centre / civic** | Campfire (Camp) → Well (Hamlet) → Town hall (Village) → Guildhall (Town) → Palace / seat of government (Metropolis) · Guardhouse (Village) · Courthouse, jail, stocks, gallows, mint (Town) |
| **Housing** | Tent, lean-to, hovel, cottage, family house, townhouse, tenement, manor, noble estate, boarding house, almshouse, barracks (see §12.7) |
| **Food** | Farm field, orchard, ranch, fishery, windmill/watermill, bakery, butcher (Hamlet) · Brewery, winery, dairy (Village) · Market stalls (Hamlet) → Market hall (Town) → Grand bazaar (Metropolis) |
| **Industry** | Lumber camp, quarry, mine (Hamlet) · Smithy, tannery, weaver, carpenter, mason (Hamlet–Village) · Smelter, tailor, armory, jeweller, glassworks (Village–Town) · Shipyard (Town, coastal) |
| **Commerce** | General store, warehouse, trading post (Village) · Bank/moneylender, merchant guild (Town) · Docks and harbour (coastal) |
| **Religion** | Shrine (Hamlet) → Chapel (Village) → Temple (Town) → Cathedral (City) · Monastery, seminary · Graveyard → Crypt / ossuary |
| **Education** | Village school (Village) · Grammar school/academy (Town) · University (City) · Library → Great library · Military academy |
| **Health** | Herbalist (Hamlet) · Physician (Town) · Hospital (City) |
| **Social** | Tavern, inn (Village) · Bathhouse (Town) · Theatre (City) · Festival grounds · Arena (Metropolis) |
| **Military & defence** | Palisade (Hamlet) → Stone walls, gates, towers (Town) → City walls (City) · Barracks, training yard, stables (Village–Town) · Keep → Castle · Siege workshop |
| **Infrastructure** | Roads, bridges, plazas, fountains, lamp posts, docks, canals, aqueduct (City) |

**Graveyards** [DECIDED: D31]. With ~5-hour lives, 1M villagers produce ~55 deaths per second worldwide; a town of 2,000 buries someone every ~9 seconds. Physical graves for everyone would swallow the map, so:
- **Named, physical graves** only for notable villagers: the player's family and friends, rulers, heroes, famous craftsmen, anyone with a chronicle entry.
- Everyone else is recorded in a **crypt / ossuary ledger** that players can browse (name, family, dates, cause of death, epitaph).
- Graveyards look full and lived-in but don't grow without bound; they upgrade to crypts and ossuaries as the village grows.

### 9.3 Buildings as firms
Productive buildings are businesses with an owner (§17.3), staff, inventory and hours.

---

## 10. Construction & Mining

[DECIDED: D4]

### 10.1 Blueprints
- MineColonies-style blueprints, **split per chunk section**.
- Builders physically place blocks in T0 sections. Unloaded sections advance abstractly (progress + consumed materials).
- A single building (e.g. a cathedral) can span tiers.

### 10.2 Deferred reconciliation
- The sim records the *intended* world state per section (e.g. "house 60% built", "tunnel extended 40 blocks").
- **When a chunk loads, the bridge applies the difference:** places the blueprint blocks up to current progress, carves tunnels, removes harvested trees.
- *The world catches up to the story.*

### 10.3 Abstract resource yields
- Mine and quarry output is drawn from a **statistical model of the local geology**, sampled from worldgen noise **without generating chunks**.
- Farms: farmland area × biome × season × skill. Logging: forest cover.
- **Depletion is real.** Mines exhaust veins and must extend, which creates new tunnel work to reconcile.

### 10.4 Zoning-driven construction
- The ruler zones districts (§13.4). **Builders fill zones on their own based on demand** (price and housing signals, §17).
- The player can **commission** specific buildings and blueprints, overriding demand.

### 10.5 Who builds
Three sources, all producing the same thing, a **construction project** (plot + blueprint + materials + builders):
1. **The village planner (sim AI)** builds to meet demand within zones: a housing shortage leads to houses, expensive bread to a bakery, a district with no well within walking distance to a well.
2. **The ruler:** zoning and specific commissions (§13.4).
3. **Private owners:** a rich family builds a manor, a successful baker opens a second shop, a guild funds its hall.

Projects reserve a plot, source materials from stockpiles and markets (§17), and are worked by builders (a job). Progress is physical at T0 and reconciled elsewhere (§10.2).

### 10.6 Plots, roads & walls
- **Villages grow along roads.** Road hierarchy: footpath → dirt road → gravel road → paved street → avenue. Roads are **upgraded as traffic grows**; traffic is measured from the road graph villagers already travel.
- **Plots** are laid out along roads. Buildings go on plots that fit their footprint; plots in busy districts are **subdivided as land value rises**.
- **Squares and plazas** form at important crossroads and become market or temple squares.
- **Walls grow in rings:** palisade → stone wall. When the town outgrows it, a new ring goes up further out and the old one stays inside, as in real medieval cities.
- Terrain is adapted per plot (foundations, terracing, stilts, retaining walls) according to culture and biome.

### 10.7 Building lifecycle
[DECIDED: D33] **Planned → under construction → active → upgraded → (repurposed / neglected / damaged) → demolished or ruined**
- **Levels 1–5** per building type (MineColonies-style). Higher levels change the blueprint, raise capacity and output quality, and upgrade materials (§12.8).
- **Repurposing:** a failed bakery can be bought and turned into a tavern. The shell stays; the interior, sign and advertisements change.
- **Neglect:** unmaintained buildings visibly decay (cracked stone, broken windows, cobwebs, overgrowth) and can be repaired.
- **Damage:** fire, war, raids and zombie sieges. Rebuilding creates construction jobs.
- **Ruins:** buildings in abandoned villages, or destroyed and never rebuilt, become ruins (§12.4).
- All transitions go through reconciliation, so they play out while the player is away.

---

## 11. The Player as a Sim Agent

[DECIDED: D6]

### 11.1 Player record
The player has a sim record like a villager's: relationships, reputation (per village, spread by gossip), home, household, job/offices, legal record, faith, wealth.

### 11.2 Affecting the simulation
- **Economy:** selling and buying changes supply and prices; owning firms; trading between villages.
- **Construction:** commissioning, funding and supplying buildings.
- **Employment:** hiring villagers, running shops.
- **Governance:** full rulership (§13).
- **Crime:** any act in the catalogue (§14), judged under the local code.

### 11.3 Relationships with villagers
- Conversation/dialogue, gifts, favours, shared activities, invitations (which become appointments).
- **Friendship, romance, marriage** with villagers.
- **Companions** who follow the player (kept at T0).
- Villagers **remember the player while offline**: they miss them, and relationships drift.

### 11.4 Children
[DECIDED: D9] Player-villager children are full sim villagers. They inherit traits and appearance from both parents; the player's "traits" come from how they actually play (generous, violent, pious, etc.). They go to school, form their own relationships, and are **heirs** (§13.8).

### 11.5 Quests
Generated from villagers' **real wants** (the baker needs wheat, the smith wants rare ore, a family seeks justice), never generic.

### 11.6 Attention pinning
Villagers who matter to the player (spouse, children, close friends, active quest-givers, officials) are **pinned to at least T1** wherever they are, so their stories stay detailed. There's a "What's new" feed and a chronicle of their lives. The cost is tiny (dozens per player).

---

## 12. Villages: Districts, Cultures, Growth, Housing & Architecture

### 12.1 Districts
[DECIDED: D8] Semantic, named districts with a zoning purpose, their own approval rating, crime rate, character and gossip network. They are the unit of government (district governors), statistics and threading.

### 12.2 Village cultures
Every pre-spawned village has a **culture archetype** [DECIDED: D17] that sets default **laws, moral values, preferred government, religion tendencies and building mix**:

| Culture | Typical crimes | Notably legal | Default government |
|---|---|---|---|
| **Pious / monastic** | Blasphemy, public drunkenness, working on the holy day, gambling | Religious tithes | Theocracy |
| **Merchant town** | Fraud, breach of contract, smuggling, tariff evasion | Gambling, moneylending | Merchant republic / guild council |
| **Mining hold** | Shirking shifts, mine sabotage, hoarding ore, wasting supports | Heavy drinking, brawling | Council of mine-masters |
| **Martial / warrior** | Cowardice, desertion, refusing conscription | Duelling | Military chiefdom |
| **Farming hamlet** | Poaching, livestock theft, trampling fields, arson | Most things; few laws | Village elder |
| **Aristocratic city** | Commoners in noble quarter, insolence to nobility, dressing above your station | Noble privileges | Monarchy |
| **Outlaw haven** | Betraying the crew, **snitching** | Theft from outsiders, smuggling | Strongman / pirate code |
| **Scholarly** | Book theft, unlicensed alchemy (chemistry; no magic) | Open debate, even blasphemy | Council of scholars |

- Biome adds regional flavour (architecture, diet, goods).
- **Cultural drift (DF):** immigration mixes values, and long-standing laws slowly change morality.
- **Data-driven:** cultures are datapack definitions so modpacks can add their own.

### 12.3 World population
- ~5,000 villages at ~200 average, but sizes vary widely, including mega-cities of tens of thousands.
- Populations come from worldgen placement plus organic growth (founding, migration, births). [OPEN: ratio]

### 12.4 Village stages
[DECIDED: D26] A village's stage depends on **population and institutions** (Anno/Civilization-style): reaching a stage needs both a population threshold and certain buildings.

| Stage | Population | Requires | Unlocks |
|---|---|---|---|
| **Camp** | 3–15 | A campfire and a claimed site | Tents, lean-tos, a well |
| **Hamlet** | 15–50 | Houses, a farm, a well | Basic trades (bakery, smithy), shrine, market stalls, palisade |
| **Village** | 50–250 | Chapel, tavern, village school | First districts, guilds, town hall → **council government** |
| **Town** | 250–2,500 | Town hall, market hall, walls | Multiple districts, grammar school, temple, courthouse, mint, **democracy / republic** |
| **City** | 2,500–25,000 | Cathedral or great temple, university | Guild halls, hospital, theatre, city walls with towers, noble quarter |
| **Metropolis / capital** | 25,000+ | Palace or grand seat of government | Arena, grand bazaar, great library, vassal villages |

**Decline and ruins.** Famine, plague, war or economic collapse can lower a village's stage (institutions close, districts empty, buildings decay). A village that empties completely becomes **ruins**. Ruins keep their chronicle, so players can discover the history of a dead town, and they can be resettled.

### 12.5 Layout by culture
| Culture | Layout |
|---|---|
| Farming hamlet | Scattered farmsteads along a road, a small centre |
| Merchant town | Along a river or coast; docks, dense market core, warehouses |
| Mining hold | Partly carved into a mountain or cliff; tunnels are part of the village |
| Pious / monastic | Built around a hilltop monastery or temple |
| Aristocratic city | Castle at the centre, concentric walls, noble quarter near the castle |
| Martial | Fort or keep first; barracks, training yards, tight walled layout |
| Outlaw haven | Hidden (cove, swamp, canyon); stilt houses, docks, caves |
| Scholarly | Campus-like core of towers and libraries |

### 12.6 Families & households
[DECIDED: D32]
- **Family** = kinship (parents, children, grandparents, cousins). Part of the relationship graph (§8); can span many households.
- **Household** = the people who **live together and share a budget**. The economic unit (§17.3) and what a home is assigned to.
- **Typical household: 2–6 people.** Usually a couple and their children; some cultures add grandparents or unmarried siblings (**extended households**).
- **Children per couple:** a distribution averaging roughly 2–4, shaped by culture, faith, wealth, housing space and approval of the ruler.
- **Timing:** at the default lifespan, adulthood lasts ~2.3 real hours (§5.2), so a couple has about an hour to have children. Families form and grow within one long session.

**Moving out** [DECIDED: D29] is culture-dependent:
- **Default:** young adults stay home until they **marry or can afford their own place**, then form a new household and move out.
- **Individualist cultures** (merchant, scholarly) push young adults out early. **Family cultures** (farming, pious) keep extended households; a son may bring his wife home to the family farm.
- **Life paths bring their own housing:** apprentices at the master's workshop, students in university halls, soldiers in barracks, clergy in the monastery, single workers in a **boarding house, inn or tenement**.
- **Elders** stay in their home, move in with a child, or go to an almshouse or monastery.
- **Inheritance:** when owners die, the home passes to heirs (§13.8, §17.8).
- **No free housing:** stay with parents (overcrowding → unhappiness), rent a room at an inn, or become **homeless** (sleeping rough → crime and petitions). This pressure drives the village planner to build (§10.5).

### 12.7 Housing
- **The bed is the unit of housing capacity.** A blueprint's beds decide how many people it holds (vanilla beds are detected automatically, [ARCHITECTURE §13.5](ARCHITECTURE.md#135-optional-vanilla-integrations)).
- Households **own, rent, or are assigned** homes (state housing, barracks, servants' quarters).

| Type | Beds | For |
|---|---|---|
| Tent / lean-to | 1–2 | Camp stage, the poorest |
| Hovel | 2–3 | Labourers |
| Cottage | 3–5 | Rural families |
| Family house | 4–6 | Artisans |
| Townhouse (row house, shared walls, 2–3 storeys) | 4–6 | Dense town districts |
| Tenement (several households) | 12–30 | Dense poor districts in cities |
| Boarding house | 6–20 single beds | Single workers, newcomers |
| Manor | 6–10 + servants | Merchants, lesser nobles |
| Noble estate / palace | 10+ + servants | Nobility, rulers |
| Barracks / dormitory / cloister | 10–50 | Soldiers, students, clergy |
| Almshouse | 6–20 | Elders, the destitute |

### 12.8 Architecture & look
**Style = culture × biome × wealth/level.**

| Culture | Look |
|---|---|
| Farming hamlet | Thatch roofs, wattle and daub, timber frames, fences, haystacks |
| Merchant town | Tall timber-frame houses with overhanging upper floors, coloured plaster, shopfronts with hanging signs, busy docks |
| Mining hold | Heavy stone and deepslate, built into cliffs, lantern-lit tunnels, mine carts |
| Pious / monastic | Whitewashed stone, bell towers, cloisters, gardens |
| Martial | Palisades to stone keeps, towers, banners, training yards |
| Aristocratic | Brick and dressed stone, formal gardens, manors, a castle |
| Outlaw haven | Ramshackle wood, stilts, rope bridges, hidden caves |
| Scholarly | Stone towers, domes, libraries, observatories |

- **Biome variants:** desert (sandstone, terracotta, flat roofs), snow (spruce, steep roofs), jungle and swamp (stilts), badlands (terracotta), cherry grove (cherry wood, pink accents), etc.
- **Wealth and level show in materials:** the same bakery goes from wattle and thatch (level 1) to timber frame (level 3) to stone with glass windows (level 5).
- **Living decoration** reflects sim state (applied through reconciliation):
  - market stalls **stocked** in good times, **empty in a famine**
  - banners and flowers during festivals; black cloth after a ruler's death
  - decay when neglected, scorch marks after fire, damage after raids
- **Shop signs** show the product (bread, sword, tankard) so players can read a town at a glance.
- **Furnished interiors** everywhere: embodied villagers need real workstations, tables and beds to animate at.

**Blocks** [DECIDED: D30]: **prefer vanilla blocks whenever possible.** Custom blocks only where vanilla can't express something essential, for example:
- functional blocks: building anchor, market stall, shop sign, stocks/pillory, gallows
- a small set of decorative blocks that vanilla lacks and the look depends on (e.g. **thatch**)

Every custom block needs a written reason; vanilla-only builds should still read correctly.

### 12.9 Content pipeline
[DECIDED: D27, D28] Building types × levels × cultures × biomes adds up to thousands of variants, far too many to hand-build. The pipeline has three layers:

1. **Hand-built landmarks:** town hall, cathedral, castle, palace, university, guildhalls. A few per culture, high quality.
2. **Modular kits** for everything else: houses, shops and workshops **assembled from authored pieces** (foundations, wall bays, roofs, facades, interior sets), sized to the plot.
3. **Palette swapping:** pieces are authored once in a **neutral palette** and remapped per culture, biome and level (oak → spruce, cobblestone → sandstone, thatch → tile). One piece set covers many looks.

**Format:** vanilla structure `.nbt` + metadata JSON (workstations, beds, doors, storage, capacity, kit sockets), per [ARCHITECTURE §13.1](ARCHITECTURE.md#131-buildings).

**Authoring tools:**
- **Structure Lab (`D:\MinecraftMods\MinecraftStructureInjector`)** for **agentic building**. It's a NeoForge 1.21.1 mod plus an MCP server that lets an AI agent:
  - generate `.nbt` structures (Python builder library or JSON `set`/`fill`/`room` operations),
  - validate them against live registries,
  - place each revision in a dedicated superflat lab world and capture labelled screenshots for review,
  - run **native walking tests** (a real player hitbox walking defined routes), which we'll use to prove villagers can path through doors, stairs and workstations,
  - export clean `.nbt` + datapack files.

  Landmarks, kit pieces and interior sets are built and reviewed in this loop, then given our metadata JSON.
- **In-game structure editor** (our mod) for players, builders and pack makers:
  - capture a region into a blueprint or kit piece
  - place and edit metadata markers visually: workstations, beds, doors, storage, kit sockets, sign and decoration slots
  - mark palette roles (e.g. "primary wood", "roof", "wall fill") for palette swapping
  - preview a piece in every culture × biome × level palette
  - validate (walkability, capacity, required markers) and export to a datapack

---
---

## 13. Governance

[DECIDED: D10, D11, D12, D14, D15]

### 13.1 Paths to rule (all five)
| Path | How | Starting legitimacy |
|---|---|---|
| **Found** | Place a town hall charter in the wild; attract settlers | High, small village |
| **Elected** | Build reputation (via gossip), campaign, win a vote | Solid; must be re-won |
| **Inherit** | Marry into a ruling family; children become heirs | Depends on the family's standing |
| **Appointed** | Serve as an official; promoted by the current ruler | Tied to the patron |
| **Seize** | Coup or conquest (§18) | Low: expect unrest |

### 13.2 Legitimacy
Each villager holds an **opinion of the ruler**, from:
- **Outcomes:** food, safety, prosperity, housing.
- **Values alignment:** each villager judges the laws by their own values.
- **Personal relationships:** friends and family support the ruler.
- **Gossip.**

These combine into **approval per district**. Low approval escalates: grumbling → petitions → strikes (skipped shifts) → emigration → rival candidates → revolt/coup. A deposed player becomes an ordinary citizen or is exiled, and the sim continues.

### 13.3 Government types (data-driven)
A government definition specifies:
- **Power holders:** ruler, council, assembly, clergy, guild masters.
- **Decision procedure:** decree, council majority, referendum, veto rights.
- **Office selection:** hereditary, elected, appointed, merit, strength.
- **Term lengths & succession rules.**
- **Constitutional limits:** locked laws, supermajority requirements.

Initial set: chiefdom, absolute monarchy, constitutional monarchy, merchant republic (oligarchy), representative democracy, direct democracy, theocracy, military junta, commune.

- **In a monarchy** the ruler decrees. **In a democracy** the ruler proposes, the council votes on their values and relationships, and proposals can fail. Campaigning, favours and (illegal) bribery all play in.
- **Villager politicians** run on platforms drawn from their values.
- **Government change** through legal reform or revolution.
- **Multiplayer:** players can co-rule, hold council seats, vote against each other, run for office against villagers and other players, or live as citizens under another player's rule.

### 13.4 Tools of rule
- **Zoning:** paint districts with a purpose (residential, market, industrial, farmland, noble quarter, religious, military). Builders fill by demand; the ruler can commission specific builds.
- **Treasury & taxes:** income, sales, property and tariffs; minting (§17.7).
- **Spending:** construction, guard wages, public services (wells, schools, hospitals), festivals, officials' salaries, army.
- **Laws:** see §13.5 and §14.
- **Offices:** see §13.6.
- **Justice:** judge cases or delegate (§14).
- **Festivals & events:** harvest festival, royal wedding, tournament. Mood boost, social burst (more romance and growth), legitimacy bump.
- **Diplomacy & war** (§18).

### 13.5 Laws that change schedules
Laws are **constraints injected into the plan generator** (§7.1), so their effects are visible:

| Law | Effect |
|---|---|
| Curfew | No outdoor plan entries after hour X |
| Mandatory schooling | Children get school blocks; child labour disappears |
| Work-hour limits | Shorter shifts, more leisure, less output |
| Temperance / tavern ban | Tavern advertisements off; social life moves elsewhere (and underground) |
| Day of rest | No work entries one day per week |
| Conscription | Some adults assigned guard/army duty |
| Border policy | Controls immigration and emigration |
| Education restrictions | E.g. higher education for nobility only |
| Marriage laws | Who may marry whom |
| Succession law | Eldest child / elected heir / council choice |

The law book UI shows **projected reactions** before enactment (e.g. "Farmers −12, Clergy +20").

### 13.6 Offices & delegation
- Treasurer, captain of the guard, magistrate, master builder, high priest, headmaster, marshal, **district governors**.
- Officials carry out delegated decisions using their own skills and personality. They can be **loyal, incompetent, ambitious or corrupt** (embezzlement, plotting).
- **Offline ruler:** officials or a regent (spouse/heir) govern by the ruler's **standing orders**.

### 13.7 Petitions
Villagers bring **requests generated from real, aggregated state**:
- "The market district has no well; people walk 400 blocks for water." (travel-time data)
- "Grain reserves will run out in 6 days." (economy)
- "The Harlow family demands justice." (crime event)
- "Miners want safety supports after the collapse." (memory event)

Granting, denying or ignoring each affects approval. Petitions turn a huge sim into a short list of meaningful decisions.

### 13.8 Dynasty
- The spouse can be regent; children are heirs; succession rules are a law.
- **Heirs can be rivals:** a neglected, ambitious child may scheme against the ruler.
- On player death or abdication, the heir takes over as an AI ruler. The player may try to reclaim the throne.
- With ~5-hour lifespans, several generations pass in a long play session (§5.4).

### 13.9 Ruler's interface (town hall)
- **Map:** districts with heatmaps for approval, crime, food, housing, wealth, faith and education.
- **Ledger:** income, spending, reserves over time.
- **Law book:** active laws, drafting, projected reactions.
- **Council:** officials, loyalty, performance.
- **Petitions inbox.**
- **Chronicle:** notable history.

---

## 14. Crime & Justice

[DECIDED: D16, D17, D18, D19]

### 14.1 Acts vs. laws
- **Acts** are sim-detectable things villagers or players do. They're a **fixed catalogue** [DECIDED: D18], defined as data so addons can add acts.
- **Laws** classify acts as crimes with **rich conditions**. The ruler decides what is a crime.

### 14.2 Act catalogue (initial)
Theft, burglary, robbery, assault, murder, trespass, vandalism, arson, public drunkenness, brawling, duelling, blasphemy, heresy, working on the holy day, gambling, poaching, livestock theft, smuggling, tariff evasion, tax evasion, fraud, counterfeiting, bribery, embezzlement, breach of contract, debt default, desertion, cowardice, refusing conscription, insolence to authority, sumptuary violation (dressing above station), adultery, snitching/informing, treason, sedition, jailbreak, grave robbing, bandit raiding.

(The MVP starts with ~10; see §23.)

### 14.3 Law structure
```
Law: "Night Trespass in the Noble Quarter"
  Act:        trespass
  Where:      district = Noble Quarter
  When:       22:00–06:00
  Applies to: class = commoner
  Exempt:     guards, nobility, servants on duty
  Severity:   moderate
  Punishment: fine 20 coins OR 3 days in the stocks
  Enforcement priority: high
```
**Condition dimensions:** location/district, time and day, actor (class, faith, office, citizenship, age), target/victim, item/good involved, repeat-offender status. **Exemptions:** offices, classes, the ruler ("above the law", at a steep legitimacy cost).

### 14.4 Morality vs. legality
Villagers judge acts by **their own morality** (culture + religion + personality), separate from the law:

| Situation | Result |
|---|---|
| Criminalising what the culture accepts | Widespread lawbreaking, black markets, witnesses refuse to report, approval loss (prohibition) |
| Legalising what the culture abhors | Vigilantism, shunning, clergy agitation |
| Law and morality agree | Fast reporting, popular punishments |

**Propensity to offend** = need (hunger, debt) × personality (honesty, greed, temper) × deterrence (chance of being caught × severity).

### 14.5 Enforcement pipeline
**Act → witnessed? → reported? → investigated → accused → trial → sentence → consequences**
- **Reporting** depends on the witness's morality, relationship to the offender (friends may stay quiet, rivals may report trivia) and culture (snitching is itself a crime in outlaw havens).
- **T0:** guards physically pursue and arrest. **T2/T3:** statistical resolution per district (poverty, patrol coverage, culture), but **every crime still has a named culprit and victim**, which can surface as petitions, gossip or memories.
- **The player is subject to the local law** in every village.

### 14.6 Trial procedures (by government/culture)
Ruler's judgement, magistrate, jury of villagers, trial by combat, religious court.

### 14.7 Punishments
[DECIDED: D19] A spectrum from light to very dark. The harshest tiers are gated by server config (§21).

| Tier | Punishments |
|---|---|
| **Light** | Warning, public apology, restitution to the victim, community service, fines |
| **Moderate** | Stocks/pillory (public shaming, social hit), jail (removed from plans for N days), labour sentence (e.g. assigned to the mine), confiscation, loss of rights (can't vote or hold office), demotion from office/class |
| **Severe** | Flogging, **branding** (visible mark on the model + permanent reputation hit), mutilation, exile (forced migration), execution (hanging, beheading, and culture-specific methods) |

Also: **pardons** and **clemency**.

**Consequences:** harsh punishments deter but frighten or anger villagers depending on values; the victim's and punished person's families form grief/anger memories, and possibly **revenge motives**.

---

## 15. Religion

[DECIDED: D20] A full system, **no supernatural effects** (D25). Faith is belief, institution and culture.

- **Faiths** are data-defined: deities, tenets, **sins & virtues** (a major input to morality, §14.4), holy days (put into plans), dietary rules (affect demand), marriage rules, burial customs, clergy structure.
- **Temples** are smart objects satisfying the Spiritual need; services are social venues.
- **Clergy:** a career path and office hierarchy (priest → high priest); seminaries train clergy (§16).
- **Spread:** beliefs travel through gossip, family, schooling and **missionaries**.
- **Devotion levels** per villager affect how strongly tenets bind morality.
- **Dynamics:** schisms, heresy (an act), religious tolerance or persecution laws, tithes (an economic flow), **holy war** as a casus belli (§18).
- **Theocracy** as a government type; clergy as a political power bloc elsewhere.

---

## 16. Education

[DECIDED: D21]

### 16.1 Levels
| Level | Building | Teaches | Unlocks |
|---|---|---|---|
| **Primary** | Village school | Literacy, numbers | Clerk, shopkeeper, basic trades |
| **Secondary** | Grammar school / academy | History, rhetoric, bookkeeping | Merchant, official, primary teacher |
| **Higher** | University (faculties: law, medicine, engineering, theology) | Specialised knowledge | Magistrate, physician, master builder, secondary teacher |
| **Apprenticeship** | Any workshop | Practical skill (DF-style learning by doing) | Master craftsman (no literacy needed) |
| **Specialist** | Seminary, military academy | Clergy, officers | High clergy, generals |

Timing is compressed by the life clock (§5): e.g. primary school ≈ ages 6–11 ≈ ~24 real minutes by default.

### 16.2 How education connects to other systems
- **Bootstrapping:** teachers need a higher level than they teach. New villages must import scholars or send children elsewhere, giving a natural growth arc.
- **Jobs & skills:** education gates jobs and raises skill caps and learning rates.
- **Values shift:** the educated tend toward tolerance, religious scepticism and political demands. They're more productive but **harder to rule as a tyrant.**
- **Class mobility:** education is the main way up (§17.2).
- **Social:** schools are venues; classmate bonds last into adulthood.
- **Economy:** schools use books and supplies and pay teachers; tuition vs. public funding is a policy.
- **Law & culture:** mandatory schooling, noble-only education, religious curriculum, banned faculties.
- The player's children attend; the player can teach.
- **Libraries** store books (a good) and boost learning.

---

## 17. Economy

[DECIDED: D22, D24]

### 17.1 Goods
- **~100–200 goods** at launch, in chains: raw → intermediate → finished (wheat → flour → bread; iron ore → ingot → sword).
- **Tags** so categories can meet needs: `food`, `drink`, `luxury`, `tool`, `weapon`, `armor`, `clothing`, `building_material`, `religious`, `book`.
- **Perishability** (bread spoils, ore doesn't): drives storage and logistics.
- **Quality levels** from the maker's skill.

### 17.2 Consumption tiers by class (Anno)
| Class | Wants |
|---|---|
| Labourer | Bread, fish, basic clothes, beer |
| Artisan | Meat, good clothes, tools, books |
| Merchant | Wine, fine clothes, furniture, schooling |
| Noble | Jewellery, spices, art, servants |
| Clergy | Religious goods, books, alms to give |

Class comes from wealth + education. Meeting a class's needs brings happiness and **draws migrants**; failing brings unrest.

### 17.3 Actors
- **Households** are the buying and saving unit (~250k at 1M villagers): income, savings, rent or property, shopping list from members' needs and class.
- **Firms:** each productive building is a business owned by a **villager, player, the state, a guild or a temple**. It buys inputs, hires, sets prices, earns or loses, **expands** (commissions a builder) or **goes bankrupt**.
- **The state:** treasury, taxes, public works, state-owned firms. Government type sets the private/state mix.

### 17.4 Markets & prices
- **A market per district**, plus a central exchange per village.
- **Periodic clearing** (per sim-hour; daily at T3). Firms post supply, households post demand, and prices adjust toward balance (Bazaar Bot-style belief ranges / double auction).
- **Prices are signals across the whole sim:** a high bread price leads to a new bakery in the commercial zone; high wages pull workers; price gaps start caravans.
- **T0 purchases are real** item exchanges, recorded in the same ledgers.

### 17.5 Goods are physical
- Stockpiles and shop inventories are **one set of data with two views:** a ledger at T2, real containers at T0.
- **Theft is real** and the sim notices.
- **Raw materials come from the world** (§10.3), with real depletion.

### 17.6 Labour market
- Firms post jobs (wage, skill, education, guild requirements).
- Villagers choose by pay, fit, values, commute distance and relationships.
- Unemployment, shortages, **strikes**, **guilds** (entry control, wage setting).

### 17.7 Money
[DECIDED: D24]
- **Each realm can mint its own coin** (mint building); **emeralds are the cross-realm standard.**
- **Over-minting causes inflation;** debasing coinage to fund a war is a real ruler temptation.
- **Floating exchange rates** based on trade balance and trust.
- **Credit (later phase):** moneylenders and banks, loans, interest, debt. Default can be a crime depending on culture; debtors' prison as a punishment.

### 17.8 Housing & property
- Houses are owned, rented or state-owned; rent follows district demand.
- Shortage → rising rent → overcrowding → unhappiness → petitions or emigration.
- **Inheritance** passes property down families (dynasties, player inheritance).

### 17.9 Trade between villages
- **Merchant caravans** travel the road graph: visible and robbable at T0, simulated at T2. They profit from price differences, evening out prices and spreading goods, culture and gossip.
- **Trade agreements, tariffs, embargoes, blockades.**
- **Bandits and piracy:** an act in the catalogue and a career in outlaw havens.

### 17.10 Stability safeguards
- **Explicit money sources and sinks,** tracked globally. Sources: minting, player sales, state spending. Sinks: taxes, decay, luxury consumption, fines, bribes, tithes.
- Price bounds and inventory caps.
- Perishability and wear as natural sinks.
- **Headless economy soak tests in Phase 1:** 1,000 villages × 100 sim-years, charting prices, inflation and wealth inequality.

### 17.11 Scale
| Thing | Count | Cost |
|---|---|---|
| Households | ~250k | Small demand vector each, summed per district |
| Firms | ~100–200k | Recalculated at clearing time |
| Markets | ~5k villages × a few districts | markets × ~200 goods per sim-hour: trivial |
| T0 transactions | Near players only | Real item exchange, same ledger |

The economy is one of the **cheaper** systems at scale, because it aggregates by nature.

### 17.12 Player economic play
Sell raw materials; own shops or whole production chains; hire; trade between villages. **As ruler:** taxes, tariffs, minting, state firms, **price controls** (leading to shortages and black markets), public works. **Shady:** cornering markets, smuggling, counterfeiting (all acts in the catalogue).

---

## 18. Diplomacy & War

[DECIDED: D13]

### 18.1 Diplomacy
Relations between villages/realms: trade agreements, alliances, royal marriages, vassalage, rivalries, embargoes. Player-ruled villages on multiplayer servers form real geopolitics.

### 18.2 Casus belli (from existing systems)
Crimes by foreign citizens, border/district disputes, broken trade deals, **succession claims through marriage**, holy war, raids, revenge for conquest.

### 18.3 Armies
- Drawn from **conscription laws** and military offices; soldiers are ordinary villagers taken off their plans.
- Equipment comes from **armory and smithy output** (economy link); officers from military academies.

### 18.4 Battles use the tiers
- **T0:** real entity combat.
- **T2/T3:** abstract resolution from numbers, equipment quality, morale, commanders' skill, terrain and fortifications.

### 18.5 Sieges & conquest
- Sieges cut caravans; starvation collapses approval inside the walls.
- **Conquest** is one of the five paths to rule. Conquered villagers keep **resentment** through memories and gossip; cultures clash and drift.
- War creates grief memories, widows and orphans, revenge motives and chronicle entries.

---

## 19. Macro → Micro Compatibility Rules

[DECIDED: D12] These rules are **mandatory from the first line of code**, so CK-style intrigue can be layered on later without rewrites:

1. **Individuals from day one.** Aggregate stats (district approval, crime rate, faith share) are always *computed from* individual state, never stored only as aggregates.
2. **Every decision goes through a procedure.** Laws and policies are *Proposal → Procedure → Enactment* from the start. The MVP procedure is "instant decree"; council votes, referendums and vetoes are added later as procedure types.
3. **Offices exist in data from the start.** They're auto-filled and passive at first; intrigue gives the holders ambitions and agency.
4. **Every event records actor, cause, witnesses and who knows.** This is the foundation for secrets, blackmail, rumours and plots.
5. **Plans support hidden entries** (§7.2): schemes are plan entries that can be discovered.
6. **Relationships have typed bonds** (co-conspirator, patron/client, rival), even if unused at first.

Micro/CK layer (later): **schemes** (assassination, seduction, fabricating claims, coups), **secrets & blackmail**, **personal ambitions** per villager, **factions** that push demands on the ruler, **favours/hooks**.

---

## 20. Presentation: Models, Animation, UI

- **Custom villager models** with genetic appearance (inherited from parents), class-based outfits (sumptuary laws are visible), and visible marks (branding, age, injuries).
- **Animations:** work (smithing, baking, mining, building), social (talking, laughing, arguing, flirting), religious (praying), civic (stocks, trials), combat. **GeckoLib** [DECIDED]
- **Crowd rendering:** LOD and instancing for 200+ visible villagers; compatibility with Sodium/Iris to be evaluated.
- **UIs:** town hall management (§13.9), law book, petitions, chronicle/legends viewer, relationship view, family tree, "What's new" feed, dialogue.
- **Chronicle/legends:** a selective log of notable events (marriages, feuds, disasters, famous craftsmen, wars, executions). It's the main way players *experience* a sim they can't watch directly.

---

## 21. Configuration

Server config (initial list):

| Key (illustrative) | Default | Notes |
|---|---|---|
| `lifespan.baseRealMinutes` | 300 | ~5 real hours [DECIDED: D23] |
| `lifespan.varianceStdDev` | TBD | Individual variance |
| `lifespan.stageFractions` | see §5.2 | Life-stage boundaries |
| `lifespan.playerBondedAgingMultiplier` | 1.0 | [OPEN] slower aging for the player's spouse/family |
| `punishments.maxSeverity` | `severe` | `light` / `moderate` / `severe`; gates dark punishments [DECIDED: D19] |
| `sim.maxPopulation` | 1,000,000 | Global cap for performance |
| `sim.workerThreads` | auto | Sim core parallelism |
| `sim.t0Radius` / `sim.t1Radius` | ~64 / loaded | Tier boundaries |
| `sim.syncIntervalSimMinutes` | 60 | District synchronisation window |
| `economy.clearingIntervalSimMinutes` | 60 | Market clearing |
| `war.enabled` | true | |
| `governance.allowPlayerRule` | true | |

---

## 22. Open Questions & Risks

### Open questions
1. **Player-bonded aging:** should the player's spouse and close family age slower by default, or do we embrace short lives? (§5.4)
2. ~~**Persistence backend**~~: resolved, **SQLite**. (§4.9)
3. **Population source:** worldgen-placed vs. organically grown villages (ratio). (§12.3)
4. ~~**Model/animation stack**~~: resolved, **GeckoLib** (crowd figures reuse its assets via instancing). (§20)
5. **Multiplayer rules details:** how co-rule seats are allocated, what happens when a ruling player is offline for days.
6. **Chronicle selectivity:** what counts as "notable" when ~55 births and deaths happen per second.

### Key risks
| Risk | Mitigation |
|---|---|
| Sim core can't reach 1M on consumer CPUs | Headless benchmark runs from Phase 1 and is the **go/no-go gate** at the end of Phase 2 |
| Relationship memory blow-up | Sparse, packed encoding; decay of weak ties; memory budget tests |
| Economy instability | Explicit sources/sinks; headless soak tests (§17.10) |
| Tier-transition glitches | Plan-as-truth (§4.4); dedicated promotion/demotion tests |
| Offscreen building/mining disagreeing with the observed world | Per-section blueprints and deterministic reconciliation (§10) |
| Save size & format evolution | Custom storage with versioning from day one |
| Scope | Strict phasing (§23); macro-first with micro-compatibility rules (§19) |
| Building content volume (types × levels × cultures × biomes) | Modular kits + palette swapping; agentic authoring with Structure Lab; in-game editor (§12.9) |

---

## 23. Roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| **1. Vertical slice (in Minecraft)** | Sim core + expression language + base modules (lifecycle, needs, plans, buildings, social), bridge with tier manager and puppet entities, one ~30-villager village (house, bakery and tavern blueprints built with Structure Lab), debug tools, KubeJS skeleton; headless benchmark on the same code | Walk through a mixed-tier village with no teleporting or state loss; benchmark trending toward 1M (see [ARCHITECTURE.md §21](ARCHITECTURE.md#21-phase-1-plan-vertical-slice)) |
| **2. Scale & persistence** | Districts + message passing at scale, custom persistence backend, life clock, basic economy loop, venue-tier interactions | **1M villagers run headless at a sustainable real-time rate**; economy soak test is stable |
| **3. Player agent** | Player record, dialogue, gifts, relationships, romance/marriage, children, quests from wants, gossip reputation, attention pinning | Befriend/marry a villager, have a child who grows up in the sim |
| **4. Economy & buildings** | Goods catalogue, firms, markets, labour, classes, households & housing assignment (§12.6–12.7), currency/minting | Prices respond to player actions; builders respond to demand |
| **5. Governance MVP** | Found + Elected paths; town hall; zoning; treasury & taxes; ~5 schedule-changing laws; approval per district; petitions; monarchy + democracy procedures | Rule a village; pass a law and see behaviour change |
| **6. Crime MVP** | ~10 core acts; law book with conditions; 3–4 starter cultures; witness → report → magistrate → punishment (full spectrum, config-gated); T2 statistical crime | Ruler redefines a crime and the population reacts per its morality |
| **7. Physical world & village growth** | Per-section blueprints, construction & mining with reconciliation, resource sampling; village planner, plots & roads, building levels & lifecycle, village stages & decline (§10.5–10.7, §12.4); modular kits + palette swapping; **in-game structure editor**; landmark and kit content built with Structure Lab (§12.9) | Leave a site, come back, and find it progressed consistently; a hamlet grows into a town on its own |
| **8. Religion & education** | Faiths, temples, clergy, conversion; school levels, apprenticeships, bootstrapping | Education changes jobs and values; faith shapes morality |
| **9. Models & animation** | Custom villager models, genetics, outfits, animations, crowd LOD | 200+ animated villagers visible at playable FPS |
| **10. Full governance & dynasty** | All five paths, offices & delegation, all government types, succession, justice procedures, multiplayer co-rule | Inherit a throne; lose an election |
| **11. Diplomacy & war** | Relations, casus belli, armies, battles across tiers, sieges, conquest | Conquer a village and manage the resentment |
| **12. Scale-out** | Thousands of villages, migration, caravans, worldgen integration, chronicle UI | 1M villagers in a real world, playable |
| **13. CK micro layer** | Schemes, secrets & blackmail, ambitions, factions, favours | An official plots a coup the player can uncover |
