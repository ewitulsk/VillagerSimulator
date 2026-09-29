package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;

/** One object per entity, kept sorted by entity index so iteration order is deterministic. */
public final class SparseStore<T> {
    private final SparseComponent<T> component;
    private final Int2ObjectSortedMap<T> values = new Int2ObjectAVLTreeMap<>();

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

    public Int2ObjectSortedMap<T> values() {
        return values;
    }

    @SuppressWarnings("unchecked")
    public void setUnchecked(int index, Object value) {
        set(index, (T) value);
    }
}
