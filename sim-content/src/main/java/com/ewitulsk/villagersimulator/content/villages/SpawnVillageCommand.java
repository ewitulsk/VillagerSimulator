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
 * Creates a village in the sim: buildings at the given placements, and {@code villagers} residents with homes and
 * jobs. It doesn't touch the Minecraft world; the bridge places blueprints separately. All randomness comes from
 * {@code seed}, so the same command gives the same village in any world.
 *
 * @param onCreated called on the sim thread with the new village, or {@code null}
 */
public record SpawnVillageCommand(String name, long seed, int centerX, int centerY, int centerZ,
                                  List<Placement> placements, int villagers, Consumer<EntityId> onCreated) implements SimCommand {

    /** A building at blueprint origin {@code (x, y, z)} (the min corner). */
    public record Placement(Id type, int x, int y, int z) {}

    @Override
    public void apply(SimContext ctx) {
        EntityId village = ctx.create();
        List<EntityId> buildings = new ArrayList<>();
        for (Placement p : placements) {
            BuildingType type = ctx.registry(BuildingType.REGISTRY).get(p.type());
            EntityId b = ctx.create();
            ctx.set(b, Buildings.BUILDING, new Building(p.type(), p.x(), p.y(), p.z(), village));
            ctx.set(b, Buildings.STOCK, new Stock(type.initialStock()));
            buildings.add(b);
        }

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

        List<EntityId> residents = new ArrayList<>();
        for (int n = 0; n < villagers; n++) {
            EntityId v = ctx.create();
            long vSeed = SimRandom.hash(seed, n, SimRandom.salt("villager"));
            boolean hasBed = n < bedHomes.size();
            boolean hasJob = n < jobSites.size();
            ctx.set(v, Villages.VILLAGER, new Villager(
                    Names.pick(vSeed),
                    village,
                    hasBed ? bedHomes.get(n) : EntityId.NONE,
                    hasBed ? bedIndex.get(n) : 0,
                    hasJob ? jobSites.get(n) : EntityId.NONE,
                    hasJob ? jobSlot.get(n) : 0,
                    n,
                    vSeed));
            ctx.add(v, CoreComponents.TIER);
            ctx.set(v, CoreComponents.TIER_CURRENT, Tier.T2.ordinal());
            Needs.init(ctx, v, vSeed);
            Appearance.init(ctx, v, vSeed);
            residents.add(v);
        }

        ctx.set(village, Villages.VILLAGE, new Village(name, seed, centerX, centerY, centerZ, ctx.now(), buildings, residents));
        ctx.events().record(Villages.EVENT_FOUNDED, village, name);
        for (EntityId v : residents) {
            ctx.events().record(Villages.EVENT_ARRIVED, v, ctx.get(v, Villages.VILLAGER).name() + " arrived in " + name);
            ctx.publish(new VillagerCreated(v));
        }
        if (onCreated != null) onCreated.accept(village);
    }
}
