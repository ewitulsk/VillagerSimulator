package com.ewitulsk.villagersimulator.core.data;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.google.gson.JsonElement;

import java.util.Map;

/**
 * Supplies data definitions for registries. In game this is the datapack contents; headless it's the classpath.
 * Paths are {@code data/<namespace>/villagersimulator/<folder>/<path>.json}, giving the id {@code namespace:path}.
 */
@FunctionalInterface
public interface DataSource {
    Map<Id, JsonElement> load(String folder);

    static DataSource of(Map<String, Map<Id, JsonElement>> byFolder) {
        Map<String, Map<Id, JsonElement>> copy = Map.copyOf(byFolder);
        return folder -> copy.getOrDefault(folder, Map.of());
    }
}
