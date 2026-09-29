package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads sim data definitions from datapacks: {@code data/<ns>/villagersimulator/<folder>/<path>.json}. On
 * {@code /reload} the running sim re-reads its registries (component schemas still need a restart).
 */
public final class SimDataReloadListener extends SimplePreparableReloadListener<Map<String, Map<Id, JsonElement>>> {
    private static final Logger LOG = LoggerFactory.getLogger("VillagerSim/Data");
    private static final String ROOT = "villagersimulator";
    private static volatile Map<String, Map<Id, JsonElement>> latest = Map.of();

    /** The most recently loaded data. */
    public static DataSource current() {
        return DataSource.of(latest);
    }

    @Override
    protected Map<String, Map<Id, JsonElement>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Map<Id, JsonElement>> out = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(ROOT, rl -> rl.getPath().endsWith(".json")).entrySet()) {
            String path = e.getKey().getPath().substring(ROOT.length() + 1); // <folder>/<id path>.json
            int slash = path.indexOf('/');
            if (slash < 0) continue;
            String folder = path.substring(0, slash);
            String idPath = path.substring(slash + 1, path.length() - ".json".length());
            try (Reader r = e.getValue().openAsReader()) {
                out.computeIfAbsent(folder, k -> new TreeMap<>()).put(Id.of(e.getKey().getNamespace(), idPath), JsonParser.parseReader(r));
            } catch (IOException | RuntimeException ex) {
                LOG.error("Couldn't read sim data {}", e.getKey(), ex);
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<String, Map<Id, JsonElement>> data, ResourceManager manager, ProfilerFiller profiler) {
        latest = Map.copyOf(data);
        int count = data.values().stream().mapToInt(Map::size).sum();
        LOG.info("Loaded {} sim data definitions", count);
        SimServer running = SimServer.get();
        if (running != null) running.reload(current());
    }
}
