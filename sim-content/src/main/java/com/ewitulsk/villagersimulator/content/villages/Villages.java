package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Village and villager components and helpers. */
public final class Villages {
    public static final SparseComponent<Village> VILLAGE = new SparseComponent<>(VS.id("village"), 1, Village.CODEC);
    public static final SparseComponent<Villager> VILLAGER = new SparseComponent<>(VS.id("villager"), 1, Villager.CODEC);
    public static final SparseComponent<District> DISTRICT = new SparseComponent<>(VS.id("district"), 1, District.CODEC);

    /** Districts for the debug overlay and maps: name, purpose, village name and area. */
    public record DistrictInfo(String name, String purpose, String village, int x0, int z0, int x1, int z1) {}

    public static final com.ewitulsk.villagersimulator.api.sim.view.ViewKey<List<DistrictInfo>> DISTRICTS =
            new com.ewitulsk.villagersimulator.api.sim.view.ViewKey<>(VS.id("districts"));

    /** Event log types. */
    public static final Id EVENT_FOUNDED = VS.id("village_founded");
    public static final Id EVENT_ARRIVED = VS.id("villager_arrived");

    private Villages() {}

    static List<DistrictInfo> districts(SimContext ctx) {
        List<DistrictInfo> out = new ArrayList<>();
        ctx.forEach(DISTRICT, (id, d) -> {
            Village v = ctx.get(d.village(), VILLAGE);
            out.add(new DistrictInfo(d.name(), d.purpose(), v == null ? "?" : v.name(), d.x0(), d.z0(), d.x1(), d.z1()));
        });
        return List.copyOf(out);
    }

    /** The district a villager lives in (their home's), or {@link EntityId#NONE}. */
    public static EntityId districtOf(SimContext ctx, EntityId villager) {
        Villager v = ctx.get(villager, VILLAGER);
        if (v == null || v.home().isNone() || !ctx.alive(v.home())) return EntityId.NONE;
        return ctx.get(v.home(), Buildings.BUILDING).district();
    }

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
