package com.ewitulsk.villagersimulator.content.needs;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;

public final class NeedsModule implements SimModule {
    public static final Id ID = VS.id("needs");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(Needs.HUNGER_COMPONENT);
        r.component(Needs.ENERGY_COMPONENT);
    }
}
