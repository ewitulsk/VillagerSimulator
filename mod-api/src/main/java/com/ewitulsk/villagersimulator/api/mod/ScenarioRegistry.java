package com.ewitulsk.villagersimulator.api.mod;

import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;

import java.util.List;
import java.util.Optional;

/** Named scenarios for {@code /vs scenario run}. Thread-safe. Registering a name again replaces it. */
public interface ScenarioRegistry {
    void register(ScenarioDefinition scenario);

    void remove(String name);

    Optional<ScenarioDefinition> find(String name);

    List<ScenarioDefinition> all();
}
