package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;

import java.util.List;

/**
 * Forces entities to a tier (debug command and tests). {@code tier == null} releases the force; the tier manager
 * then sets the tier again from player proximity. An empty list means every entity with a tier.
 */
public record ForceTierCommand(List<EntityId> entities, Tier tier) implements SimCommand {
    @Override
    public void apply(SimContext ctx) {
        List<EntityId> targets = entities.isEmpty() ? ctx.with(CoreComponents.TIER) : entities;
        for (EntityId e : targets) {
            if (!ctx.alive(e) || !ctx.has(e, CoreComponents.TIER)) continue;
            if (tier == null) {
                ctx.set(e, CoreComponents.TIER_FORCED, 0);
            } else {
                ctx.set(e, CoreComponents.TIER_FORCED, tier.ordinal() + 1);
                CoreComponents.setTier(ctx, e, tier);
            }
        }
    }
}
