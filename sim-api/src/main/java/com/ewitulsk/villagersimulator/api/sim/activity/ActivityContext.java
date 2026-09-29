package com.ewitulsk.villagersimulator.api.sim.activity;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;

/** @param venue the building the activity happens at, or {@link EntityId#NONE} */
public record ActivityContext(SimContext sim, EntityId actor, EntityId venue) {}
