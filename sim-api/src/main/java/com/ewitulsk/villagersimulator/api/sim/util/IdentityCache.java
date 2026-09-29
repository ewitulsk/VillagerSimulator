package com.ewitulsk.villagersimulator.api.sim.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A small thread-safe cache keyed by object identity, for hot paths that look up the same data objects (parsed
 * definitions, their expression strings) over and over. Reads never lock: the map is copied on write. Meant for a
 * bounded set of keys; past {@code limit} entries it starts over, so keys created at runtime can't grow it forever.
 */
@ApiStatus.Internal
public final class IdentityCache<K, V> {
    private final int limit;
    private volatile IdentityHashMap<K, V> map = new IdentityHashMap<>();

    public IdentityCache(int limit) {
        this.limit = limit;
    }

    public V get(K key, Function<? super K, ? extends V> compute) {
        V v = map.get(key);
        if (v != null) return v;
        v = compute.apply(key);
        synchronized (this) {
            IdentityHashMap<K, V> next = map.size() >= limit ? new IdentityHashMap<>() : new IdentityHashMap<>(map);
            next.put(key, v);
            map = next;
        }
        return v;
    }

    public void clear() {
        synchronized (this) {
            map = new IdentityHashMap<>();
        }
    }

    public int size() {
        return map.size();
    }

    /** A read-only view of the current entries. */
    public Map<K, V> snapshot() {
        return java.util.Collections.unmodifiableMap(map);
    }
}
