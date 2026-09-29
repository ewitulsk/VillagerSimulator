package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.content.VS;

import java.util.List;
import java.util.Map;

/**
 * Personality facets (docs/DESIGN.md §6, Dwarf Fortress-style), each 0-1. Phase 2's first set:
 * <ul>
 *   <li>{@code sociability}: how much company is worth to them</li>
 *   <li>{@code kindness}: how warmly they treat others</li>
 *   <li>{@code temper}: how easily they argue</li>
 * </ul>
 */
public final class Personality {
    public static final DenseComponent COMPONENT = new DenseComponent(VS.id("personality"), 1);
    public static final FloatField SOCIABILITY = COMPONENT.floatField("sociability");
    public static final FloatField KINDNESS = COMPONENT.floatField("kindness");
    public static final FloatField TEMPER = COMPONENT.floatField("temper");

    public static final Map<String, FloatField> FACETS = Map.of(
            "sociability", SOCIABILITY, "kindness", KINDNESS, "temper", TEMPER);
    public static final List<String> ORDER = List.of("sociability", "kindness", "temper");

    private Personality() {}

    public static void init(SimContext ctx, EntityId v, long seed) {
        ctx.add(v, COMPONENT);
        for (int i = 0; i < ORDER.size(); i++) {
            // Mean of two draws: most villagers are moderate, a few are extreme.
            double a = SimRandom.unit(seed, i, SimRandom.salt("facet_a"));
            double b = SimRandom.unit(seed, i, SimRandom.salt("facet_b"));
            ctx.set(v, FACETS.get(ORDER.get(i)), (float) ((a + b) / 2));
        }
    }

    /** A facet's value, 0.5 if the villager has no personality yet. */
    public static float get(SimContext ctx, EntityId v, FloatField facet) {
        return !v.isNone() && ctx.has(v, COMPONENT) ? ctx.get(v, facet) : 0.5f;
    }
}
