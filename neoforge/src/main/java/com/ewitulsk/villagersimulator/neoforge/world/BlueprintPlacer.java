package com.ewitulsk.villagersimulator.neoforge.world;

import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * Places Phase 0 blueprints: flattens the plot, fills a foundation and places the structure template. Later phases
 * replace this with per-section construction and reconciliation (docs/ARCHITECTURE.md §13.3).
 */
public final class BlueprintPlacer {
    private static final int FOUNDATION_DEPTH = 6;
    private static final int CLEARANCE = 3;

    private BlueprintPlacer() {}

    /**
     * The blueprint origin y for a plot at {@code (x, z)}: the floor layer (y=0 of the blueprint) replaces the top
     * ground block at the plot's centre.
     */
    public static int groundY(ServerLevel level, int x, int z, BuildingType type) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + type.sizeX() / 2, z + type.sizeZ() / 2) - 1;
    }

    public static boolean place(ServerLevel level, BuildingType type, BlockPos origin) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(type.blueprint().namespace(), type.blueprint().path());
        Optional<StructureTemplate> template = level.getStructureManager().get(id);
        if (template.isEmpty()) return false;

        int flags = Block.UPDATE_CLIENTS;
        for (int dx = 0; dx < type.sizeX(); dx++) {
            for (int dz = 0; dz < type.sizeZ(); dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                for (int y = origin.getY() + 1; y <= origin.getY() + type.sizeY() + CLEARANCE; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), flags);
                }
                for (int y = origin.getY() - 1; y >= origin.getY() - FOUNDATION_DEPTH; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.canBeReplaced() && s.getFluidState().isEmpty()) break;
                    level.setBlock(p, Blocks.DIRT.defaultBlockState(), flags);
                }
            }
        }
        template.get().placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random, flags);
        return true;
    }
}
