package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.villages.Village;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 0 scenarios. All run in virtual time. */
class HamletScenarioTest {

    @Test
    void fiveDaysNobodyStarvesAndTheBakeryBakes() {
        long started = System.nanoTime();
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Testford", 42, 8);
        int initialBread = s.stock(village, "bakery", Buildings.BREAD);

        s.warp(SimTime.days(5));

        assertEquals(0, s.events(Plans.EVENT_STARVING), "nobody starved");
        assertEquals(0, s.events(BasicActivities.EVENT_NO_FOOD), "there was always bread");
        assertTrue(s.stock(village, "bakery", Buildings.BREAD) > 0, "bread in stock");
        assertTrue(initialBread > 0);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue(elapsedMs < 1_000, "5 sim-days took " + elapsedMs + " ms of wall-clock time");
    }

    @Test
    void villagersGetHomesJobsAndAFullDayPlan() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Testford", 7, 8);
        Village v = s.village(village);
        assertEquals(8, v.residents().size());

        long bakers = v.residents().stream().map(r -> s.world().get(r, Villages.VILLAGER)).filter(Villager::employed).count();
        assertEquals(3, bakers, "three bakery slots filled");
        for (EntityId r : v.residents()) {
            Villager villager = s.world().get(r, Villages.VILLAGER);
            assertTrue(!villager.home().isNone(), villager.name() + " has a home");
            List<PlanEntry> entries = s.world().get(r, Plans.PLAN).entries();
            assertEquals(0, entries.get(0).start(), "plan starts at the day start");
            assertEquals(SimTime.TICKS_PER_DAY, entries.get(entries.size() - 1).end(), "plan covers the whole day");
            for (int i = 1; i < entries.size(); i++) assertEquals(entries.get(i - 1).end(), entries.get(i).start(), "contiguous");
        }
    }

    @Test
    void scheduleFollowsTheClock() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Testford", 3, 8);
        EntityId baker = s.village(village).residents().get(0);

        s.warp(SimTime.hours(3)); // 09:00 of day 0
        assertEquals("villagersimulator:bake", Plans.current(s.world(), baker).activity().toString());
        s.warp(SimTime.hours(15) + SimTime.hours(1)); // 01:00 at night
        assertEquals(BasicActivities.SLEEP, Plans.current(s.world(), baker).activity());
    }

    @Test
    void embodimentViewTracksPlanPositions() {
        Scenario s = Scenario.start();
        s.spawnHamlet("Testford", 5, 8);
        s.warp(SimTime.hours(2));
        List<Embodiment> view = s.world().snapshotViews().get(Embodiment.VIEW);
        assertEquals(8, view.size());
        for (Embodiment e : view) {
            PlanEntry entry = Plans.current(s.world(), e.id());
            double[] p = entry.positionAt(s.now());
            assertEquals(p[0], e.x(), 1e-9);
            assertEquals(p[2], e.z(), 1e-9);
        }
    }

    @Test
    void sameCommandGivesSameVillageInDifferentWorlds() {
        // World A has unrelated entities first, so handles differ; the fingerprint must not.
        Scenario a = Scenario.start(SimTime.hours(5));
        a.spawnHamlet("Elsewhere", 99, 4);
        EntityId va = a.spawnHamlet("Testford", 42, 8);
        Scenario b = Scenario.start(SimTime.hours(5));
        EntityId vb = b.spawnHamlet("Testford", 42, 8);

        a.warp(SimTime.days(1));
        b.warp(SimTime.days(1));
        assertEquals(a.fingerprint(va), b.fingerprint(vb));
    }
}
