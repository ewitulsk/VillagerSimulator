package com.ewitulsk.villagersimulator.api.sim.module;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.event.EventHandler;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.logic.LogicFactories;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;
import com.ewitulsk.villagersimulator.api.sim.view.ViewProvider;

/** What a module can register. Everything is registered before the sim starts and is fixed afterwards. */
public interface SimRegistrar {
    void component(DenseComponent component);

    void component(SparseComponent<?> component);

    void task(TaskType type);

    <T> void registry(RegistryKey<T> key);

    void activity(Activity activity);

    <E extends SimEvent> void subscribe(Class<E> type, EventHandler<E> handler);

    <T> void view(ViewKey<T> key, ViewProvider<T> provider);

    /** A function usable in every expression (docs/ARCHITECTURE.md §9.3). */
    void function(ExpressionFunction function);

    /** A condition type for data, e.g. {@code {"type": "mymod:is_raining"}}. */
    void condition(Id type, LogicFactories.ConditionFactory factory);

    /** An effect type for data, e.g. {@code {"type": "mymod:give_gold", "amount": 3}}. */
    void effect(Id type, LogicFactories.EffectFactory factory);

    void stat(StatType stat);

    /**
     * Checks data once the sim is built and after every data reload, e.g. that every expression in a definition
     * compiles. Problems are reported to the log and to {@code /vs} instead of crashing the server.
     */
    void validator(Validator validator);
}
