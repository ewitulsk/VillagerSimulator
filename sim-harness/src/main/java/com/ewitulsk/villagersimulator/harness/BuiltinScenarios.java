package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;

import java.util.List;

/** Scenarios that ship with the mod, for {@code /vs scenario run} and as examples for script authors. */
public final class BuiltinScenarios {
    private BuiltinScenarios() {}

    public static List<ScenarioDefinition> all() {
        return List.of(
                new ScenarioDefinition("hamlet_lives", "A hamlet of 8 lives three days: fed, baking, meeting friends", s -> {
                    EntityId village = s.spawnHamlet("Testford", 42, 8);
                    s.warp("3d");
                    s.expect("nobody starves", s.events("villagersimulator:starving") == 0);
                    s.expect("the bakery has bread", ((Scenario) s).stock(village, "bakery", Buildings.BREAD) > 0);
                    s.expect("villagers got to know each other",
                            s.residents(village).stream().anyMatch(r -> s.sim().relationships().count(r) > 0));
                }),
                new ScenarioDefinition("town_districts", "A two-district town of 60: both quarters are busy", s -> {
                    EntityId town = s.spawnTown("Greatford", 11, 60, 0, 0);
                    s.warp("2d");
                    s.expect("nobody starves", s.events("villagersimulator:starving") == 0);
                    s.expect("60 residents", s.residents(town).size() == 60);
                }));
    }
}
