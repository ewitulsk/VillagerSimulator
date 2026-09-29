package com.ewitulsk.villagersimulator.api.sim.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;

/** One side of a relationship: {@code other}, the current friendship and the bond flags. */
public record Relation(EntityId other, float friendship, int bonds) {
    public boolean has(int bond) {
        return (bonds & bond) != 0;
    }
}
