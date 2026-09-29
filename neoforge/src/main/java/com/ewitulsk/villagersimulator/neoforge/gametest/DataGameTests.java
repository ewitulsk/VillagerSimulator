package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Data-driven content tests (namespace {@code villagersimulator_data}). */
@GameTestHolder(DataGameTests.NS)
@PrefixGameTestTemplate(false)
public final class DataGameTests {
    public static final String NS = "villagersimulator_data";
    private static final String BATCH = "data";

    private DataGameTests() {}

    /**
     * A building type that only exists in reloaded data (as a datapack would add it) is used by villagers: the data
     * reaches the running sim and the utility AI picks its advertisement up with no code changes.
     */
    // The sim warps on its own thread in real time while the GameTest server runs ticks unthrottled, so waits are
    // generous in ticks (still well under the 1200-tick ceiling in ../ModTesting.md).
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void reloadedBuildingTypeIsUsed(GameTestHelper h) {
        DataSource original = sim().data();
        JsonElement fountain = JsonParser.parseString("""
                {"blueprint": "testpack:fountain", "size": [3, 3, 3],
                 "points": {"wander": [[0, 1, 3], [1, 1, 3], [2, 1, 3]]},
                 "advertisements": [{"id": "splash", "activity": "villagersimulator:socialize", "point": "wander",
                                     "duration": "1h", "needs": {"fun": 60, "social": 20}}]}
                """);
        DataSource withFountain = folder -> {
            Map<Id, JsonElement> out = new TreeMap<>(original.load(folder));
            if (folder.equals("building_types")) out.put(Id.of("testpack", "fountain"), fountain);
            return out;
        };
        sim().reload(withFountain);

        SpawnVillageCommand base = TestSupport.compactVillage(h, "Fountainville", 5, 8, null);
        List<SpawnVillageCommand.Placement> placements = new ArrayList<>(base.placements());
        BlockPos f = h.absolutePos(new BlockPos(10, 0, 10));
        placements.add(new SpawnVillageCommand.Placement(Id.of("testpack", "fountain"), f.getX(), f.getY(), f.getZ()));
        AtomicReference<EntityId> village = new AtomicReference<>();
        AtomicReference<Integer> visits = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong spawnedAt = new java.util.concurrent.atomic.AtomicLong(-1);
        TestSupport.spawn(new SpawnVillageCommand(base.name(), base.seed(), base.centerX(), base.centerY(), base.centerZ(),
                placements, base.villagers(), null), spawnedAt).thenAccept(id -> {
            village.set(id);
            sim().runtime().advanceTarget(SimTime.days(1));
        });

        h.startSequence()
                .thenWaitUntil(() -> require(village.get() != null, "village spawned"))
                .thenWaitUntil(() -> require(sim().runtime().views().time() >= spawnedAt.get() + SimTime.days(1), "warped a day"))
                .thenExecute(() -> sim().runtime().query(ctx -> {
                    var buildings = ctx.get(village.get(), Villages.VILLAGE).buildings();
                    return Buildings.visits(ctx, buildings.get(buildings.size() - 1));
                }).thenAccept(visits::set))
                .thenWaitUntil(() -> require(visits.get() != null, "queried"))
                .thenExecute(() -> {
                    sim().reload(original);
                    require(visits.get() > 0, "the reloaded fountain was used (" + visits.get() + " visits)");
                })
                .thenSucceed();
    }
}
