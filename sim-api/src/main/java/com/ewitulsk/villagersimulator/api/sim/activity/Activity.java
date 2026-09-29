package com.ewitulsk.villagersimulator.api.sim.activity;

import com.ewitulsk.villagersimulator.api.sim.Id;

/**
 * Something a villager does, with an implementation per tier (docs/ARCHITECTURE.md §6.5). Only
 * {@link #simulateAbstract} is required, so a new Activity works at every tier immediately and just looks generic
 * when observed up close.
 */
public interface Activity {
    Id id();

    /** Called when an actor starts this activity, at any tier. Use it to set need rates and similar state. */
    default void begin(ActivityContext ctx) {}

    /** Resolves the span {@code [from, to)} at once: production, consumption, need changes. Required. */
    void simulateAbstract(ActivityContext ctx, long from, long to);

    /** The embodied behaviour the Minecraft bridge plays at T0. */
    default Id embodied() {
        return EmbodiedBehaviors.IDLE;
    }

    /** A short human-readable label for name tags and inspection. */
    default String label() {
        return id().path();
    }
}
