package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;
import com.ewitulsk.villagersimulator.content.villages.Appearance;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.VillagerCreated;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.content.villages.VillagesModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Daily plans, the basic activities, and the embodiment view the Minecraft bridge spawns puppets from. */
public final class PlansModule implements SimModule {
    public static final Id ID = VS.id("plans");

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(NeedsModule.ID, BuildingsModule.ID, VillagesModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(Plans.PLAN);
        r.component(Plans.CURSOR);
        r.component(Plans.POSITION);
        r.task(Plans.ADVANCE);
        r.task(Plans.DAY_BATCH);
        r.task(Plans.DAY_BATCH_WORKER);
        r.subscribe(com.ewitulsk.villagersimulator.api.sim.core.TierChanged.class, (ctx, e) -> {
            if (e.to() == com.ewitulsk.villagersimulator.api.sim.core.Tier.T3) Plans.goCoarse(ctx, e.entity());
            else if (e.from() == com.ewitulsk.villagersimulator.api.sim.core.Tier.T3) Plans.resume(ctx, e.entity());
        });
        BasicActivities.all().forEach(r::activity);
        r.subscribe(VillagerCreated.class, (ctx, e) -> Plans.start(ctx, e.villager()));
        r.view(Embodiment.VIEW, PlansModule::embodiments);
    }

    private static List<Embodiment> embodiments(SimContext ctx) {
        List<Embodiment> out = new ArrayList<>();
        long now = ctx.now();
        ctx.forEach(Plans.PLAN, (v, plan) -> {
            PlanEntry e = Plans.current(ctx, v);
            Villager id = ctx.get(v, Villages.VILLAGER);
            // Only detailed villages: far-away villagers never become puppets, and at a million villagers
            // listing them all here every publish would dominate (docs/ROADMAP.md Phase 6).
            if (e == null || id == null || !com.ewitulsk.villagersimulator.content.villages.VillageTiers.detailed(ctx, id)) return;
            double[] p = e.positionAt(now);
            Activity a = ctx.activity(e.activity());
            // Head for the next road waypoint; the rest of the route is for the debug overlay.
            double[][] path = e.path();
            int next = e.nextPointAt(now);
            double[] route = new double[(path.length - next) * 3];
            for (int i = next; i < path.length; i++) System.arraycopy(path[i], 0, route, (i - next) * 3, 3);
            double[] t = path[next];
            out.add(new Embodiment(v, id.name(), p[0], p[1], p[2], t[0], t[1], t[2], a.id(), a.label(),
                    a.embodied(), CoreComponents.tier(ctx, v), CoreComponents.forced(ctx, v), Appearance.genes(ctx, v), route));
        });
        return List.copyOf(out);
    }
}
