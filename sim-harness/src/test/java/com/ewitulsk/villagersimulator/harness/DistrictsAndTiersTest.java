package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.SetTiersCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.Plan;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.players.PlayerCommands;
import com.ewitulsk.villagersimulator.content.villages.District;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 5: shards, windows, districts, roads, T1/T3 tiers and attention pinning. All in virtual time. */
class DistrictsAndTiersTest {

    private static EntityId join(Scenario s, String name) {
        AtomicReference<EntityId> p = new AtomicReference<>();
        s.apply(new PlayerCommands.Join("uuid-" + name, name, p::set));
        return p.get();
    }

    /** Three villages and a player befriending one villager: runs on 1 and 4 threads must end identical. */
    @Test
    void theSameWorldGivesTheSameStateOnAnyNumberOfThreads() {
        long[] hashes = new long[2];
        int[] threads = {1, 4};
        for (int k = 0; k < 2; k++) {
            Scenario s = Scenario.start(0, threads[k]);
            EntityId a = s.spawnHamlet("Ashby", 1, 8);
            s.spawnHamlet("Birchley", 2, 8);
            s.spawnTown("Cobbleton", 3, 30, 400, 0);
            EntityId me = join(s, "Alex");
            s.world().relationships().changeFriendship(s.village(a).residents().get(0), me, 60);
            s.warp(SimTime.days(3));
            hashes[k] = s.world().stateHash();
            assertTrue(s.world().shardCount() >= 4, "one shard per village plus the world");
            s.world().close();
        }
        assertEquals(hashes[0], hashes[1], "same state on 1 and 4 threads");
    }

    @Test
    void villagesGetTheirOwnShards() {
        Scenario s = Scenario.start();
        EntityId a = s.spawnHamlet("Ashby", 1, 4);
        EntityId b = s.spawnHamlet("Birchley", 2, 4);
        int sa = s.world().shardOf(a), sb = s.world().shardOf(b);
        assertTrue(sa != 0 && sb != 0 && sa != sb);
        for (EntityId r : s.village(a).residents()) assertEquals(sa, s.world().shardOf(r));
        for (EntityId x : s.village(b).buildings()) assertEquals(sb, s.world().shardOf(x));
    }

    @Test
    void aTwoDistrictTownOfSixtyLivesWell() {
        Scenario s = Scenario.start();
        EntityId town = s.spawnTown("Greatford", 11, 60, 0, 0);
        List<EntityId> districts = s.village(town).districts();
        assertEquals(2, districts.size());
        Map<EntityId, Integer> buildingsPer = new HashMap<>();
        for (EntityId b : s.village(town).buildings()) {
            Building building = s.world().get(b, Buildings.BUILDING);
            assertTrue(districts.contains(building.district()), "every building is in a district");
            buildingsPer.merge(building.district(), 1, Integer::sum);
        }
        assertEquals(2, buildingsPer.size(), "both districts have buildings");
        District market = s.world().get(districts.get(0), Villages.DISTRICT);
        assertEquals("market", market.purpose());

        s.warp(SimTime.days(3));
        assertEquals(0, s.events(Plans.EVENT_STARVING), "nobody starves in the town");
        // Old Town residents walk over to the Market Quarter.
        long crossings = s.village(town).buildings().stream()
                .filter(b -> s.world().get(b, Buildings.BUILDING).district().equals(districts.get(0)))
                .mapToInt(b -> Buildings.visits(s.world(), b)).sum();
        assertTrue(crossings > 60, "the market is busy: " + crossings + " visits");
    }

    @Test
    void walksFollowTheRoads() {
        Scenario s = Scenario.start();
        EntityId town = s.spawnTown("Greatford", 11, 60, 0, 0);
        int onRoads = 0;
        for (int i = 0; i < 40 && onRoads == 0; i++) {
            s.warp(SimTime.minutes(15));
            for (EntityId r : s.village(town).residents()) {
                PlanEntry e = Plans.current(s.world(), r);
                if (e == null || !e.activity().equals(BasicActivities.TRAVEL) || e.via().isEmpty()) continue;
                double straight = Math.hypot(e.tx() - e.fx(), e.tz() - e.fz());
                assertTrue(e.length() >= straight - 1e-6, "a road route is never shorter than a straight line");
                double[] mid = e.positionAt((e.start() + e.end()) / 2);
                assertTrue(Double.isFinite(mid[0]) && Double.isFinite(mid[2]));
                onRoads++;
            }
        }
        assertTrue(onRoads > 0, "someone walked along the roads");
    }

    /** Forcing villagers between T0 and T2 changes nothing: tiers only change how the sim is shown. */
    @Test
    void t0AndT2GiveIdenticalResults() {
        Scenario a = Scenario.start();
        EntityId va = a.spawnHamlet("Tierton", 11, 8);
        Scenario b = Scenario.start();
        EntityId vb = b.spawnHamlet("Tierton", 11, 8);
        for (int i = 0; i < 12; i++) {
            a.apply(new ForceTierCommand(a.village(va).residents(), i % 2 == 0 ? Tier.T0 : Tier.T2));
            a.warp(SimTime.hours(3));
            b.warp(SimTime.hours(3));
        }
        assertEquals(b.fingerprint(vb), a.fingerprint(va));
    }

