package com.ewitulsk.villagersimulator.content.needs;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.content.VS;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Villager needs (100 = satisfied, 0 = desperate), each lazily evaluated. Decay while awake is scaled by the need's
 * decay stat ({@code villagersimulator:<need>_decay}, base 1), so modifiers can slow or speed it up.
 */
public final class Needs {
    private static final long H = SimTime.TICKS_PER_HOUR;

    public static final DenseComponent HUNGER_COMPONENT = new DenseComponent(VS.id("hunger"), 1);
    public static final Need HUNGER = new Need(HUNGER_COMPONENT);
    /** 1 while hunger is at 0; used to record a starving event once per episode. */
    public static final IntField STARVING = HUNGER_COMPONENT.intField("starving");
    public static final DenseComponent ENERGY_COMPONENT = new DenseComponent(VS.id("energy"), 1);
    public static final Need ENERGY = new Need(ENERGY_COMPONENT);
    public static final Need SOCIAL = new Need(new DenseComponent(VS.id("social"), 1));
    public static final Need FUN = new Need(new DenseComponent(VS.id("fun"), 1));
    public static final Need HYGIENE = new Need(new DenseComponent(VS.id("hygiene"), 1));
    public static final Need COMFORT = new Need(new DenseComponent(VS.id("comfort"), 1));

    /**
     * A need with its per-tick rates awake and asleep, and its priority: how much it counts when choosing what to do.
     * Bodily needs come first (Maslow): a hungry villager eats before they socialise.
     */
    public record NeedType(String name, Need need, float awake, float asleep, float priority) {
        public com.ewitulsk.villagersimulator.api.sim.Id decayStat() {
            return VS.id(name + "_decay");
        }
    }

    private static float per(long ticks) {
        return Need.MAX / ticks;
    }

    public static final List<NeedType> ALL = List.of(
            new NeedType("hunger", HUNGER, -per(12 * H), -per(36 * H), 2.0f),
            new NeedType("energy", ENERGY, -per(16 * H), per(8 * H), 1.5f),
            new NeedType("social", SOCIAL, -per(16 * H), -per(48 * H), 1.0f),
            new NeedType("fun", FUN, -per(14 * H), -per(48 * H), 0.8f),
            new NeedType("hygiene", HYGIENE, -per(24 * H), -per(72 * H), 0.8f),
            new NeedType("comfort", COMFORT, -per(12 * H), per(10 * H), 0.7f));

    private static final Map<String, NeedType> BY_NAME = ALL.stream().collect(Collectors.toMap(NeedType::name, Function.identity()));

    private Needs() {}

    /** @return the need type, or {@code null} */
    public static NeedType byName(String name) {
        return BY_NAME.get(name);
    }

    /** Gives a new villager every need, with values drawn from its seed. */
    public static void init(SimContext ctx, EntityId e, long seed) {
        for (int i = 0; i < ALL.size(); i++) {
            NeedType t = ALL.get(i);
            ctx.add(e, t.need().component());
            t.need().set(ctx, e, 65 + (float) (SimRandom.unit(seed, i, SimRandom.salt("need")) * 25));
        }
        awake(ctx, e);
    }

    /** Adds needs a villager is missing (e.g. from a save made before a need existed). */
    public static void ensure(SimContext ctx, EntityId e) {
        for (NeedType t : ALL) {
            if (!ctx.has(e, t.need().component())) {
                ctx.add(e, t.need().component());
                t.need().set(ctx, e, 75);
            }
        }
    }

    /** Stops all needs changing (T3 villagers between day batches). */
    public static void freeze(SimContext ctx, EntityId e) {
        ensure(ctx, e);
        for (NeedType t : ALL) t.need().setRate(ctx, e, 0);
    }

    /**
     * Where a normal day leaves a villager's needs, for T3 day batches: hunger falls by a day's burn and rises by
     * the meals eaten; sleep restores energy and comfort; free time tops up the rest.
     */
    public static void coarseDay(SimContext ctx, EntityId e, int meals) {
        ensure(ctx, e);
        HUNGER.set(ctx, e, HUNGER.value(ctx, e) - 150 + meals * 55);
        ENERGY.set(ctx, e, 85);
        SOCIAL.set(ctx, e, Math.max(SOCIAL.value(ctx, e), 60));
        FUN.set(ctx, e, Math.max(FUN.value(ctx, e), 55));
        HYGIENE.set(ctx, e, Math.max(HYGIENE.value(ctx, e), 60));
        COMFORT.set(ctx, e, Math.max(COMFORT.value(ctx, e), 70));
        freeze(ctx, e);
    }

    public static void awake(SimContext ctx, EntityId e) {
        setRates(ctx, e, false);
    }

    public static void asleep(SimContext ctx, EntityId e) {
        setRates(ctx, e, true);
    }

    private static void setRates(SimContext ctx, EntityId e, boolean asleep) {
        ensure(ctx, e);
        for (NeedType t : ALL) {
            float rate = asleep ? t.asleep() : t.awake();
            if (rate < 0) rate *= (float) Math.max(0, ctx.stats().value(e, t.decayStat()));
            t.need().setRate(ctx, e, rate);
        }
    }
}
