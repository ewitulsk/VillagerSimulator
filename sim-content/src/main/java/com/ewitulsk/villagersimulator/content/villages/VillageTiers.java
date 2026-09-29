package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.api.sim.core.VillageSummary;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiers are first decided per village, then per villager (docs/ROADMAP.md Phase 6). The bridge looks at thousands
 * of villages instead of a million villagers:
 * <ul>
 *   <li>{@link #DETAILED}: near a player or in loaded chunks. The bridge sets each villager's tier (T0/T1/T2) from
 *       where they are; only these villagers appear in the embodiment view.</li>
 *   <li>{@link #ABSTRACT}: every villager at T2.</li>
 *   <li>{@link #COARSE}: far from every player; every villager at T3, a day at a time, keeping no plan.</li>
 * </ul>
 * Forced tiers are left alone, and villagers who matter to a player never go below T2 (pinning).
 */
public final class VillageTiers {
    public static final int DETAILED = VillageSummary.DETAILED;
    public static final int ABSTRACT = VillageSummary.ABSTRACT;
    public static final int COARSE = VillageSummary.COARSE;

    public static final DenseComponent COMPONENT = new DenseComponent(VS.id("village_tier"), 1);
    /** {@link #DETAILED}, {@link #ABSTRACT} or {@link #COARSE}. New villages start detailed. */
    public static final IntField MODE = COMPONENT.intField("mode");
    /** Distance from the centre to the farthest building, blocks. */
    public static final FloatField RADIUS = COMPONENT.floatField("radius");

    /** What the bridge needs to decide a village's mode: {@link VillageSummary} (public, in sim-api). */
    public static final ViewKey<List<VillageSummary>> VIEW = VillageSummary.VIEW;

    private VillageTiers() {}

    /** Records the village's extent (at founding). */
    static void init(SimContext ctx, EntityId village) {
        Village v = ctx.get(village, Villages.VILLAGE);
        double r = 16;
        for (EntityId b : v.buildings()) {
            Building building = ctx.get(b, Buildings.BUILDING);
            r = Math.max(r, Math.hypot(building.x() - v.x(), building.z() - v.z()) + 16);
        }
        if (!ctx.has(village, COMPONENT)) ctx.add(village, COMPONENT);
        ctx.set(village, RADIUS, (float) r);
    }

    public static int mode(SimContext ctx, EntityId village) {
        return village.isNone() || !ctx.has(village, COMPONENT) ? DETAILED : ctx.get(village, MODE);
    }

    public static boolean coarse(SimContext ctx, EntityId village) {
        return mode(ctx, village) == COARSE;
    }

    /** Whether the villager's village is detailed (so the bridge places and tiers them individually). */
    public static boolean detailed(SimContext ctx, Villager villager) {
        return villager != null && mode(ctx, villager.village()) == DETAILED;
    }

    static List<VillageSummary> summaries(SimContext ctx) {
        List<VillageSummary> out = new ArrayList<>();
        ctx.forEach(Villages.VILLAGE, (id, v) -> out.add(new VillageSummary(id, v.name(), v.x() + 0.5, v.z() + 0.5,
                ctx.has(id, COMPONENT) ? ctx.get(id, RADIUS) : 64, v.residents().size(), mode(ctx, id))));
        return List.copyOf(out);
    }

    /** Sets a village's mode and moves its villagers to match. */
    public record SetMode(EntityId village, int mode) implements SimCommand {
        public SetMode {
            if (mode < DETAILED || mode > COARSE) throw new IllegalArgumentException("Bad village mode " + mode);
        }

        @Override
        public void apply(SimContext ctx) {
            Village v = ctx.get(village, Villages.VILLAGE);
            if (v == null) return;
            if (!ctx.has(village, COMPONENT)) init(ctx, village);
            if (VillageTiers.mode(ctx, village) == mode) return;
            ctx.set(village, MODE, mode);
            for (EntityId r : v.residents()) {
                if (!ctx.alive(r) || !ctx.has(r, CoreComponents.TIER) || CoreComponents.forced(ctx, r)) continue;
                Tier now = CoreComponents.tier(ctx, r);
                Tier want = switch (mode) {
                    case COARSE -> CoreComponents.effective(ctx, r, Tier.T3);
                    case ABSTRACT -> Tier.T2;
                    default -> now == Tier.T3 ? Tier.T2 : now; // the bridge refines detailed villagers
                };
                if (want != now) CoreComponents.setTier(ctx, r, want);
            }
        }
    }
}
