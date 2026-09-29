package com.ewitulsk.villagersimulator.api.mod.event;

import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.List;

/** Collects scenarios for {@code /vs scenario run}. Fired on the mod bus when a server starts. */
public final class RegisterScenariosEvent extends Event implements IModBusEvent {
    private final List<ScenarioDefinition> scenarios = new ArrayList<>();

    public void register(ScenarioDefinition scenario) {
        scenarios.add(scenario);
    }

    public List<ScenarioDefinition> scenarios() {
        return List.copyOf(scenarios);
    }
}
