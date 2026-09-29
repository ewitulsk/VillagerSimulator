package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentStorageTest {
    private final SimWorld world = SimWorld.builder().module(new TestModule()).build();

    @Test
    void denseFieldsAreIndependentPerEntityAndField() {
        EntityId a = world.create();
        EntityId b = world.create();
        world.add(a, TestModule.COUNTER);
        world.add(b, TestModule.COUNTER);
        world.set(a, TestModule.COUNT, 7);
        world.set(a, TestModule.LEVEL, 1.5f);
        world.set(b, TestModule.LAST, 123L);

        assertEquals(7, world.get(a, TestModule.COUNT));
        assertEquals(1.5f, world.get(a, TestModule.LEVEL));
        assertEquals(0L, world.get(a, TestModule.LAST));
        assertEquals(0, world.get(b, TestModule.COUNT));
        assertEquals(123L, world.get(b, TestModule.LAST));
    }

    @Test
    void storageGrowsPastInitialCapacity() {
        List<EntityId> all = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            EntityId e = world.create();
            world.add(e, TestModule.COUNTER);
            world.set(e, TestModule.COUNT, i);
            all.add(e);
        }
        for (int i = 0; i < all.size(); i++) assertEquals(i, world.get(all.get(i), TestModule.COUNT));
        assertEquals(all, world.with(TestModule.COUNTER));
    }

    @Test
    void sparseValuesAndMissingComponents() {
        EntityId a = world.create();
        assertNull(world.get(a, TestModule.NOTE));
        world.set(a, TestModule.NOTE, "hello");
        assertEquals("hello", world.get(a, TestModule.NOTE));
        assertThrows(IllegalArgumentException.class, () -> world.get(a, TestModule.COUNT));
    }

    @Test
    void destroyedHandlesGoStaleAndIndicesAreReused() {
        EntityId a = world.create();
        world.add(a, TestModule.COUNTER);
        world.set(a, TestModule.NOTE, "old");
        world.destroy(a);

        assertFalse(world.alive(a));
        EntityId b = world.create();
        assertEquals(a.index(), b.index());
        assertNotEquals(a, b);
        assertTrue(world.alive(b));
        assertFalse(world.has(b, TestModule.COUNTER), "reused index starts without components");
        assertNull(world.get(b, TestModule.NOTE));
        assertNull(world.get(a, TestModule.NOTE), "stale handle reads nothing");
    }
}
