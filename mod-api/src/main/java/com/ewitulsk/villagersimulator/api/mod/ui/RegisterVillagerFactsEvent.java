package com.ewitulsk.villagersimulator.api.mod.ui;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server: collects {@link VillagerFact}s by key, e.g. {@code villagersimulator_fountain:wishes}. Fired on the mod bus
 * when a server starts.
 */
public final class RegisterVillagerFactsEvent extends Event implements IModBusEvent {
    private final Map<String, VillagerFact> facts = new LinkedHashMap<>();

    public void register(String key, VillagerFact fact) {
        facts.put(key, fact);
    }

    public Map<String, VillagerFact> facts() {
        return Map.copyOf(facts);
    }
}
