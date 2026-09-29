package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.event.EventLog;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** In-memory event log. Records are persisted to the {@code event_log} SQLite table on save. */
final class SimEventLog implements EventLog {
    private final LongSupplier clock;
    private final List<EventRecord> records = new ArrayList<>();
    private final Map<Id, List<EventRecord>> byType = new HashMap<>();
    private final Map<Integer, List<EventRecord>> byActor = new HashMap<>();
    private long nextId = 1;

    SimEventLog(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public long record(Id type, EntityId actor, long cause, List<EntityId> witnesses, String detail) {
        EventRecord r = new EventRecord(nextId++, clock.getAsLong(), type, actor, cause, List.copyOf(witnesses), detail);
        add(r);
        return r.id();
    }

    void add(EventRecord r) {
        records.add(r);
        byType.computeIfAbsent(r.type(), k -> new ArrayList<>()).add(r);
        byActor.computeIfAbsent(r.actor().raw(), k -> new ArrayList<>()).add(r);
        nextId = Math.max(nextId, r.id() + 1);
    }

    @Override
    public List<EventRecord> ofType(Id type) {
        return Collections.unmodifiableList(byType.getOrDefault(type, List.of()));
    }

    @Override
    public List<EventRecord> byActor(EntityId actor) {
        return Collections.unmodifiableList(byActor.getOrDefault(actor.raw(), List.of()));
    }

    @Override
    public int size() {
        return records.size();
    }

    /** Records with id greater than {@code id}, oldest first. */
    List<EventRecord> after(long id) {
        int i = records.size();
        while (i > 0 && records.get(i - 1).id() > id) i--;
        return List.copyOf(records.subList(i, records.size()));
    }

    long nextId() {
        return nextId;
    }
}
