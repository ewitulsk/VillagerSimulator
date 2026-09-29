package com.ewitulsk.villagersimulator.api.sim.logic;

import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;

/** A predicate built from data (docs/ARCHITECTURE.md §8.3). Pure. */
@FunctionalInterface
public interface Condition {
    Condition ALWAYS = env -> true;

    boolean test(ExprEnv env);
}
