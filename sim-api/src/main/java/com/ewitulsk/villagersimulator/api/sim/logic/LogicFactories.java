package com.ewitulsk.villagersimulator.api.sim.logic;

import com.google.gson.JsonObject;

/**
 * Factories that turn JSON into conditions and effects. Registered by {@code type} id; the JSON object is the whole
 * definition including {@code "type"}. Throw {@link com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException}
 * for bad definitions.
 */
public final class LogicFactories {
    private LogicFactories() {}

    @FunctionalInterface
    public interface ConditionFactory {
        Condition create(JsonObject json, Logic logic);
    }

    @FunctionalInterface
    public interface EffectFactory {
        Effect create(JsonObject json, Logic logic);
    }
}
