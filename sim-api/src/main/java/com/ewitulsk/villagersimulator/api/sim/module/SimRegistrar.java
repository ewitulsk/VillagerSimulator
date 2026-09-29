package com.ewitulsk.villagersimulator.api.sim.module;

import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.event.EventHandler;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
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
}
