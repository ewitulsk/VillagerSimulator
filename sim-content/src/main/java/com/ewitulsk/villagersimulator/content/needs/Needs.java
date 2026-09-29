package com.ewitulsk.villagersimulator.content.needs;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.VS;

/** Phase 0 needs: Hunger and Energy (100 = satisfied, 0 = starving / exhausted). */
public final class Needs {
    public static final DenseComponent HUNGER_COMPONENT = new DenseComponent(VS.id("hunger"), 1);
    public static final Need HUNGER = new Need(HUNGER_COMPONENT);
    /** 1 while hunger is at 0; used to record a starving event once per episode. */
    public static final IntField STARVING = HUNGER_COMPONENT.intField("starving");

    public static final DenseComponent ENERGY_COMPONENT = new DenseComponent(VS.id("energy"), 1);
    public static final Need ENERGY = new Need(ENERGY_COMPONENT);

    /** Awake: hunger empties in 12 hours, energy in 16. */
    public static final float AWAKE_HUNGER = -Need.MAX / (12 * SimTime.TICKS_PER_HOUR);
    public static final float AWAKE_ENERGY = -Need.MAX / (16 * SimTime.TICKS_PER_HOUR);
    /** Asleep: hunger drops slowly, energy refills in 8 hours. */
    public static final float ASLEEP_HUNGER = -Need.MAX / (36 * SimTime.TICKS_PER_HOUR);
    public static final float ASLEEP_ENERGY = Need.MAX / (8 * SimTime.TICKS_PER_HOUR);

    private Needs() {}

    public static void init(SimContext ctx, EntityId e, float hunger, float energy) {
        ctx.add(e, HUNGER_COMPONENT);
        ctx.add(e, ENERGY_COMPONENT);
        HUNGER.set(ctx, e, hunger);
        ENERGY.set(ctx, e, energy);
        awake(ctx, e);
    }

    public static void awake(SimContext ctx, EntityId e) {
        HUNGER.setRate(ctx, e, AWAKE_HUNGER);
        ENERGY.setRate(ctx, e, AWAKE_ENERGY);
    }

    public static void asleep(SimContext ctx, EntityId e) {
        HUNGER.setRate(ctx, e, ASLEEP_HUNGER);
        ENERGY.setRate(ctx, e, ASLEEP_ENERGY);
    }
}
