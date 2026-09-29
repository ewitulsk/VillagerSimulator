package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;

/** Published when a villager joins the sim. The plans module starts the villager's day from this. */
public record VillagerCreated(EntityId villager) implements SimEvent {}
