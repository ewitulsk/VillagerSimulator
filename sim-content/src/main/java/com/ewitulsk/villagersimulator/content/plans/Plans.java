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
    /** Bumped every time an entry begins; the entry's end task carries it, so replaced entries' tasks go stale. */
    public static final IntField CURSOR_SEQ = CURSOR.intField("seq");
    /** 1 while the villager is at T3 and advanced a day at a time ({@link #coarseDay}). */
    public static final IntField CURSOR_COARSE = CURSOR.intField("coarse");

    /** Where the villager was when the current entry began (doubles stored as raw bits). */
    public static final DenseComponent POSITION = new DenseComponent(VS.id("position"), 1);
    private static final LongField POS_X = POSITION.longField("x");
    private static final LongField POS_Y = POSITION.longField("y");
    private static final LongField POS_Z = POSITION.longField("z");

    public static final TaskType ADVANCE = new TaskType(VS.id("plan_advance"), 100, Plans::advance);
    /** A T3 villager's whole day, resolved at once at the start of the day. */
    public static final TaskType DAY_BATCH = new TaskType(VS.id("plan_day_batch"), 90, Plans::coarseDay);
    /** The same for workers, earlier at the same time, so the day's production is there before anyone eats. */
    public static final TaskType DAY_BATCH_WORKER = new TaskType(VS.id("plan_day_batch_worker"), 80, Plans::coarseDay);

    /** Event log type: a villager's hunger reached zero. */
    public static final Id EVENT_STARVING = VS.id("starving");
    /** Free time shorter than this isn't worth choosing for; the villager idles through it. */
    private static final long MIN_CHOICE = 60;
    private static final long IDLE_BLOCK = SimTime.minutes(30);

    private Plans() {}

    /** Starts a new villager's plan at the current time. */
    public static void start(SimContext ctx, EntityId v) {
        long now = ctx.now();
        ctx.add(v, CURSOR);
        Plan plan = planDay(ctx, v, SimTime.day(now));
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

    /** Generates a day, lets contributors add to it, stores it and announces it. */
    private static Plan planDay(SimContext ctx, EntityId v, long day) {
        Plan plan = generateDay(ctx, v, day);
        ctx.set(v, PLAN, plan);
        return plan;
    }

    /** Generates and announces a day without storing it (T3 keeps no plan between day batches). */
    private static Plan generateDay(SimContext ctx, EntityId v, long day) {
        Plan plan = DailyPlanner.generate(ctx, v, day);
        for (PlanHooks.PlanContributor c : ctx.extensions(PlanHooks.CONTRIBUTORS)) plan = c.contribute(ctx, v, plan);
        ctx.publish(new PlanHooks.DayPlanned(v, day));
        return plan;
    }

    /**
     * Books {@code entry} into the plan if its whole span lies inside one free-time entry that hasn't started yet
     * (or has started but covers the span). Returns the new plan, or empty if the time isn't free.
     */
    public static Optional<Plan> commit(Plan plan, PlanEntry entry, long now) {
        if (plan == null) return Optional.empty();
        List<PlanEntry> entries = plan.entries();
        for (int i = 0; i < entries.size(); i++) {
            PlanEntry e = entries.get(i);
            if (!e.activity().equals(BasicActivities.FREE_TIME)) continue;
            if (entry.start() < Math.max(e.start(), now) || entry.end() > e.end()) continue;
            List<PlanEntry> with = List.of(e.withTimes(e.start(), entry.start()), entry, e.withTimes(entry.end(), e.end()));
            return Optional.of(replace(plan, i, with));
        }
        return Optional.empty();
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
            PlanEntry walk = walk(ctx, v, now, at, e);
            long travel = Math.min(walk.end() - now, e.end() - now - 1);
            walk = walk.withTimes(now, now + travel);
            plan = replace(plan, index, List.of(walk, e.withTimes(now + travel, e.end())));
            e = walk;
        }
        ctx.set(v, PLAN, plan);
        ctx.set(v, CURSOR_INDEX, index);
        ctx.set(v, CURSOR_STARTED, now);
        int seq = ctx.get(v, CURSOR_SEQ) + 1;
        ctx.set(v, CURSOR_SEQ, seq);
        boolean atVenue = !e.venue().isNone() && ctx.alive(e.venue()) && !e.activity().equals(BasicActivities.TRAVEL);
        if (e.ad().isPresent() && atVenue) {
            Buildings.visited(ctx, e.venue());
            if (!takeGoods(ctx, v, e)) {
                // Sold out on arrival: idle here instead, and choose again afterwards.
                PlanEntry idle = PlanEntry.stay(now, Math.min(e.end(), now + IDLE_BLOCK), BasicActivities.WANDER, EntityId.NONE, at, Optional.empty());
                PlanEntry rest = PlanEntry.stay(idle.end(), e.end(), BasicActivities.FREE_TIME, EntityId.NONE, at, Optional.empty());
                plan = replace(plan, index, List.of(idle, rest));
                e = plan.entries().get(index);
                atVenue = false;
            }
        }
        ctx.activity(e.activity()).begin(new ActivityContext(ctx, v, e.venue()));
        if (atVenue) ctx.publish(new PlanHooks.VisitStarted(v, e.venue(), e.activity(), e.ad(), now, e.end()));
        ctx.schedule(Math.max(now, e.end()), ADVANCE, v, seq);
    }

    /** A walk from {@code at} to where {@code e} happens, along the village roads, starting now. */
    private static PlanEntry walk(SimContext ctx, EntityId v, long now, double[] at, PlanEntry e) {
        var villager = ctx.get(v, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGER);
        EntityId village = villager == null ? EntityId.NONE : villager.village();
        List<double[]> route = com.ewitulsk.villagersimulator.content.villages.Roads.route(ctx, village, at, e.target());
        List<Double> via = new ArrayList<>();
        for (double[] p : route) for (double c : p) via.add(c);
        PlanEntry walk = new PlanEntry(now, now + 1, BasicActivities.TRAVEL, e.venue(), at[0], at[1], at[2],
                e.tx(), e.ty(), e.tz(), Optional.empty(), via.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(via)));
        long travel = Math.max(1, (long) Math.ceil(walk.length() / DailyPlanner.WALK_SPEED));
        return walk.withTimes(now, now + travel);
    }

    /** Replaces a free-time entry: the best advertisement (or a short idle), then the remaining free time. */
    private static List<PlanEntry> fillFreeTime(SimContext ctx, EntityId v, PlanEntry free, double[] at) {
        long now = ctx.now();
        long end = free.end();
        Optional<Choices.Option> choice = end - now >= MIN_CHOICE ? Choices.choose(ctx, v, at) : Optional.empty();
        if (choice.isPresent()) {
            Choices.Option o = choice.get();
            PlanEntry probe = PlanEntry.stay(now, end, o.ad().activity(), o.venue(), o.point(), Optional.empty());
            long travel = walk(ctx, v, now, at, probe).end() - now;
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
        if (ctx.get(v, CURSOR_SEQ) != arg) return; // stale task from a replaced entry

        finish(ctx, v, plan.entries().get(index));
        if (ctx.get(v, CURSOR_COARSE) != 0) return; // at T3 the day batch takes over
        if (index + 1 < plan.entries().size()) {
            begin(ctx, v, plan, index + 1);
        } else {
            Plan next = planDay(ctx, v, plan.day() + 1);
            begin(ctx, v, next, next.indexAt(ctx.now()));
        }
    }

    /** Resolves the current entry up to now: its activity, its advertisement, the visit and the new position. */
    private static void finish(SimContext ctx, EntityId v, PlanEntry e) {
        long started = ctx.get(v, CURSOR_STARTED);
        Activity activity = ctx.activity(e.activity());
        activity.simulateAbstract(new ActivityContext(ctx, v, e.venue()), started, ctx.now());
        if (e.ad().isPresent()) fulfil(ctx, v, e, started);
        setPosition(ctx, v, e.positionAt(ctx.now()));
        if (!e.venue().isNone() && ctx.alive(e.venue()) && !e.activity().equals(BasicActivities.TRAVEL)) {
            ctx.publish(new PlanHooks.VisitEnded(v, e.venue(), e.activity(), e.ad(), started, ctx.now()));
        }
        checkStarving(ctx, v);
    }

    /**
     * Stops whatever the villager is doing and starts {@code entry} now, e.g. for a player's invitation or a test.
     * The current entry is resolved up to now; later entries that overlap {@code entry} are cut short or dropped.
     */
    public static void interrupt(SimContext ctx, EntityId v, PlanEntry entry) {
        Plan plan = ctx.get(v, PLAN);
        if (plan == null || !ctx.has(v, CURSOR)) return;
        long now = ctx.now();
        int index = ctx.get(v, CURSOR_INDEX);
        PlanEntry current = plan.entries().get(index);
        finish(ctx, v, current);

        List<PlanEntry> out = new ArrayList<>(plan.entries().subList(0, index));
        if (now > current.start()) out.add(current.withTimes(current.start(), now));
        int at = out.size();
        long end = Math.max(now + 1, entry.end());
        out.add(entry.withTimes(now, end));
        for (PlanEntry e : plan.entries().subList(index + 1, plan.entries().size())) {
            // Planned walks started from somewhere else; begin() inserts a fresh walk from wherever the villager is.
            if (e.end() <= end || e.activity().equals(BasicActivities.TRAVEL)) continue;
            out.add(e.start() < end ? e.withTimes(end, e.end()) : e);
        }
        begin(ctx, v, new Plan(plan.day(), out), at);
    }

    // ------------------------------------------------------------------------------------------------ T3

    /**
     * The villager moved to T3 (far from every player): stop entry-by-entry simulation and resolve whole days at once
     * (docs/DESIGN.md §4.2). The current entry is resolved up to now; needs are frozen until the next day batch.
     * The plan itself is dropped: a T3 villager's day only exists during its day batch, which keeps far-away
     * villagers small (docs/ROADMAP.md Phase 6).
     */
    public static void goCoarse(SimContext ctx, EntityId v) {
        if (!ctx.has(v, CURSOR) || ctx.get(v, CURSOR_COARSE) != 0) return;
        PlanEntry current = current(ctx, v);
        if (current != null) finish(ctx, v, current);
        ctx.set(v, CURSOR_COARSE, 1);
        ctx.set(v, CURSOR_SEQ, ctx.get(v, CURSOR_SEQ) + 1); // the pending entry task goes stale
        ctx.remove(v, PLAN);
        ctx.set(v, CURSOR_INDEX, -1);
        Needs.freeze(ctx, v);
        long next = com.ewitulsk.villagersimulator.api.sim.SimTime.dayStart(ctx.now()) + com.ewitulsk.villagersimulator.api.sim.SimTime.TICKS_PER_DAY;
        ctx.schedule(next, dayBatch(ctx, v), v, ctx.get(v, CURSOR_SEQ));
    }

    private static TaskType dayBatch(SimContext ctx, EntityId v) {
        var villager = ctx.get(v, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGER);
        return villager != null && villager.employed() ? DAY_BATCH_WORKER : DAY_BATCH;
    }

    /** The villager left T3: pick the plan up again at the entry for right now. */
    public static void resume(SimContext ctx, EntityId v) {
        if (!ctx.has(v, CURSOR) || ctx.get(v, CURSOR_COARSE) == 0) return;
        ctx.set(v, CURSOR_COARSE, 0);
        long day = com.ewitulsk.villagersimulator.api.sim.SimTime.day(ctx.now());
        Plan plan = ctx.get(v, PLAN);
        if (plan == null || plan.day() != day) plan = planDay(ctx, v, day);
        begin(ctx, v, plan, plan.indexAt(ctx.now()));
    }

    /**
     * One coarse day at T3: plan the day, run its fixed activities (work produces goods), eat up to three meals from
     * the village's food, and set needs to where a normal day would leave them. No venue visits, so no interactions.
     */
    private static void coarseDay(SimContext ctx, EntityId v, long arg) {
        if (!ctx.alive(v) || !ctx.has(v, CURSOR) || ctx.get(v, CURSOR_COARSE) == 0 || ctx.get(v, CURSOR_SEQ) != arg) return;
        long day = com.ewitulsk.villagersimulator.api.sim.SimTime.day(ctx.now());
        Plan plan = generateDay(ctx, v, day);
        for (PlanEntry e : plan.entries()) {
            if (e.activity().equals(BasicActivities.FREE_TIME) || e.ad().isPresent()) continue;
            ctx.activity(e.activity()).simulateAbstract(new ActivityContext(ctx, v, e.venue()), e.start(), e.end());
        }
        var villager = ctx.get(v, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGER);
        // Up to three meals from the village's eateries, starting at a different one for each villager so the bread
        // of every bakery gets eaten.
        int meals = 0;
        List<EntityId> food = villager == null ? List.of()
                : com.ewitulsk.villagersimulator.content.villages.Villages.services(ctx, villager.village(), "eat");
        int first = food.isEmpty() ? 0 : (int) Math.floorMod(villager.seed(), (long) food.size());
        for (int k = 0; k < food.size() && meals < 3; k++) {
            EntityId place = food.get((first + k) % food.size());
            int bread = Buildings.stock(ctx, place, Buildings.BREAD);
            int eat = Math.min(3 - meals, bread);
            if (eat <= 0) continue;
            Buildings.setStock(ctx, place, Buildings.BREAD, bread - eat);
            meals += eat;
        }
        Needs.coarseDay(ctx, v, meals);
        checkStarving(ctx, v);
        int index = plan.indexAt(ctx.now());
        ctx.set(v, CURSOR_INDEX, -1);
        ctx.set(v, CURSOR_STARTED, ctx.now());
        setPosition(ctx, v, plan.entries().get(index).target());
        int seq = ctx.get(v, CURSOR_SEQ) + 1;
        ctx.set(v, CURSOR_SEQ, seq);
        ctx.schedule(ctx.now() + com.ewitulsk.villagersimulator.api.sim.SimTime.TICKS_PER_DAY, dayBatch(ctx, v), v, seq);
    }

    /** Applies a finished advertisement: goods consumed, need gains and effects, scaled by how much was completed. */
    private static void fulfil(SimContext ctx, EntityId v, PlanEntry e, long started) {
        if (e.venue().isNone() || !ctx.alive(e.venue())) return;
        Advertisement ad = ctx.registry(BuildingType.REGISTRY).find(ctx.get(e.venue(), Buildings.BUILDING).type())
                .flatMap(t -> t.advertisement(e.ad().get())).orElse(null);
        if (ad == null) return; // removed by a data reload
        long planned = Math.max(1, e.end() - e.start());
        double fraction = Math.max(0, Math.min(1, (double) (ctx.now() - started) / planned));
        if (!ad.consumes().isEmpty() && fraction < 0.5) return; // the goods were taken at the start, but barely used
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

    /**
     * Takes the goods an advertisement consumes when the villager starts using it, so a rush can't sell the same
     * bread twice. Returns false (taking nothing) if anything is sold out.
     */
    private static boolean takeGoods(SimContext ctx, EntityId v, PlanEntry e) {
        Advertisement ad = ctx.registry(BuildingType.REGISTRY).find(ctx.get(e.venue(), Buildings.BUILDING).type())
                .flatMap(t -> t.advertisement(e.ad().get())).orElse(null);
        if (ad == null || ad.consumes().isEmpty()) return true;
        for (Map.Entry<String, Integer> good : ad.consumes().entrySet()) {
            if (Buildings.stock(ctx, e.venue(), good.getKey()) < good.getValue()) {
                ctx.events().record(BasicActivities.EVENT_NO_FOOD, v, "No " + good.getKey() + " left");
                return false;
            }
        }
        for (Map.Entry<String, Integer> good : ad.consumes().entrySet()) {
            Buildings.setStock(ctx, e.venue(), good.getKey(), Buildings.stock(ctx, e.venue(), good.getKey()) - good.getValue());
        }
        return true;
    }

    private static void checkStarving(SimContext ctx, EntityId v) {
        boolean starving = Needs.HUNGER.value(ctx, v) <= 0f;
        boolean flagged = ctx.get(v, Needs.STARVING) != 0;
        if (starving && !flagged) ctx.events().record(EVENT_STARVING, v, "Hunger reached zero");
        if (starving != flagged) ctx.set(v, Needs.STARVING, starving ? 1 : 0);
    }
}
