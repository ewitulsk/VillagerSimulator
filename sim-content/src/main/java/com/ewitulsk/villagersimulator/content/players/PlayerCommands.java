package com.ewitulsk.villagersimulator.content.players;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.PlanHooks;
import com.ewitulsk.villagersimulator.content.villages.Village;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Commands and queries the bridge sends about players. */
public final class PlayerCommands {
    private PlayerCommands() {}

    /** A player joined the server: find or create their sim record. Calls back with the entity. */
    public record Join(String uuid, String name, Consumer<EntityId> onReady) implements SimCommand {
        @Override
        public void apply(SimContext ctx) {
            EntityId p = Players.find(ctx, uuid);
            if (p.isNone()) {
                p = ctx.create();
                ctx.events().record(com.ewitulsk.villagersimulator.content.VS.id("player_arrived"), p, name + " arrived");
            }
            ctx.set(p, Players.PLAYER, new Players.PlayerRecord(uuid, name));
            if (onReady != null) onReady.accept(p);
        }
    }

    /**
     * Where a player is, reported by the bridge about once a second. Entering or leaving a building starts or ends
     * a visit there, so players take part in venue interactions exactly like villagers.
     */
    public record Position(EntityId player, double x, double y, double z) implements SimCommand {
        @Override
        public void apply(SimContext ctx) {
            if (!Players.isPlayer(ctx, player)) return;
            EntityId venue = buildingAt(ctx, x, y, z);
            Players.Presence was = ctx.get(player, Players.PRESENCE);
            EntityId before = was == null ? EntityId.NONE : was.venue();
            if (venue.equals(before)) return;
            long now = ctx.now();
            if (!before.isNone() && ctx.alive(before)) {
                ctx.publish(new PlanHooks.VisitEnded(player, before, Players.VISIT, ad(was.ad()), was.since(), now));
            }
            if (venue.isNone()) {
                ctx.remove(player, Players.PRESENCE);
                return;
            }
            String ad = socialAd(ctx, venue);
            ctx.set(player, Players.PRESENCE, new Players.Presence(venue, now, ad));
            ctx.publish(new PlanHooks.VisitStarted(player, venue, Players.VISIT, ad(ad), now, now + SimTime.TICKS_PER_DAY));
        }

        private static Optional<String> ad(String ad) {
            return ad.isEmpty() ? Optional.empty() : Optional.of(ad);
        }
    }

    /** The first advertisement of the building that satisfies Social, so a player's visit there counts as social. */
    static String socialAd(SimContext ctx, EntityId venue) {
        for (Advertisement a : Buildings.type(ctx, venue).advertisements()) {
            if (a.needs().getOrDefault("social", 0.0) > 0) return a.id();
        }
        return "";
    }

    /** The building whose footprint (plus a margin for doorsteps) contains the position. */
    static EntityId buildingAt(SimContext ctx, double x, double y, double z) {
        List<EntityId> found = new ArrayList<>();
        ctx.forEach(Buildings.BUILDING, (id, b) -> {
            if (!found.isEmpty()) return;
            BuildingType t = Buildings.type(ctx, id);
            if (x >= b.x() - 1 && x < b.x() + t.sizeX() + 1 && z >= b.z() - 1 && z < b.z() + t.sizeZ() + 1
                    && y >= b.y() - 1 && y < b.y() + t.sizeY() + 2) {
                found.add(id);
            }
        });
        return found.isEmpty() ? EntityId.NONE : found.get(0);
    }

    /** The player's standing in each village: average opinion of residents (0 for those who never heard of them). */
    public static SimQuery<List<String>> reputation(EntityId player) {
        return ctx -> {
            List<String> out = new ArrayList<>();
            ctx.forEach(Villages.VILLAGE, (id, v) -> {
                double sum = 0;
                int known = 0;
                for (EntityId r : v.residents()) {
                    float f = ctx.relationships().friendship(r, player);
                    sum += f;
                    if (f != 0) known++;
                }
                out.add(String.format("%s: reputation %+.1f, known by %d of %d", v.name(),
                        v.residents().isEmpty() ? 0 : sum / v.residents().size(), known, v.residents().size()));
            });
            return out;
        };
    }

    /** Average opinion of the player among a village's residents. */
    public static double reputation(SimContext ctx, EntityId player, EntityId villageId) {
        Village v = ctx.get(villageId, Villages.VILLAGE);
        if (v == null || v.residents().isEmpty()) return 0;
        double sum = 0;
        for (EntityId r : v.residents()) sum += ctx.relationships().friendship(r, player);
        return sum / v.residents().size();
    }
}
