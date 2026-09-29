package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled village layouts for Phase 0. The village planner (Phase 15) replaces these. Shared by the
 * {@code /vs village spawn} command and headless scenarios, so both build the same village.
 */
public final class VillageLayouts {
    public static final Id WELL = VS.id("well");
    public static final Id BAKERY = VS.id("bakery");
    public static final Id HOUSE = VS.id("house");
    private static final int GAP = 4;

    private VillageLayouts() {}

    /**
     * A hamlet around a well at {@code (cx, y, cz)}: the bakery to the east and enough houses for {@code villagers}
     * spread west, north and south. Placements use {@code y} for every building; in game each is then dropped onto
     * the terrain.
     */
    public static List<SpawnVillageCommand.Placement> hamlet(SimRegistry<BuildingType> types, int cx, int y, int cz, int villagers) {
        BuildingType well = types.get(WELL);
        BuildingType bakery = types.get(BAKERY);
        BuildingType house = types.get(HOUSE);
        List<SpawnVillageCommand.Placement> out = new ArrayList<>();

        int wellX = cx - well.sizeX() / 2;
        int wellZ = cz - well.sizeZ() / 2;
        out.add(new SpawnVillageCommand.Placement(WELL, wellX, y, wellZ));
        out.add(new SpawnVillageCommand.Placement(BAKERY, wellX + well.sizeX() + GAP, y, cz - bakery.sizeZ() / 2));

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
