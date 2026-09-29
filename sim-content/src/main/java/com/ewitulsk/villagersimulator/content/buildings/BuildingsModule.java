package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;

import java.util.Set;

/** Building types (data-driven), placed buildings, their stock, and the bakery's work. */
public final class BuildingsModule implements SimModule {
    public static final Id ID = VS.id("buildings");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(NeedsModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.registry(BuildingType.REGISTRY);
        r.component(Buildings.BUILDING);
        r.component(Buildings.STOCK);
        r.activity(new BakeActivity());
    }
}
