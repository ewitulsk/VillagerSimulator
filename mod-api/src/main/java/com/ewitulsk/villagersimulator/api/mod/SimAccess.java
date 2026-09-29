package com.ewitulsk.villagersimulator.api.mod;

import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioResult;
import com.ewitulsk.villagersimulator.api.sim.view.SimViews;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * The running sim, as addons and scripts see it (docs/ARCHITECTURE.md §10.2). The sim runs on its own thread:
 * commands are applied at its next boundary, queries complete later, and views are the latest published snapshot.
 * Nothing here blocks. Get it from {@link VillagerSimApi#server()}.
 */
public interface SimAccess {
    MinecraftServer server();

    /** Applies a command on the sim thread at its next boundary. */
    void submit(SimCommand command);

    /** Runs a read on the sim thread. Complete-handlers run on the sim thread: hop back with {@code server().execute}. */
    <T> CompletableFuture<T> query(SimQuery<T> query);

    /** The latest published views. Safe on any thread. */
    SimViews views();

    /** Sim time of the latest views, in sim ticks. */
    long time();

    /** Scenarios that {@code /vs scenario run} and scripts can run. */
    ScenarioRegistry scenarios();

    /**
     * Runs a registered scenario headless, in a fresh world built from the server's modules and data, off the sim
     * thread. The live sim is never touched.
     */
    CompletableFuture<ScenarioResult> runScenario(String name);
}
