package com.ewitulsk.villagersimulator.api.sim.module;

import com.ewitulsk.villagersimulator.api.sim.Id;

/**
 * A place where modules plug behaviour into another module (docs/ARCHITECTURE.md §8.5), e.g. extra utility-AI
 * considerations or plan contributors. The owning module declares the point; any module registers values with
 * {@link SimRegistrar#extend}, and the owner reads them with
 * {@link com.ewitulsk.villagersimulator.api.sim.SimContext#extensions}. Values are kept in registration order.
 */
public record ExtensionPoint<T>(Id id, Class<T> type) {}
