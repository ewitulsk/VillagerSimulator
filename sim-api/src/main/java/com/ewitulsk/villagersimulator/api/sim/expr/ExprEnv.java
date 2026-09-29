package com.ewitulsk.villagersimulator.api.sim.expr;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;

/**
 * What an expression is evaluated against: the sim, the actor (usually a villager), the venue (usually a building)
 * and the actor's position. Any of actor/venue may be {@link EntityId#NONE}; functions must cope.
 */
public interface ExprEnv {
    SimContext sim();

    EntityId actor();

    EntityId venue();

    /** The actor's current position {@code {x, y, z}}, or {@code null} if unknown. */
    double[] actorPos();

    /** A simple immutable environment. */
    static ExprEnv of(SimContext sim, EntityId actor, EntityId venue, double[] actorPos) {
        return new ExprEnv() {
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
        };
    }
}
