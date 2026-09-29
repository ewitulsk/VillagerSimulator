package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.event.EventLog;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The event log. Record ids are {@code (per-shard counter << 20) | shard}, so shards running in parallel create
 * unique ids without coordinating. Each shard buffers its records during a window; buffers are merged into the log
 * at the boundary in (time, shard, order) order. The log's order is the merge order; persistence saves by position.
 */
final class SimEventLog {
    static final int SHARD_BITS = 20;

    private final List<EventRecord> records = new ArrayList<>();
    private final Map<Id, List<EventRecord>> byType = new HashMap<>();
    private final Map<Integer, List<EventRecord>> byActor = new HashMap<>();
    private long[] counters = new long[16];

    /** A shard's writer: buffers records until the boundary merges them. */
    final class Writer implements EventLog {
        private final int shard;
        private final LongSupplier clock;
        final List<EventRecord> buffer = new ArrayList<>();
        private final boolean direct;

        Writer(int shard, LongSupplier clock, boolean direct) {
            this.shard = shard;
            this.clock = clock;
            this.direct = direct;
        }

        @Override
        public long record(Id type, EntityId actor, long cause, List<EntityId> witnesses, String detail) {
            long id = nextId(shard);
            EventRecord r = new EventRecord(id, clock.getAsLong(), type, actor, cause, List.copyOf(witnesses), detail);
            if (direct) add(r);
            else buffer.add(r);
            return id;
        }

        @Override
        public List<EventRecord> ofType(Id type) {
            return SimEventLog.this.ofType(type);
        }

        @Override
        public List<EventRecord> byActor(EntityId actor) {
            return SimEventLog.this.byActor(actor);
        }

        @Override
        public int size() {
            return records.size();
        }
    }

    private long nextId(int shard) {
        if (shard >= counters.length) counters = java.util.Arrays.copyOf(counters, Math.max(shard + 1, counters.length * 2));
        return (++counters[shard] << SHARD_BITS) | shard;
    }

    /** Reserves counter storage for a new shard (boundary). */
    void reserveShard(int shard) {
        if (shard >= counters.length) counters = java.util.Arrays.copyOf(counters, Math.max(shard + 1, counters.length * 2));
    }

    /** Merges shards' buffered records into the log in a deterministic order. */
    void merge(List<Writer> writers) {
        List<EventRecord> all = new ArrayList<>();
        for (Writer w : writers) {
            all.addAll(w.buffer);
            w.buffer.clear();
        }
        if (all.isEmpty()) return;
        // Stable sort: records of one shard keep their order; shards interleave by time, then shard.
        all.sort(Comparator.comparingLong(EventRecord::time).thenComparingLong(r -> r.id() & ((1L << SHARD_BITS) - 1)));
        for (EventRecord r : all) add(r);
    }

    void add(EventRecord r) {
        records.add(r);
        byType.computeIfAbsent(r.type(), k -> new ArrayList<>()).add(r);
        byActor.computeIfAbsent(r.actor().raw(), k -> new ArrayList<>()).add(r);
        int shard = (int) (r.id() & ((1L << SHARD_BITS) - 1));
        reserveShard(shard);
        counters[shard] = Math.max(counters[shard], r.id() >>> SHARD_BITS);
    }

    List<EventRecord> ofType(Id type) {
        return Collections.unmodifiableList(byType.getOrDefault(type, List.of()));
    }

    List<EventRecord> byActor(EntityId actor) {
        return Collections.unmodifiableList(byActor.getOrDefault(actor.raw(), List.of()));
    }

    int size() {
        return records.size();
    }

    /** Records from position {@code from} on, in log order. */
    List<EventRecord> from(int from) {
        return List.copyOf(records.subList(Math.min(from, records.size()), records.size()));
    }
}
