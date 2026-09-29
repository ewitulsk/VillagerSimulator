package com.ewitulsk.villagersimulator.api.sim.component;

/** A long column of a {@link DenseComponent}; {@code slot} is its index among the component's long fields. */
public record LongField(DenseComponent component, int slot, String name) {}
