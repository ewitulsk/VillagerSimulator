package com.ewitulsk.villagersimulator.api.mod.blueprint;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blueprint metadata from blocks (docs/ARCHITECTURE.md §13.3): a block in a building's blueprint becomes a point of
 * the given kind, e.g. a cauldron becomes a {@code wish} point that advertisements can send villagers to. The same
 * mapping can be given as data in {@code data/<ns>/villagersimulator/point_blocks/*.json}. Fired on the mod bus when
 * a server starts.
 */
public final class RegisterPointBlocksEvent extends Event implements IModBusEvent {
    private final Map<Block, String> points = new LinkedHashMap<>();

    public void register(Block block, String pointKind) {
        points.put(block, pointKind);
    }

    public Map<Block, String> points() {
        return Map.copyOf(points);
    }
}
