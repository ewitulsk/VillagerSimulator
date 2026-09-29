package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.api.sim.social.Relation;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.Plan;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.PlanHooks;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Appointments v1 (docs/DESIGN.md §7.1): when a villager plans a day, they may arrange to meet a close friend at the
 * tavern (or the well) the next evening. Both keep a copy; each books it into their own plan when they plan that day,
 * so appointments work across tiers and districts without either villager touching the other's plan.
 */
public final class Appointments {
    private static final long H = SimTime.TICKS_PER_HOUR;

    private Appointments() {}

    public static List<Appointment> of(SimContext ctx, EntityId v) {
        List<Appointment> list = ctx.get(v, Social.APPOINTMENTS);
        return list == null ? List.of() : list;
    }

    private static void set(SimContext ctx, EntityId v, List<Appointment> list) {
        if (list.isEmpty()) ctx.remove(v, Social.APPOINTMENTS);
        else ctx.set(v, Social.APPOINTMENTS, List.copyOf(list));
    }

    private static boolean onDay(Appointment a, long day) {
        return SimTime.day(a.start()) == day;
    }

    /** Plan contributor: book today's appointments, and maybe arrange one for tomorrow. */
    static Plan contribute(SimContext ctx, EntityId v, Plan plan) {
        Villager villager = ctx.get(v, Villages.VILLAGER);
        if (villager == null) return plan;
        long day = plan.day();
        List<Appointment> kept = new ArrayList<>();
        for (Appointment a : of(ctx, v)) if (SimTime.day(a.end()) >= day - 1) kept.add(a);
        set(ctx, v, kept);

        for (Appointment a : kept) {
            if (!onDay(a, day) || a.resolved() || !ctx.alive(a.venue())) continue;
            Optional<Advertisement> ad = Buildings.type(ctx, a.venue()).advertisement(a.ad());
            if (ad.isEmpty()) continue;
            double[] point = Choices.point(ctx, villager, a.venue(), ad.get());
            PlanEntry entry = PlanEntry.stay(a.start(), a.end(), ad.get().activity(), a.venue(), point, Optional.of(a.ad()));
            plan = Plans.commit(plan, entry, ctx.now()).orElse(plan);
        }
        arrange(ctx, v, villager, day + 1);
        return plan;
    }

    private static void arrange(SimContext ctx, EntityId v, Villager villager, long day) {
        if (of(ctx, v).stream().anyMatch(a -> onDay(a, day))) return;
        double chance = 0.2 + 0.5 * Personality.get(ctx, v, Personality.SOCIABILITY);
        if (SimRandom.unit(villager.seed(), day, SimRandom.salt("appointment")) >= chance) return;

        EntityId friend = null;
        // Strongest friend first; ties broken by seed so the choice doesn't depend on entity handles.
        List<Relation> friends = new ArrayList<>(ctx.relationships().of(v));
        friends.sort(java.util.Comparator.comparingDouble((Relation r) -> -r.friendship()).thenComparingLong(r -> Social.seed(ctx, r.other())));
        for (Relation r : friends) {
            if (!r.has(Bonds.FRIEND) || r.friendship() < Social.APPOINTMENT_FRIENDSHIP) continue;
            Villager other = ctx.get(r.other(), Villages.VILLAGER);
            if (other == null || !other.village().equals(villager.village())) continue;
            if (of(ctx, r.other()).stream().anyMatch(a -> onDay(a, day))) continue;
            friend = r.other();
            break;
        }
        if (friend == null) return;

        EntityId venue = Villages.findService(ctx, villager.village(), "drink").orElse(null);
        String ad = "drink";
        if (venue == null) {
            venue = Villages.findService(ctx, villager.village(), "gather").orElse(null);
            ad = "gather";
        }
        if (venue == null || Buildings.type(ctx, venue).advertisement(ad).isEmpty()) return;

        long start = day * SimTime.TICKS_PER_DAY + 12 * H + (long) (SimRandom.unit(villager.seed(), day, SimRandom.salt("meet_at")) * H);
        long end = start + H + H / 2;
        long id = SimRandom.hash(villager.seed(), Social.seed(ctx, friend), day);
        List<Appointment> mine = new ArrayList<>(of(ctx, v));
        mine.add(new Appointment(id, friend, venue, ad, start, end, false));
        set(ctx, v, mine);
        List<Appointment> theirs = new ArrayList<>(of(ctx, friend));
        theirs.add(new Appointment(id, v, venue, ad, start, end, false));
        set(ctx, friend, theirs);
        ctx.events().record(Social.EVENT_APPOINTMENT_MADE, v, 0, List.of(friend),
                "Arranged to meet at " + ctx.get(venue, Buildings.BUILDING).type().path() + " at " + SimTime.describe(start));
    }

    /** At the end of a stay: was it an appointment, and did the friend show up? */
    static void onVisitEnded(SimContext ctx, PlanHooks.VisitEnded e, List<Visit> visits) {
        EntityId v = e.villager();
        List<Appointment> mine = new ArrayList<>(of(ctx, v));
        for (int i = 0; i < mine.size(); i++) {
            Appointment a = mine.get(i);
            if (a.resolved() || !a.venue().equals(e.venue()) || e.to() <= a.start() || e.from() >= a.end()) continue;
            boolean met = false;
            for (Visit other : visits) {
                if (other.villager().equals(a.other()) && other.overlap(e.from(), e.to()) > 0) {
                    met = true;
                    break;
                }
            }
            if (met) {
                long record = ctx.events().record(Social.EVENT_APPOINTMENT_KEPT, v, 0, List.of(a.other()), "Met as arranged");
                Memories.add(ctx, v, Social.MEM_MET, record, a.other(), 5, SimTime.days(1));
                Memories.add(ctx, a.other(), Social.MEM_MET, record, v, 5, SimTime.days(1));
                resolveFor(ctx, a.other(), a.id());
            } else if (e.to() >= a.end() - 1 || ctx.now() >= a.end()) {
                long record = ctx.events().record(Social.EVENT_STOOD_UP, v, 0, List.of(a.other()), "Friend didn't come");
                Memories.add(ctx, v, Social.MEM_STOOD_UP, record, a.other(), -5, SimTime.days(1));
                ctx.relationships().changeFriendship(v, a.other(), -3);
            } else {
                continue;
            }
            mine.set(i, a.resolve());
        }
        set(ctx, v, mine);
    }

    private static void resolveFor(SimContext ctx, EntityId v, long id) {
        List<Appointment> list = new ArrayList<>(of(ctx, v));
        list.replaceAll(a -> a.id() == id ? a.resolve() : a);
        set(ctx, v, list);
    }
}
