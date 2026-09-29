package com.ewitulsk.villagersimulator.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** The {@code VillagerSimEvents} group for server scripts. */
public interface VillagerSimEvents {
    EventGroup GROUP = EventGroup.of("VillagerSimEvents");

    /** {@code VillagerSimEvents.recorded(e => ...)}: the sim logged an event record. */
    EventHandler RECORDED = GROUP.server("recorded", () -> SimRecordKubeEvent.class);

    /** {@code VillagerSimEvents.scenarios(e => e.add(name, description, s => ...))}: add scenarios. */
    EventHandler SCENARIOS = GROUP.server("scenarios", () -> ScenariosKubeEvent.class);
}
