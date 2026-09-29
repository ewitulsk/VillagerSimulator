package com.ewitulsk.villagersimulator.api.sim;

import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.event.EventLog;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.logic.Logic;
import com.ewitulsk.villagersimulator.api.sim.module.ExtensionPoint;
import com.ewitulsk.villagersimulator.api.sim.social.Relationships;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.api.sim.stat.Stats;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * The sim as seen by modules, commands and queries.
 *
 * <p><b>Shards</b> (docs/ARCHITECTURE.md §6.3): entities belong to shards (in practice, one per village; shard 0 is the
 * world: players and anything unassigned). Between window boundaries shards run in parallel. A task handler may
 * freely read and write entities of its own shard; writes to other shards' entities are deferred and applied, in a
 * fixed order, at the next boundary. Reading another shard's mutable state mid-window is not deterministic, so
 * don't. Entities are created, destroyed and moved between shards only by commands, which run at boundaries.
 */
public interface SimContext {
    /** Current sim time in ticks. Monotonic. */
    long now();

    // --- entities ---

    EntityId create();

    /** Destroys the entity and removes all its components. Pending tasks targeting it still run; check {@link #alive}. */
    void destroy(EntityId entity);

    boolean alive(EntityId entity);

    // --- shards ---

    /** Creates a new shard (commands only). */
    int newShard();

    /** Moves an entity (and its pending tasks) to a shard (commands only). */
    void setShard(EntityId entity, int shard);

    int shardOf(EntityId entity);

    // --- dense components ---

    /** Adds the component with all fields zero. No-op if present. */
    void add(EntityId entity, DenseComponent component);

    boolean has(EntityId entity, DenseComponent component);

    void remove(EntityId entity, DenseComponent component);

    float get(EntityId entity, FloatField field);

    void set(EntityId entity, FloatField field, float value);

    long get(EntityId entity, LongField field);

    void set(EntityId entity, LongField field, long value);

    int get(EntityId entity, IntField field);

    void set(EntityId entity, IntField field, int value);

    /** Entities with the component, in ascending index order. */
    List<EntityId> with(DenseComponent component);

    // --- sparse components ---

    /** @return the value, or {@code null} if absent */
    <T> T get(EntityId entity, SparseComponent<T> component);

    <T> void set(EntityId entity, SparseComponent<T> component, T value);

    boolean has(EntityId entity, SparseComponent<?> component);

    void remove(EntityId entity, SparseComponent<?> component);

    /** Visits entities with the component in ascending index order. */
    <T> void forEach(SparseComponent<T> component, BiConsumer<EntityId, T> action);

    // --- time & events ---

    /** Schedules a task at {@code time} (must be {@code >= now()}). */
    void schedule(long time, TaskType type, EntityId target, long arg);

    /** Publishes an event to subscribers, synchronously. */
    void publish(SimEvent event);

    EventLog events();

    // --- registries ---

    <T> SimRegistry<T> registry(RegistryKey<T> key);

    /** @throws IllegalArgumentException if no module registered {@code id} */
    Activity activity(Id id);

    // --- data-driven logic ---

    Logic logic();

    Stats stats();

    /** The relationship graph. */
    Relationships relationships();

    /** Values registered for an extension point, in registration order. */
    <T> List<T> extensions(ExtensionPoint<T> point);
}
