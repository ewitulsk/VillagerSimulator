package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Structure-of-arrays storage for one dense component, indexed by entity index. Phase 0 has a single shard, so rows
 * are simply entity indices; sharding (Phase 5) changes this class, not the API.
 */
public final class DenseStore {
    private final DenseComponent component;
    private final BitSet present = new BitSet();
    private final float[][] floats;
    private final long[][] longs;
    private final int[][] ints;
    private int capacity = 64;

    public DenseStore(DenseComponent component) {
        this.component = component;
        this.floats = new float[component.floatNames().size()][capacity];
        this.longs = new long[component.longNames().size()][capacity];
        this.ints = new int[component.intNames().size()][capacity];
    }

    public DenseComponent component() {
        return component;
    }

    public boolean has(int index) {
        return present.get(index);
    }

    public void add(int index) {
        if (present.get(index)) return;
        ensure(index);
        present.set(index);
        for (float[] c : floats) c[index] = 0;
        for (long[] c : longs) c[index] = 0;
        for (int[] c : ints) c[index] = 0;
    }

    public void remove(int index) {
        present.clear(index);
    }

    public BitSet present() {
        return present;
    }

    public float getFloat(int slot, int index) {
        return floats[slot][index];
    }

    public void setFloat(int slot, int index, float v) {
        floats[slot][index] = v;
    }

    public long getLong(int slot, int index) {
        return longs[slot][index];
    }

    public void setLong(int slot, int index, long v) {
        longs[slot][index] = v;
    }

    public int getInt(int slot, int index) {
        return ints[slot][index];
    }

    public void setInt(int slot, int index, int v) {
        ints[slot][index] = v;
    }

    private void ensure(int index) {
        if (index < capacity) return;
        int size = Math.max(index + 1, capacity * 2);
        for (int i = 0; i < floats.length; i++) floats[i] = Arrays.copyOf(floats[i], size);
        for (int i = 0; i < longs.length; i++) longs[i] = Arrays.copyOf(longs[i], size);
        for (int i = 0; i < ints.length; i++) ints[i] = Arrays.copyOf(ints[i], size);
        capacity = size;
    }
}
