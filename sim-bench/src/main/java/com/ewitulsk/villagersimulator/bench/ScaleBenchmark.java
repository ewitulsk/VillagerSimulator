package com.ewitulsk.villagersimulator.bench;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.SetTiersCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.VillageLayouts;
import com.ewitulsk.villagersimulator.core.SimProfile;
import com.ewitulsk.villagersimulator.harness.Scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Phase 6 scale gate (docs/ROADMAP.md): spawns {@code villages * size} villagers on a flat stand-in world,
 * simulates {@code days} sim-days at one tier, and reports memory per villager, wall time per sim-day against the
 * real-time budget, task rate and the per-system profile.
 *
 * <p>Real time: a sim-day is 24,000 ticks, which Minecraft plays in 20 real minutes, so the sim keeps up while one
 * sim-day takes under 1,200 s of wall time. The report gives the fraction of that budget used.
 */
public final class ScaleBenchmark {
    private static final double REAL_SECONDS_PER_DAY = SimTime.TICKS_PER_DAY / 20.0;
    private static final int SPACING = 600;

    public static void main(String[] args) throws IOException {
        Map<String, String> opt = new HashMap<>();
        for (String a : args) {
            if (!a.startsWith("--") || !a.contains("=")) throw new IllegalArgumentException("Bad argument " + a);
            opt.put(a.substring(2, a.indexOf('=')), a.substring(a.indexOf('=') + 1));
        }
        int villages = Integer.parseInt(opt.getOrDefault("villages", "5000"));
        int size = Integer.parseInt(opt.getOrDefault("size", "200"));
        int days = Integer.parseInt(opt.getOrDefault("days", "2"));
        Tier tier = Tier.valueOf(opt.getOrDefault("tier", "t3").toUpperCase(Locale.ROOT));
        int threads = Integer.parseInt(opt.getOrDefault("threads", String.valueOf(Runtime.getRuntime().availableProcessors())));
        Path out = Path.of(opt.getOrDefault("out", "build/reports/scale"));

        Result r = run(villages, size, days, tier, threads, true, Boolean.parseBoolean(opt.getOrDefault("histo", "false")));
        String name = String.format(Locale.ROOT, "scale-%dx%d-%s.md", villages, size, tier.name().toLowerCase(Locale.ROOT));
        Files.createDirectories(out);
        Files.writeString(out.resolve(name), r.markdown());
        System.out.println(r.markdown());
        System.out.println("Report: " + out.resolve(name).toAbsolutePath());
    }

    /** What one run measured. */
    public record Result(int villages, int size, int days, Tier tier, int threads, long villagers, int entities,
                         double spawnSeconds, long heapBytes, double wallPerDay, long eventsPerDay, int retainedEvents,
                         double snapshotSeconds, long snapshotBytes, int starving, SimProfile profile) {
        public double bytesPerVillager() {
            return (double) heapBytes / villagers;
        }

        public double budgetUsed() {
            return wallPerDay / REAL_SECONDS_PER_DAY;
        }

        public String markdown() {
            StringBuilder b = new StringBuilder();
            b.append(String.format(Locale.ROOT, "# Scale benchmark: %,d villagers at %s%n%n", villagers, tier));
            b.append("| Measure | Value |\n|---|---|\n");
            b.append(String.format(Locale.ROOT, "| Villages x villagers | %,d x %d |%n", villages, size));
            b.append(String.format(Locale.ROOT, "| Entities | %,d |%n", entities));
            b.append(String.format(Locale.ROOT, "| Worker threads | %d |%n", threads));
            b.append(String.format(Locale.ROOT, "| Spawn time | %.1f s |%n", spawnSeconds));
            b.append(String.format(Locale.ROOT, "| Heap after spawn + run | %,.0f MB |%n", heapBytes / 1e6));
            b.append(String.format(Locale.ROOT, "| Heap per villager (all state) | %,.0f bytes |%n", bytesPerVillager()));
            b.append(String.format(Locale.ROOT, "| Wall time per sim-day | %.2f s |%n", wallPerDay));
            b.append(String.format(Locale.ROOT, "| Real-time budget used (1 sim-day = %.0f s) | %.2f%% |%n", REAL_SECONDS_PER_DAY, budgetUsed() * 100));
            b.append(String.format(Locale.ROOT, "| Tasks per second (wall) | %,.0f |%n", profile.tasks() / (profile.wallNanos() / 1e9)));
            b.append(String.format(Locale.ROOT, "| Tasks per villager per sim-day | %.1f |%n", (double) profile.tasks() / villagers / days));
            b.append(String.format(Locale.ROOT, "| Event records per sim-day | %,d |%n", eventsPerDay));
            b.append(String.format(Locale.ROOT, "| Event records in memory at the end | %,d |%n", retainedEvents));
            b.append(String.format(Locale.ROOT, "| Snapshot (save) time | %.2f s |%n", snapshotSeconds));
            b.append(String.format(Locale.ROOT, "| Snapshot size | %,.1f MB |%n", snapshotBytes / 1e6));
            b.append(String.format(Locale.ROOT, "| Starving villagers at the end | %,d |%n", starving));
            b.append("\n## Profile (").append(days).append(" sim-days)\n\n```\n");
            for (String line : profile.lines(20)) b.append(line).append('\n');
            b.append("```\n");
            return b.toString();
        }
    }

