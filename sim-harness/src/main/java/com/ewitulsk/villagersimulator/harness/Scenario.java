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
public final class Scenario implements com.ewitulsk.villagersimulator.api.sim.scenario.SimScenario {
    private final SimWorld world;
    private final List<Check> checks = new java.util.ArrayList<>();

    private Scenario(SimWorld world) {
        this.world = world;
    }

    /** A world with the base game modules and data from the classpath, starting at tick 0 (day 0, 06:00). */
    public static Scenario start() {
        return start(0);
    }

    public static Scenario start(long startTime) {
        return start(startTime, 1);
    }

    /** A world running shards on {@code threads} workers (results are the same for any count). */
    public static Scenario start(long startTime, int threads) {
        return new Scenario(SimWorld.builder().modules(ContentModules.all()).data(ClasspathDataSource.of(Scenario.class))
                .startTime(startTime).threads(threads).build());
    }

    /** A scenario over an existing fresh world, e.g. one built from the running server's modules and data. */
    public static Scenario of(SimWorld world) {
        return new Scenario(world);
    }

    public static SimWorld world(List<SimModule> modules, DataSource data, long startTime) {
        return SimWorld.builder().modules(modules).data(data).startTime(startTime).build();
    }

    /** Spawns the two-district town layout centred at {@code (x, 64, z)} and returns the village. */
    @Override
    public EntityId spawnTown(String name, long seed, int villagers, int x, int z) {
        var layout = VillageLayouts.town(world.registry(BuildingType.REGISTRY), x, 64, z, villagers);
        return spawn(new SpawnVillageCommand(name, seed, x, 64, z, layout.placements(), layout.districts(), villagers, null));
    }

    public SimWorld world() {
        return world;
    }

    @Override
    public SimWorld sim() {
        return world;
    }

    @Override
    public Scenario warp(String duration) {
        return warp(com.ewitulsk.villagersimulator.api.sim.SimTime.parseDuration(duration));
    }

    @Override
    public List<EntityId> residents(EntityId village) {
        return village(village).residents();
    }

    @Override
    public int events(String type) {
        return events(Id.parse(type));
    }

    @Override
    public Scenario expect(String description, boolean passed) {
        checks.add(new Check(description, passed));
        return this;
    }

    @Override
    public List<Check> checks() {
        return List.copyOf(checks);
    }

    @Override
    public long now() {
        return world.now();
    }

    /** Spawns the standard Phase 0 hamlet centred at the origin and returns the village. */
    @Override
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
        world.apply(command.withCallback(id -> {
            out.set(id);
            if (previous != null) previous.accept(id);
        }));
        return out.get();
    }

    public Scenario apply(SimCommand command) {
        world.apply(command);
        return this;
    }

    @Override
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
