package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

/** A small module for engine tests: a counter that ticks itself forward, and a note. */
final class TestModule implements SimModule {
    static final DenseComponent COUNTER = new DenseComponent(Id.of("test", "counter"), 1);
    static final IntField COUNT = COUNTER.intField("count");
    static final FloatField LEVEL = COUNTER.floatField("level");
    static final LongField LAST = COUNTER.longField("last");
    static final SparseComponent<String> NOTE = new SparseComponent<>(Id.of("test", "note"), 1, Codec.STRING);

    /** Every run is appended here as "target:arg@time" so ordering can be checked. */
    static final List<String> RUNS = new ArrayList<>();

    static final TaskType TICK = new TaskType(Id.of("test", "tick"), 10, (ctx, e, arg) -> {
        if (!ctx.alive(e)) return;
        ctx.set(e, COUNT, ctx.get(e, COUNT) + 1);
        ctx.set(e, LEVEL, ctx.get(e, LEVEL) + 0.5f);
        ctx.set(e, LAST, ctx.now());
        ctx.schedule(ctx.now() + arg, TestModule.TICK, e, arg);
    });

    static final TaskType RECORD = new TaskType(Id.of("test", "record"), 5, (ctx, e, arg) ->
            RUNS.add(e.index() + ":" + arg + "@" + ctx.now()));
    static final TaskType RECORD_LATE = new TaskType(Id.of("test", "record_late"), 50, (ctx, e, arg) ->
            RUNS.add(e.index() + ":" + arg + "@" + ctx.now()));

    @Override
    public Id id() {
        return Id.of("test", "module");
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(COUNTER);
        r.component(NOTE);
        r.task(TICK);
        r.task(RECORD);
        r.task(RECORD_LATE);
    }

    static EntityId counter(SimWorld w, long period) {
        EntityId e = w.create();
        w.add(e, COUNTER);
        w.schedule(w.now() + period, TICK, e, period);
        return e;
    }
}
