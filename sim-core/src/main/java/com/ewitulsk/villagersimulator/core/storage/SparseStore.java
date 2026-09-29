package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One object per entity. Thread-safe, so shards can write their own entities in parallel; iteration is always in
 * entity-index order, so it stays deterministic.
 */
public final class SparseStore<T> {
    private final SparseComponent<T> component;
    private final ConcurrentHashMap<Integer, T> values = new ConcurrentHashMap<>();

    public SparseStore(SparseComponent<T> component) {
        this.component = component;
    }

    public SparseComponent<T> component() {
        return component;
    }

    public T get(int index) {
        return values.get(index);
    }

    public void set(int index, T value) {
        if (value == null) values.remove(index);
        else values.put(index, value);
    }

    public boolean has(int index) {
        return values.containsKey(index);
    }

    public void remove(int index) {
        values.remove(index);
    }

    public int size() {
        return values.size();
    }

    /** Entries sorted by entity index (a copy). */
    public List<Map.Entry<Integer, T>> sorted() {
        List<Map.Entry<Integer, T>> out = new ArrayList<>(values.entrySet());
        out.sort(Map.Entry.comparingByKey());
        return out;
    }

    @SuppressWarnings("unchecked")
    public void setUnchecked(int index, Object value) {
        set(index, (T) value);
    }
}
