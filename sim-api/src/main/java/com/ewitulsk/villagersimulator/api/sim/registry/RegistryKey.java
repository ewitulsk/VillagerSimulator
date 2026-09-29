package com.ewitulsk.villagersimulator.api.sim.registry;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;

/**
 * A data-driven registry. Entries are JSON files under {@code data/<namespace>/villagersimulator/<folder>/}; the file
 * path gives the entry id. Registries are frozen once the sim is built (docs/ARCHITECTURE.md §8.2).
 */
public record RegistryKey<T>(Id id, String folder, Codec<T> codec) {}
