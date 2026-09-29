package com.ewitulsk.villagersimulator.neoforge.world;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Vanilla workstations and beds integration (docs/ARCHITECTURE.md §13.5): beds and workstation blocks inside a
 * blueprint become {@code bed} / {@code work} points automatically, so blueprint authors don't have to list them.
 * Points a building type lists explicitly always win. Which blocks count is data:
 * {@code data/<ns>/villagersimulator/point_blocks/*.json}, e.g. {@code {"work": ["minecraft:smoker"]}}.
 */
public final class VanillaPoints {
    private static final String TYPES = "building_types";
    private static final String BLOCKS = "point_blocks";

    private VanillaPoints() {}

    /** The data with detected points added to building types that don't list them. */
    public static DataSource augment(MinecraftServer server, DataSource base) {
        return augment(server, base, Map.of());
    }

    /** The same, with point blocks addons registered in code ({@code RegisterPointBlocksEvent}) as well as data. */
    public static DataSource augment(MinecraftServer server, DataSource base, Map<Block, String> extra) {
        Map<String, List<Block>> kinds = pointBlocks(base);
        extra.forEach((block, kind) -> {
            List<Block> list = kinds.computeIfAbsent(kind, k -> new ArrayList<>());
            if (!list.contains(block)) list.add(block);
        });
        Map<Id, JsonElement> types = new TreeMap<>();
        base.load(TYPES).forEach((id, json) -> types.put(id, augmentType(server, json, kinds)));
        return folder -> folder.equals(TYPES) ? types : base.load(folder);
    }

    /** Point kind → blocks, from the {@code point_blocks} data. */
    public static Map<String, List<Block>> pointBlocks(DataSource data) {
        Map<String, List<Block>> out = new LinkedHashMap<>();
        for (JsonElement file : data.load(BLOCKS).values()) {
            if (!file.isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> kind : file.getAsJsonObject().entrySet()) {
                if (!kind.getValue().isJsonArray()) continue;
                for (JsonElement id : kind.getValue().getAsJsonArray()) {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id.getAsString()));
                    block.ifPresent(b -> out.computeIfAbsent(kind.getKey(), k -> new ArrayList<>()).add(b));
                }
            }
        }
        return out;
    }

    private static JsonElement augmentType(MinecraftServer server, JsonElement json, Map<String, List<Block>> kinds) {
        if (!json.isJsonObject() || !json.getAsJsonObject().has("blueprint")) return json;
        JsonObject copy = json.getAsJsonObject().deepCopy();
        JsonObject points = copy.has("points") && copy.get("points").isJsonObject() ? copy.getAsJsonObject("points") : new JsonObject();
        boolean needBeds = !points.has("bed");
        boolean needKinds = kinds.keySet().stream().anyMatch(k -> !points.has(k));
        if (!needBeds && !needKinds) return json;

        ResourceLocation blueprint = ResourceLocation.tryParse(copy.get("blueprint").getAsString());
        if (blueprint == null) return json;
        Optional<StructureTemplate> template = server.getStructureManager().get(blueprint);
        if (template.isEmpty()) return json;

        Map<String, List<List<Integer>>> detected = detect(template.get(), kinds);
        detected.forEach((kind, list) -> {
            if (points.has(kind) || list.isEmpty()) return;
            JsonArray array = new JsonArray();
            for (List<Integer> p : list) {
                JsonArray a = new JsonArray();
                p.forEach(a::add);
                array.add(a);
            }
            points.add(kind, array);
        });
        copy.add("points", points);
        return copy;
    }

    /** Detected points by kind: bed heads, and the standing spot in front of each workstation. Sorted. */
    public static Map<String, List<List<Integer>>> detect(StructureTemplate template, Map<String, List<Block>> kinds) {
        Map<String, List<List<Integer>>> out = new LinkedHashMap<>();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        List<List<Integer>> beds = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof BedBlock)) continue;
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, block)) {
                if (info.state().getValue(BedBlock.PART) == BedPart.HEAD) beds.add(List.of(info.pos().getX(), info.pos().getY(), info.pos().getZ()));
            }
        }
        out.put("bed", sorted(beds));
        kinds.forEach((kind, blocks) -> {
            List<List<Integer>> list = new ArrayList<>();
            for (Block block : blocks) {
                for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, settings, block)) {
                    BlockPos stand = info.pos().relative(front(info.state()));
                    list.add(List.of(stand.getX(), stand.getY(), stand.getZ()));
                }
            }
            out.put(kind, sorted(list));
        });
        return out;
    }

    /** The side a worker stands on: the block's horizontal facing, or south. */
    private static Direction front(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING).getAxis().isHorizontal()) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return Direction.SOUTH;
    }

    private static List<List<Integer>> sorted(List<List<Integer>> points) {
        points.sort(Comparator.<List<Integer>>comparingInt(p -> p.get(0)).thenComparingInt(p -> p.get(1)).thenComparingInt(p -> p.get(2)));
        return points;
    }
}
