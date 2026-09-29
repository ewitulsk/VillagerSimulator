package com.ewitulsk.villagersimulator.api.sim.core;

/** Level-of-detail tiers (docs/DESIGN.md §4.2). */
public enum Tier {
    /** Near a player: a real entity. */
    T0,
    /** Loaded, no player nearby. */
    T1,
    /** Unloaded: event-driven. */
    T2,
    /** Far away: daily batch. */
    T3;

    public static Tier byOrdinal(int ordinal) {
        return values()[ordinal];
    }
}
