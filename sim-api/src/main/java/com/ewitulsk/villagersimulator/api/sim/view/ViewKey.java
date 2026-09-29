package com.ewitulsk.villagersimulator.api.sim.view;

import com.ewitulsk.villagersimulator.api.sim.Id;

/** Names a read-only snapshot the sim publishes for the Minecraft side (docs/ARCHITECTURE.md §7). */
public record ViewKey<T>(Id id) {}
