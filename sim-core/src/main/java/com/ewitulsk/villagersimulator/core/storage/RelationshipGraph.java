package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.api.sim.social.Relation;
import com.ewitulsk.villagersimulator.api.sim.social.Relationships;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Packed relationship storage (docs/ARCHITECTURE.md §5.4). Per entity index, a {@code long[]} of edges
 * ({@code target:32 | friendship*100:16 | bonds:16}) and a parallel {@code int[]} of the sim day each edge last
 * changed. Friendship fades toward 0 by {@link #DAILY_FADE} per day since it last changed, unless a
 * {@link Bonds#LASTING} bond is set. Edges are symmetric. At {@link #CAPACITY} the weakest unbonded tie is evicted.
 *
 * <p>Access goes through a {@link View}: it carries the caller's clock and, for a shard running in parallel, which
 * entities it owns. Writes to an edge list the caller doesn't own are deferred to the boundary.
 */
public final class RelationshipGraph {
    public static final int CAPACITY = 48;
    public static final double DAILY_FADE = 0.97;
    private static final int SCALE = 100;

    private final Predicate<EntityId> alive;
    private long[][] edges = new long[64][];
    private int[][] touched = new int[64][];
    private int[] counts = new int[64];

    public RelationshipGraph(Predicate<EntityId> alive) {
        this.alive = alive;
    }

    /** A view for callers that own every entity (boundary code, single-threaded use). */
    public View view(LongSupplier clock) {
        return new View(clock, i -> true, Runnable::run);
    }

    /** A view for a shard: {@code local} tells which entity indices it owns; other writes go to {@code defer}. */
    public View view(LongSupplier clock, IntPredicate local, Consumer<Runnable> defer) {
        return new View(clock, local, defer);
    }

    private static long pack(int target, float friendship, int bonds) {
        int f = Math.round(Math.max(-100f, Math.min(100f, friendship)) * SCALE);
        return ((long) target << 32) | ((long) (f & 0xFFFF) << 16) | (bonds & 0xFFFF);
    }

    private static int target(long e) {
        return (int) (e >>> 32);
    }

    private static float stored(long e) {
        return (short) ((e >>> 16) & 0xFFFF) / (float) SCALE;
    }

    private static int bonds(long e) {
        return (int) (e & 0xFFFF);
    }

    /** Grows storage to hold {@code index}; called when an entity is created (at a boundary). */
    public synchronized void reserve(int index) {
        if (index < counts.length) return;
        int size = Math.max(index + 1, counts.length * 2);
        edges = Arrays.copyOf(edges, size);
        touched = Arrays.copyOf(touched, size);
        counts = Arrays.copyOf(counts, size);
    }

    private int find(int index, int target) {
        if (index >= counts.length) return -1;
        long[] list = edges[index];
        for (int i = 0; i < counts[index]; i++) if (target(list[i]) == target) return i;
        return -1;
    }

    private void removeAt(int index, int i) {
        int last = --counts[index];
        edges[index][i] = edges[index][last];
        touched[index][i] = touched[index][last];
    }

    private void removeDirected(int index, int target) {
        int i = find(index, target);
        if (i >= 0) removeAt(index, i);
    }

    /** Removes every edge to and from a destroyed entity (boundary only). */
    public void forget(EntityId a) {
        int index = a.index();
        if (index >= counts.length) return;
        for (int i = 0; i < counts[index]; i++) removeDirected(new EntityId(target(edges[index][i])).index(), a.raw());
        counts[index] = 0;
    }

    /** The graph as seen by one caller. */
    public final class View implements Relationships {
        private final LongSupplier clock;
        private final IntPredicate local;
        private final Consumer<Runnable> defer;

        private View(LongSupplier clock, IntPredicate local, Consumer<Runnable> defer) {
            this.clock = clock;
            this.local = local;
            this.defer = defer;
        }

        private int today() {
            return (int) SimTime.day(clock.getAsLong());
        }

        private float current(long e, int touchedDay) {
            if ((bonds(e) & Bonds.LASTING) != 0) return stored(e);
            int days = today() - touchedDay;
            return days <= 0 ? stored(e) : (float) (stored(e) * Math.pow(DAILY_FADE, days));
        }

        private int bondsOf(EntityId a, EntityId b) {
            int i = find(a.index(), b.raw());
            return i < 0 ? 0 : bonds(edges[a.index()][i]);
        }

        /** Sets one direction's friendship and bonds (adding the edge if needed), or defers it if not owned. */
        private void put(EntityId from, EntityId to, float friendship, int bondBits) {
            if (!local.test(from.index())) {
                int day = today();
                defer.accept(() -> putNow(from, to, friendship, bondBits, day));
                return;
            }
            putNow(from, to, friendship, bondBits, today());
        }

        private void putNow(EntityId from, EntityId to, float friendship, int bondBits, int day) {
            int index = from.index(); // storage was reserved when the entity was created
            int i = find(index, to.raw());
            if (i < 0) {
                if (edges[index] == null) {
                    edges[index] = new long[8];
                    touched[index] = new int[8];
                }
                if (counts[index] >= CAPACITY) {
                    int weakest = weakestUnbonded(index);
                    if (weakest < 0) return; // everyone known is bonded; no room
                    int evicted = new EntityId(target(edges[index][weakest])).index();
                    removeAt(index, weakest);
                    int fromRaw = from.raw();
                    if (local.test(evicted)) removeDirected(evicted, fromRaw);
                    else defer.accept(() -> removeDirected(evicted, fromRaw));
                }
                if (counts[index] == edges[index].length) {
                    edges[index] = Arrays.copyOf(edges[index], counts[index] * 2);
                    touched[index] = Arrays.copyOf(touched[index], counts[index] * 2);
                }
                i = counts[index]++;
            }
            edges[index][i] = pack(to.raw(), friendship, bondBits);
            touched[index][i] = day;
        }

        private int weakestUnbonded(int index) {
            int best = -1;
            float bestStrength = Float.MAX_VALUE;
            for (int i = 0; i < counts[index]; i++) {
                long e = edges[index][i];
                if (bonds(e) != 0) continue;
                float s = Math.abs(current(e, touched[index][i]));
                if (s < bestStrength) {
                    bestStrength = s;
                    best = i;
                }
            }
            return best;
        }

        @Override
        public float friendship(EntityId a, EntityId b) {
            int i = find(a.index(), b.raw());
            return i < 0 ? 0 : current(edges[a.index()][i], touched[a.index()][i]);
        }

        @Override
        public void changeFriendship(EntityId a, EntityId b, float delta) {
            if (a.equals(b)) return;
            // Read the side this caller owns; both sides always hold the same value.
            float next = (local.test(a.index()) ? friendship(a, b) : friendship(b, a)) + delta;
            put(a, b, next, bondsOf(a, b));
            put(b, a, next, bondsOf(b, a));
        }

        @Override
        public boolean hasBond(EntityId a, EntityId b, int bond) {
            return (bondsOf(a, b) & bond) != 0;
        }

        @Override
        public void setBond(EntityId a, EntityId b, int bond, boolean on) {
            if (a.equals(b)) return;
            float f = local.test(a.index()) ? friendship(a, b) : friendship(b, a);
            int ab = on ? bondsOf(a, b) | bond : bondsOf(a, b) & ~bond;
            int ba = on ? bondsOf(b, a) | bond : bondsOf(b, a) & ~bond;
            put(a, b, f, ab);
            put(b, a, f, ba);
        }

        @Override
        public List<Relation> of(EntityId a) {
            int index = a.index();
            if (index >= counts.length) return List.of();
            List<Relation> out = new ArrayList<>(counts[index]);
            for (int i = 0; i < counts[index]; i++) {
                long e = edges[index][i];
                EntityId other = new EntityId(target(e));
                if (alive.test(other)) out.add(new Relation(other, current(e, touched[index][i]), bonds(e)));
            }
            out.sort(Comparator.comparingDouble((Relation r) -> -Math.abs(r.friendship())).thenComparingInt(r -> r.other().raw()));
            return out;
        }

        @Override
        public int count(EntityId a) {
            return a.index() < counts.length ? counts[a.index()] : 0;
        }
    }

    // --- persistence & hashing ---

    public void write(DataOutputStream out) throws IOException {
        int n = 0;
        for (int c : counts) if (c > 0) n++;
        out.writeInt(n);
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] == 0) continue;
            out.writeInt(index);
            out.writeInt(counts[index]);
            for (int i = 0; i < counts[index]; i++) {
                out.writeLong(edges[index][i]);
                out.writeInt(touched[index][i]);
            }
        }
    }

    public void read(DataInputStream in) throws IOException {
        int n = in.readInt();
        for (int k = 0; k < n; k++) {
            int index = in.readInt();
            int count = in.readInt();
            reserve(index);
            edges[index] = new long[Math.max(8, count)];
            touched[index] = new int[Math.max(8, count)];
            for (int i = 0; i < count; i++) {
                edges[index][i] = in.readLong();
                touched[index][i] = in.readInt();
            }
            counts[index] = count;
        }
    }

    /** Feeds every edge, in a stable order, to {@code sink}. */
    public void hash(LongConsumer sink) {
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] == 0) continue;
            Integer[] order = new Integer[counts[index]];
            for (int i = 0; i < order.length; i++) order[i] = i;
            long[] list = edges[index];
            Arrays.sort(order, Comparator.comparingLong(i -> list[i]));
            sink.accept(index);
            for (int i : order) {
                sink.accept(list[i]);
                sink.accept(touched[index][i]);
            }
        }
    }
}
