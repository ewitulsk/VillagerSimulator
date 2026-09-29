package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.buildings.Stock;
import com.ewitulsk.villagersimulator.content.needs.Needs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates a village in the sim: its own shard, its districts, buildings at the given placements, the road graph,
 * and {@code villagers} residents with homes and jobs. It doesn't touch the Minecraft world; the bridge places
 * blueprints separately. All randomness comes from {@code seed}, so the same command gives the same village in any
 * world.
 *
 * @param districts districts to create; if empty, one district covering the whole village
 * @param onCreated called on the sim thread with the new village, or {@code null}
 */
public record SpawnVillageCommand(String name, long seed, int centerX, int centerY, int centerZ,
                                  List<Placement> placements, List<District.Spec> districts, int villagers,
                                  Consumer<EntityId> onCreated) implements SimCommand {

    /** A building at blueprint origin {@code (x, y, z)} (the min corner). */
    public record Placement(Id type, int x, int y, int z) {}

    /** A village with a single district. */
    public SpawnVillageCommand(String name, long seed, int centerX, int centerY, int centerZ, List<Placement> placements,
                               int villagers, Consumer<EntityId> onCreated) {
        this(name, seed, centerX, centerY, centerZ, placements, List.of(), villagers, onCreated);
    }

    /** The same command with a different callback. */
    public SpawnVillageCommand withCallback(Consumer<EntityId> callback) {
        return new SpawnVillageCommand(name, seed, centerX, centerY, centerZ, placements, districts, villagers, callback);
    }

    @Override
    public void apply(SimContext ctx) {
        int shard = ctx.newShard();
        EntityId village = ctx.create();
        ctx.setShard(village, shard);

        List<District.Spec> specs = districts.isEmpty() ? List.of(wholeVillage(ctx)) : districts;
        List<EntityId> districtIds = new ArrayList<>();
        List<District> districtRecords = new ArrayList<>();
        for (District.Spec spec : specs) {
            EntityId d = ctx.create();
            ctx.setShard(d, shard);
            District record = new District(spec.name(), spec.purpose(), spec.x0(), spec.z0(), spec.x1(), spec.z1(), village);
            ctx.set(d, Villages.DISTRICT, record);
            districtIds.add(d);
            districtRecords.add(record);
        }

        List<EntityId> buildings = new ArrayList<>();
        for (Placement p : placements) {
            BuildingType type = ctx.registry(BuildingType.REGISTRY).get(p.type());
            EntityId b = ctx.create();
            ctx.setShard(b, shard);
            EntityId district = districtOf(districtIds, districtRecords, p.x() + type.sizeX() / 2.0, p.z() + type.sizeZ() / 2.0);
            ctx.set(b, Buildings.BUILDING, new Building(p.type(), p.x(), p.y(), p.z(), village, district));
            ctx.set(b, Buildings.STOCK, new Stock(type.initialStock()));
            buildings.add(b);
        }
        ctx.set(village, Roads.COMPONENT, Roads.build(ctx, new double[]{centerX + 0.5, centerY, centerZ + 0.5}, buildings));

        // Beds and job slots, in placement order.
        List<EntityId> bedHomes = new ArrayList<>();
        List<Integer> bedIndex = new ArrayList<>();
        List<EntityId> jobSites = new ArrayList<>();
        List<Integer> jobSlot = new ArrayList<>();
        for (EntityId b : buildings) {
            BuildingType type = Buildings.type(ctx, b);
            for (int i = 0; i < type.beds(); i++) {
                bedHomes.add(b);
                bedIndex.add(i);
            }
            type.job().ifPresent(job -> {
                for (int i = 0; i < job.slots(); i++) {
                    jobSites.add(b);
                    jobSlot.add(i);
                }
            });
        }

        // Each job slot goes to the nearest unassigned resident, so nobody commutes across town.
        int[] jobOf = new int[villagers];
        java.util.Arrays.fill(jobOf, -1);
        boolean[] taken = new boolean[villagers];
        for (int j = 0; j < jobSites.size(); j++) {
            double[] site = Buildings.centre(ctx, jobSites.get(j));
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int n = 0; n < villagers; n++) {
                if (taken[n]) continue;
                double[] home = n < bedHomes.size() ? Buildings.centre(ctx, bedHomes.get(n)) : new double[]{centerX, centerY, centerZ};
                double d = Math.hypot(home[0] - site[0], home[2] - site[2]);
                if (d < bestDist) {
                    bestDist = d;
                    best = n;
                }
            }
            if (best < 0) break;
            taken[best] = true;
            jobOf[best] = j;
        }

        List<EntityId> residents = new ArrayList<>();
        for (int n = 0; n < villagers; n++) {
            EntityId v = ctx.create();
            ctx.setShard(v, shard);
            long vSeed = SimRandom.hash(seed, n, SimRandom.salt("villager"));
            boolean hasBed = n < bedHomes.size();
            boolean hasJob = jobOf[n] >= 0;
            ctx.set(v, Villages.VILLAGER, new Villager(
                    Names.pick(vSeed),
                    village,
                    hasBed ? bedHomes.get(n) : EntityId.NONE,
                    hasBed ? bedIndex.get(n) : 0,
                    hasJob ? jobSites.get(jobOf[n]) : EntityId.NONE,
                    hasJob ? jobSlot.get(jobOf[n]) : 0,
                    n,
                    vSeed));
            ctx.add(v, CoreComponents.TIER);
            ctx.set(v, CoreComponents.TIER_CURRENT, Tier.T2.ordinal());
            Needs.init(ctx, v, vSeed);
            Appearance.init(ctx, v, vSeed);
            residents.add(v);
        }

        ctx.set(village, Villages.VILLAGE, new Village(name, seed, centerX, centerY, centerZ, ctx.now(), buildings, residents, districtIds));
        ctx.events().record(Villages.EVENT_FOUNDED, village, name);
        for (EntityId v : residents) {
            ctx.events().record(Villages.EVENT_ARRIVED, v, ctx.get(v, Villages.VILLAGER).name() + " arrived in " + name);
            ctx.publish(new VillagerCreated(v));
        }
        if (onCreated != null) onCreated.accept(village);
    }

    /** A district around every placement, with a margin. */
    private District.Spec wholeVillage(SimContext ctx) {
        int x0 = centerX - 8, z0 = centerZ - 8, x1 = centerX + 8, z1 = centerZ + 8;
        for (Placement p : placements) {
            BuildingType t = ctx.registry(BuildingType.REGISTRY).get(p.type());
            x0 = Math.min(x0, p.x() - 4);
            z0 = Math.min(z0, p.z() - 4);
            x1 = Math.max(x1, p.x() + t.sizeX() + 4);
            z1 = Math.max(z1, p.z() + t.sizeZ() + 4);
        }
        return new District.Spec(name, "village", x0, z0, x1, z1);
    }

    private static EntityId districtOf(List<EntityId> ids, List<District> records, double x, double z) {
        int best = 0;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).contains(x, z)) return ids.get(i);
            if (records.get(i).distanceTo(x, z) < records.get(best).distanceTo(x, z)) best = i;
        }
        return ids.get(best);
    }
}
