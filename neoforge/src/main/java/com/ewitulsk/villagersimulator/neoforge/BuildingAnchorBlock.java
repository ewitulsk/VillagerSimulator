package com.ewitulsk.villagersimulator.neoforge;

import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Marks a sim building in the world (docs/ARCHITECTURE.md §13.1). Every blueprint contains one. Using it shows what
 * the sim knows about the building.
 */
public class BuildingAnchorBlock extends Block {
    public BuildingAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        SimServer sim = SimServer.get();
        if (sim == null) return InteractionResult.PASS;
        sim.runtime().query(VillageQueries.buildingAt(pos.getX(), pos.getY(), pos.getZ()))
                .thenAccept(found -> sim.server().execute(() -> player.displayClientMessage(
                        Component.literal(found.orElse("This anchor isn't part of a sim building.")), false)));
        return InteractionResult.CONSUME;
    }
}
