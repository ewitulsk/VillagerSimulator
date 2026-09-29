package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.core.SimWorld;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Time control tests (namespace {@code villagersimulator_time}). */
@GameTestHolder(TimeGameTests.NS)
@PrefixGameTestTemplate(false)
public final class TimeGameTests {
    public static final String NS = "villagersimulator_time";
    private static final String BATCH = "time";

    private TimeGameTests() {}

    /**
     * A village at T2 is warped one sim-day within a few game ticks, and ends in the same state as a headless replay
     * of the same command from the same time (docs/ROADMAP.md Phase 0).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void warpedVillageMatchesHeadlessReplay(GameTestHelper h) {
        SpawnVillageCommand command = TestSupport.compactVillage(h, "Warpford", 42, 8, null);
        AtomicLong spawnedAt = new AtomicLong(-1);
        AtomicReference<EntityId> village = new AtomicReference<>();
        AtomicReference<long[]> result = new AtomicReference<>();
        long startNanos = System.nanoTime();

        TestSupport.spawn(command, spawnedAt).thenAccept(id -> {
            village.set(id);
            sim().runtime().advanceTarget(SimTime.days(1));
        });

        h.startSequence()
                .thenWaitUntil(() -> require(village.get() != null, "village spawned"))
                .thenWaitUntil(() -> require(sim().runtime().views().time() >= spawnedAt.get() + SimTime.days(1), "warped a day"))
                .thenExecute(() -> sim().runtime().query(ctx -> new long[]{ctx.now(), VillageQueries.fingerprint(ctx, village.get())})
                        .thenAccept(result::set))
                .thenWaitUntil(() -> require(result.get() != null, "fingerprint queried"))
                .thenExecute(() -> {
                    long[] inGame = result.get();
                    SimWorld headless = sim().newWorld(spawnedAt.get());
                    AtomicReference<EntityId> replayed = new AtomicReference<>();
                    headless.apply(command.withCallback(replayed::set));
                    headless.advanceTo(inGame[0]);
                    long expected = VillageQueries.fingerprint(headless, replayed.get());
                    require(expected == inGame[1], "in-game village matches headless replay");
                    // The GameTest server runs ticks unthrottled, so measure wall-clock time: a sim-day warp must not
                    // cost anything like a real day (20 minutes).
                    long ms = (System.nanoTime() - startNanos) / 1_000_000;
                    require(ms < 5_000, "a sim-day warp took " + ms + " ms");
                })
                .thenSucceed();
    }
}
