package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;

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
    }
}
