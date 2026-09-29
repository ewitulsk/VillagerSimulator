package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.api.sim.social.Relationships;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.PlanHooks;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Social life (docs/DESIGN.md §8). Every building remembers who has been there recently; when a stay ends, the
 * villager interacts with everyone whose stay overlapped. That resolution is the same at every tier, so a T0
 * conversation and a T2 one have the same outcome; the bridge only adds the visuals.
 */
public final class Social {
    /** Recent stays at a building. */
    public static final SparseComponent<List<Visit>> OCCUPANCY = new SparseComponent<>(VS.id("occupancy"), 1, Visit.CODEC.listOf());
    public static final SparseComponent<List<Appointment>> APPOINTMENTS =
            new SparseComponent<>(VS.id("appointments"), 1, Appointment.CODEC.listOf());

    /** Who is talking to whom right now: each pair appears in both directions. */
    public static final ViewKey<Map<EntityId, EntityId>> CONVERSATIONS = new ViewKey<>(VS.id("conversations"));

    public static final Id EVENT_FRIENDS = VS.id("became_friends");
    public static final Id EVENT_RIVALS = VS.id("became_rivals");
    public static final Id EVENT_ARGUMENT = VS.id("argument");
    public static final Id EVENT_APPOINTMENT_MADE = VS.id("appointment_made");
    public static final Id EVENT_APPOINTMENT_KEPT = VS.id("appointment_kept");
    public static final Id EVENT_STOOD_UP = VS.id("stood_up");

    public static final Id MEM_FRIEND = VS.id("made_a_friend");
    public static final Id MEM_RIVAL = VS.id("made_a_rival");
    public static final Id MEM_ARGUMENT = VS.id("argued");
    public static final Id MEM_MET = VS.id("met_a_friend");
    public static final Id MEM_STOOD_UP = VS.id("was_stood_up");

    public static final float FRIEND_AT = 40, UNFRIEND_BELOW = 25, RIVAL_AT = -30, UNRIVAL_ABOVE = -15;
    /** Friends at least this close make appointments. */
    public static final float APPOINTMENT_FRIENDSHIP = 30;
    private static final long KEEP_VISITS = SimTime.hours(3);
    private static final long MIN_OVERLAP = SimTime.minutes(5);
    private static final int MAX_VISITS = 32;

    private Social() {}

    // ------------------------------------------------------------------------------------------------ occupancy

    /** How social a stay is: socialising ads 1, working 0.5, other stays 0.25, sleep 0. */
    static float intensity(SimContext ctx, EntityId venue, Id activity, Optional<String> adId) {
        if (activity.equals(BasicActivities.SLEEP) || activity.equals(BasicActivities.NAP)) return 0;
        BuildingType type = Buildings.type(ctx, venue);
        if (adId.isPresent()) {
            Optional<Advertisement> ad = type.advertisement(adId.get());
            if (ad.isPresent() && ad.get().needs().getOrDefault("social", 0.0) > 0) return 1f;
            return 0.25f;
        }
        if (isWork(type, activity)) return 0.5f;
        return 0.25f;
    }

    static boolean isWork(BuildingType type, Id activity) {
        return type.job().map(j -> j.activity().equals(activity)).orElse(false);
    }

    static List<Visit> visits(SimContext ctx, EntityId venue) {
        List<Visit> list = ctx.get(venue, OCCUPANCY);
        return list == null ? List.of() : list;
    }

    static void onVisitStarted(SimContext ctx, PlanHooks.VisitStarted e) {
        float intensity = intensity(ctx, e.venue(), e.activity(), e.ad());
        if (intensity <= 0) return;
        boolean work = isWork(Buildings.type(ctx, e.venue()), e.activity());
        List<Visit> list = pruned(ctx, e.venue());
        list.add(new Visit(e.villager(), e.start(), e.plannedEnd(), intensity, work, false));
        ctx.set(e.venue(), OCCUPANCY, List.copyOf(list));
    }

