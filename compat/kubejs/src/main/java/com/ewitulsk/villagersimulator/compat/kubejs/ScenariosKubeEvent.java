package com.ewitulsk.villagersimulator.compat.kubejs;

import com.ewitulsk.villagersimulator.api.mod.VillagerSimApi;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import com.ewitulsk.villagersimulator.api.sim.scenario.SimScenario;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@code VillagerSimEvents.scenarios(e => e.add(name, description, s => ...))}: scenarios written in JavaScript. The
 * body gets the scenario DSL ({@link SimScenario}) and runs in a world of its own, in virtual time.
 *
 * <pre>{@code
 * VillagerSimEvents.scenarios(e => {
 *   e.add('my_hamlet', 'A hamlet lives two days', s => {
 *     const village = s.spawnHamlet('Scriptford', 7, 8)
 *     s.warp('2d')
 *     s.expect('nobody starves', s.events('villagersimulator:starving') == 0)
 *   })
 * })
 * }</pre>
 */
public final class ScenariosKubeEvent implements KubeEvent {
    private final List<ScenarioDefinition> scenarios = new ArrayList<>();

    public void add(String name, String description, Consumer<SimScenario> body) {
        scenarios.add(new ScenarioDefinition(name, description, onServerThread(body)));
    }

    public void add(String name, Consumer<SimScenario> body) {
        add(name, "", body);
    }

    List<ScenarioDefinition> scenarios() {
        return List.copyOf(scenarios);
    }

    /**
     * Scenarios run on their own thread, but script functions belong to the server thread: run the body there. It
     * only touches the scenario's own world.
     */
    private static Consumer<SimScenario> onServerThread(Consumer<SimScenario> body) {
        return scenario -> {
            SimScenario s = new ScriptScenario(scenario);
            var server = VillagerSimApi.server().orElseThrow(() -> new IllegalStateException("The sim isn't running")).server();
            if (server.isSameThread()) {
                body.accept(s);
                return;
            }
            try {
                server.submit(() -> body.accept(s)).get(5, TimeUnit.MINUTES);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                throw cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
            } catch (Exception e) {
                throw new IllegalStateException("Scenario script didn't finish: " + e, e);
            }
        };
    }
}
