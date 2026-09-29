package com.ewitulsk.villagersimulator.api.sim.component;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;

/**
 * A component of cold or variable-size data, stored as one object per entity (docs/ARCHITECTURE.md §5.2).
 * Values should be immutable; replace them with {@code set} to change them. The codec is used for saving.
 */
public final class SparseComponent<T> {
    private static final java.util.concurrent.atomic.AtomicInteger SERIALS = new java.util.concurrent.atomic.AtomicInteger();
    private final int serial = SERIALS.getAndIncrement();
    private final Id id;
    private final int version;
    private final Codec<T> codec;

    public SparseComponent(Id id, int version, Codec<T> codec) {
        this.id = id;
        this.version = version;
        this.codec = codec;
    }

    public Id id() {
        return id;
    }

    public int version() {
        return version;
    }

    public Codec<T> codec() {
        return codec;
    }

    /** A small number unique to this component object in the JVM; the engine indexes its stores by it. */
    public int serial() {
        return serial;
    }

    @Override
    public String toString() {
        return "SparseComponent[" + id + "]";
    }
}
