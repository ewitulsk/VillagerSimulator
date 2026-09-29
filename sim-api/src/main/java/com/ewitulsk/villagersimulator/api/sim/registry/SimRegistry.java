package com.ewitulsk.villagersimulator.api.sim.registry;

import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.Collection;
import java.util.Optional;

public interface SimRegistry<T> {
    RegistryKey<T> key();

    Optional<T> find(Id id);

    /** @throws IllegalArgumentException if there is no entry {@code id} */
    default T get(Id id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("No " + key().id() + " entry " + id));
    }

    /** Entry ids in sorted order. */
    Collection<Id> ids();

    int size();
}
