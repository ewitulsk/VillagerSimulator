package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;
import com.ewitulsk.villagersimulator.content.needs.Needs;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Building types (data-driven), placed buildings, stock, usage, the bakery's work, and the {@code stock()} /
 * {@code distance()} expression functions and {@code consume} effect.
 */
public final class BuildingsModule implements SimModule {
    public static final Id ID = VS.id("buildings");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(NeedsModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.registry(BuildingType.REGISTRY);
        r.component(Buildings.BUILDING);
        r.component(Buildings.STOCK);
        r.component(Buildings.USAGE);
        r.activity(new BakeActivity());

        // Stock of a good at the venue.
        r.function(ExpressionFunction.number("stock", List.of(ExprType.STRING), (env, a) -> {
            EntityId venue = env.venue();
            if (venue.isNone() || !env.sim().has(venue, Buildings.BUILDING)) return 0;
            return Buildings.stock(env.sim(), venue, a.string(0, env));
        }).describe("`stock('good')`: how many of a good the venue has."));
        // Blocks from the actor to the centre of the venue.
        r.function(ExpressionFunction.number("distance", List.of(), (env, a) -> {
            double[] p = env.actorPos();
            EntityId venue = env.venue();
            if (p == null || venue.isNone() || !env.sim().has(venue, Buildings.BUILDING)) return 0;
            double[] c = Buildings.centre(env.sim(), venue);
            double dx = p[0] - c[0], dz = p[2] - c[2];
            return Math.sqrt(dx * dx + dz * dz);
        }).describe("Distance in blocks from the actor's position to the venue."));
        // {"type": "consume", "good": "bread", "count": 1}: take goods from the venue's stock.
        r.effect(VS.id("consume"), (json, logic) -> {
            if (!json.has("good")) throw new ExpressionException("\"consume\" needs a \"good\": " + json);
            String good = json.get("good").getAsString();
            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            return env -> {
                if (env.venue().isNone() || !env.sim().has(env.venue(), Buildings.BUILDING)) return;
                int have = Buildings.stock(env.sim(), env.venue(), good);
                Buildings.setStock(env.sim(), env.venue(), good, Math.max(0, have - count));
            };
        });
        r.validator(BuildingsModule::validate);
    }

    /** Every advertisement's activity must exist and its logic must compile; bad ones are reported and skipped. */
    private static void validate(SimContext ctx, Consumer<String> problems) {
        var types = ctx.registry(BuildingType.REGISTRY);
        for (Id typeId : types.ids()) {
            for (Advertisement ad : types.get(typeId).advertisements()) {
                String where = typeId + " advertisement '" + ad.id() + "'";
                String problem = check(ctx, ad);
                if (problem != null) problems.accept(where + ": " + problem);
            }
        }
    }

    /** @return a description of what's wrong with the advertisement, or {@code null} if it's usable */
    public static String check(SimContext ctx, Advertisement ad) {
        try {
            ctx.activity(ad.activity());
            ad.durationTicks();
            for (String need : ad.needs().keySet()) {
                if (Needs.byName(need) == null) return "unknown need '" + need + "'";
            }
            ad.score().ifPresent(s -> ctx.logic().expression(s, ExprType.NUMBER));
            ad.condition().ifPresent(c -> ctx.logic().condition(c));
            ad.effects().ifPresent(e -> ctx.logic().effect(e));
            return null;
        } catch (ExpressionException | IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
