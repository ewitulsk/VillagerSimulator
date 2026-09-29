package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.core.data.ClasspathDataSource;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 1: needs drive choices between buildings' advertisements. All in virtual time. */
class ChoicesScenarioTest {

    /** A hamlet at 14:00 on day 0, and one unemployed villager with every need satisfied. */
    private static Scenario afternoon() {
        return Scenario.start(SimTime.hours(8));
    }

    private static EntityId idleVillager(Scenario s, EntityId village) {
        for (EntityId r : s.village(village).residents()) {
            Villager v = s.world().get(r, Villages.VILLAGER);
            if (!v.employed()) {
                for (Needs.NeedType t : Needs.ALL) t.need().set(s.world(), r, 95);
                return r;
            }
        }
        throw new AssertionError("no unemployed villager");
    }

    private static String choice(Scenario s, EntityId v) {
        return Choices.choose(s.world(), v, Plans.positionNow(s.world(), v)).map(o -> o.ad().id()).orElse("(idle)");
    }

    @Test
    void eachNeedLeadsToTheRightBuilding() {
        Scenario s = afternoon();
        EntityId village = s.spawnHamlet("Choiceton", 42, 8);
        EntityId v = idleVillager(s, village);
        assertEquals("(idle)", choice(s, v), "a content villager has nothing worth getting up for");

        Needs.ENERGY.set(s.world(), v, 10);
        assertEquals("nap", choice(s, v), "tired villagers go home to nap");
        Needs.ENERGY.set(s.world(), v, 95);

        Needs.HUNGER.set(s.world(), v, 10);
        assertEquals("eat", choice(s, v), "hungry villagers go to the bakery");
        Needs.HUNGER.set(s.world(), v, 95);

        Needs.SOCIAL.set(s.world(), v, 10);
        Needs.FUN.set(s.world(), v, 20);
        assertEquals("drink", choice(s, v), "lonely, bored villagers go to the tavern");
        Needs.SOCIAL.set(s.world(), v, 95);
        Needs.FUN.set(s.world(), v, 95);

        Needs.HYGIENE.set(s.world(), v, 5);
        assertEquals("wash", choice(s, v), "dirty villagers wash at the well");
    }

    @Test
    void conditionsGateAdvertisements() {
        Scenario s = Scenario.start(SimTime.hours(1)); // 07:00: the tavern opens at 11
        EntityId village = s.spawnHamlet("Choiceton", 42, 8);
        EntityId v = idleVillager(s, village);
        Needs.SOCIAL.set(s.world(), v, 10);
        Needs.FUN.set(s.world(), v, 20);
        assertTrue(!choice(s, v).equals("drink"), "tavern is closed in the morning");
    }

    @Test
    void overSeveralDaysEveryBuildingGetsUsedAndNobodyStarves() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Choiceton", 7, 8);
        s.warp(SimTime.days(3));
        for (EntityId b : s.village(village).buildings()) {
            String type = s.world().get(b, Buildings.BUILDING).type().path();
            assertTrue(Buildings.visits(s.world(), b) > 0, type + " was used");
        }
        assertEquals(0, s.events(Plans.EVENT_STARVING));
        for (EntityId r : s.village(village).residents()) {
            assertTrue(Needs.HUNGER.value(s.world(), r) > 0, "hunger stays above zero");
        }
    }

    @Test
    void washingSlowsHygieneDecayThroughAModifier() {
        Scenario s = afternoon();
        EntityId village = s.spawnHamlet("Choiceton", 42, 8);
        EntityId v = idleVillager(s, village);
        Id decay = Needs.byName("hygiene").decayStat();
        assertEquals(1.0, s.world().stats().value(v, decay));
        Needs.HYGIENE.set(s.world(), v, 5);
        // Warp until the villager has washed.
        for (int i = 0; i < 20 && s.world().stats().modifiers(v).isEmpty(); i++) s.warp(SimTime.minutes(30));
        assertEquals(0.5, s.world().stats().value(v, decay), 1e-9, "washed: hygiene decays at half speed");
    }

    @Test
    void reloadedDataAddsABuildingVillagersUse() {
        Scenario s = Scenario.start(SimTime.hours(8));
        s.world().reloadData(withFountain("\"needs\": {\"fun\": 60, \"social\": 20}"));
        assertTrue(s.world().problems().isEmpty(), s.world().problems().toString());

        List<SpawnVillageCommand.Placement> placements = new ArrayList<>(Scenario.hamletCommand(
                s.world().registry(com.ewitulsk.villagersimulator.content.buildings.BuildingType.REGISTRY),
                "Fountainville", 5, 8, 0, 64, 0, null).placements());
        placements.add(new SpawnVillageCommand.Placement(Id.of("testpack", "fountain"), 2, 64, 30));
        EntityId village = s.spawn(new SpawnVillageCommand("Fountainville", 5, 0, 64, 0, placements, 8, null));
        s.warp(SimTime.days(1));

        EntityId fountain = s.village(village).buildings().get(placements.size() - 1);
        assertTrue(Buildings.visits(s.world(), fountain) > 0, "the datapack's fountain was used");
    }

    @Test
    void badAdvertisementsAreReportedNotFatal() {
        Scenario s = Scenario.start(SimTime.hours(8));
        s.world().reloadData(withFountain("\"needs\": {\"fun\": 60}, \"score\": \"need('fun') + true\""));
        assertTrue(s.world().problems().stream().anyMatch(p -> p.contains("testpack:fountain") && p.contains("needs a number")),
                s.world().problems().toString());
        EntityId village = s.spawnHamlet("Stillworks", 1, 4);
        s.warp(SimTime.days(1));
        assertTrue(s.village(village).residents().size() == 4, "the sim keeps running");
    }

    /** The classpath data plus a {@code testpack:fountain} building type with one advertisement. */
    private static DataSource withFountain(String adBody) {
        DataSource base = ClasspathDataSource.of(Scenario.class);
        JsonElement fountain = JsonParser.parseString("""
                {"blueprint": "testpack:fountain", "size": [3, 3, 3],
                 "points": {"wander": [[0, 1, 3], [1, 1, 3], [2, 1, 3]]},
                 "advertisements": [{"id": "splash", "activity": "villagersimulator:socialize", "point": "wander",
                                     "duration": "1h", %s}]}
                """.formatted(adBody));
        return folder -> {
            Map<Id, JsonElement> out = new TreeMap<>(base.load(folder));
            if (folder.equals("building_types")) out.put(Id.of("testpack", "fountain"), fountain);
            return out;
        };
    }
}
