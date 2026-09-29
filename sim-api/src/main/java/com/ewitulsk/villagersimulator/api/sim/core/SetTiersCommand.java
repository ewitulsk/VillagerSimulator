package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;

import java.util.Map;

/** Sent by the bridge's tier manager when chunk/player proximity changes an entity's tier. Forced tiers win. */
public record SetTiersCommand(Map<EntityId, Tier> tiers) implements SimCommand {
    @Override
    public void apply(SimContext ctx) {
        tiers.forEach((e, tier) -> {
            if (!ctx.alive(e) || !ctx.has(e, CoreComponents.TIER)) return;
            if (ctx.get(e, CoreComponents.TIER_FORCED) != 0) return;
            ctx.set(e, CoreComponents.TIER_CURRENT, tier.ordinal());
        });
    }
}
