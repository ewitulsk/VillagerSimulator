package com.ewitulsk.villagersimulator.api.sim.task;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;

@FunctionalInterface
public interface TaskHandler {
    /** Runs at the task's time; {@code ctx.now()} is that time. {@code target} may have been destroyed since. */
    void run(SimContext ctx, EntityId target, long arg);
}
