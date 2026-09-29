package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.mod.ScenarioRegistry;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

/** Named scenarios for {@code /vs scenario run}: built-ins, addons' and scripts'. Thread-safe, sorted by name. */
final class ScenarioRegistryImpl implements ScenarioRegistry {
    private final Map<String, ScenarioDefinition> scenarios = new ConcurrentSkipListMap<>();

    @Override
    public void register(ScenarioDefinition scenario) {
        scenarios.put(scenario.name(), scenario);
    }

    @Override
    public void remove(String name) {
        scenarios.remove(name);
    }

    @Override
    public Optional<ScenarioDefinition> find(String name) {
        return Optional.ofNullable(scenarios.get(name));
    }

    @Override
    public List<ScenarioDefinition> all() {
        return List.copyOf(scenarios.values());
    }
}
