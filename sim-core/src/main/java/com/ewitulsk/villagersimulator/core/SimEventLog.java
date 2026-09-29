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
 *
 * <p>Only recent records stay in memory: once saved, records older than the retention are forgotten
 * ({@link #forget}). Positions stay absolute, so {@link #size()} counts forgotten records too. Record times never
 * decrease along the log, so forgotten records are always a prefix.
 */
final class SimEventLog {
    static final int SHARD_BITS = 20;

    private final List<EventRecord> records = new ArrayList<>();
    private final Map<Id, List<EventRecord>> byType = new HashMap<>();
    private final Map<Integer, List<EventRecord>> byActor = new HashMap<>();
    private long[] counters = new long[16];
    /** Records forgotten from the front of the log. */
    private int offset;

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
            return SimEventLog.this.size();
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

    /** Every record ever logged, including forgotten ones. */
    int size() {
        return offset + records.size();
    }

    /** Records in memory. */
    int retained() {
        return records.size();
    }

    /** Records from absolute position {@code from} on, in log order (forgotten ones are skipped). */
    List<EventRecord> from(int from) {
        int start = Math.max(0, Math.min(from - offset, records.size()));
        return List.copyOf(records.subList(start, records.size()));
    }

    /** Starts the log after {@code forgotten} records that are saved but not loaded (loading a save). */
    void startAfter(int forgotten) {
        if (!records.isEmpty() || offset != 0) throw new IllegalStateException("Log already has records");
        offset = forgotten;
    }

    /**
     * Forgets records at absolute positions below {@code saved} (they're persisted) that are older than
     * {@code before}. Returns how many were forgotten.
     */
    int forget(int saved, long before) {
        int n = 0;
        while (n < records.size() && offset + n < saved && records.get(n).time() < before) n++;
        if (n == 0) return 0;
        Map<Id, Integer> types = new HashMap<>();
        Map<Integer, Integer> actors = new HashMap<>();
        for (int i = 0; i < n; i++) {
            EventRecord r = records.get(i);
            types.merge(r.type(), 1, Integer::sum);
            actors.merge(r.actor().raw(), 1, Integer::sum);
        }
        // Forgotten records are the oldest, so they're at the front of every index list too.
        types.forEach((type, count) -> {
            List<EventRecord> list = byType.get(type);
            list.subList(0, count).clear();
            if (list.isEmpty()) byType.remove(type);
        });
        actors.forEach((actor, count) -> {
            List<EventRecord> list = byActor.get(actor);
            list.subList(0, count).clear();
            if (list.isEmpty()) byActor.remove(actor);
        });
        records.subList(0, n).clear();
        offset += n;
        return n;
    }
}
