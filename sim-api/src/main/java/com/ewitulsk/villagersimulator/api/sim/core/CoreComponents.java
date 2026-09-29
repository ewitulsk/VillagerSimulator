package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;

/** Components every sim has; the engine registers them itself. */
public final class CoreComponents {
    /** The tier an entity is simulated at, and an optional forced tier (debug and tests). */
    public static final DenseComponent TIER = new DenseComponent(Id.of("villagersimulator", "tier"), 1);
    /** {@link Tier#ordinal()}. */
    public static final IntField TIER_CURRENT = TIER.intField("current");
    /** {@link Tier#ordinal()} + 1, or 0 when not forced. */
    public static final IntField TIER_FORCED = TIER.intField("forced");

    private CoreComponents() {}

    public static Tier tier(SimContext ctx, EntityId e) {
        return ctx.has(e, TIER) ? Tier.byOrdinal(ctx.get(e, TIER_CURRENT)) : Tier.T2;
    }

    public static boolean forced(SimContext ctx, EntityId e) {
        return ctx.has(e, TIER) && ctx.get(e, TIER_FORCED) != 0;
    }
}
