package com.ewitulsk.villagersimulator.api.sim.expr;

/** Types in the VS expression language (docs/ARCHITECTURE.md §9). */
public enum ExprType {
    NUMBER, BOOL, STRING;

    public String displayName() {
        return name().toLowerCase();
    }
}
