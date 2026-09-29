package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioResult;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/**
 * KubeJS integration (namespace {@code villagersimulator_kubejs}), driven by
 * {@code neoforge/src/gametest/kubejs/server_scripts/villagersimulator_test.js}: a script's scenario runs, and a
 * script reacts to a sim event by reading a view and sending a command back.
 */
@GameTestHolder(KubeJsGameTests.NS)
@PrefixGameTestTemplate(false)
public final class KubeJsGameTests {
    public static final String NS = "villagersimulator_kubejs";
    private static final String BATCH = "kubejs";

    private KubeJsGameTests() {}

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100_000)
    public static void aKubeJsScenarioRuns(GameTestHelper h) {
        AtomicReference<CompletableFuture<ScenarioResult>> run = new AtomicReference<>();
        h.startSequence()
                .thenWaitUntil(() -> require(sim().scenarios().find("kubejs_hamlet").isPresent(), "the script's scenario is registered"))
                .thenExecute(() -> run.set(sim().runScenario("kubejs_hamlet")))
                .thenWaitUntil(() -> require(run.get().isDone(), "scenario finished"))
                .thenExecute(() -> {
                    ScenarioResult r = run.get().join();
                    require(r.passed(), String.join(" / ", r.lines()));
                    require(r.checks().size() == 2, "both script checks ran");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100_000)
    public static void aKubeJsScriptReactsToSimEvents(GameTestHelper h) {
        AtomicReference<List<EventRecord>> hello = new AtomicReference<>(List.of());
        h.startSequence()
                .thenExecute(() -> sim().runtime().submit(TestSupport.compactVillage(h, "Scripton", 5, 8, null)))
                .thenWaitUntil(() -> {
                    sim().runtime().query(ctx -> List.copyOf(ctx.events().ofType(Id.parse("kubejs:hello")))).thenAccept(hello::set);
                    require(!hello.get().isEmpty(), "the script answered the founding");
                })
                .thenExecute(() -> require(hello.get().get(0).detail().equals("Hello Scripton of 8"),
                        "the script read the village view: " + hello.get().get(0).detail()))
                .thenSucceed();
    }
}
