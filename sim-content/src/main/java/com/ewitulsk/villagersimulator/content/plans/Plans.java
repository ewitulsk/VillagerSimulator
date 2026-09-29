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
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.logic.EffectEnv;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Running plans. Each villager's current entry is resolved when it ends (one scheduled task per entry), then the next
 * entry begins. When an entry begins:
 * <ul>
 *   <li>free time is filled by the utility AI ({@link Choices});</li>
 *   <li>if the villager isn't where the entry happens, a travel entry is inserted from their actual position.</li>
 * </ul>
 * The same loop runs at every tier; only the Minecraft bridge's rendering of it differs.
 */
public final class Plans {
    public static final SparseComponent<Plan> PLAN = new SparseComponent<>(VS.id("plan"), 1, Plan.CODEC);

    public static final DenseComponent CURSOR = new DenseComponent(VS.id("plan_cursor"), 1);
    public static final IntField CURSOR_INDEX = CURSOR.intField("index");
    /** When the current entry actually started (later than planned if the villager joined mid-entry). */
    public static final LongField CURSOR_STARTED = CURSOR.longField("started");

    /** Where the villager was when the current entry began (doubles stored as raw bits). */
    public static final DenseComponent POSITION = new DenseComponent(VS.id("position"), 1);
    private static final LongField POS_X = POSITION.longField("x");
    private static final LongField POS_Y = POSITION.longField("y");
    private static final LongField POS_Z = POSITION.longField("z");

    public static final TaskType ADVANCE = new TaskType(VS.id("plan_advance"), 100, Plans::advance);

    /** Event log type: a villager's hunger reached zero. */
    public static final Id EVENT_STARVING = VS.id("starving");
    /** Free time shorter than this isn't worth choosing for; the villager idles through it. */
    private static final long MIN_CHOICE = 60;
    private static final long IDLE_BLOCK = SimTime.minutes(30);

    private Plans() {}

    /** Starts a new villager's plan at the current time. */
    public static void start(SimContext ctx, EntityId v) {
        long now = ctx.now();
        Plan plan = DailyPlanner.generate(ctx, v, SimTime.day(now));
        ctx.set(v, PLAN, plan);
        ctx.add(v, CURSOR);
        int index = plan.indexAt(now);
        setPosition(ctx, v, plan.entries().get(index).target());
        begin(ctx, v, plan, index);
    }

    public static PlanEntry current(SimContext ctx, EntityId v) {
        Plan plan = ctx.get(v, PLAN);
        if (plan == null || !ctx.has(v, CURSOR)) return null;
        int i = ctx.get(v, CURSOR_INDEX);
        return i >= 0 && i < plan.entries().size() ? plan.entries().get(i) : null;
    }

    /** The villager's position now, per the plan. */
    public static double[] positionNow(SimContext ctx, EntityId v) {
        PlanEntry e = current(ctx, v);
        return e != null ? e.positionAt(ctx.now()) : position(ctx, v);
    }

    public static double[] position(SimContext ctx, EntityId v) {
        if (!ctx.has(v, POSITION)) return null;
        return new double[]{Double.longBitsToDouble(ctx.get(v, POS_X)), Double.longBitsToDouble(ctx.get(v, POS_Y)),
                Double.longBitsToDouble(ctx.get(v, POS_Z))};
    }

    private static void setPosition(SimContext ctx, EntityId v, double[] p) {
        if (!ctx.has(v, POSITION)) ctx.add(v, POSITION);
        ctx.set(v, POS_X, Double.doubleToRawLongBits(p[0]));
        ctx.set(v, POS_Y, Double.doubleToRawLongBits(p[1]));
        ctx.set(v, POS_Z, Double.doubleToRawLongBits(p[2]));
    }

    private static long key(long day, int index) {
        return day * 10_000 + index;
    }

    private static Plan replace(Plan plan, int index, List<PlanEntry> with) {
        List<PlanEntry> entries = new ArrayList<>(plan.entries());
        entries.remove(index);
        List<PlanEntry> kept = with.stream().filter(e -> e.end() > e.start()).toList();
        entries.addAll(index, kept);
        return new Plan(plan.day(), entries);
    }

    private static void begin(SimContext ctx, EntityId v, Plan plan, int index) {
        long now = ctx.now();
        PlanEntry e = plan.entries().get(index);
        double[] at = position(ctx, v);
        if (at == null) {
            at = e.target();
            setPosition(ctx, v, at);
        }
        if (e.activity().equals(BasicActivities.FREE_TIME)) {
            plan = replace(plan, index, fillFreeTime(ctx, v, e, at));
            e = plan.entries().get(index);
        }
        double dist = Choices.distance(at, e.target());
        if (!e.activity().equals(BasicActivities.TRAVEL) && dist > 0.5 && e.end() - now > 1) {
            long travel = Math.min((long) Math.ceil(dist / DailyPlanner.WALK_SPEED), e.end() - now - 1);
            PlanEntry walk = new PlanEntry(now, now + travel, BasicActivities.TRAVEL, e.venue(),
                    at[0], at[1], at[2], e.tx(), e.ty(), e.tz(), Optional.empty());
            plan = replace(plan, index, List.of(walk, e.withTimes(now + travel, e.end())));
            e = walk;
        }
        ctx.set(v, PLAN, plan);
        ctx.set(v, CURSOR_INDEX, index);
        ctx.set(v, CURSOR_STARTED, now);
        if (e.ad().isPresent() && !e.venue().isNone() && ctx.alive(e.venue())) Buildings.visited(ctx, e.venue());
        ctx.activity(e.activity()).begin(new ActivityContext(ctx, v, e.venue()));
        ctx.schedule(Math.max(now, e.end()), ADVANCE, v, key(plan.day(), index));
    }

