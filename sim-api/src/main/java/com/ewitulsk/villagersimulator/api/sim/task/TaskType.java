package com.ewitulsk.villagersimulator.api.sim.task;

import com.ewitulsk.villagersimulator.api.sim.Id;

/**
 * A kind of scheduled task. Scheduled tasks are plain data ({@code time, priority, type, target, arg}), never
 * lambdas, so the whole event queue can be saved. Tasks at the same time run in {@code priority} order, then in the
 * order they were scheduled (docs/ARCHITECTURE.md §6.2).
 */
public record TaskType(Id id, int priority, TaskHandler handler) {}
