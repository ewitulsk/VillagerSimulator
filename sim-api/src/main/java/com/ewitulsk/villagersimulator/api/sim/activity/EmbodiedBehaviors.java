package com.ewitulsk.villagersimulator.api.sim.activity;

import com.ewitulsk.villagersimulator.api.sim.Id;

/** Built-in embodied behaviour keys. The bridge maps each key to entity behaviour and animation. */
public final class EmbodiedBehaviors {
    public static final Id IDLE = Id.of("villagersimulator", "idle");
    public static final Id WALK = Id.of("villagersimulator", "walk");
    public static final Id WORK = Id.of("villagersimulator", "work");
    public static final Id SLEEP = Id.of("villagersimulator", "sleep");
    public static final Id EAT = Id.of("villagersimulator", "eat");

    private EmbodiedBehaviors() {}
}
