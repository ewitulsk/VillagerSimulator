package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.Need;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.villages.Village;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility AI (docs/DESIGN.md §9.1): every advertisement a villager can use is scored by a list of
 * {@link Consideration}s, and the best one wins. All randomness is a hash of the villager, time and option, so
 * choices are deterministic.
 */
public final class Choices {
    /** Options scoring below this aren't worth getting up for; the villager just idles. */
    public static final double MIN_SCORE = 20;
    /** Score lost per block walked. */
    public static final double DISTANCE_COST = 0.6;
    /** Spread of the per-option random bonus, so equal options don't always resolve the same way. */
    public static final double VARIETY = 4;

    /** One candidate: advertisement {@code ad} at {@code venue}. */
    public record Option(EntityId venue, Advertisement ad, double[] point, double score) {}

    /** What a consideration sees when scoring one option. */
    public record Candidate(SimContext sim, EntityId actor, Villager villager, EntityId venue, Building building,
                            Advertisement ad, double[] from, double[] point, ExprEnv env) {}

    /** Scores one aspect of an option. Considerations are summed. */
    @FunctionalInterface
    public interface Consideration {
        double score(Candidate c);
    }

    /** Need gains, weighted steeply by how badly each need is felt: a starving villager values food far above fun. */
    public static final Consideration NEEDS = c -> {
        double total = 0;
        for (Map.Entry<String, Double> gain : c.ad().needs().entrySet()) {
            Needs.NeedType t = Needs.byName(gain.getKey());
            if (t == null) continue;
            double deficit = Need.MAX - t.need().value(c.sim(), c.actor());
            double urgency = deficit / Need.MAX;
            double useful = gain.getValue() > 0 ? Math.min(gain.getValue(), deficit) : gain.getValue();
            total += useful * (1 + 12 * urgency * urgency * urgency) * t.priority();
        }
        return total;
    };

    /** A need at or below this is critical. */
    public static final float CRITICAL = 25;
    /** An option has to restore at least this much of a critical need to count as helping. */
    public static final double REAL_HELP = 20;

    /**
     * While a need is critical, anything that doesn't really help the most pressing one is nearly out of the
     * question. Only the most pressing counts (Maslow: a starving, lonely villager eats first), weighted by priority.
     */
    public static final Consideration URGENT = c -> {
        Needs.NeedType worst = null;
        double worstUrgency = 0;
        for (Needs.NeedType t : Needs.ALL) {
            double value = t.need().value(c.sim(), c.actor());
            if (value > CRITICAL) continue;
            double urgency = (Need.MAX - value) * t.priority();
            if (urgency > worstUrgency) {
                worstUrgency = urgency;
                worst = t;
            }
        }
        if (worst == null || c.ad().needs().getOrDefault(worst.name(), 0.0) >= REAL_HELP) return 0;
        return -150 * worst.priority();
    };

    public static final Consideration DISTANCE = c -> -DISTANCE_COST * distance(c.from(), c.point());

    /** The advertisement's own score expression, if any. */
    public static final Consideration AD_SCORE = c -> c.ad().score()
            .map(s -> c.sim().logic().expression(s, ExprType.NUMBER).number(c.env()))
            .orElse(0.0);

    public static final Consideration VARIETY_BONUS = c -> VARIETY * SimRandom.unit(c.villager().seed(), c.sim().now(),
            SimRandom.salt(c.ad().id()), c.building().x(), c.building().y(), c.building().z());

    /** The base game's considerations. */
    public static final List<Consideration> CONSIDERATIONS = List.of(NEEDS, URGENT, DISTANCE, AD_SCORE, VARIETY_BONUS);

    private Choices() {}

    /** The best option for the villager at {@code from}, or empty if nothing beats {@link #MIN_SCORE}. */
    public static Optional<Option> choose(SimContext ctx, EntityId actor, double[] from) {
        Villager villager = ctx.get(actor, Villages.VILLAGER);
        Village village = villager == null ? null : ctx.get(villager.village(), Villages.VILLAGE);
        if (village == null) return Optional.empty();
        Option best = null;
        for (EntityId venue : village.buildings()) {
            if (!ctx.alive(venue)) continue;
            Building building = ctx.get(venue, Buildings.BUILDING);
            BuildingType type = ctx.registry(BuildingType.REGISTRY).find(building.type()).orElse(null);
            if (type == null) continue;
            for (Advertisement ad : type.advertisements()) {
                Option o = score(ctx, actor, villager, venue, building, ad, from);
                if (o != null && (best == null || o.score() > best.score())) best = o;
            }
        }
        return best != null && best.score() >= MIN_SCORE ? Optional.of(best) : Optional.empty();
    }

    /** Scores one option, or returns {@code null} if the villager can't use it right now. */
    public static Option score(SimContext ctx, EntityId actor, Villager villager, EntityId venue, Building building,
                               Advertisement ad, double[] from) {
        if (BuildingsModule.check(ctx, ad) != null) return null; // reported by the validator
        ExprEnv env = ExprEnv.of(ctx, actor, venue, from);
        try {
            if (ad.condition().isPresent() && !ctx.logic().condition(ad.condition().get()).test(env)) return null;
        } catch (ExpressionException e) {
            return null;
        }
        for (Map.Entry<String, Integer> need : ad.consumes().entrySet()) {
            if (Buildings.stock(ctx, venue, need.getKey()) < need.getValue()) return null;
        }
        double[] point = point(ctx, villager, venue, ad);
        Candidate c = new Candidate(ctx, actor, villager, venue, building, ad, from, point, env);
        double total = 0;
        for (Consideration k : CONSIDERATIONS) total += k.score(c);
        for (Consideration k : ctx.extensions(PlanHooks.CONSIDERATIONS)) total += k.score(c);
        return new Option(venue, ad, point, total);
    }

    /** Where the villager stands for the advertisement. {@code bed} at their own home is their own bed. */
    public static double[] point(SimContext ctx, Villager villager, EntityId venue, Advertisement ad) {
        if (ad.point().equals(BuildingType.BED) && venue.equals(villager.home())) {
            return Buildings.point(ctx, venue, BuildingType.BED, villager.bed());
        }
        int n = Math.max(1, Buildings.pointCount(ctx, venue, ad.point()));
        int index = Math.floorMod(villager.number() + (int) SimRandom.salt(ad.id()), n);
        return Buildings.point(ctx, venue, ad.point(), index);
    }

    static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
