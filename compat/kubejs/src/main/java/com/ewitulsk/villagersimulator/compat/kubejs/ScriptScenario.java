package com.ewitulsk.villagersimulator.compat.kubejs;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.scenario.SimScenario;

import java.util.List;

/**
 * What scripts see of a scenario: exactly the {@link SimScenario} DSL, so Rhino never has to pick between an
 * implementation's extra overloads (e.g. an {@code Id} and a {@code String} version of {@code events}).
 */
public final class ScriptScenario implements SimScenario {
    private final SimScenario s;

    ScriptScenario(SimScenario s) {
        this.s = s;
    }

    @Override
    public SimContext sim() {
        return s.sim();
    }

    @Override
    public long now() {
        return s.now();
    }

    @Override
    public EntityId spawnHamlet(String name, long seed, int villagers) {
        return s.spawnHamlet(name, seed, villagers);
    }

    @Override
    public EntityId spawnTown(String name, long seed, int villagers, int x, int z) {
        return s.spawnTown(name, seed, villagers, x, z);
    }

    @Override
    public SimScenario warp(long ticks) {
        s.warp(ticks);
        return this;
    }

    @Override
    public SimScenario warp(String duration) {
        s.warp(duration);
        return this;
    }

    @Override
    public List<EntityId> residents(EntityId village) {
        return s.residents(village);
    }

    @Override
    public int events(String type) {
        return s.events(type);
    }

    @Override
    public SimScenario expect(String description, boolean passed) {
        s.expect(description, passed);
        return this;
    }

    @Override
    public List<Check> checks() {
        return s.checks();
    }
}
