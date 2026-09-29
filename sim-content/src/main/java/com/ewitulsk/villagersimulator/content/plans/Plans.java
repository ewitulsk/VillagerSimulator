package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.activity.ActivityContext;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.needs.Needs;

/**
 * Running plans: each villager's current entry is resolved when it ends (one scheduled task per entry), then the
 * next entry starts. This is the whole Phase 0 behaviour loop, and it runs the same at every tier.
 */
public final class Plans {
    public static final SparseComponent<Plan> PLAN = new SparseComponent<>(VS.id("plan"), 1, Plan.CODEC);

    public static final DenseComponent CURSOR = new DenseComponent(VS.id("plan_cursor"), 1);
    public static final IntField CURSOR_INDEX = CURSOR.intField("index");
    /** When the current entry actually started (later than its planned start if the villager joined mid-entry). */
    public static final LongField CURSOR_STARTED = CURSOR.longField("started");

    public static final TaskType ADVANCE = new TaskType(VS.id("plan_advance"), 100, Plans::advance);

    /** Event log type: a villager's hunger reached zero. */
    public static final Id EVENT_STARVING = VS.id("starving");

    private Plans() {}

    /** Starts a new villager's plan at the current time. */
    public static void start(SimContext ctx, EntityId v) {
        long now = ctx.now();
        Plan plan = DailyPlanner.generate(ctx, v, SimTime.day(now));
        ctx.set(v, PLAN, plan);
        ctx.add(v, CURSOR);
        begin(ctx, v, plan, plan.indexAt(now));
    }

    public static PlanEntry current(SimContext ctx, EntityId v) {
        Plan plan = ctx.get(v, PLAN);
        if (plan == null || !ctx.has(v, CURSOR)) return null;
        int i = ctx.get(v, CURSOR_INDEX);
        return i >= 0 && i < plan.entries().size() ? plan.entries().get(i) : null;
    }

    private static long key(long day, int index) {
        return day * 1_000 + index;
    }

    private static void begin(SimContext ctx, EntityId v, Plan plan, int index) {
        PlanEntry e = plan.entries().get(index);
        ctx.set(v, CURSOR_INDEX, index);
        ctx.set(v, CURSOR_STARTED, ctx.now());
        ctx.activity(e.activity()).begin(new ActivityContext(ctx, v, e.venue()));
        ctx.schedule(Math.max(ctx.now(), e.end()), ADVANCE, v, key(plan.day(), index));
    }

    private static void advance(SimContext ctx, EntityId v, long arg) {
        if (!ctx.alive(v)) return;
        Plan plan = ctx.get(v, PLAN);
        if (plan == null) return;
        int index = ctx.get(v, CURSOR_INDEX);
        if (key(plan.day(), index) != arg) return; // stale task from a replaced plan

        PlanEntry e = plan.entries().get(index);
        Activity activity = ctx.activity(e.activity());
        activity.simulateAbstract(new ActivityContext(ctx, v, e.venue()), ctx.get(v, CURSOR_STARTED), ctx.now());
        checkStarving(ctx, v);

        if (index + 1 < plan.entries().size()) {
            begin(ctx, v, plan, index + 1);
        } else {
            Plan next = DailyPlanner.generate(ctx, v, plan.day() + 1);
            ctx.set(v, PLAN, next);
            begin(ctx, v, next, next.indexAt(ctx.now()));
        }
    }

    private static void checkStarving(SimContext ctx, EntityId v) {
        boolean starving = Needs.HUNGER.value(ctx, v) <= 0f;
        boolean flagged = ctx.get(v, Needs.STARVING) != 0;
        if (starving && !flagged) ctx.events().record(EVENT_STARVING, v, "Hunger reached zero");
        if (starving != flagged) ctx.set(v, Needs.STARVING, starving ? 1 : 0);
    }
}
