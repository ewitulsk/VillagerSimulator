package com.ewitulsk.villagersimulator.api.sim.scenario;

import org.jetbrains.annotations.ApiStatus;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;

import java.util.List;

/**
 * The scenario DSL (docs/ARCHITECTURE.md §19): build a small world, run it in virtual time, and check what happened.
 * Scenarios run headless in a world of their own, so "3 sim-days" takes milliseconds and never touches the live
 * sim. Java tests, {@code /vs scenario run} and KubeJS scripts all use this interface.
 *
 * <pre>{@code
 * s.spawnHamlet("Testford", 42, 8);
 * s.warp("3d");
 * s.expect("nobody starves", s.events("villagersimulator:starving") == 0);
 * }</pre>
 */
@ApiStatus.Experimental
public interface SimScenario {
    /** The scenario's world, for anything the DSL doesn't cover. */
    SimContext sim();

    long now();

    /** Spawns the standard hamlet (well, bakery, houses) and returns the village. */
    EntityId spawnHamlet(String name, long seed, int villagers);

    /** Spawns the two-district town layout centred at {@code (x, z)} and returns the village. */
    EntityId spawnTown(String name, long seed, int villagers, int x, int z);

    /** Runs the sim forward {@code ticks} sim ticks. */
    SimScenario warp(long ticks);

    /** Runs the sim forward a duration such as {@code "3d"}, {@code "6h"} or {@code "30m"}. */
    SimScenario warp(String duration);

    /** The village's residents. */
    List<EntityId> residents(EntityId village);

    /** How many event records of {@code type} (e.g. {@code "villagersimulator:starving"}) were logged. */
    int events(String type);

    /** Records a check; the scenario passes when every check passed. */
    SimScenario expect(String description, boolean passed);

    List<Check> checks();

    record Check(String description, boolean passed) {}
}