    /** Replaces a free-time entry: the best advertisement (or a short idle), then the remaining free time. */
    private static List<PlanEntry> fillFreeTime(SimContext ctx, EntityId v, PlanEntry free, double[] at) {
        long now = ctx.now();
        long end = free.end();
        Optional<Choices.Option> choice = end - now >= MIN_CHOICE ? Choices.choose(ctx, v, at) : Optional.empty();
        if (choice.isPresent()) {
            Choices.Option o = choice.get();
            long travel = (long) Math.ceil(Choices.distance(at, o.point()) / DailyPlanner.WALK_SPEED);
            long chosenEnd = Math.min(end, now + travel + o.ad().durationTicks());
            return List.of(
                    PlanEntry.stay(now, chosenEnd, o.ad().activity(), o.venue(), o.point(), Optional.of(o.ad().id())),
                    PlanEntry.stay(chosenEnd, end, BasicActivities.FREE_TIME, EntityId.NONE, o.point(), Optional.empty()));
        }
        long idleEnd = Math.min(end, now + IDLE_BLOCK);
        return List.of(
                PlanEntry.stay(now, idleEnd, BasicActivities.WANDER, EntityId.NONE, at, Optional.empty()),
                PlanEntry.stay(idleEnd, end, BasicActivities.FREE_TIME, EntityId.NONE, at, Optional.empty()));
    }

    private static void advance(SimContext ctx, EntityId v, long arg) {
        if (!ctx.alive(v)) return;
        Plan plan = ctx.get(v, PLAN);
        if (plan == null) return;
        int index = ctx.get(v, CURSOR_INDEX);
        if (key(plan.day(), index) != arg) return; // stale task from a replaced plan

        PlanEntry e = plan.entries().get(index);
        long started = ctx.get(v, CURSOR_STARTED);
        Activity activity = ctx.activity(e.activity());
        activity.simulateAbstract(new ActivityContext(ctx, v, e.venue()), started, ctx.now());
        if (e.ad().isPresent()) fulfil(ctx, v, e, started);
        setPosition(ctx, v, e.positionAt(ctx.now()));
        checkStarving(ctx, v);

        if (index + 1 < plan.entries().size()) {
            begin(ctx, v, plan, index + 1);
        } else {
            Plan next = DailyPlanner.generate(ctx, v, plan.day() + 1);
            begin(ctx, v, next, next.indexAt(ctx.now()));
        }
    }

    /** Applies a finished advertisement: goods consumed, need gains and effects, scaled by how much was completed. */
    private static void fulfil(SimContext ctx, EntityId v, PlanEntry e, long started) {
        if (e.venue().isNone() || !ctx.alive(e.venue())) return;
        Advertisement ad = ctx.registry(BuildingType.REGISTRY).find(ctx.get(e.venue(), Buildings.BUILDING).type())
                .flatMap(t -> t.advertisement(e.ad().get())).orElse(null);
        if (ad == null) return; // removed by a data reload
        long planned = Math.max(1, e.end() - e.start());
        double fraction = Math.max(0, Math.min(1, (double) (ctx.now() - started) / planned));
        if (!ad.consumes().isEmpty()) {
            if (fraction < 0.5) return;
            for (Map.Entry<String, Integer> good : ad.consumes().entrySet()) {
                if (Buildings.stock(ctx, e.venue(), good.getKey()) < good.getValue()) {
                    ctx.events().record(BasicActivities.EVENT_NO_FOOD, v, "No " + good.getKey() + " left");
                    return;
                }
            }
            for (Map.Entry<String, Integer> good : ad.consumes().entrySet()) {
                Buildings.setStock(ctx, e.venue(), good.getKey(), Buildings.stock(ctx, e.venue(), good.getKey()) - good.getValue());
            }
        }
        for (Map.Entry<String, Double> gain : ad.needs().entrySet()) {
            Needs.NeedType t = Needs.byName(gain.getKey());
            if (t != null) t.need().add(ctx, v, (float) (gain.getValue() * fraction));
        }
        if (ad.effects().isPresent()) {
            try {
                ctx.logic().effect(ad.effects().get()).apply(EffectEnv.of(ctx, v, e.venue(), e.target(), fraction));
            } catch (ExpressionException ignored) {
                // reported by the buildings validator
            }
        }
    }

    private static void checkStarving(SimContext ctx, EntityId v) {
        boolean starving = Needs.HUNGER.value(ctx, v) <= 0f;
        boolean flagged = ctx.get(v, Needs.STARVING) != 0;
        if (starving && !flagged) ctx.events().record(EVENT_STARVING, v, "Hunger reached zero");
        if (starving != flagged) ctx.set(v, Needs.STARVING, starving ? 1 : 0);
    }
}
