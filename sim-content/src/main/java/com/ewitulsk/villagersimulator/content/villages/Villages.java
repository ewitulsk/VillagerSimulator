package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;

import java.util.Optional;

/** Village and villager components and helpers. */
public final class Villages {
    public static final SparseComponent<Village> VILLAGE = new SparseComponent<>(VS.id("village"), 1, Village.CODEC);
    public static final SparseComponent<Villager> VILLAGER = new SparseComponent<>(VS.id("villager"), 1, Villager.CODEC);

    /** Event log types. */
    public static final Id EVENT_FOUNDED = VS.id("village_founded");
    public static final Id EVENT_ARRIVED = VS.id("villager_arrived");

    private Villages() {}

    /** The first building of the villager's village that offers {@code service}. */
    public static Optional<EntityId> findService(SimContext ctx, EntityId villageId, String service) {
        Village v = ctx.get(villageId, VILLAGE);
        if (v == null) return Optional.empty();
        for (EntityId b : v.buildings()) {
            if (!ctx.alive(b)) continue;
            BuildingType t = Buildings.type(ctx, b);
            if (t.offers(service)) return Optional.of(b);
        }
        return Optional.empty();
    }
}