    @Test
    void farAwayVillagersLiveCoarseDaysAndComeBackCleanly() {
        Scenario s = Scenario.start(SimTime.hours(4));
        EntityId village = s.spawnHamlet("Farholm", 5, 8);
        List<EntityId> residents = s.village(village).residents();
        Map<EntityId, Tier> far = new HashMap<>();
        for (EntityId r : residents) far.put(r, Tier.T3);
        s.apply(new SetTiersCommand(far));
        s.warp(SimTime.days(3));
        for (EntityId r : residents) {
            assertEquals(Tier.T3, CoreComponents.tier(s.world(), r));
            assertTrue(Needs.HUNGER.value(s.world(), r) > 0, "fed during coarse days");
        }
        assertEquals(0, s.events(Plans.EVENT_STARVING));

        Map<EntityId, Tier> near = new HashMap<>();
        for (EntityId r : residents) near.put(r, Tier.T2);
        s.apply(new SetTiersCommand(near));
        for (EntityId r : residents) {
            PlanEntry e = Plans.current(s.world(), r);
            Plan plan = s.world().get(r, Plans.PLAN);
            assertEquals(SimTime.day(s.now()), plan.day(), "plan is for today");
            assertTrue(e.contains(s.now()) || e.activity().equals(BasicActivities.TRAVEL), "resumed at the right entry");
        }
        int visitsBefore = s.village(village).buildings().stream().mapToInt(b -> Buildings.visits(s.world(), b)).sum();
        s.warp(SimTime.days(1));
        int visitsAfter = s.village(village).buildings().stream().mapToInt(b -> Buildings.visits(s.world(), b)).sum();
        assertTrue(visitsAfter > visitsBefore, "normal life resumes");
        assertEquals(0, s.events(Plans.EVENT_STARVING));
    }

    @Test
    void villagersWhoMatterToAPlayerArePinned() {
        Scenario s = Scenario.start(SimTime.hours(4));
        EntityId village = s.spawnHamlet("Pinfold", 5, 8);
        EntityId friend = s.village(village).residents().get(0), other = s.village(village).residents().get(1);
        EntityId me = join(s, "Alex");
        s.world().relationships().changeFriendship(friend, me, 50);
        s.warp(SimTime.days(1)); // pins are refreshed when a day is planned
        assertTrue(CoreComponents.pinned(s.world(), friend));
        s.apply(new SetTiersCommand(Map.of(friend, Tier.T3, other, Tier.T3)));
        assertEquals(Tier.T2, CoreComponents.tier(s.world(), friend), "pinned villagers keep full detail");
        assertEquals(Tier.T3, CoreComponents.tier(s.world(), other));
    }

    /** Gossip in a village shard changes the player's side of edges (world shard): deferred, but kept symmetric. */
    @Test
    void crossShardWritesStaySymmetric() {
        Scenario s = Scenario.start(0, 4);
        EntityId village = s.spawnHamlet("Rumourford", 5, 8);
        EntityId me = join(s, "Alex");
        s.world().relationships().changeFriendship(s.village(village).residents().get(0), me, 80);
        s.warp(SimTime.days(2));
        Set<EntityId> heard = new HashSet<>();
        for (EntityId r : s.village(village).residents()) {
            assertEquals(s.world().relationships().friendship(r, me), s.world().relationships().friendship(me, r), 1e-4);
            if (s.world().relationships().friendship(r, me) > 0) heard.add(r);
        }
        assertTrue(heard.size() > 2, "gossip spread across shards");
        s.world().close();
    }

    /** Phase 6: a whole village goes coarse at once, keeps no plans, stays fed, and comes back to today's plan. */
    @Test
    void wholeVillagesGoCoarseAndComeBack() {
        Scenario s = Scenario.start(SimTime.hours(4));
        EntityId village = s.spawnHamlet("Farford", 9, 8);
        List<EntityId> residents = s.village(village).residents();
        EntityId friend = residents.get(0);
        EntityId me = join(s, "Alex");
        s.world().relationships().changeFriendship(friend, me, 50);
        s.warp(SimTime.days(1)); // pins refresh when days are planned
        s.apply(new com.ewitulsk.villagersimulator.content.villages.VillageTiers.SetMode(village, com.ewitulsk.villagersimulator.content.villages.VillageTiers.COARSE));
        assertTrue(com.ewitulsk.villagersimulator.content.villages.VillageTiers.coarse(s.world(), village));
        assertEquals(Tier.T2, CoreComponents.tier(s.world(), friend), "the player's friend stays detailed");
        for (EntityId r : residents.subList(1, residents.size())) {
            assertEquals(Tier.T3, CoreComponents.tier(s.world(), r));
            assertEquals(null, s.world().get(r, Plans.PLAN), "coarse villagers keep no plan");
        }
        Set<EntityId> embodied = new HashSet<>();
        for (var e : s.world().snapshotViews().get(com.ewitulsk.villagersimulator.api.sim.core.Embodiment.VIEW)) embodied.add(e.id());
        assertTrue(embodied.isEmpty(), "only detailed villages are in the embodiment view");

        s.warp(SimTime.days(3));
        assertEquals(0, s.events(Plans.EVENT_STARVING), "fed while coarse");
        s.apply(new com.ewitulsk.villagersimulator.content.villages.VillageTiers.SetMode(village, com.ewitulsk.villagersimulator.content.villages.VillageTiers.ABSTRACT));
        for (EntityId r : residents) {
            assertEquals(Tier.T2, CoreComponents.tier(s.world(), r));
            assertEquals(SimTime.day(s.now()), s.world().get(r, Plans.PLAN).day(), "back on today's plan");
        }
        s.apply(new com.ewitulsk.villagersimulator.content.villages.VillageTiers.SetMode(village, com.ewitulsk.villagersimulator.content.villages.VillageTiers.DETAILED));
        assertEquals(residents.size(), s.world().snapshotViews().get(com.ewitulsk.villagersimulator.api.sim.core.Embodiment.VIEW).size());
        var summary = s.world().snapshotViews().get(com.ewitulsk.villagersimulator.content.villages.VillageTiers.VIEW);
        assertEquals(1, summary.size());
        assertTrue(summary.get(0).radius() > 16);
    }
}
