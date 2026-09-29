package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;

/** Published after data definitions were reloaded ({@code /reload}); registries and compiled logic are fresh. */
public record DataReloaded() implements SimEvent {}
