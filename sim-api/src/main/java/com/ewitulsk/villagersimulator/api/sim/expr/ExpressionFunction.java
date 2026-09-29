package com.ewitulsk.villagersimulator.api.sim.expr;

import java.util.List;

/**
 * A function callable from expressions, e.g. {@code need('social')}. Modules register functions and they become
 * usable in every expression (docs/ARCHITECTURE.md §9.3). Implementations receive their arguments unevaluated
 * ({@link Args}) so the language stays allocation-free.
 */
public record ExpressionFunction(String name, List<ExprType> params, ExprType result, Impl impl, String description) {

    public ExpressionFunction(String name, List<ExprType> params, ExprType result, Impl impl) {
        this(name, params, result, impl, "");
    }

    /** The same function with a description for the generated reference ({@code docs/EXPRESSIONS.md}). */
    public ExpressionFunction describe(String text) {
        return new ExpressionFunction(name, params, result, impl, text);
    }

    public interface Impl {
        default double number(ExprEnv env, Args args) {
            throw new UnsupportedOperationException();
        }

        default boolean bool(ExprEnv env, Args args) {
            throw new UnsupportedOperationException();
        }

        default String string(ExprEnv env, Args args) {
            throw new UnsupportedOperationException();
        }
    }

    /** Arguments of one call, evaluated on demand. */
    public interface Args {
        double number(int i, ExprEnv env);

        boolean bool(int i, ExprEnv env);

        String string(int i, ExprEnv env);

        /** The argument's value if it is a string literal, else {@code null}. Lets functions pre-resolve names. */
        String constant(int i);
    }

    @FunctionalInterface
    public interface NumberImpl {
        double apply(ExprEnv env, Args args);
    }

    @FunctionalInterface
    public interface BoolImpl {
        boolean apply(ExprEnv env, Args args);
    }

    public static ExpressionFunction number(String name, List<ExprType> params, NumberImpl impl) {
        return new ExpressionFunction(name, params, ExprType.NUMBER, new Impl() {
            @Override
            public double number(ExprEnv env, Args args) {
                return impl.apply(env, args);
            }
        });
    }

    public static ExpressionFunction bool(String name, List<ExprType> params, BoolImpl impl) {
        return new ExpressionFunction(name, params, ExprType.BOOL, new Impl() {
            @Override
            public boolean bool(ExprEnv env, Args args) {
                return impl.apply(env, args);
            }
        });
    }

    public String signature() {
        StringBuilder b = new StringBuilder(name).append('(');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(params.get(i).displayName());
        }
        return b.append(") -> ").append(result.displayName()).toString();
    }
}
