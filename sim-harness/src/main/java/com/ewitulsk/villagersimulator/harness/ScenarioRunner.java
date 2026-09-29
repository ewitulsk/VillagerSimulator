package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioResult;
import com.ewitulsk.villagersimulator.core.SimWorld;

/** Runs a {@link ScenarioDefinition} in a fresh world of its own, in virtual time, and reports its checks. */
public final class ScenarioRunner {
    private ScenarioRunner() {}

    public static ScenarioResult run(ScenarioDefinition definition, SimWorld freshWorld) {
        Scenario s = Scenario.of(freshWorld);
        long start = System.nanoTime(), from = freshWorld.now();
        String error = null;
        try {
            definition.body().accept(s);
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            freshWorld.close();
        }
        return new ScenarioResult(definition.name(), s.checks(), error, freshWorld.now() - from,
                (System.nanoTime() - start) / 1_000_000);
    }

    /** Runs it in a fresh world with the base game modules and classpath data. */
    public static ScenarioResult run(ScenarioDefinition definition) {
        return run(definition, Scenario.start().world());
    }
}
