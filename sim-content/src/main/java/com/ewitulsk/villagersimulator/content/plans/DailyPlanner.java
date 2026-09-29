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
import java.util.Optional;

/**
 * The day's skeleton: obligations only (sleep, work shifts). Everything else is free time, filled when it starts by
 * the utility AI ({@link Choices}). Travel is inserted at runtime from wherever the villager actually is
 * ({@link Plans}), so the plan stays true to the villager's position at every moment (docs/DESIGN.md §4.4).
 */
public final class DailyPlanner {
    /** Walking speed along roads, blocks per tick (3 blocks a second). */
    public static final double WALK_SPEED = 0.15;
    private static final long H = SimTime.TICKS_PER_HOUR;

    private DailyPlanner() {}

    public static Plan generate(SimContext ctx, EntityId villagerId, long day) {
        Villager v = ctx.get(villagerId, Villages.VILLAGER);
        long d0 = day * SimTime.TICKS_PER_DAY;
        // Workers are up early enough for breakfast before a 07:00 shift.
        long wake = (long) (SimRandom.unit(v.seed(), day, SimRandom.salt("wake")) * (v.employed() ? 250 : 600));
        long bed = (long) (SimRandom.unit(v.seed(), day, SimRandom.salt("bed")) * 900);
        long bedtime = d0 + 14 * H + H / 2 + bed;

        List<PlanEntry> out = new ArrayList<>();
        double[] bedPos = bedPosition(ctx, v);
        add(out, PlanEntry.stay(d0, d0 + wake, BasicActivities.SLEEP, v.home(), bedPos, Optional.empty()));
        if (v.employed()) {
            double[] work = Buildings.point(ctx, v.workplace(), BuildingType.WORK, v.workSlot());
            Id activity = Buildings.type(ctx, v.workplace()).job().map(BuildingType.Job::activity).orElse(BasicActivities.WANDER);
            add(out, free(d0 + wake, d0 + H, bedPos));
            add(out, PlanEntry.stay(d0 + H, d0 + 6 * H, activity, v.workplace(), work, Optional.empty()));
            add(out, free(d0 + 6 * H, d0 + 6 * H + 3 * H / 4, work));
            add(out, PlanEntry.stay(d0 + 6 * H + 3 * H / 4, d0 + 9 * H, activity, v.workplace(), work, Optional.empty()));
            add(out, free(d0 + 9 * H, bedtime, work));
        } else {
            add(out, free(d0 + wake, bedtime, bedPos));
        }
        add(out, PlanEntry.stay(bedtime, d0 + SimTime.TICKS_PER_DAY, BasicActivities.SLEEP, v.home(), bedPos, Optional.empty()));
        return new Plan(day, out);
    }

    private static void add(List<PlanEntry> out, PlanEntry e) {
        if (e.end() > e.start()) out.add(e);
    }

    private static PlanEntry free(long start, long end, double[] at) {
        return PlanEntry.stay(start, end, BasicActivities.FREE_TIME, EntityId.NONE, at, Optional.empty());
    }

    static double[] bedPosition(SimContext ctx, Villager v) {
        if (v.home().isNone() || !ctx.alive(v.home())) {
            var village = ctx.get(v.village(), Villages.VILLAGE);
            return new double[]{village.x() + 0.5, village.y(), village.z() + 0.5};
        }
        return Buildings.point(ctx, v.home(), BuildingType.BED, v.bed());
    }
}
