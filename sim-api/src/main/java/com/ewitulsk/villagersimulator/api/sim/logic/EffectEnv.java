package com.ewitulsk.villagersimulator.api.sim.logic;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;

/**
 * An {@link ExprEnv} for applying effects. {@code fraction} is how much of the source (e.g. an activity) was
 * completed, 0-1, so effects can scale with it.
 */
public interface EffectEnv extends ExprEnv {
    double fraction();

    static EffectEnv of(SimContext sim, EntityId actor, EntityId venue, double[] actorPos, double fraction) {
        return new EffectEnv() {
            @Override
            public SimContext sim() {
                return sim;
            }

            @Override
            public EntityId actor() {
                return actor;
            }

            @Override
            public EntityId venue() {
                return venue;
            }

            @Override
            public double[] actorPos() {
                return actorPos;
            }

            @Override
            public double fraction() {
                return fraction;
            }
        };
    }
}
