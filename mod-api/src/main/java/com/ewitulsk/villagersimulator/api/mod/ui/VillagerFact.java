package com.ewitulsk.villagersimulator.api.mod.ui;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;

/**
 * A fact about a villager, read on the sim thread when a player opens the dialogue screen and sent to the client for
 * {@link DialoguePanel}s. Return {@code null} to send nothing.
 */
@FunctionalInterface
public interface VillagerFact {
    String read(SimContext sim, EntityId villager);
}
