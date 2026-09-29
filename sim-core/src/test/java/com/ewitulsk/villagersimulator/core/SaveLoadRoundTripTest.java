package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.core.persistence.SqliteSimStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveLoadRoundTripTest {
    @TempDir
    Path dir;

    private static SimWorld populated() {
        SimWorld w = SimWorld.builder().module(new TestModule()).build();
        for (int i = 0; i < 20; i++) {
            EntityId c = TestModule.counter(w, 50 + i * 7);
            w.set(c, TestModule.NOTE, "counter " + i);
        }
        // Free a few indices so the allocator's free list is exercised.
        w.destroy(w.with(TestModule.COUNTER).get(3));
        w.destroy(w.with(TestModule.COUNTER).get(7));
        w.advanceTo(1_234);
        w.events().record(Id.of("test", "saved"), EntityId.NONE, "before save");
        return w;
    }

    @Test
    void sqliteRoundTripRestoresIdenticalStateThatKeepsRunningIdentically() {
        SimWorld original = populated();
        long hashBefore = original.stateHash();

        try (SqliteSimStore store = SqliteSimStore.open(dir.resolve("sim.db"))) {
            store.save(original.snapshot(store.savedEvents()), SimWorld.SNAPSHOT_FORMAT);
        }

        SimWorld restored = SimWorld.builder().module(new TestModule()).build();
        try (SqliteSimStore store = SqliteSimStore.open(dir.resolve("sim.db"))) {
            var loaded = store.load().orElseThrow();
            restored.restore(loaded.data(), loaded.events());
            assertEquals(1, store.savedEvents());
        }

        assertEquals(hashBefore, restored.stateHash(), "restored state matches");
        assertEquals(original.events().ofType(Id.of("test", "saved")), restored.events().ofType(Id.of("test", "saved")));

        original.advanceTo(10_000);
        restored.advanceTo(10_000);
        assertEquals(original.stateHash(), restored.stateHash(), "both continue identically");
        assertTrue(original.pendingTasks() > 0);
    }

    @Test
    void secondSaveOnlyAppendsNewEvents() {
        SimWorld w = populated();
        try (SqliteSimStore store = SqliteSimStore.open(dir.resolve("sim.db"))) {
            store.save(w.snapshot(store.savedEvents()), SimWorld.SNAPSHOT_FORMAT);
            w.events().record(Id.of("test", "later"), EntityId.NONE, "after first save");
            var second = w.snapshot(store.savedEvents());
            assertEquals(1, second.newEvents().size());
            store.save(second, SimWorld.SNAPSHOT_FORMAT);
            assertEquals(2, store.load().orElseThrow().events().size());
        }
    }

    @Test
    void unknownComponentsSurviveARoundTripThroughAWorldWithoutThem() {
        SimWorld original = populated();
        byte[] saved = original.snapshot(0).data();

        // A world missing the module (e.g. an addon was removed) keeps the unknown sections...
        SimWorld without = SimWorld.builder().build();
        without.restore(saved, java.util.List.of());
        byte[] resaved = without.snapshot(0).data();

        // ...and writes them back, so re-adding the module restores the data.
        SimWorld back = SimWorld.builder().module(new TestModule()).build();
        back.restore(resaved, java.util.List.of());
        EntityId first = back.with(TestModule.COUNTER).get(0);
        assertEquals("counter 0", back.get(first, TestModule.NOTE));
    }
}
