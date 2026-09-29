package com.ewitulsk.villagersimulator.api.sim.logic;

/** An action built from data (docs/ARCHITECTURE.md §8.3). Effects are how data changes sim state. */
@FunctionalInterface
public interface Effect {
    Effect NONE = env -> {};

    void apply(EffectEnv env);
}
