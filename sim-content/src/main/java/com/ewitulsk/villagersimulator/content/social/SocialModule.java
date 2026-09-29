package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.api.sim.social.Relation;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.PlanHooks;
import com.ewitulsk.villagersimulator.content.plans.PlansModule;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.VillagerCreated;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.content.villages.VillagesModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Relationships, personality, memories, venue interactions and appointments. Built entirely on other modules'
 * extension points and events: nothing in plans or villages knows this module exists.
 */
public final class SocialModule implements SimModule {
    public static final Id ID = VS.id("social");

    /** Company at the venue: friends there make it more attractive, rivals make villagers stay away. */
    public static final Choices.Consideration COMPANY = c -> {
        double score = 0;
        for (EntityId other : Social.present(c.sim(), c.venue())) {
            if (other.equals(c.actor())) continue;
            if (c.sim().relationships().hasBond(c.actor(), other, Bonds.RIVAL)) score -= 35;
            else score += Math.min(15, Math.max(0, c.sim().relationships().friendship(c.actor(), other)) / 5);
        }
        return score;
    };

    /** Sociable villagers value social gains more; loners less. */
    public static final Choices.Consideration SOCIABILITY = c -> {
        double social = c.ad().needs().getOrDefault("social", 0.0);
        return social * (Personality.get(c.sim(), c.actor(), Personality.SOCIABILITY) - 0.5) * 0.8;
    };

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(NeedsModule.ID, BuildingsModule.ID, VillagesModule.ID, PlansModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(Personality.COMPONENT);
        r.component(Memories.COMPONENT);
        r.component(Social.OCCUPANCY);
        r.component(Social.APPOINTMENTS);

        r.subscribe(VillagerCreated.class, (ctx, e) -> {
            Villager v = ctx.get(e.villager(), Villages.VILLAGER);
            Personality.init(ctx, e.villager(), v.seed());
        });
        r.subscribe(PlanHooks.VisitStarted.class, Social::onVisitStarted);
        r.subscribe(PlanHooks.VisitEnded.class, Social::onVisitEnded);
        r.extend(PlanHooks.CONTRIBUTORS, Appointments::contribute);
        r.extend(PlanHooks.CONSIDERATIONS, COMPANY);
        r.extend(PlanHooks.CONSIDERATIONS, SOCIABILITY);
        r.extend(VillageQueries.INSPECT, SocialModule::describe);
        r.view(Social.CONVERSATIONS, Social::conversations);

        r.function(ExpressionFunction.number("trait", List.of(ExprType.STRING), (env, a) -> {
            FloatField f = Personality.FACETS.get(a.string(0, env));
            return f == null || env.actor().isNone() ? 0 : Personality.get(env.sim(), env.actor(), f);
        }).describe("`trait('kindness')`: a personality trait, 0 to 1."));
        r.function(ExpressionFunction.number("mood", List.of(),
                (env, a) -> env.actor().isNone() ? 0 : Memories.mood(env.sim(), env.actor())).describe("The actor's mood: the sum of their memories' current effects."));
        r.function(ExpressionFunction.number("friends_here", List.of(), (env, a) -> count(env.sim(), env.actor(), env.venue(), Bonds.FRIEND)).describe("How many of the actor's friends are at the venue now."));
        r.function(ExpressionFunction.number("rivals_here", List.of(), (env, a) -> count(env.sim(), env.actor(), env.venue(), Bonds.RIVAL)).describe("How many of the actor's rivals are at the venue now."));
    }

    private static int count(SimContext ctx, EntityId actor, EntityId venue, int bond) {
        if (actor.isNone() || venue.isNone() || !ctx.alive(venue)) return 0;
        int n = 0;
        for (EntityId other : Social.present(ctx, venue)) if (ctx.relationships().hasBond(actor, other, bond)) n++;
        return n;
    }

    /** Lines for {@code /vs inspect}. */
    static List<String> describe(SimContext ctx, EntityId v) {
        List<String> out = new ArrayList<>();
        out.add(String.format("Personality: sociability %.2f, kindness %.2f, temper %.2f   Mood %+.1f",
                Personality.get(ctx, v, Personality.SOCIABILITY), Personality.get(ctx, v, Personality.KINDNESS),
                Personality.get(ctx, v, Personality.TEMPER), Memories.mood(ctx, v)));
        List<Relation> rel = ctx.relationships().of(v);
        StringBuilder b = new StringBuilder("Knows " + rel.size() + ":");
        for (int i = 0; i < Math.min(4, rel.size()); i++) {
            Relation r = rel.get(i);
            Villager other = ctx.get(r.other(), Villages.VILLAGER);
            b.append(i == 0 ? " " : "; ").append(other == null ? r.other().toString() : other.name())
                    .append(String.format(" %.0f", r.friendship()));
            if (r.bonds() != 0) b.append(" (").append(Bonds.describe(r.bonds())).append(')');
        }
        out.add(b.toString());
        long now = ctx.now();
        for (Memory m : Memories.get(ctx, v)) {
            if (Math.abs(m.effect(now)) < 1) continue;
            Villager about = m.about().isNone() ? null : ctx.get(m.about(), Villages.VILLAGER);
            out.add(String.format("  remembers %s%s (%+.1f)", m.kind().path().replace('_', ' '),
                    about == null ? "" : " with " + about.name(), m.effect(now)));
        }
        for (Appointment a : Appointments.of(ctx, v)) {
            if (a.resolved() || a.end() < now) continue;
            Villager other = ctx.get(a.other(), Villages.VILLAGER);
            out.add("  meeting " + (other == null ? "?" : other.name()) + " at " + SimTime.describe(a.start()));
        }
        return out;
    }
}
