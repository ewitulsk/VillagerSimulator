package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.api.sim.core.SetTiersCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.api.sim.view.SimViews;
import com.ewitulsk.villagersimulator.core.SimRuntime;
import com.ewitulsk.villagersimulator.neoforge.ModContent;
import com.ewitulsk.villagersimulator.neoforge.SimConfig;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Minecraft side of the sim (docs/ARCHITECTURE.md §12): drives the sim clock from game time, works out tiers
 * from player proximity, and spawns/removes villager puppets to match the sim's embodiment view. Server thread only.
 * Phase 0 embodies in the overworld only.
 */
public final class SimBridge {
    private static final int TIER_INTERVAL = 20;
    private static final int RECONCILE_INTERVAL = 5;
    private static final int SWEEP_INTERVAL = 100;

    private final MinecraftServer server;
    private final SimRuntime runtime;
    private final Map<Integer, SimVillagerEntity> puppets = new HashMap<>();
    private long lastGameTime;
    private long lastDayTime;
    private double pendingTicks;
    private long tickCount;
    private SimViews viewsSeen;
    private Map<Integer, Embodiment> embodiments = Map.of();

    SimBridge(MinecraftServer server, SimRuntime runtime) {
        this.server = server;
        this.runtime = runtime;
        ServerLevel overworld = server.overworld();
        this.lastGameTime = overworld.getGameTime();
        this.lastDayTime = overworld.getDayTime();
    }

    void tick() {
        ServerLevel level = server.overworld();
        advanceClock(level);
        refreshViews();
        tickCount++;
        if (tickCount % TIER_INTERVAL == 0) updateTiers(level);
        if (tickCount % RECONCILE_INTERVAL == 0) reconcile(level);
        if (tickCount % SWEEP_INTERVAL == 0) sweepOrphans(level);
    }

    /**
     * Sim time follows game time. Sleeping through the night or {@code /time add} jumps the day time forward, so the
     * sim takes the larger of the two deltas; day time going backwards ({@code /time set}) never rewinds the sim.
     */
    private void advanceClock(ServerLevel level) {
        long game = level.getGameTime();
        long day = level.getDayTime();
        long delta = Math.max(game - lastGameTime, day - lastDayTime);
        lastGameTime = game;
        lastDayTime = day;
        if (delta <= 0) return;
        pendingTicks += delta * SimConfig.DEBUG_TIME_SCALE.get();
        long whole = (long) pendingTicks;
        if (whole > 0) {
            pendingTicks -= whole;
            runtime.advanceTarget(whole);
        }
    }

    private void refreshViews() {
        SimViews views = runtime.views();
        if (views == viewsSeen) return;
        viewsSeen = views;
        List<Embodiment> list = views.get(Embodiment.VIEW);
        Map<Integer, Embodiment> map = new LinkedHashMap<>();
        if (list != null) for (Embodiment e : list) map.put(e.id().raw(), e);
        embodiments = map;
    }

    /** The latest embodiment for a sim entity, or {@code null} if the sim no longer has it. */
    public Embodiment embodiment(int handle) {
        return embodiments.get(handle);
    }

    private void updateTiers(ServerLevel level) {
        int radius = SimConfig.T0_RADIUS.get();
        double r2 = (double) radius * radius;
        List<ServerPlayer> players = level.players();
        Map<EntityId, Tier> changes = new LinkedHashMap<>();
        for (Embodiment e : embodiments.values()) {
            if (e.forced()) continue;
            boolean near = false;
            for (ServerPlayer p : players) {
                double dx = p.getX() - e.x();
                double dz = p.getZ() - e.z();
                if (!p.isSpectator() && dx * dx + dz * dz <= r2) {
                    near = true;
                    break;
                }
            }
            Tier want = near && level.isPositionEntityTicking(BlockPos.containing(e.x(), e.y(), e.z())) ? Tier.T0 : Tier.T2;
            if (want != e.tier()) changes.put(e.id(), want);
        }
        if (!changes.isEmpty()) runtime.submit(new SetTiersCommand(Map.copyOf(changes)));
    }

    /** Spawns a puppet for every T0 villager that lacks one, and removes puppets the sim no longer embodies. */
    private void reconcile(ServerLevel level) {
        puppets.entrySet().removeIf(entry -> {
            SimVillagerEntity puppet = entry.getValue();
            Embodiment e = embodiments.get(entry.getKey());
            if (puppet.isRemoved()) return true;
            if (e == null || e.tier() != Tier.T0) {
                puppet.discard();
                return true;
            }
            return false;
        });
        for (Embodiment e : embodiments.values()) {
            if (e.tier() != Tier.T0 || puppets.containsKey(e.id().raw())) continue;
            if (!level.isPositionEntityTicking(BlockPos.containing(e.x(), e.y(), e.z()))) continue;
            SimVillagerEntity puppet = ModContent.VILLAGER.get().create(level);
            if (puppet == null) continue;
            puppet.bind(e.id().raw());
            puppet.moveTo(e.x(), e.y(), e.z(), 0, 0);
            if (level.addFreshEntity(puppet)) puppets.put(e.id().raw(), puppet);
        }
    }

    /** Removes villager entities the bridge didn't spawn (e.g. left over after a crash): no duplicates, ever. */
    private void sweepOrphans(ServerLevel level) {
        for (Entity entity : level.getEntities(ModContent.VILLAGER.get(), e -> true)) {
            SimVillagerEntity v = (SimVillagerEntity) entity;
            if (puppets.get(v.handle()) != v) v.discard();
        }
    }

    /** True if {@code entity} is the puppet the bridge currently tracks for its handle. */
    public boolean owns(SimVillagerEntity entity) {
        return puppets.get(entity.handle()) == entity;
    }

    public SimVillagerEntity puppet(EntityId id) {
        return puppets.get(id.raw());
    }

    public int puppetCount() {
        return puppets.size();
    }

    void shutdown() {
        for (SimVillagerEntity p : puppets.values()) p.discard();
        puppets.clear();
    }
}
