package com.ewitulsk.villagersimulator.api.sim.stat;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.List;

/** Stat values and modifiers for entities. Expired modifiers are ignored and pruned lazily. */
public interface Stats {
    /** @throws IllegalArgumentException if {@code stat} isn't registered */
    double value(EntityId entity, Id stat);

    void add(EntityId entity, Modifier modifier);

    void remove(EntityId entity, Id stat, Id source);

    /** Active modifiers of an entity. */
    List<Modifier> modifiers(EntityId entity);
}
