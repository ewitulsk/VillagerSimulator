package com.ewitulsk.villagersimulator.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchedulerOrderingTest {
    private SimWorld world;

    @BeforeEach
    void setUp() {
        TestModule.RUNS.clear();
        world = SimWorld.builder().module(new TestModule()).build();
    }

    @Test
    void runsByTimeThenPriorityThenSchedulingOrder() {
        var a = world.create();
        world.schedule(20, TestModule.RECORD, a, 1);
        world.schedule(10, TestModule.RECORD_LATE, a, 2); // same time as the next two, lower priority
        world.schedule(10, TestModule.RECORD, a, 3);
        world.schedule(10, TestModule.RECORD, a, 4);
        world.schedule(5, TestModule.RECORD_LATE, a, 5);

        world.advanceTo(100);

        assertEquals(List.of("1:5@5", "1:3@10", "1:4@10", "1:2@10", "1:1@20"), TestModule.RUNS);
        assertEquals(100, world.now());
    }

    @Test
    void advanceStopsAtTargetAndKeepsLaterTasks() {
        var a = world.create();
        world.schedule(50, TestModule.RECORD, a, 1);
        world.schedule(150, TestModule.RECORD, a, 2);

        world.advanceTo(100);
        assertEquals(List.of("1:1@50"), TestModule.RUNS);
        assertEquals(1, world.pendingTasks());

        world.advanceTo(200);
        assertEquals(List.of("1:1@50", "1:2@150"), TestModule.RUNS);
    }

    @Test
    void cannotScheduleInThePast() {
        world.advanceTo(100);
        var a = world.create();
        assertThrows(IllegalArgumentException.class, () -> world.schedule(99, TestModule.RECORD, a, 0));
    }

    @Test
    void selfReschedulingTaskRunsOncePerPeriod() {
        var c = TestModule.counter(world, 100);
        world.advanceTo(1_000);
        assertEquals(10, world.get(c, TestModule.COUNT));
        assertEquals(1_000, world.get(c, TestModule.LAST));
    }
}
