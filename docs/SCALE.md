# Scale Gate Report (Phase 6)

**Verdict: GO.** One million villagers run at T3 in **0.45 s of wall time per sim-day**, which is **0.04% of the real-time budget**, using **763 MB** of heap. T2, the detailed tier, costs about 50 µs of CPU per villager per sim-day. That is cheap enough for the tens of thousands of villagers near players. The gaps and the plans to close them are at the end.

Measured on a 16-thread desktop (31 GB RAM) with JDK 25 and the parallel GC. Reproduce with:

```bash
./gradlew :sim-bench:scale -Pvillages=5000 -Psize=200 -Pdays=4             # 1M at T3
./gradlew :sim-bench:scale -Pvillages=250 -Psize=200 -Pdays=2 -Ptier=t2    # 50k at T2
./gradlew :sim-bench:jmh                                                   # micro-benchmarks
```

Reports are written to `sim-bench/build/reports/`. Add `-Pjfr=true` for a flight recording and `-Phisto=true` for a heap class histogram.

## The world

The benchmark uses a flat stand-in world: 5,000 two-district towns of 200 villagers each, 600 blocks apart. Every town is the standard layout: houses, 17 bakeries, 7 taverns, 7 market stalls and a well, which comes to about 85 buildings. That is 1,430,000 entities in total. It spawns in 10 s. Warm-up takes one sim-day, and then the benchmark measures several sim-days with a snapshot (save) at the end of each.

A sim-day is 24,000 ticks. Minecraft plays it in 20 real minutes, so the sim keeps up as long as one sim-day takes less than 1,200 s of wall time.

## Results

| | 1M villagers, T3 | 50k villagers, T2 |
|---|---|---|
| Wall time per sim-day | 0.45 s | 2.51 s |
| Real-time budget used | 0.04% | 0.21% |
| Tasks per villager per sim-day | 1.0 | 26.6 |
| Tasks per second | 2.2M | 530k |
| CPU per task | 6–9 µs (day batch) | 28 µs (plan step incl. utility AI) |
| Heap per villager, all state | 763 bytes | 5.2 KB (incl. 3 days of events) |
| Event records per villager per sim-day | 0 | 4.2 |
| Snapshot time / size | 3.9 s / 103 MB | 1.7 s / 32 MB |
| Starving villagers after the run | 0 | 0 |

Extrapolated to a mixed world, with 50k villagers near players at T2 and 950k far away at T3, the cost is about 3 s per sim-day (0.25% of the budget) and about 1 GB of heap.

### Against the budgets in [DESIGN §4.10](DESIGN.md#410-budgets-targets-to-validate-in-phase-1)

| Budget | Estimate | Measured |
|---|---|---|
| Core state per villager | 150–300 B | 763 B at T3, all state included (dense columns ~420 B, sparse records, tasks, road graphs, buildings' share) |
| Relationships | 400–600 B | At most 48 ties × 12 B = 576 B per villager (only formed at T2 and closer) |
| T2 event rate | 10–30 events per villager per day | 26.6 tasks and 4.2 logged records per villager per day |
| Full fidelity is impossible | 20M agent updates/s | Tiers work: 1M at T3 is 1 task per villager per day |

The core state is above the estimate because the estimate counted only needs and position. Everything else is included here, and the total is still under 1 GB at 1M.

### Micro-benchmarks (JMH, ns/op)

| Hot path | Time |
|---|---|
| Event queue: poll one task and schedule the next (100k pending) | 123 |
| Dense component read (`need('hunger')`) | 11 |
| Compiled expression (bakery meal-time score) | 12 |
| Friendship lookup / change | 12 / 38 |
| Iterate someone's ties (30) | 115 |
| One utility-AI choice in a 200-villager town (~170 options) | 11,200 |

### In game

`/vs stress 20000` adds 100 sim-only towns far away, and the GameTest `villagersimulator_scale` checks that the server stays well under its tick budget: **0.07–0.16 ms per tick**. `/vs profile` shows the server tick time, villagers per tier, and where the sim spends its time.

## What the optimisation pass changed

The optimisation pass was driven by the per-system profile (`SimProfile`, which feeds `/vs profile`) and by JFR.

- **T3 keeps no plan.** A coarse villager's day only exists while its day batch runs. This cut 1.1 KB to 0.76 KB per villager. The food a coarse villager eats now comes from every eatery in the village, not just the first one. Before this fix, 92% of villagers in big towns starved at T3.
- **Tiers are decided per village first.** A village is *detailed* when a player is near or it's in loaded chunks, *abstract* (T2) otherwise, and *coarse* (T3) when it's beyond `tiers.t3Radius` of every player. Only detailed villages appear in the embodiment view. The bridge therefore looks at 5,000 villages instead of 1M villagers, and the view is no longer O(all villagers) every 50 ms. Pinned villagers still stay at T2 or better.
- **Event records leave memory** once they are saved and older than the retention (3 sim-days, `SimWorld.Builder.eventRetention`). Positions stay absolute, so saves and loads work unchanged. Loading reads only the recent records.
- **Hot-path lookups:**
  - Store lookups use component serials (array index) instead of identity maps.
  - Compiled conditions and expressions are cached by identity, so the hot path no longer builds a JSON or string key.
  - Durations are cached rather than regex-parsed each time.
  - Validity checks for advertisements are cached.
  - The scheduler uses a direct comparator.
  - Road graphs are primitive arrays with cached shortest-path trees.
  - Gossip iterates ties without sorting.

  Together these took T2 from 84 µs to 28 µs per task, and T2 wall time per sim-day from 3.0 s to 1.0 s at 20k villagers.
- **Snapshots are deflate-compressed** (508 MB → 103 MB at 1M), and component sections are encoded in parallel.
- **Dense columns grow by 1.5×** instead of 2×.

## Gaps, and the plan to close them

| Gap | Impact | Plan |
|---|---|---|
| A snapshot at 1M blocks the sim thread for ~4 s | The server isn't affected (the sim runs on its own thread). The sim falls ~4 s behind at each autosave and catches up. | Binary codecs for the big sparse components (villager, stock) instead of JSON; incremental snapshots of changed shards. Needed by Phase 40 (Full Scale). |
| T2 memory is 5.2 KB per villager, 2 KB of it event records within the retention | Fine for tens of thousands of villagers near players. 1M at T2 would need 5 GB. | T2 is bounded by player proximity. If needed: a shorter retention, and memories that reference records rather than copying them (already the design). |
| One utility-AI choice is 11 µs in a 200-villager town, linear in buildings | This is most of the T2 cost. Towns of 1,000+ would be proportionally slower. | Spatial pre-filtering (only buildings within walking budget) and per-type best-point caches; needed when the village planner makes big towns (Phase 15). |
| Spawning 1M villagers takes 10 s | One-off. | Worldgen spreads it over exploration (Phase 19). |
| Births and deaths (~55 each per real second at 1M) aren't simulated yet | Unknown cost | Re-run this benchmark in Phase 8; the report format stays the same. |
| Relationships form only at T2 and closer | At T3 nobody meets anybody | By design (DESIGN §4.2). Coarse social drift for T3 villages can be added when a phase needs it. |
