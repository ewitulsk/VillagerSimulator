package com.ewitulsk.villagersimulator.api.sim.command;

import com.ewitulsk.villagersimulator.api.sim.SimContext;

/** A read-only request answered on the sim thread; used for on-demand UI and inspection data. */
@FunctionalInterface
public interface SimQuery<T> {
    T run(SimContext ctx);
}
