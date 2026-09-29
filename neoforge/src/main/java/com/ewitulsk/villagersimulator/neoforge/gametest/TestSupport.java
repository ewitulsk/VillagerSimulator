package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.VillageLayouts;
import com.ewitulsk.villagersimulator.neoforge.ModContent;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Shared GameTest helpers. Tests set up the situation directly instead of waiting for it (see ../ModTesting.md). */
final class TestSupport {
    private TestSupport() {}

    static SimServer sim() {
        SimServer s = SimServer.get();
        if (s == null) throw new GameTestAssertException("Sim isn't running");
        return s;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /**
     * A compact village inside the 16x16 test arena: every building's origin is the same corner, so all points
     * (beds, work spots, wander spots) are within reach. No blocks are placed; the sim doesn't need them.
     */
    static SpawnVillageCommand compactVillage(GameTestHelper h, String name, long seed, int villagers, Consumer<EntityId> onCreated) {
        BlockPos o = h.absolutePos(new BlockPos(1, 0, 1));
        List<SpawnVillageCommand.Placement> placements = new ArrayList<>();
        placements.add(new SpawnVillageCommand.Placement(VillageLayouts.WELL, o.getX(), o.getY(), o.getZ()));
        placements.add(new SpawnVillageCommand.Placement(VillageLayouts.BAKERY, o.getX(), o.getY(), o.getZ()));
        for (int i = 0; i < Math.max(1, (villagers + 3) / 4); i++) {
            placements.add(new SpawnVillageCommand.Placement(VillageLayouts.HOUSE, o.getX(), o.getY(), o.getZ()));
        }
        return new SpawnVillageCommand(name, seed, o.getX(), o.getY() + 1, o.getZ(), placements, villagers, onCreated);
    }

    /** Submits {@code command}, recording the sim time it was applied at. Completes with the new village. */
    static CompletableFuture<EntityId> spawn(SpawnVillageCommand command, AtomicLong appliedAt) {
        CompletableFuture<EntityId> village = new CompletableFuture<>();
        SpawnVillageCommand withCallback = new SpawnVillageCommand(command.name(), command.seed(), command.centerX(),
                command.centerY(), command.centerZ(), command.placements(), command.villagers(), village::complete);
        sim().runtime().submit(ctx -> {
            if (appliedAt != null) appliedAt.set(ctx.now());
            withCallback.apply(ctx);
        });
        return village;
    }

    /** Puppets in the level embodying {@code villager}. */
    static int puppets(GameTestHelper h, EntityId villager) {
        return h.getLevel().getEntities(ModContent.VILLAGER.get(), e -> e.handle() == villager.raw()).size();
    }

    /**
     * Runs the server as fast as it can for {@code ticks} ticks ({@code /tick sprint}). Only for the rare embodied test
     * that truly needs many real ticks; ModTesting.md asks for a written reason, so one is required here.
     */
    static void sprint(GameTestHelper h, int ticks, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("sprint needs a reason");
        h.getLevel().getServer().tickRateManager().requestGameToSprint(ticks);
    }
}
