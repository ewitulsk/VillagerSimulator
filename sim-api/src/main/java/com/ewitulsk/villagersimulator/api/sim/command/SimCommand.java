package com.ewitulsk.villagersimulator.api.sim.command;

import com.ewitulsk.villagersimulator.api.sim.SimContext;

/**
 * A request from outside the sim (bridge, UI, scripts). Commands are queued and applied on the sim thread at a
 * window boundary (docs/ARCHITECTURE.md §7).
 */
@FunctionalInterface
public interface SimCommand {
    void apply(SimContext ctx);
}
