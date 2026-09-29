package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;

import java.util.List;
import java.util.Set;

/** Villages and villager identity. Spawning publishes {@link VillagerCreated} for other modules to react to. */
public final class VillagesModule implements SimModule {
    public static final Id ID = VS.id("villages");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(BuildingsModule.ID, NeedsModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(Villages.VILLAGE);
        r.component(Villages.VILLAGER);
        r.component(Appearance.COMPONENT);
        r.component(Villages.DISTRICT);
        r.component(Roads.COMPONENT);
        r.component(VillageTiers.COMPONENT);
        r.view(Villages.DISTRICTS, Villages::districts);
        r.view(VillageTiers.VIEW, VillageTiers::summaries);
        // True when the venue is the actor's home / workplace.
        r.function(ExpressionFunction.bool("is_home", List.of(), (env, a) -> {
            Villager v = env.actor().isNone() ? null : env.sim().get(env.actor(), Villages.VILLAGER);
            return v != null && !env.venue().isNone() && v.home().equals(env.venue());
        }));
        r.function(ExpressionFunction.bool("is_workplace", List.of(), (env, a) -> {
            Villager v = env.actor().isNone() ? null : env.sim().get(env.actor(), Villages.VILLAGER);
            return v != null && v.employed() && v.workplace().equals(env.venue());
        }));
        r.function(ExpressionFunction.bool("has_job", List.of(), (env, a) -> {
            Villager v = env.actor().isNone() ? null : env.sim().get(env.actor(), Villages.VILLAGER);
            return v != null && v.employed();
        }));
    }
}
