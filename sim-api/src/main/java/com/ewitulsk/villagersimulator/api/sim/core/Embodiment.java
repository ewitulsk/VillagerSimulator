package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;

import java.util.List;

/**
 * What the bridge needs to embody one sim entity: where the plan says it is now (for spawning), where it is heading
 * (for navigation) and what it is doing. Published every window as the {@link #VIEW} view.
 *
 * @param x,y,z       plan position at the view's time
 * @param tx,ty,tz    current destination
 * @param behavior    embodied behaviour key (see {@code EmbodiedBehaviors})
 * @param appearance  appearance data for the client (for villagers: packed genes)
 * @param route       remaining waypoints {@code x, y, z, ...} to the final destination (empty when not travelling);
 *                    {@code tx, ty, tz} is the next of them
 */
public record Embodiment(EntityId id, String name, double x, double y, double z, double tx, double ty, double tz,
                         Id activity, String activityLabel, Id behavior, Tier tier, boolean forced, long appearance,
                         double[] route) {
    public static final ViewKey<List<Embodiment>> VIEW = new ViewKey<>(Id.of("villagersimulator", "embodiment"));
}
