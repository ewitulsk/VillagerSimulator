package com.ewitulsk.villagersimulator.api.sim.expr;

/**
 * A compiled expression. Pure: evaluating it never changes sim state, so it is safe to call any number of times, in
 * any order. Call the method matching {@link #type()}.
 */
public interface Expression {
    ExprType type();

    String source();

    double number(ExprEnv env);

    boolean bool(ExprEnv env);

    String string(ExprEnv env);

    /** The value as an object, for display ({@code /vs expr eval}). */
    default Object value(ExprEnv env) {
        return switch (type()) {
            case NUMBER -> number(env);
            case BOOL -> bool(env);
            case STRING -> string(env);
        };
    }
}
