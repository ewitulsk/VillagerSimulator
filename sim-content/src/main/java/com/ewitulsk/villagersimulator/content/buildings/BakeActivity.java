package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.activity.ActivityContext;
import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.needs.Needs;

/** Working a bakery shift: one bread per {@link #TICKS_PER_BREAD}, into the bakery's stock. */
public final class BakeActivity implements Activity {
    public static final Id ID = VS.id("bake");
    public static final long TICKS_PER_BREAD = 500;

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public void begin(ActivityContext ctx) {
        Needs.awake(ctx.sim(), ctx.actor());
    }

    @Override
    public void simulateAbstract(ActivityContext ctx, long from, long to) {
        if (ctx.venue().isNone() || !ctx.sim().alive(ctx.venue())) return;
        // Counting boundaries crossed (not duration / rate) keeps output independent of how the span is split.
        long baked = Math.floorDiv(to, TICKS_PER_BREAD) - Math.floorDiv(from, TICKS_PER_BREAD);
        int stock = Buildings.stock(ctx.sim(), ctx.venue(), Buildings.BREAD);
        Buildings.setStock(ctx.sim(), ctx.venue(), Buildings.BREAD, (int) Math.min(Buildings.MAX_BREAD, stock + baked));
    }

    @Override
    public Id embodied() {
        return EmbodiedBehaviors.WORK;
    }

    @Override
    public String label() {
        return "Baking";
    }
}
