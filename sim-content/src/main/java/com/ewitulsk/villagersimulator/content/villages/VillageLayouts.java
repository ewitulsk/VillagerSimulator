package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled village layouts. The village planner (Phase 15) replaces these. Shared by the
 * {@code /vs village spawn} command and headless scenarios, so both build the same village.
 */
public final class VillageLayouts {
    public static final Id WELL = VS.id("well");
    public static final Id BAKERY = VS.id("bakery");
    public static final Id HOUSE = VS.id("house");
    public static final Id TAVERN = VS.id("tavern");
    public static final Id MARKET_STALL = VS.id("market_stall");
    private static final int GAP = 4;

    private VillageLayouts() {}

    /** Building placements plus the districts they're grouped into. */
    public record Layout(List<SpawnVillageCommand.Placement> placements, List<District.Spec> districts) {}

    /**
     * A two-district town for larger populations (docs/ROADMAP.md Phase 5): a Market Quarter east of the centre with
     * taverns and stalls, a residential Old Town to the west, and bakeries in both so nobody walks far for bread.
     * About one bakery per 12 villagers, a tavern per 30, a house per 4.
     */
    public static Layout town(SimRegistry<BuildingType> types, int cx, int y, int cz, int villagers) {
        List<SpawnVillageCommand.Placement> out = new ArrayList<>();
        int bakeries = Math.max(1, (villagers + 11) / 12);
        int oldTownBakeries = bakeries / 2;
        int taverns = Math.max(1, (villagers + 29) / 30);
        int stalls = Math.max(1, taverns);
        int houses = Math.max(1, (villagers + types.get(HOUSE).beds() - 1) / Math.max(1, types.get(HOUSE).beds()));

        // Market Quarter: rows of 3 lots, 14 blocks apart, starting just east of the centre.
        List<Id> market = new ArrayList<>();
        market.add(WELL);
        for (int i = 0; i < bakeries - oldTownBakeries; i++) market.add(BAKERY);
        for (int i = 0; i < taverns; i++) market.add(TAVERN);
        for (int i = 0; i < stalls; i++) market.add(MARKET_STALL);
        int marketRows = (market.size() + 2) / 3;
        int top = cz - (marketRows * 14) / 2;
        for (int i = 0; i < market.size(); i++) {
            out.add(new SpawnVillageCommand.Placement(market.get(i), cx + 6 + (i % 3) * 14, y, top + (i / 3) * 14));
        }
        int marketEast = cx + 6 + 3 * 14 + 4;

        // Old Town: a well among rows of 5 houses, 13 blocks apart, west of the centre.
        List<Id> oldTown = new ArrayList<>();
        for (int i = 0; i < houses; i++) oldTown.add(HOUSE);
        // Spread the Old Town bakeries through the houses.
        for (int i = 0; i < oldTownBakeries; i++) oldTown.add((i + 1) * oldTown.size() / (oldTownBakeries + 1), BAKERY);
        int houseRows = (oldTown.size() + 4) / 5;
        int htop = cz - (houseRows * 12) / 2;
        out.add(new SpawnVillageCommand.Placement(WELL, cx - 8, y, htop - 8));
        for (int i = 0; i < oldTown.size(); i++) {
            out.add(new SpawnVillageCommand.Placement(oldTown.get(i), cx - 6 - (i % 5 + 1) * 13, y, htop + (i / 5) * 12));
        }
        int townWest = cx - 6 - 5 * 13 - 4;
        int north = Math.min(top, htop - 8) - 6, south = Math.max(top + marketRows * 14, htop + houseRows * 12) + 6;
        List<District.Spec> districts = List.of(
                new District.Spec("Market Quarter", "market", cx, north, marketEast, south),
                new District.Spec("Old Town", "residential", townWest, north, cx, south));
        return new Layout(out, districts);
    }

    /**
     * A hamlet around a well at {@code (cx, y, cz)}: the bakery to the east, the tavern north-east, a market stall
     * south-east, and enough houses for {@code villagers} spread west, north and south. Placements use {@code y} for
     * every building; in game each is then dropped onto the terrain.
     */
    public static List<SpawnVillageCommand.Placement> hamlet(SimRegistry<BuildingType> types, int cx, int y, int cz, int villagers) {
        BuildingType well = types.get(WELL);
        BuildingType bakery = types.get(BAKERY);
        BuildingType house = types.get(HOUSE);
        List<SpawnVillageCommand.Placement> out = new ArrayList<>();

        int wellX = cx - well.sizeX() / 2;
        int wellZ = cz - well.sizeZ() / 2;
        out.add(new SpawnVillageCommand.Placement(WELL, wellX, y, wellZ));
        int bakeryX = wellX + well.sizeX() + GAP;
        out.add(new SpawnVillageCommand.Placement(BAKERY, bakeryX, y, cz - bakery.sizeZ() / 2));
        BuildingType tavern = types.get(TAVERN);
        out.add(new SpawnVillageCommand.Placement(TAVERN, bakeryX, y, cz - bakery.sizeZ() / 2 - GAP - tavern.sizeZ()));
        out.add(new SpawnVillageCommand.Placement(MARKET_STALL, bakeryX + 2, y, cz + bakery.sizeZ() / 2 + GAP + 1));

        int houses = Math.max(1, (villagers + house.beds() - 1) / Math.max(1, house.beds()));
        // Candidate slots: west, north, south, then further out.
        int[][] slots = {
                {wellX - GAP - house.sizeX(), cz - house.sizeZ() / 2},
                {cx - house.sizeX() / 2, wellZ - GAP - house.sizeZ()},
                {cx - house.sizeX() / 2, wellZ + well.sizeZ() + GAP},
                {wellX - GAP - house.sizeX(), wellZ - GAP - house.sizeZ()},
                {wellX - GAP - house.sizeX(), wellZ + well.sizeZ() + GAP},
        };
        for (int i = 0; i < houses; i++) {
            int ring = i / slots.length;
            int[] s = slots[i % slots.length];
            int push = ring * (house.sizeX() + GAP);
            out.add(new SpawnVillageCommand.Placement(HOUSE, s[0] - push, y, s[1]));
        }
        return out;
    }
}
