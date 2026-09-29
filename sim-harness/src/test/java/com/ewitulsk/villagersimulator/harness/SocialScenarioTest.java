package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.api.sim.social.Bonds;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.social.Memories;
import com.ewitulsk.villagersimulator.content.social.Social;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 2: venues, relationships, memories and appointments. All in virtual time. */
class SocialScenarioTest {

    private static List<EntityId> employed(Scenario s, EntityId village, boolean employed) {
        return s.village(village).residents().stream()
                .filter(r -> s.world().get(r, Villages.VILLAGER).employed() == employed).toList();
    }

    @Test
    void coWorkersBecomeColleaguesAndWarmUp() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Socialton", 42, 8);
        s.warp(SimTime.days(3));
        List<EntityId> bakers = employed(s, village, true);
        assertEquals(3, bakers.size());
        for (int i = 0; i < bakers.size(); i++) {
            for (int j = i + 1; j < bakers.size(); j++) {
                EntityId a = bakers.get(i), b = bakers.get(j);
                assertTrue(s.world().relationships().hasBond(a, b, Bonds.COLLEAGUE), "bakers are colleagues");
                assertTrue(s.world().relationships().friendship(a, b) > 10,
                        "bakers warm up: " + s.world().relationships().friendship(a, b));
            }
        }
    }

    @Test
    void overAWeekFriendshipsFormAndAppointmentsAreKept() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Socialton", 7, 8);
        // Half the village embodied: tiers must not change the outcome.
        List<EntityId> residents = s.village(village).residents();
        s.apply(new ForceTierCommand(residents.subList(0, 4), Tier.T0));
        s.warp(SimTime.days(7));

        assertTrue(s.events(Social.EVENT_FRIENDS) > 0, "friendships formed");
        assertTrue(s.events(Social.EVENT_APPOINTMENT_MADE) > 0, "appointments arranged");
        assertTrue(s.events(Social.EVENT_APPOINTMENT_KEPT) > 0, "appointments kept");
        int known = 0;
        for (EntityId r : residents) known += s.world().relationships().count(r);
        assertTrue(known >= residents.size() * 3, "villagers know each other (" + known + " ties)");
    }

    @Test
    void tiersDoNotChangeWhatHappens() {
        Scenario a = Scenario.start();
        EntityId va = a.spawnHamlet("Tierton", 11, 8);
        a.apply(new ForceTierCommand(a.village(va).residents(), Tier.T0));
        Scenario b = Scenario.start();
        EntityId vb = b.spawnHamlet("Tierton", 11, 8);
        a.warp(SimTime.days(4));
        b.warp(SimTime.days(4));
        for (int i = 0; i < 8; i++) {
            EntityId x = a.village(va).residents().get(i), y = b.village(vb).residents().get(i);
            for (int j = 0; j < 8; j++) {
                EntityId x2 = a.village(va).residents().get(j), y2 = b.village(vb).residents().get(j);
                assertEquals(a.world().relationships().friendship(x, x2), b.world().relationships().friendship(y, y2), 1e-4);
            }
        }
    }

    @Test
    void rivalsAvoidEachOthersVenues() {
        Scenario s = Scenario.start(SimTime.hours(8)); // 14:00, tavern open
        EntityId village = s.spawnHamlet("Grudgeford", 42, 8);
        List<EntityId> idle = employed(s, village, false);
        EntityId me = idle.get(0), rival = idle.get(1);
        for (Needs.NeedType t : Needs.ALL) t.need().set(s.world(), me, 95);
        Needs.SOCIAL.set(s.world(), me, 15);
        Needs.FUN.set(s.world(), me, 20);

        EntityId tavern = s.village(village).buildings().stream()
                .filter(b -> s.world().get(b, Buildings.BUILDING).type().path().equals("tavern")).findFirst().orElseThrow();
        Villager villager = s.world().get(me, Villages.VILLAGER);
        Advertisement drink = Buildings.type(s.world(), tavern).advertisement("drink").orElseThrow();
        double[] from = Plans.positionNow(s.world(), me);
        double before = Choices.score(s.world(), me, villager, tavern, s.world().get(tavern, Buildings.BUILDING), drink, from).score();

        s.world().relationships().changeFriendship(me, rival, -60);
        s.world().relationships().setBond(me, rival, Bonds.RIVAL, true);
        // Put the rival in the tavern right now.
        s.world().publish(new com.ewitulsk.villagersimulator.content.plans.PlanHooks.VisitStarted(rival, tavern,
                drink.activity(), java.util.Optional.of("drink"), s.now(), s.now() + SimTime.hours(1)));
        double after = Choices.score(s.world(), me, villager, tavern, s.world().get(tavern, Buildings.BUILDING), drink, from).score();
        assertEquals(before - 35, after, 1e-6, "a rival at the tavern costs 35");
    }

    @Test
    void memoriesFadeAndInspectShowsSocialLife() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Socialton", 7, 8);
        EntityId v = s.village(village).residents().get(0);
        Memories.add(s.world(), v, Social.MEM_ARGUMENT, 0, EntityId.NONE, -8, SimTime.days(1));
        assertEquals(-8, Memories.mood(s.world(), v), 1e-6);
        s.warp(SimTime.days(1));
        assertEquals(-4, Memories.mood(s.world(), v) - moodFromOthers(s, v), 0.5, "halved after one half-life");

        s.warp(SimTime.days(2));
        List<String> lines = s.world().query(VillageQueries.inspect(v));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Personality:")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Knows ")), lines.toString());
    }

    /** Mood from memories other than the test's own argument memory. */
    private static double moodFromOthers(Scenario s, EntityId v) {
        long now = s.now();
        return Memories.get(s.world(), v).stream().filter(m -> !(m.kind().equals(Social.MEM_ARGUMENT) && m.time() == 0))
                .mapToDouble(m -> m.effect(now)).sum();
    }
}
