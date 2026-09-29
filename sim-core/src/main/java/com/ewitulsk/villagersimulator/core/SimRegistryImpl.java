package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** A frozen registry parsed from data definitions. */
final class SimRegistryImpl<T> implements SimRegistry<T> {
    private final RegistryKey<T> key;
    private final Map<Id, T> entries;

    private SimRegistryImpl(RegistryKey<T> key, Map<Id, T> entries) {
        this.key = key;
        this.entries = Collections.unmodifiableMap(entries);
    }

    static <T> SimRegistryImpl<T> parse(RegistryKey<T> key, Map<Id, JsonElement> json) {
        Map<Id, T> entries = new TreeMap<>();
        json.forEach((id, element) -> {
            DataResult<T> result = key.codec().parse(JsonOps.INSTANCE, element);
            T value = result.result().orElseThrow(() -> new IllegalArgumentException(
                    "Bad " + key.id() + " definition " + id + ": " + result.error().map(e -> e.message()).orElse("?")));
            entries.put(id, value);
        });
        return new SimRegistryImpl<>(key, entries);
    }

    @Override
    public RegistryKey<T> key() {
        return key;
    }

    @Override
    public Optional<T> find(Id id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public Collection<Id> ids() {
        return entries.keySet();
    }

    @Override
    public int size() {
        return entries.size();
    }
}
