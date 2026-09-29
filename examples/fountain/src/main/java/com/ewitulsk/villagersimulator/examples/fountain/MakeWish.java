package com.ewitulsk.villagersimulator.examples.fountain;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.activity.ActivityContext;

/**
 * Making a wish at the fountain. Like every Activity it works at every tier from {@link #simulateAbstract} alone:
 * a visit of at least ten minutes logs a wish. Up close the puppet shows it with {@link WishBehaviour}.
 */
public final class MakeWish implements Activity {
    public static final Id ID = FountainAddon.id("make_wish");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public void simulateAbstract(ActivityContext ctx, long from, long to) {
        if (to - from >= SimTime.minutes(10)) {
            ctx.sim().events().record(FountainModule.EVENT_WISH, ctx.actor(), "Made a wish at the fountain");
        }
    }

    @Override
    public Id embodied() {
        return ID;
    }

    @Override
    public String label() {
        return "Making a wish";
    }
}
