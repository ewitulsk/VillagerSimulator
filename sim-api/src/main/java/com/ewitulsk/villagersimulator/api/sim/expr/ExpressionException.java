package com.ewitulsk.villagersimulator.api.sim.expr;

/** A parse, type or definition error, reported at load time with the position in the source. */
public class ExpressionException extends RuntimeException {
    private final int position;

    public ExpressionException(String message, int position) {
        super(message);
        this.position = position;
    }

    public ExpressionException(String message) {
        this(message, -1);
    }

    /** Character offset of the error in the expression, or -1. */
    public int position() {
        return position;
    }
}
