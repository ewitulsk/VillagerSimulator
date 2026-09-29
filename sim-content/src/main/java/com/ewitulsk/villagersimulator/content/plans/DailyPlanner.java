package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 0's fixed daily template: sleep, breakfast, work (or free time), lunch, work, free time, dinner, free time,
 * sleep. Times get a small per-villager, per-day jitter. Utility-AI choice replaces the fixed free-time slots in
 * Phase 1. Travel entries are inserted between stays at different places, so the plan says where a villager is at
 * every moment (docs/DESIGN.md §4.4).
 */
public final class DailyPlanner {
    /** Walking speed along a straight line, blocks per tick (Phase 5 moves travel onto the road graph). */
    public static final double WALK_SPEED = 0.1;
    private static final long H = SimTime.TICKS_PER_HOUR;

    private DailyPlanner() {}

    /** A stay at one place: the plan before travel is inserted. */
    private record Stay(long start, Id activity, EntityId venue, double[] pos) {}

    public static Plan generate(SimContext ctx, EntityId villagerId, long day) {
        Villager v = ctx.get(villagerId, Villages.VILLAGER);
        long d0 = day * SimTime.TICKS_PER_DAY;
        long wake = (long) (SimRandom.unit(v.seed(), day, SimRandom.salt("wake")) * 600);
        long dinner = (long) (SimRandom.unit(v.seed(), day, SimRandom.salt("dinner")) * 300);
        long bed = (long) (SimRandom.unit(v.seed(), day, SimRandom.salt("bed")) * 600);

        EntityId food = Villages.findService(ctx, v.village(), "eat").orElse(EntityId.NONE);
        EntityId gather = Villages.findService(ctx, v.village(), "gather").orElse(v.home());

        List<Stay> stays = new ArrayList<>();
        stays.add(sleep(ctx, v, d0));
        stays.add(eat(ctx, v, food, d0 + wake, day, 0));
        if (v.employed()) {
            stays.add(work(ctx, v, d0 + H));
            stays.add(eat(ctx, v, food, d0 + 6 * H, day, 1));
            stays.add(work(ctx, v, d0 + 6 * H + H / 2));
            stays.add(wander(ctx, v, gather, d0 + 9 * H, day, 0));
        } else {
            stays.add(wander(ctx, v, gather, d0 + H, day, 0));
            stays.add(eat(ctx, v, food, d0 + 6 * H, day, 1));
            stays.add(wander(ctx, v, gather, d0 + 6 * H + H / 2, day, 1));
        }
        stays.add(eat(ctx, v, food, d0 + 12 * H + dinner, day, 2));
        stays.add(wander(ctx, v, gather, d0 + 12 * H + 3 * H / 4, day, 2));
        stays.add(sleep(ctx, v, d0 + 14 * H + H / 2 + bed));

        return new Plan(day, withTravel(stays, d0 + SimTime.TICKS_PER_DAY));
    }

    /** Turns stays into contiguous entries, inserting travel at the start of any stay at a new place. */
    private static List<PlanEntry> withTravel(List<Stay> stays, long dayEnd) {
        List<PlanEntry> out = new ArrayList<>();
        double[] at = stays.get(0).pos();
        for (int i = 0; i < stays.size(); i++) {
            Stay s = stays.get(i);
            long end = i + 1 < stays.size() ? stays.get(i + 1).start() : dayEnd;
            long start = s.start();
            if (end <= start) continue;
            double dist = distance(at, s.pos());
            if (dist > 0.5) {
                long travel = Math.max(1, (long) Math.ceil(dist / WALK_SPEED));
                travel = Math.min(travel, end - start - 1);
                if (travel > 0) {
                    out.add(new PlanEntry(start, start + travel, BasicActivities.TRAVEL, s.venue(),
                            at[0], at[1], at[2], s.pos()[0], s.pos()[1], s.pos()[2]));
                    start += travel;
                }
            }
            double[] p = s.pos();
            out.add(new PlanEntry(start, end, s.activity(), s.venue(), p[0], p[1], p[2], p[0], p[1], p[2]));
            at = p;
        }
        return out;
    }

    private static Stay sleep(SimContext ctx, Villager v, long start) {
        if (v.home().isNone()) return new Stay(start, BasicActivities.SLEEP, EntityId.NONE, villageCentre(ctx, v));
        return new Stay(start, BasicActivities.SLEEP, v.home(), Buildings.point(ctx, v.home(), BuildingType.BED, v.bed()));
    }

    private static Stay eat(SimContext ctx, Villager v, EntityId food, long start, long day, int meal) {
        if (food.isNone()) return wanderAt(ctx, v, v.home(), start, day, meal);
        int n = Math.max(1, Buildings.pointCount(ctx, food, BuildingType.SERVICE));
        int spot = (v.number() + meal) % n;
        return new Stay(start, BasicActivities.EAT, food, Buildings.point(ctx, food, BuildingType.SERVICE, spot));
    }

    private static Stay work(SimContext ctx, Villager v, long start) {
        BuildingType type = Buildings.type(ctx, v.workplace());
        Id activity = type.job().map(BuildingType.Job::activity).orElse(BasicActivities.WANDER);
        return new Stay(start, activity, v.workplace(), Buildings.point(ctx, v.workplace(), BuildingType.WORK, v.workSlot()));
    }

    private static Stay wander(SimContext ctx, Villager v, EntityId place, long start, long day, int slot) {
        return wanderAt(ctx, v, place, start, day, slot);
    }

    private static Stay wanderAt(SimContext ctx, Villager v, EntityId place, long start, long day, int slot) {
        if (place.isNone()) return new Stay(start, BasicActivities.WANDER, EntityId.NONE, villageCentre(ctx, v));
        int n = Math.max(1, Buildings.pointCount(ctx, place, BuildingType.WANDER));
        int spot = SimRandom.below(n, v.seed(), day, slot, SimRandom.salt("wander"));
        return new Stay(start, BasicActivities.WANDER, place, Buildings.point(ctx, place, BuildingType.WANDER, spot));
    }

    private static double[] villageCentre(SimContext ctx, Villager v) {
        var village = ctx.get(v.village(), Villages.VILLAGE);
        return new double[]{village.x() + 0.5, village.y(), village.z() + 0.5};
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
