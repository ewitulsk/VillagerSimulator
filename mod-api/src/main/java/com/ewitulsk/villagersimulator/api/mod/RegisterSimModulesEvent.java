package com.ewitulsk.villagersimulator.api.mod;

import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects sim modules from every mod (docs/ARCHITECTURE.md §8.1). Fired on the mod bus each time a server starts,
 * so register the same modules every time.
 *
 * <pre>{@code
 * modBus.addListener(RegisterSimModulesEvent.class, e -> e.register(new FountainModule()));
 * }</pre>
 */
public final class RegisterSimModulesEvent extends Event implements IModBusEvent {
    private final List<SimModule> modules = new ArrayList<>();

    public void register(SimModule module) {
        modules.add(module);
    }

    public List<SimModule> modules() {
        return Collections.unmodifiableList(modules);
    }
}
