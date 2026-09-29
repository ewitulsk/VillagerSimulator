package com.ewitulsk.villagersimulator.content.needs;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.content.VS;
import com.google.gson.JsonElement;

import java.util.List;

/**
 * Needs, their decay stats, the {@code need()} / {@code deficit()} expression functions and the {@code need} effect:
 * {@code {"type": "need", "need": "social", "amount": 30}} (scaled by how much of the activity was completed).
 */
public final class NeedsModule implements SimModule {
    public static final Id ID = VS.id("needs");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public void register(SimRegistrar r) {
        for (Needs.NeedType t : Needs.ALL) {
            r.component(t.need().component());
            r.stat(new StatType(t.decayStat(), 1.0));
        }
        r.function(ExpressionFunction.number("need", List.of(ExprType.STRING), (env, a) -> value(env, a.string(0, env))).describe("`need('hunger')`: the actor's need, 0 (desperate) to 100 (satisfied)."));
        r.function(ExpressionFunction.number("deficit", List.of(ExprType.STRING), (env, a) -> Need.MAX - value(env, a.string(0, env))).describe("`deficit('hunger')`: 100 minus the need."));
        r.effect(VS.id("need"), (json, logic) -> {
            JsonElement need = json.get("need");
            JsonElement amount = json.get("amount");
            if (need == null || amount == null) throw new ExpressionException("\"need\" effect needs \"need\" and \"amount\": " + json);
            Needs.NeedType type = Needs.byName(need.getAsString());
            if (type == null) throw new ExpressionException("Unknown need '" + need.getAsString() + "'");
            double delta = amount.getAsDouble();
            return env -> {
                EntityId actor = env.actor();
                if (actor.isNone() || !env.sim().has(actor, type.need().component())) return;
                type.need().add(env.sim(), actor, (float) (delta * env.fraction()));
            };
        });
    }

    private static double value(ExprEnv env, String name) {
        Needs.NeedType t = Needs.byName(name);
        SimContext ctx = env.sim();
        if (t == null || env.actor().isNone() || !ctx.has(env.actor(), t.need().component())) return 0;
        return t.need().value(ctx, env.actor());
    }
}
