package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.content.villages.VillageTiers;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Scale tests (namespace {@code villagersimulator_scale}): {@code /vs stress} and {@code /vs profile}. */
@GameTestHolder(ScaleGameTests.NS)
@PrefixGameTestTemplate(false)
public final class ScaleGameTests {
    public static final String NS = "villagersimulator_scale";
    private static final String BATCH = "scale";
    private static final int STRESS = 20_000;

    private ScaleGameTests() {}

    /**
     * {@code /vs stress 20000} creates sim-only villages far away. They go abstract (or coarse, if another test's mock player is online),
     * stay out of the embodiment view, and the server keeps its tick time while the sim runs a sim-hour for them.
     */
    // Unthrottled GameTest ticks are far faster than real time, and 20k villagers take the sim real time to catch
    // up, so this test gets a large tick budget (a few seconds of wall-clock).
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100_000)
    public static void stressVillagersDontSlowTheServer(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        AtomicLong simStart = new AtomicLong();
        long startNanos = System.nanoTime();

        h.startSequence()
                .thenExecute(() -> {
                    // The GameTest server ticks unthrottled, so wait for the sim here rather than in ticks.
                    int before = stressVillagers();
                    server.getCommands().performPrefixedCommand(source, "vs stress " + STRESS);
                    int added = stressVillagers() - before;
                    require(added == STRESS, "stress villagers: " + added);
                })
                .thenWaitUntil(() -> {
                    var summaries = sim().runtime().views().get(VillageTiers.VIEW);
                    long stress = summaries == null ? 0 : summaries.stream().filter(v -> v.name().startsWith("Stress")).count();
                    // Abstract with nobody online; coarse if another test's mock player is around.
                    long far = summaries == null ? 0 : summaries.stream()
                            .filter(v -> v.name().startsWith("Stress") && v.mode() != VillageTiers.DETAILED).count();
                    require(stress > 0 && far == stress, "stress villages abstract or coarse: " + far + " of " + stress);
                })
                .thenExecute(() -> {
                    var embodied = sim().runtime().views().get(Embodiment.VIEW);
                    require(embodied.size() < 100, "stress villagers aren't in the embodiment view: " + embodied.size());
                    simStart.set(sim().runtime().views().time());
                    sim().runtime().advanceTarget(1_000); // a sim-hour for 20k villagers
                })
                .thenWaitUntil(() -> require(sim().runtime().views().time() >= simStart.get() + 1_000, "simulated a sim-hour"))
                .thenIdle(100) // the server's tick-time average covers the last 100 ticks
                .thenExecute(() -> {
                    double mspt = server.getAverageTickTimeNanos() / 1e6;
                    require(mspt < 25, "server tick time " + mspt + " ms with 20k stress villagers");
                    server.getCommands().performPrefixedCommand(source, "vs profile");
                    // Leave the shared sim as we found it for the other tests.
                    server.getCommands().performPrefixedCommand(source, "vs stress clear");
                    require(stressVillagers() == 0, "stress villages cleared");
                    com.mojang.logging.LogUtils.getLogger().info("VS_SCALE stress test took {} ms, {} ms/tick",
                            (System.nanoTime() - startNanos) / 1_000_000, mspt);
                })
                .thenSucceed();
    }

    private static int stressVillagers() {
        try {
            return sim().runtime().query(ctx -> {
                int[] count = {0};
                ctx.forEach(Villages.VILLAGE, (id, v) -> {
                    if (v.name().startsWith("Stress")) count[0] += v.residents().size();
                });
                return count[0];
            }).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("counting stress villagers", e);
        }
    }
}
