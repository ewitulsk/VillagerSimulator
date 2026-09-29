package com.ewitulsk.villagersimulator.api.sim.module;

import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.Set;

/**
 * A unit of sim functionality. All base game features are modules built on this API, exactly like addons
 * (docs/ARCHITECTURE.md §8.1). In game, modules are collected with {@code RegisterSimModulesEvent}; headless, through
 * {@link java.util.ServiceLoader}.
 */
public interface SimModule {
    Id id();

    /** Modules that must register before this one. Cycles and missing modules are errors. */
    default Set<Id> dependencies() {
        return Set.of();
    }

    void register(SimRegistrar registrar);
}
