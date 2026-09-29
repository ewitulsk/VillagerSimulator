package com.ewitulsk.villagersimulator.examples.fountain;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;

import java.util.List;

/** The fountain's sim module: the wish Activity and the {@code wishes()} expression function. */
public final class FountainModule implements SimModule {
    public static final Id ID = FountainAddon.id("fountain");
    /** Event log type: a villager made a wish at the fountain. */
    public static final Id EVENT_WISH = FountainAddon.id("wish");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public void register(SimRegistrar r) {
        r.activity(new MakeWish());
        // Used by the fountain's own advertisement: villagers who've made few wishes are keener to make one.
        r.function(ExpressionFunction.number("wishes", List.of(),
                        (env, args) -> env.actor().isNone() ? 0 : wishes(env.sim(), env.actor()))
                .describe("How many wishes the actor has made at a fountain (villagersimulator_fountain addon)."));
    }

    /** Wishes the villager has made, from the event log. */
    public static int wishes(SimContext sim, EntityId villager) {
        int n = 0;
        for (EventRecord r : sim.events().byActor(villager)) if (r.type().equals(EVENT_WISH)) n++;
        return n;
    }
}
