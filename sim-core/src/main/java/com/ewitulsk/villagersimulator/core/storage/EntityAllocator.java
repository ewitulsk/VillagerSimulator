package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.EntityId;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Hands out entity handles. Freed indices are reused first-in first-out with a bumped generation, so stale handles
 * are detectable and ID recycling stays cheap at high birth/death rates.
 */
public final class EntityAllocator {
    private byte[] generations = new byte[64];
    private boolean[] alive = new boolean[64];
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private int nextIndex = 1;
    private int count;

    public EntityId allocate() {
        int index;
        if (!free.isEmpty()) {
            index = free.pollFirst();
        } else {
            index = nextIndex++;
            if (index > EntityId.MAX_INDEX) throw new IllegalStateException("Out of entity indices");
            ensure(index);
        }
        alive[index] = true;
        count++;
        return EntityId.of(index, generations[index]);
    }

    public void free(EntityId id) {
        if (!isAlive(id)) return;
        int index = id.index();
        alive[index] = false;
        generations[index]++;
        count--;
        free.addLast(index);
    }

    public boolean isAlive(EntityId id) {
        int index = id.index();
        return index > 0 && index < nextIndex && alive[index] && (generations[index] & 0xFF) == id.generation();
    }

    /** The live handle at {@code index}, or {@link EntityId#NONE}. */
    public EntityId idOf(int index) {
        return aliveAt(index) ? EntityId.of(index, generations[index]) : EntityId.NONE;
    }

    public boolean aliveAt(int index) {
        return index > 0 && index < nextIndex && alive[index];
    }

    public int count() {
        return count;
    }

    /** One past the highest index ever used. */
    public int indexLimit() {
        return nextIndex;
    }

    public byte generation(int index) {
        return generations[index];
    }

    public int[] freeList() {
        return free.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Restores saved state. */
    public void restore(int nextIndex, byte[] generations, boolean[] alive, int[] freeList) {
        this.nextIndex = nextIndex;
        this.generations = Arrays.copyOf(generations, Math.max(64, nextIndex));
        this.alive = Arrays.copyOf(alive, Math.max(64, nextIndex));
        this.free.clear();
        for (int i : freeList) this.free.addLast(i);
        int c = 0;
        for (int i = 1; i < nextIndex; i++) if (this.alive[i]) c++;
        this.count = c;
    }

    private void ensure(int index) {
        if (index >= alive.length) {
            int size = Math.max(index + 1, alive.length * 2);
            alive = Arrays.copyOf(alive, size);
            generations = Arrays.copyOf(generations, size);
        }
    }
}
