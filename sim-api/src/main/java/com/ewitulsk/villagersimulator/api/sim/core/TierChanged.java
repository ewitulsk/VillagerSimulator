package com.ewitulsk.villagersimulator.api.sim.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;

/** An entity's simulation tier changed (published by the tier commands, at a boundary). */
public record TierChanged(EntityId entity, Tier from, Tier to) implements SimEvent {}
