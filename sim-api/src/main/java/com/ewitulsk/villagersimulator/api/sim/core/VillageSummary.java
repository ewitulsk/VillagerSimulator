package com.ewitulsk.villagersimulator.api.sim.core;

import org.jetbrains.annotations.ApiStatus;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;

import java.util.List;

/**
 * One village, as the bridge and scripts see it ({@link #VIEW}).
 *
 * @param radius distance from the centre to the farthest building, blocks
 * @param mode   {@link #DETAILED}, {@link #ABSTRACT} or {@link #COARSE} (docs/ROADMAP.md Phase 6)
 */
@ApiStatus.Experimental
public record VillageSummary(EntityId village, String name, double x, double z, double radius, int population, int mode) {
    /** Near a player or in loaded chunks: villagers are tiered one by one. */
    public static final int DETAILED = 0;
    /** Every villager at T2. */
    public static final int ABSTRACT = 1;
    /** Far from every player: every villager at T3. */
    public static final int COARSE = 2;

    public static final ViewKey<List<VillageSummary>> VIEW = new ViewKey<>(Id.of("villagersimulator", "village_summaries"));
}