    public static Result run(int villages, int size, int days, Tier tier, int threads, boolean log) {
        return run(villages, size, days, tier, threads, log, false);
    }

    public static Result run(int villages, int size, int days, Tier tier, int threads, boolean log, boolean histogram) {
        Scenario s = Scenario.start(0, threads);
        var types = s.world().registry(BuildingType.REGISTRY);
        long heap0 = usedHeap();
        long t0 = System.nanoTime();
        int side = (int) Math.ceil(Math.sqrt(villages));
        long villagers = 0;
        for (int i = 0; i < villages; i++) {
            int x = (i % side) * SPACING, z = (i / side) * SPACING;
            SpawnVillageCommand cmd;
            if (size > 16) {
                var layout = VillageLayouts.town(types, x, 64, z, size);
                cmd = new SpawnVillageCommand("Bench " + i, 1000L + i, x, 64, z, layout.placements(), layout.districts(), size, null);
            } else {
                cmd = Scenario.hamletCommand(types, "Bench " + i, 1000L + i, size, x, 64, z, null);
            }
            EntityId v = s.spawn(cmd);
            Map<EntityId, Tier> tiers = new HashMap<>();
            for (EntityId r : s.village(v).residents()) tiers.put(r, tier);
            s.apply(new SetTiersCommand(tiers));
            villagers += s.village(v).residents().size();
            if (log && (i + 1) % 500 == 0) System.out.printf(Locale.ROOT, "spawned %,d villages (%,d villagers)%n", i + 1, villagers);
        }
        double spawnSeconds = (System.nanoTime() - t0) / 1e9;

        // Warm up for a day (T3 day batches start at the next midnight), then measure.
        s.warp(SimTime.TICKS_PER_DAY);
        s.world().resetProfile();
        int events0 = s.world().events().size();
        // A save a day, like autosave: snapshot time and size, and saved events leave memory after the retention.
        long snapshotNanos = 0, snapshotBytes = 0;
        for (int d = 0; d < days; d++) {
            long start = System.nanoTime();
            s.warp(SimTime.TICKS_PER_DAY);
            long mid = System.nanoTime();
            var snap = s.world().snapshot(s.world().events().size());
            snapshotNanos += System.nanoTime() - mid;
            snapshotBytes = snap.data().length;
            if (log) System.out.printf(Locale.ROOT, "day %d: %.2f s, snapshot %.2f s (%,d KB)%n", d + 1, (mid - start) / 1e9,
                    (System.nanoTime() - mid) / 1e9, snapshotBytes / 1024);
        }
        SimProfile profile = s.world().profile();
        long eventsPerDay = (s.world().events().size() - events0) / Math.max(1, days);
        int starving = 0;
        for (EntityId v : s.world().with(com.ewitulsk.villagersimulator.content.needs.Needs.HUNGER_COMPONENT)) {
            if (s.world().get(v, com.ewitulsk.villagersimulator.content.needs.Needs.STARVING) != 0) starving++;
        }
        long heap = usedHeap() - heap0;
        if (histogram) System.out.println(histogram(30));
        Result r = new Result(villages, size, days, tier, threads, villagers, s.world().entityCount(), spawnSeconds, heap,
                profile.wallNanos() / 1e9 / days, eventsPerDay, s.world().retainedEvents(), snapshotNanos / 1e9 / days,
                snapshotBytes, starving, profile);
        s.world().close();
        return r;
    }

    /** The top of the live-object class histogram (what the memory is made of). */
    static String histogram(int lines) {
        try {
            Object out = java.lang.management.ManagementFactory.getPlatformMBeanServer().invoke(
                    new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                    new Object[]{new String[0]}, new String[]{String[].class.getName()});
            String[] all = out.toString().split("\\R");
            return String.join("\n", java.util.Arrays.copyOf(all, Math.min(all.length, lines + 3)));
        } catch (Exception e) {
            return "histogram unavailable: " + e;
        }
    }

    static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    private ScaleBenchmark() {}
}
