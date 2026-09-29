package com.ewitulsk.villagersimulator.api.sim.view;

import com.ewitulsk.villagersimulator.api.sim.SimContext;

/** Builds an immutable snapshot. Runs on the sim thread; the result is read on other threads. */
@FunctionalInterface
public interface ViewProvider<T> {
    T snapshot(SimContext ctx);
}
