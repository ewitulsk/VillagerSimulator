package com.ewitulsk.villagersimulator.api.sim.event;

import com.ewitulsk.villagersimulator.api.sim.SimContext;

@FunctionalInterface
public interface EventHandler<E extends SimEvent> {
    void handle(SimContext ctx, E event);
}
