package com.ewitulsk.villagersimulator.compat.kubejs;

import com.ewitulsk.villagersimulator.api.mod.VillagerSimApi;
import com.ewitulsk.villagersimulator.api.mod.event.RegisterScenariosEvent;
import com.ewitulsk.villagersimulator.api.mod.event.SimRecordEvent;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * KubeJS integration v1 (docs/ARCHITECTURE.md §10). KubeJS loads this class through {@code kubejs.plugins.txt} only
 * when it is installed; nothing else in the mod refers to KubeJS.
 * <ul>
 *   <li><b>Events out:</b> {@code VillagerSimEvents.recorded(e => ...)} for every event-log record the sim writes,
 *       delivered on the server thread through the outbox ({@link SimRecordEvent}).</li>
 *   <li><b>Commands back and view reads:</b> the {@code VillagerSim} binding ({@link VillagerSimBinding}).</li>
 *   <li><b>Scenarios:</b> {@code VillagerSimEvents.scenarios(e => e.add(name, description, s => ...))}, run with
 *       {@code /vs scenario run}.</li>
 * </ul>
 * ProbeJS picks the events and binding up for typings automatically.
 */
public final class VillagerSimKubeJSPlugin implements KubeJSPlugin {
    static final Logger LOG = LoggerFactory.getLogger("VillagerSim/KubeJS");
    /** Scenarios the server scripts added, re-collected on every script (re)load. */
    private static volatile List<ScenarioDefinition> scriptScenarios = List.of();
    private static volatile List<String> registeredNames = List.of();

    @Override
    public void init() {
        NeoForge.EVENT_BUS.addListener(SimRecordEvent.class, VillagerSimKubeJSPlugin::onRecord);
        // Scripts load before the sim starts, so hand their scenarios over when it does.
        ModList.get().getModContainerById("villagersimulator").ifPresent(c -> {
            var bus = c.getEventBus();
            if (bus != null) bus.addListener(RegisterScenariosEvent.class, e -> scriptScenarios.forEach(e::register));
        });
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(VillagerSimEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type().isServer()) bindings.add("VillagerSim", new VillagerSimBinding());
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow("com.ewitulsk.villagersimulator.api");
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType != ScriptType.SERVER) return;
        ScenariosKubeEvent event = new ScenariosKubeEvent();
        if (VillagerSimEvents.SCENARIOS.hasListeners()) VillagerSimEvents.SCENARIOS.post(ScriptType.SERVER, event);
        List<ScenarioDefinition> scenarios = event.scenarios();
        scriptScenarios = scenarios;
        // After /reload, replace the previous scripts' scenarios in the running sim.
        VillagerSimApi.server().ifPresent(sim -> {
            registeredNames.forEach(sim.scenarios()::remove);
            scenarios.forEach(sim.scenarios()::register);
        });
        List<String> names = new ArrayList<>();
        for (ScenarioDefinition d : scenarios) names.add(d.name());
        registeredNames = List.copyOf(names);
        if (!scenarios.isEmpty()) LOG.info("KubeJS scripts added {} sim scenarios: {}", scenarios.size(), names);
    }

    private static void onRecord(SimRecordEvent event) {
        if (!VillagerSimEvents.RECORDED.hasListeners()) return;
        VillagerSimEvents.RECORDED.post(ScriptType.SERVER, new SimRecordKubeEvent(event));
    }
}
