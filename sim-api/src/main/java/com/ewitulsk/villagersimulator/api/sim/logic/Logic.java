package com.ewitulsk.villagersimulator.api.sim.logic;

import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.expr.Expression;
import com.google.gson.JsonElement;

import java.util.Collection;

/**
 * Compiles data-defined logic: expressions, conditions and effects. Results are cached by source, and the cache is
 * cleared when data reloads. All methods throw {@link com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException}
 * on bad input.
 */
public interface Logic {
    /** Compiles an expression that must have type {@code expected}. */
    Expression expression(String source, ExprType expected);

    /** A condition from JSON: an object with {@code "type"}, or a string (shorthand for an {@code expr} condition). */
    Condition condition(JsonElement json);

    /** An effect from JSON: an object with {@code "type"}, or an array (all of them). */
    Effect effect(JsonElement json);

    Collection<ExpressionFunction> functions();
}
