package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.api.sim.core.SetTiersCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.api.sim.view.SimViews;
import com.ewitulsk.villagersimulator.content.social.Social;
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
    private Map<Integer, Integer> partners = Map.of();

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
        if (tickCount % TIER_INTERVAL == 0 && SimServer.get() != null) SimPlayers.reportPositions(SimServer.get());
        if (tickCount % TIER_INTERVAL == 0) sendOverlays(level);
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
        Map<EntityId, EntityId> talks = views.get(Social.CONVERSATIONS);
        Map<Integer, Integer> p = new HashMap<>();
        if (talks != null) talks.forEach((a, b) -> p.put(a.raw(), b.raw()));
        partners = p;
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
            Tier want = tierFor(level, players, e.x(), e.y(), e.z(), near);
            if (want != e.tier()) changes.put(e.id(), want);
        }
        if (!changes.isEmpty()) runtime.submit(new SetTiersCommand(Map.copyOf(changes)));
    }

    /**
     * T0 near a player (in a ticking chunk), T1 in other ticking chunks, T3 beyond {@code tiers.t3Radius} of every
     * player, T2 otherwise (docs/DESIGN.md §4.2). With no players online everything stays at T2.
     */
    static Tier tierFor(ServerLevel level, List<ServerPlayer> players, double x, double y, double z, boolean nearPlayer) {
        boolean ticking = level.isPositionEntityTicking(BlockPos.containing(x, y, z));
        if (nearPlayer && ticking) return Tier.T0;
        if (ticking) return Tier.T1;
        if (players.isEmpty()) return Tier.T2; // nobody to be far from: keep full detail
        double far = SimConfig.T3_RADIUS.get();
        for (ServerPlayer p : players) {
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz <= far * far) return Tier.T2;
        }
        return Tier.T3;
    }

    // ------------------------------------------------------------------------------------------------ debug overlay

    private final java.util.Set<java.util.UUID> overlayViewers = new java.util.HashSet<>();

    /** Turns the debug overlay on or off for a player; returns whether it is now on. */
    public boolean toggleOverlay(ServerPlayer player) {
        if (overlayViewers.remove(player.getUUID())) {
            send(player, new com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload(new int[0], List.of(), List.of()));
            return false;
        }
        overlayViewers.add(player.getUUID());
        return true;
    }

    private void sendOverlays(ServerLevel level) {
        if (overlayViewers.isEmpty()) return;
        List<com.ewitulsk.villagersimulator.content.villages.Villages.DistrictInfo> districts =
                viewsSeen == null ? null : viewsSeen.get(com.ewitulsk.villagersimulator.content.villages.Villages.DISTRICTS);
        List<ServerPlayer> players = level.players();
        int radius = SimConfig.T0_RADIUS.get();
        for (ServerPlayer p : players) {
            if (!overlayViewers.contains(p.getUUID())) continue;
            int pcx = p.chunkPosition().x, pcz = p.chunkPosition().z;
            List<Integer> chunks = new java.util.ArrayList<>();
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    double x = cx * 16 + 8, z = cz * 16 + 8;
                    boolean near = false;
                    for (ServerPlayer q : players) {
                        double ddx = q.getX() - x, ddz = q.getZ() - z;
                        if (ddx * ddx + ddz * ddz <= (double) radius * radius) near = true;
                    }
                    chunks.add(cx);
                    chunks.add(cz);
                    chunks.add(tierFor(level, players, x, p.getY(), z, near).ordinal());
                }
            }
            List<com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload.District> ds = new java.util.ArrayList<>();
            if (districts != null) {
                for (var d : districts) {
                    if (Math.abs(d.x0() - p.getX()) < 400 && Math.abs(d.z0() - p.getZ()) < 400) {
                        ds.add(new com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload.District(d.name(), d.x0(), d.z0(), d.x1(), d.z1()));
                    }
                }
            }
            List<double[]> routes = new java.util.ArrayList<>();
            for (Embodiment e : embodiments.values()) {
                if (e.route().length < 6 || Math.abs(e.x() - p.getX()) > 96 || Math.abs(e.z() - p.getZ()) > 96) continue;
                double[] r = new double[e.route().length + 3];
                r[0] = e.x();
                r[1] = e.y();
                r[2] = e.z();
                System.arraycopy(e.route(), 0, r, 3, e.route().length);
                routes.add(r);
            }
            send(p, new com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload(
                    chunks.stream().mapToInt(Integer::intValue).toArray(), ds, routes));
        }
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException ignored) {
            // players without the channel (fake connections)
        }
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

    /** The embodiment of whoever {@code handle} is talking to, or {@code null}. */
    public Embodiment partnerOf(int handle) {
        Integer other = partners.get(handle);
        return other == null ? null : embodiments.get(other);
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