    static void onVisitEnded(SimContext ctx, PlanHooks.VisitEnded e) {
        List<Visit> list = pruned(ctx, e.venue());
        Visit mine = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Visit v = list.get(i);
            if (v.villager().equals(e.villager()) && !v.ended()) {
                mine = new Visit(v.villager(), e.from(), e.to(), v.intensity(), v.work(), true);
                list.set(i, mine);
                break;
            }
        }
        if (mine == null) return;
        ctx.set(e.venue(), OCCUPANCY, List.copyOf(list));
        // Interact with everyone whose (already finished) stay overlapped; ongoing stays handle this pair later.
        for (Visit other : list) {
            if (other == mine || !other.ended() || other.villager().equals(e.villager()) || !ctx.alive(other.villager())) continue;
            long overlap = mine.overlap(other.from(), other.to());
            if (overlap < MIN_OVERLAP) continue;
            interact(ctx, e.villager(), other.villager(), overlap, Math.min(mine.intensity(), other.intensity()),
                    mine.work() && other.work());
        }
        Appointments.onVisitEnded(ctx, e, list);
    }

    private static List<Visit> pruned(SimContext ctx, EntityId venue) {
        long now = ctx.now();
        List<Visit> out = new ArrayList<>();
        for (Visit v : visits(ctx, venue)) if (!v.ended() || now - v.to() <= KEEP_VISITS) out.add(v);
        while (out.size() > MAX_VISITS) out.remove(0);
        return out;
    }

    /** Villagers at {@code venue} right now (ongoing stays). */
    public static List<EntityId> present(SimContext ctx, EntityId venue) {
        List<EntityId> out = new ArrayList<>();
        long now = ctx.now();
        for (Visit v : visits(ctx, venue)) if (!v.ended() && v.from() <= now && v.to() > now) out.add(v.villager());
        return out;
    }

    // ------------------------------------------------------------------------------------------------ interactions

    /**
     * Two villagers spent {@code overlap} ticks together. Kindness and similar sociability warm them to each other;
     * temper can turn it into an argument. Crossing thresholds makes or breaks friend and rival bonds.
     */
    static void interact(SimContext ctx, EntityId a, EntityId b, long overlap, float intensity, boolean colleagues) {
        Relationships rel = ctx.relationships();
        double hours = Math.min(2.0, overlap / (double) SimTime.TICKS_PER_HOUR);
        float kindA = Personality.get(ctx, a, Personality.KINDNESS), kindB = Personality.get(ctx, b, Personality.KINDNESS);
        float tempA = Personality.get(ctx, a, Personality.TEMPER), tempB = Personality.get(ctx, b, Personality.TEMPER);
        float socA = Personality.get(ctx, a, Personality.SOCIABILITY), socB = Personality.get(ctx, b, Personality.SOCIABILITY);
        // Seeded from the villagers (not their handles), so the same village behaves the same in any world.
        long seedA = seed(ctx, a), seedB = seed(ctx, b);
        long pairSeed = SimRandom.hash(Math.min(seedA, seedB), Math.max(seedA, seedB));
        double roll = SimRandom.unit(pairSeed, ctx.now(), SimRandom.salt("interaction"));

        double argueChance = (tempA * tempB * 0.6 + (rel.friendship(a, b) < -10 ? 0.2 : 0)) * intensity;
        if (roll < argueChance) {
            float drop = (float) -(4 + 8 * Math.max(tempA, tempB));
            rel.changeFriendship(a, b, drop);
            long record = ctx.events().record(EVENT_ARGUMENT, a, 0, List.of(b), "Argument");
            Memories.add(ctx, a, MEM_ARGUMENT, record, b, -6, SimTime.days(1));
            Memories.add(ctx, b, MEM_ARGUMENT, record, a, -6, SimTime.days(1));
        } else {
            // Per hour together. Lives are short (docs/DESIGN.md §5), so ties form within a few days.
            double warmth = 3 + 4 * (kindA + kindB) / 2 + 2 * (1 - Math.abs(socA - socB));
            float gain = (float) (warmth * hours * intensity * (0.75 + 0.5 * roll));
            rel.changeFriendship(a, b, gain);
        }
        if (colleagues) rel.setBond(a, b, Bonds.COLLEAGUE, true);
        updateBonds(ctx, a, b);
    }

    static long seed(SimContext ctx, EntityId v) {
        var villager = ctx.get(v, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGER);
        return villager == null ? v.raw() : villager.seed();
    }

    static void updateBonds(SimContext ctx, EntityId a, EntityId b) {
        Relationships rel = ctx.relationships();
        float f = rel.friendship(a, b);
        if (f >= FRIEND_AT && !rel.hasBond(a, b, Bonds.FRIEND)) {
            rel.setBond(a, b, Bonds.FRIEND, true);
            long record = ctx.events().record(EVENT_FRIENDS, a, 0, List.of(b), "Became friends");
            Memories.add(ctx, a, MEM_FRIEND, record, b, 8, SimTime.days(2));
            Memories.add(ctx, b, MEM_FRIEND, record, a, 8, SimTime.days(2));
        } else if (f < UNFRIEND_BELOW && rel.hasBond(a, b, Bonds.FRIEND)) {
            rel.setBond(a, b, Bonds.FRIEND, false);
        }
        if (f <= RIVAL_AT && !rel.hasBond(a, b, Bonds.RIVAL)) {
            rel.setBond(a, b, Bonds.RIVAL, true);
            long record = ctx.events().record(EVENT_RIVALS, a, 0, List.of(b), "Became rivals");
            Memories.add(ctx, a, MEM_RIVAL, record, b, -8, SimTime.days(2));
            Memories.add(ctx, b, MEM_RIVAL, record, a, -8, SimTime.days(2));
        } else if (f > UNRIVAL_ABOVE && rel.hasBond(a, b, Bonds.RIVAL)) {
            rel.setBond(a, b, Bonds.RIVAL, false);
        }
    }

    // ------------------------------------------------------------------------------------------------ view

    /** Pairs of villagers socialising at the same venue now, for embodied conversations at T0. */
    static Map<EntityId, EntityId> conversations(SimContext ctx) {
        Map<EntityId, List<EntityId>> byVenue = new TreeMap<>((x, y) -> Integer.compare(x.raw(), y.raw()));
        ctx.forEach(OCCUPANCY, (venue, list) -> {
            for (EntityId v : present(ctx, venue)) {
                PlanEntry e = Plans.current(ctx, v);
                if (e == null || !venue.equals(e.venue()) || e.activity().equals(BasicActivities.TRAVEL)) continue;
                if (intensity(ctx, venue, e.activity(), e.ad()) < 0.5f) continue;
                byVenue.computeIfAbsent(venue, k -> new ArrayList<>()).add(v);
            }
        });
        Map<EntityId, EntityId> out = new LinkedHashMap<>();
        for (List<EntityId> group : byVenue.values()) {
            group.sort((x, y) -> Integer.compare(x.raw(), y.raw()));
            for (int i = 0; i + 1 < group.size(); i += 2) {
                out.put(group.get(i), group.get(i + 1));
                out.put(group.get(i + 1), group.get(i));
            }
        }
        return Map.copyOf(out);
    }
}
