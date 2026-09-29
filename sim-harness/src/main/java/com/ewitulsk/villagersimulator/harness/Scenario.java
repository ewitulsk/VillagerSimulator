package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.content.ContentModules;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.Village;
import com.ewitulsk.villagersimulator.content.villages.VillageLayouts;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.core.SimWorld;
import com.ewitulsk.villagersimulator.core.data.ClasspathDataSource;
import com.ewitulsk.villagersimulator.core.data.DataSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Headless scenario DSL (docs/ARCHITECTURE.md §19). Runs in <b>virtual time</b>: {@link #warp} jumps straight through
 * the event queue, so "5 sim-days" takes milliseconds.
 *
 * <pre>{@code
 * Scenario s = Scenario.start();
 * EntityId v = s.spawnHamlet("Testford", 42, 8);
 * s.warp(SimTime.days(5));
 * assertTrue(s.stock(v, "bakery", "bread") > 0);
 * }</pre>
 */
public final class Scenario {
    private final SimWorld world;

    private Scenario(SimWorld world) {
        this.world = world;
    }

    /** A world with the base game modules and data from the classpath, starting at tick 0 (day 0, 06:00). */
    public static Scenario start() {
        return start(0);
    }

    public static Scenario start(long startTime) {
        return new Scenario(world(ContentModules.all(), ClasspathDataSource.of(Scenario.class), startTime));
    }

    public static SimWorld world(List<SimModule> modules, DataSource data, long startTime) {
        return SimWorld.builder().modules(modules).data(data).startTime(startTime).build();
    }

    public SimWorld world() {
        return world;
    }

    public long now() {
        return world.now();
    }

    /** Spawns the standard Phase 0 hamlet centred at the origin and returns the village. */
    public EntityId spawnHamlet(String name, long seed, int villagers) {
        var types = world.registry(BuildingType.REGISTRY);
        return spawn(hamletCommand(types, name, seed, villagers, 0, 64, 0, null));
    }

    public static SpawnVillageCommand hamletCommand(com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry<BuildingType> types,
                                                    String name, long seed, int villagers, int x, int y, int z,
                                                    java.util.function.Consumer<EntityId> onCreated) {
        return new SpawnVillageCommand(name, seed, x, y, z, VillageLayouts.hamlet(types, x, y, z, villagers), villagers, onCreated);
    }

    public EntityId spawn(SpawnVillageCommand command) {
        AtomicReference<EntityId> out = new AtomicReference<>();
        java.util.function.Consumer<EntityId> previous = command.onCreated();
        world.apply(new SpawnVillageCommand(command.name(), command.seed(), command.centerX(), command.centerY(),
                command.centerZ(), command.placements(), command.villagers(), id -> {
            out.set(id);
            if (previous != null) previous.accept(id);
        }));
        return out.get();
    }

    public Scenario apply(SimCommand command) {
        world.apply(command);
        return this;
    }

    public Scenario warp(long ticks) {
        world.advanceTo(world.now() + ticks);
        return this;
    }

    public Village village(EntityId village) {
        return world.get(village, Villages.VILLAGE);
    }

    /** Stock of {@code good} in the first building of type {@code typePath} in the village. */
    public int stock(EntityId village, String typePath, String good) {
        for (EntityId b : village(village).buildings()) {
            if (world.get(b, Buildings.BUILDING).type().equals(Id.of("villagersimulator", typePath))) {
                return Buildings.stock(world, b, good);
            }
        }
        throw new IllegalArgumentException("No " + typePath + " in village");
    }

    public int events(Id type) {
        return world.events().ofType(type).size();
    }

    public long fingerprint(EntityId village) {
        return VillageQueries.fingerprint(world, village);
    }
}
