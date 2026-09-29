package com.ewitulsk.villagersimulator.api.sim.component;

/** A float column of a {@link DenseComponent}; {@code slot} is its index among the component's float fields. */
public record FloatField(DenseComponent component, int slot, String name) {}
