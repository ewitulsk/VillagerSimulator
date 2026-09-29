package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.api.mod.ui.DialoguePanel;
import com.ewitulsk.villagersimulator.api.mod.ui.RegisterDialoguePanelsEvent;
import net.neoforged.fml.ModLoader;

import java.util.List;

/** Addons' dialogue panels (mod-api), collected once during client setup. */
public final class DialoguePanels {
    private static volatile List<DialoguePanel> panels = List.of();

    private DialoguePanels() {}

    static void load() {
        RegisterDialoguePanelsEvent event = new RegisterDialoguePanelsEvent();
        ModLoader.postEvent(event);
        panels = event.panels();
    }

    public static List<DialoguePanel> all() {
        return panels;
    }
}
