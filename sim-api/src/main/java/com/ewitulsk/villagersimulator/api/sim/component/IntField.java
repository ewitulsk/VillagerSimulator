package com.ewitulsk.villagersimulator.api.sim.component;

/** A int column of a {@link DenseComponent}; {@code slot} is its index among the component's int fields. */
public record IntField(DenseComponent component, int slot, String name) {}
