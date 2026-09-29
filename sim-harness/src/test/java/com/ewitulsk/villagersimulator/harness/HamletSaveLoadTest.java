package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.ContentModules;
import com.ewitulsk.villagersimulator.core.SimWorld;
import com.ewitulsk.villagersimulator.core.data.ClasspathDataSource;
import com.ewitulsk.villagersimulator.core.persistence.SqliteSimStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A hamlet saved mid-day and restored continues exactly like one that was never saved. */
class HamletSaveLoadTest {
    @TempDir
    Path dir;

    @Test
    void savedHamletContinuesIdentically() {
        Scenario original = Scenario.start();
        EntityId village = original.spawnHamlet("Testford", 42, 8);
        original.warp(SimTime.days(2) + SimTime.hours(7));

        try (SqliteSimStore store = SqliteSimStore.open(dir.resolve("sim.db"))) {
            store.save(original.world().snapshot(store.lastSavedEventId()), SimWorld.SNAPSHOT_FORMAT);
        }
        SimWorld restored = Scenario.world(ContentModules.all(), ClasspathDataSource.of(Scenario.class), 0);
        try (SqliteSimStore store = SqliteSimStore.open(dir.resolve("sim.db"))) {
            var loaded = store.load().orElseThrow();
            restored.restore(loaded.data(), loaded.events());
        }
        assertEquals(original.world().stateHash(), restored.stateHash());

        original.warp(SimTime.days(2));
        restored.advanceTo(original.now());
        assertEquals(original.world().stateHash(), restored.stateHash());
    }
}
