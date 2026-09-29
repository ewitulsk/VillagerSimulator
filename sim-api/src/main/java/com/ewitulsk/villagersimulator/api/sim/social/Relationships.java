package com.ewitulsk.villagersimulator.api.sim.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;

import java.util.List;

/**
 * The relationship graph (docs/ARCHITECTURE.md §5.4): sparse, symmetric, one friendship value (-100..100) and a set
 * of typed bonds per pair. Weak ties fade over time and are evicted when a villager knows too many people; pairs
 * with bonds never fade.
 */
public interface Relationships {
    /** Friendship between two entities now; 0 if they don't know each other. */
    float friendship(EntityId a, EntityId b);

    /** Adds {@code delta} to both directions, clamped to [-100, 100]. */
    void changeFriendship(EntityId a, EntityId b, float delta);

    boolean hasBond(EntityId a, EntityId b, int bond);

    /** Sets or clears a bond ({@link Bonds}) in both directions. */
    void setBond(EntityId a, EntityId b, int bond, boolean on);

    /** Everyone {@code a} knows, strongest ties first. */
    List<Relation> of(EntityId a);

    /**
     * Everyone {@code a} knows, in storage order (deterministic, but not sorted). Cheaper than {@link #of} for hot
     * paths that look at every tie.
     */
    default void forEach(EntityId a, java.util.function.Consumer<Relation> action) {
        of(a).forEach(action);
    }

    /** How many people {@code a} knows. */
    int count(EntityId a);
}
