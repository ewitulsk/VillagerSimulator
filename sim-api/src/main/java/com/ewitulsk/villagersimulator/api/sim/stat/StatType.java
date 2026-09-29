package com.ewitulsk.villagersimulator.api.sim.stat;

import com.ewitulsk.villagersimulator.api.sim.Id;

/**
 * A tunable number: {@code value = (base + sum of adds) * (1 + sum of mults)} (docs/ARCHITECTURE.md §8.4). Traits,
 * laws, buildings and events change stats by adding {@link Modifier}s, so new systems influence old ones without
 * code changes.
 */
public record StatType(Id id, double base) {}
