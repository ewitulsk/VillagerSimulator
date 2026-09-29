package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.api.sim.module.ExtensionPoint;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.Plan;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only queries used by commands, UI and tests. */
public final class VillageQueries {
    /** Adds lines to {@code /vs inspect} for a villager. */
    @FunctionalInterface
    public interface InspectContributor {
        List<String> describe(SimContext ctx, EntityId villager);
    }

    public static final ExtensionPoint<InspectContributor> INSPECT =
            new ExtensionPoint<>(com.ewitulsk.villagersimulator.content.VS.id("inspect"), InspectContributor.class);

    private VillageQueries() {}

    /** Human-readable state of a villager for {@code /vs inspect}. */
    public static SimQuery<List<String>> inspect(EntityId villager) {
        return ctx -> {
            List<String> out = new ArrayList<>();
            Villager v = ctx.get(villager, Villages.VILLAGER);
            if (v == null) {
                out.add("No such villager " + villager);
                return out;
            }
            Village village = ctx.get(v.village(), Villages.VILLAGE);
            out.add(v.name() + " (" + villager + ") of " + (village == null ? "?" : village.name()));
            out.add("Time: " + SimTime.describe(ctx.now()) + "   Tier: " + CoreComponents.tier(ctx, villager)
                    + (CoreComponents.forced(ctx, villager) ? " (forced)" : ""));
            out.add(String.format("Hunger %.0f   Energy %.0f", Needs.HUNGER.value(ctx, villager), Needs.ENERGY.value(ctx, villager)));
            out.add("Home: " + describe(ctx, v.home()) + "   Work: " + (v.employed() ? describe(ctx, v.workplace()) : "none"));
            Plan plan = ctx.get(villager, Plans.PLAN);
            PlanEntry now = Plans.current(ctx, villager);
            if (plan != null && now != null) {
                int i = ctx.get(villager, Plans.CURSOR_INDEX);
                out.add("Now: " + ctx.activity(now.activity()).label() + " until " + SimTime.describe(now.end()));
                for (int k = i + 1; k < Math.min(plan.entries().size(), i + 5); k++) {
                    PlanEntry e = plan.entries().get(k);
                    out.add("  then " + ctx.activity(e.activity()).label() + " at " + SimTime.describe(e.start()));
                }
            }
            for (InspectContributor c : ctx.extensions(INSPECT)) out.addAll(c.describe(ctx, villager));
            List<EventRecord> events = ctx.events().byActor(villager);
            for (int k = Math.max(0, events.size() - 3); k < events.size(); k++) {
                EventRecord r = events.get(k);
                out.add("  [" + SimTime.describe(r.time()) + "] " + r.type().path() + ": " + r.detail());
            }
            return out;
        };
    }

    /** The building whose footprint contains the block, described for players. */
    public static SimQuery<Optional<String>> buildingAt(int x, int y, int z) {
        return ctx -> {
            List<String> found = new ArrayList<>();
            ctx.forEach(Buildings.BUILDING, (id, b) -> {
                if (!found.isEmpty()) return;
                BuildingType t = ctx.registry(BuildingType.REGISTRY).get(b.type());
                if (x >= b.x() && x < b.x() + t.sizeX() && y >= b.y() && y < b.y() + t.sizeY()
                        && z >= b.z() && z < b.z() + t.sizeZ()) {
                    Village village = ctx.get(b.village(), Villages.VILLAGE);
                    String stock = ctx.get(id, Buildings.STOCK) == null ? "" : "   Stock: " + ctx.get(id, Buildings.STOCK).items();
                    found.add(b.type().path() + " of " + (village == null ? "?" : village.name()) + stock);
                }
            });
            return found.stream().findFirst();
        };
    }

    /** Villages, for {@code /vs village list}. */
    public static SimQuery<List<String>> villages() {
        return ctx -> {
            List<String> out = new ArrayList<>();
            ctx.forEach(Villages.VILLAGE, (id, v) -> out.add(v.name() + " at " + v.x() + " " + v.y() + " " + v.z()
                    + ": " + v.residents().size() + " villagers, " + v.buildings().size() + " buildings"));
            return out;
        };
    }

    /** Residents of every village, for tier forcing and tests. */
    public static SimQuery<List<EntityId>> residents(EntityId village) {
        return ctx -> {
            Village v = ctx.get(village, Villages.VILLAGE);
            return v == null ? List.of() : v.residents();
        };
    }

    /**
     * A hash of a village's state that doesn't depend on entity handles, so two different worlds that simulate the
     * same village can be compared (determinism test in docs/ROADMAP.md Phase 0).
     */
    public static SimQuery<Long> fingerprint(EntityId village) {
        return ctx -> fingerprint(ctx, village);
    }

    public static long fingerprint(SimContext ctx, EntityId villageId) {
        Village village = ctx.get(villageId, Villages.VILLAGE);
        if (village == null) return 0;
        long h = SimRandom.hash(village.seed(), ctx.now());
        for (EntityId b : village.buildings()) {
            Building building = ctx.get(b, Buildings.BUILDING);
            h = SimRandom.hash(h, SimRandom.salt(building.type().toString()), building.x(), building.y(), building.z(),
                    Buildings.stock(ctx, b, Buildings.BREAD));
        }
        for (EntityId r : village.residents()) {
            Villager v = ctx.get(r, Villages.VILLAGER);
            h = SimRandom.hash(h, SimRandom.salt(v.name()), v.seed(),
                    Float.floatToIntBits(Needs.HUNGER.value(ctx, r)), Float.floatToIntBits(Needs.ENERGY.value(ctx, r)),
                    ctx.get(r, Needs.STARVING), ctx.get(r, Plans.CURSOR_INDEX), ctx.get(r, Plans.CURSOR_STARTED));
            Plan plan = ctx.get(r, Plans.PLAN);
            h = SimRandom.hash(h, plan.day());
            for (PlanEntry e : plan.entries()) {
                h = SimRandom.hash(h, e.start(), e.end(), SimRandom.salt(e.activity().toString()),
                        Double.doubleToLongBits(e.tx()), Double.doubleToLongBits(e.tz()));
            }
            h = SimRandom.hash(h, ctx.events().byActor(r).size());
        }
        return h;
    }

    private static String describe(SimContext ctx, EntityId building) {
        if (building.isNone() || !ctx.alive(building)) return "none";
        Building b = ctx.get(building, Buildings.BUILDING);
        return b.type().path() + " at " + b.x() + " " + b.y() + " " + b.z();
    }
}
