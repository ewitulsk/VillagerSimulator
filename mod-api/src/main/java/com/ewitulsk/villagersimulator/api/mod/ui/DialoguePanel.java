package com.ewitulsk.villagersimulator.api.mod.ui;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Client: extra lines on the villager dialogue screen (docs/ARCHITECTURE.md §16, UI panels), built from the facts the
 * server sent ({@link VillagerFact}). Return an empty list to show nothing.
 */
@FunctionalInterface
public interface DialoguePanel {
    List<Component> lines(Context context);

    /** @param facts every fact the server sent for this villager, by key */
    record Context(String villagerName, Map<String, String> facts) {}
}
