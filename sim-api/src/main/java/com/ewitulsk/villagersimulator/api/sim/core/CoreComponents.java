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
    /**
     * 1 when the entity matters to a player (docs/DESIGN.md §11.6, attention pinning): it's never simulated coarser
     * than {@link Tier#T2}, even when far away.
     */
    public static final IntField TIER_PINNED = TIER.intField("pinned");

    private CoreComponents() {}

    public static Tier tier(SimContext ctx, EntityId e) {
        return ctx.has(e, TIER) ? Tier.byOrdinal(ctx.get(e, TIER_CURRENT)) : Tier.T2;
    }

    public static boolean forced(SimContext ctx, EntityId e) {
        return ctx.has(e, TIER) && ctx.get(e, TIER_FORCED) != 0;
    }

    public static boolean pinned(SimContext ctx, EntityId e) {
        return ctx.has(e, TIER) && ctx.get(e, TIER_PINNED) != 0;
    }

    /** The tier an unforced request results in: pinned entities never go coarser than T2. */
    public static Tier effective(SimContext ctx, EntityId e, Tier requested) {
        return requested == Tier.T3 && pinned(ctx, e) ? Tier.T2 : requested;
    }

    /** Sets the tier, publishing {@link TierChanged} if it changed. */
    public static void setTier(SimContext ctx, EntityId e, Tier tier) {
        Tier from = tier(ctx, e);
        ctx.set(e, TIER_CURRENT, tier.ordinal());
        if (from != tier) ctx.publish(new TierChanged(e, from, tier));
    }

    /** Pins or unpins an entity; a pinned entity at T3 moves up to T2 at once. */
    public static void setPinned(SimContext ctx, EntityId e, boolean pinned) {
        if (!ctx.has(e, TIER) || pinned(ctx, e) == pinned) return;
        ctx.set(e, TIER_PINNED, pinned ? 1 : 0);
        if (pinned && tier(ctx, e) == Tier.T3 && !forced(ctx, e)) setTier(ctx, e, Tier.T2);
    }
}
