package com.ewitulsk.villagersimulator.content.needs;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;

/**
 * A lazily evaluated need (docs/DESIGN.md §4.6): stored as {@code (value at t0, rate)} and computed only when read.
 * There is no per-tick decay loop. Values are clamped to [0, 100].
 */
public final class Need {
    public static final float MAX = 100f;

    private final DenseComponent component;
    private final FloatField value;
    private final FloatField rate;
    private final LongField t0;

    Need(DenseComponent component) {
        this.component = component;
        this.value = component.floatField("value");
        this.rate = component.floatField("rate");
        this.t0 = component.longField("t0");
    }

    public DenseComponent component() {
        return component;
    }

    public float value(SimContext ctx, EntityId e) {
        return clamp(ctx.get(e, value) + ctx.get(e, rate) * (ctx.now() - ctx.get(e, t0)));
    }

    public float rate(SimContext ctx, EntityId e) {
        return ctx.get(e, rate);
    }

    public void set(SimContext ctx, EntityId e, float v) {
        ctx.set(e, value, clamp(v));
        ctx.set(e, t0, ctx.now());
    }

    public void add(SimContext ctx, EntityId e, float delta) {
        set(ctx, e, value(ctx, e) + delta);
    }

    /** Changes the rate from now on, keeping the current value. */
    public void setRate(SimContext ctx, EntityId e, float perTick) {
        float current = value(ctx, e);
        ctx.set(e, value, current);
        ctx.set(e, t0, ctx.now());
        ctx.set(e, rate, perTick);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(MAX, v));
    }
}
