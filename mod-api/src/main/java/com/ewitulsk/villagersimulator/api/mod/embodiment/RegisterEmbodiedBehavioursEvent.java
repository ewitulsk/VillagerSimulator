package com.ewitulsk.villagersimulator.api.mod.embodiment;

import com.ewitulsk.villagersimulator.api.sim.Id;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** Collects embodied behaviours by key. Fired on the mod bus when a server starts. */
public final class RegisterEmbodiedBehavioursEvent extends Event implements IModBusEvent {
    private final Map<Id, EmbodiedBehaviour> behaviours = new LinkedHashMap<>();

    /** Registers the behaviour for activities whose {@code embodied()} key is {@code key}. */
    public void register(Id key, EmbodiedBehaviour behaviour) {
        if (behaviours.putIfAbsent(key, behaviour) != null) throw new IllegalArgumentException("Duplicate embodied behaviour " + key);
    }

    public Map<Id, EmbodiedBehaviour> behaviours() {
        return Map.copyOf(behaviours);
    }
}
