package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.core.storage.RelationshipGraph;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipGraphTest {
    private final SimWorld world = SimWorld.builder().build();

    @Test
    void friendshipIsSymmetricAndClamped() {
        EntityId a = world.create(), b = world.create();
        world.relationships().changeFriendship(a, b, 30);
        assertEquals(30, world.relationships().friendship(a, b), 0.01);
        assertEquals(30, world.relationships().friendship(b, a), 0.01);
        world.relationships().changeFriendship(b, a, 500);
        assertEquals(100, world.relationships().friendship(a, b), 0.01);
        world.relationships().changeFriendship(a, b, -250);
        assertEquals(-100, world.relationships().friendship(a, b), 0.01);
    }

    @Test
    void bondsAreSymmetricAndIndependentOfFriendship() {
        EntityId a = world.create(), b = world.create();
        world.relationships().setBond(a, b, Bonds.COLLEAGUE, true);
        world.relationships().changeFriendship(a, b, 12);
        assertTrue(world.relationships().hasBond(b, a, Bonds.COLLEAGUE));
        assertFalse(world.relationships().hasBond(a, b, Bonds.FRIEND));
        world.relationships().setBond(b, a, Bonds.COLLEAGUE, false);
        assertFalse(world.relationships().hasBond(a, b, Bonds.COLLEAGUE));
        assertEquals(12, world.relationships().friendship(a, b), 0.01);
    }

    @Test
    void weakTiesFadeButLastingBondsDoNot() {
        EntityId a = world.create(), b = world.create(), c = world.create();
        world.relationships().changeFriendship(a, b, 50);
        world.relationships().changeFriendship(a, c, 50);
        world.relationships().setBond(a, c, Bonds.FAMILY, true);
        world.advanceTo(SimTime.days(10));
        assertEquals(50 * Math.pow(RelationshipGraph.DAILY_FADE, 10), world.relationships().friendship(a, b), 0.05);
        assertEquals(50, world.relationships().friendship(a, c), 0.01);
    }

    @Test
    void atCapacityTheWeakestUnbondedTieIsEvicted() {
        EntityId a = world.create();
        List<EntityId> others = new ArrayList<>();
        for (int i = 0; i < RelationshipGraph.CAPACITY; i++) {
            EntityId o = world.create();
            others.add(o);
            world.relationships().changeFriendship(a, o, 10 + i);
        }
        EntityId newcomer = world.create();
        world.relationships().changeFriendship(a, newcomer, 90);
        assertEquals(RelationshipGraph.CAPACITY, world.relationships().count(a));
        assertEquals(0, world.relationships().friendship(a, others.get(0)), "weakest (10) evicted");
        assertEquals(0, world.relationships().friendship(others.get(0), a), "both directions");
        assertEquals(90, world.relationships().friendship(a, newcomer), 0.01);
        assertEquals(newcomer, world.relationships().of(a).get(0).other(), "strongest first");
    }

    @Test
    void destroyingAnEntityForgetsItsTies() {
        EntityId a = world.create(), b = world.create();
        world.relationships().changeFriendship(a, b, 40);
        world.destroy(b);
        assertEquals(0, world.relationships().count(a));
        assertTrue(world.relationships().of(a).isEmpty());
    }

    @Test
    void graphSurvivesASaveAndLoad() {
        EntityId a = world.create(), b = world.create();
        world.relationships().changeFriendship(a, b, -35);
        world.relationships().setBond(a, b, Bonds.RIVAL, true);
        long hash = world.stateHash();
        SimWorld restored = SimWorld.builder().build();
        restored.restore(world.snapshot(0).data(), List.of());
        assertEquals(hash, restored.stateHash());
        assertEquals(-35, restored.relationships().friendship(a, b), 0.01);
        assertTrue(restored.relationships().hasBond(b, a, Bonds.RIVAL));
    }
}
