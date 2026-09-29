package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.core.SimRuntime;
import com.ewitulsk.villagersimulator.core.SimWorld;
import com.ewitulsk.villagersimulator.core.persistence.SqliteSimStore;
import com.ewitulsk.villagersimulator.neoforge.RegisterSimModulesEvent;
import com.ewitulsk.villagersimulator.neoforge.world.VanillaPoints;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The sim for one running server: the world on its own thread, its SQLite store, and the bridge. Created when the
 * server starts, saved with the world, closed when the server stops (docs/ARCHITECTURE.md §12.2).
 */
public final class SimServer {
    private static final Logger LOG = LoggerFactory.getLogger("VillagerSim");
    private static volatile SimServer instance;

    private final MinecraftServer server;
    private final List<SimModule> modules;
    private final com.ewitulsk.villagersimulator.core.data.DataSource data;
    private final SqliteSimStore store;
    private final SimRuntime runtime;
    private final SimBridge bridge;
    private final SimWorld world;
    private final ExecutorService saveThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VillagerSim-Save");
        t.setDaemon(true);
        return t;
    });

    private SimServer(MinecraftServer server) {
        this.server = server;
        RegisterSimModulesEvent event = new RegisterSimModulesEvent();
        ModLoader.postEvent(event);
        this.modules = List.copyOf(event.modules());
        this.data = VanillaPoints.augment(server, SimDataReloadListener.current());

        ServerLevel overworld = server.overworld();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("villagersimulator").resolve("sim.db");
        this.store = SqliteSimStore.open(file);
        SimWorld world = newWorld(overworld.getDayTime());
        store.load().ifPresent(saved -> world.restore(saved.data(), saved.events()));
        LOG.info("Sim started: {} modules, {} entities, time {} ({})", modules.size(), world.entityCount(), world.now(), file);
        logProblems(world.problems());

        this.world = world;
        this.runtime = new SimRuntime(world, "VillagerSim");
        this.bridge = new SimBridge(server, runtime);
        runtime.start();
    }

    private static void logProblems(List<String> problems) {
        for (String p : problems) LOG.warn("Sim data problem: {}", p);
    }

    /** Applies reloaded datapack data to the running sim ({@code /reload}). Server thread. */
    public void reload(com.ewitulsk.villagersimulator.core.data.DataSource raw) {
        com.ewitulsk.villagersimulator.core.data.DataSource augmented = VanillaPoints.augment(server, raw);
        runtime.submitWorld(w -> {
            w.reloadData(augmented);
            logProblems(w.problems());
        });
    }

    /** The data the sim was started with (datapacks plus detected vanilla points). */
    public com.ewitulsk.villagersimulator.core.data.DataSource data() {
        return data;
    }

    /** A fresh world with this server's modules and data, e.g. to replay a scenario headless in a GameTest. */
    public SimWorld newWorld(long startTime) {
        return SimWorld.builder().modules(modules).data(data).startTime(startTime).build();
    }

    /** A frozen data registry. Registries never change after the sim is built, so this is safe on any thread. */
    public <T> SimRegistry<T> registry(RegistryKey<T> key) {
        return world.registry(key);
    }

    public static SimServer get() {
        return instance;
    }

    public SimRuntime runtime() {
        return runtime;
    }

    public SimBridge bridge() {
        return bridge;
    }

    public MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    public static synchronized void start(MinecraftServer server) {
        if (instance != null) return;
        instance = new SimServer(server);
    }

    public static void tick(MinecraftServer server) {
        SimServer s = instance;
        if (s == null) {
            // Some server types (e.g. the GameTest server) may not fire ServerStartingEvent before ticking.
            start(server);
            s = instance;
        }
        s.bridge.tick();
    }

    public static void onLevelSave(LevelEvent.Save event) {
        SimServer s = instance;
        if (s != null && event.getLevel() instanceof Level level && level.dimension() == Level.OVERWORLD) s.save();
    }

    public static synchronized void stop() {
        SimServer s = instance;
        if (s == null) return;
        instance = null;
        try {
            s.save().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error("Final sim save failed", e);
        }
        s.bridge.shutdown();
        s.runtime.close();
        s.saveThread.shutdown();
        try {
            s.saveThread.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        s.store.close();
        LOG.info("Sim stopped");
    }

    /** Snapshots on the sim thread, then writes on the save thread. Never blocks the caller. */
    public CompletableFuture<Void> save() {
        return runtime.snapshot(store.lastSavedEventId())
                .thenAcceptAsync(snapshot -> store.save(snapshot, SimWorld.SNAPSHOT_FORMAT), saveThread)
                .whenComplete((ok, error) -> {
                    if (error != null) LOG.error("Sim save failed", error);
                });
    }
}
