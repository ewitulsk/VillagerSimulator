package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.content.VS;

import java.util.List;

/** Building components and helpers. */
public final class Buildings {
    public static final SparseComponent<Building> BUILDING = new SparseComponent<>(VS.id("building"), 1, Building.CODEC);
    public static final SparseComponent<Stock> STOCK = new SparseComponent<>(VS.id("stock"), 1, Stock.CODEC);

    public static final String BREAD = "bread";
    /** Bakers stop baking once the bakery holds this much bread. */
    public static final int MAX_BREAD = 64;

    private Buildings() {}

    public static BuildingType type(SimContext ctx, EntityId building) {
        return ctx.registry(BuildingType.REGISTRY).get(ctx.get(building, BUILDING).type());
    }

    /** World position (standing spot, block centre) of point {@code index} of {@code kind}, wrapping the index. */
    public static double[] point(SimContext ctx, EntityId building, String kind, int index) {
        Building b = ctx.get(building, BUILDING);
        List<List<Integer>> points = type(ctx, building).points(kind);
        if (points.isEmpty()) {
            // No such point: stand at the middle of the footprint's front edge.
            BuildingType t = type(ctx, building);
            return new double[]{b.x() + t.sizeX() / 2 + 0.5, b.y() + 1, b.z() + t.sizeZ() + 0.5};
        }
        List<Integer> p = points.get(Math.floorMod(index, points.size()));
        return new double[]{b.x() + p.get(0) + 0.5, b.y() + p.get(1), b.z() + p.get(2) + 0.5};
    }

    public static int pointCount(SimContext ctx, EntityId building, String kind) {
        return type(ctx, building).points(kind).size();
    }

    public static int stock(SimContext ctx, EntityId building, String good) {
        Stock s = ctx.get(building, STOCK);
        return s == null ? 0 : s.count(good);
    }

    public static void setStock(SimContext ctx, EntityId building, String good, int count) {
        Stock s = ctx.get(building, STOCK);
        ctx.set(building, STOCK, (s == null ? new Stock(java.util.Map.of()) : s).with(good, count));
    }
}
