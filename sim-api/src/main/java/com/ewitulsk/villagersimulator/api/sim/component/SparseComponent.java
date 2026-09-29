package com.ewitulsk.villagersimulator.api.sim.component;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;

/**
 * A component of cold or variable-size data, stored as one object per entity (docs/ARCHITECTURE.md §5.2).
 * Values should be immutable; replace them with {@code set} to change them. The codec is used for saving.
 */
public record SparseComponent<T>(Id id, int version, Codec<T> codec) {}
