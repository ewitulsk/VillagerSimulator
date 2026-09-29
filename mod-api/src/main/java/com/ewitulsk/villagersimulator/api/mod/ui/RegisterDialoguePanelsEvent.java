package com.ewitulsk.villagersimulator.api.mod.ui;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.List;

/** Client: collects {@link DialoguePanel}s, shown in registration order. Fired on the mod bus during client setup. */
public final class RegisterDialoguePanelsEvent extends Event implements IModBusEvent {
    private final List<DialoguePanel> panels = new ArrayList<>();

    public void register(DialoguePanel panel) {
        panels.add(panel);
    }

    public List<DialoguePanel> panels() {
        return List.copyOf(panels);
    }
}
