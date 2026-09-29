package com.ewitulsk.villagersimulator.api.sim.scenario;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Consumer;

/** A named scenario: a body that builds a world, warps it and records checks. */
@ApiStatus.Experimental
public record ScenarioDefinition(String name, String description, Consumer<SimScenario> body) {}
