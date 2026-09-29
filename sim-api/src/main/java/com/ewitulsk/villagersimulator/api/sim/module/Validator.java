package com.ewitulsk.villagersimulator.api.sim.module;

import com.ewitulsk.villagersimulator.api.sim.SimContext;

import java.util.function.Consumer;

/** Validates loaded data; see {@link SimRegistrar#validator}. */
@FunctionalInterface
public interface Validator {
    void validate(SimContext ctx, Consumer<String> problems);
}
